package com.budgetbuddy.transaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Räumt verwaiste Import-Jobs ab (BE-PDF-11, #197).
 *
 * <p><strong>Das Problem:</strong> Stirbt die JVM während eines laufenden Kategorisierungslaufs,
 * bleibt die Zeile in {@code import_jobs} auf {@link ImportJobStatus#RUNNING} stehen — der Prozess,
 * der sie hätte abschliessen sollen, existiert nicht mehr. Vor dieser Komponente gab es keinen
 * Mechanismus, der sie danach noch bewegte.
 *
 * <p>Der Fall ist nicht theoretisch: Jeder Merge auf {@code main} deployt und startet die
 * Render-Instanz neu. {@code AsyncConfig} wartet zwar mit
 * {@code waitForTasksToCompleteOnShutdown(true)} und 60 Sekunden auf den Abschluss, Renders
 * Grace-Period vor dem {@code SIGKILL} ist aber kürzer.
 *
 * <p><strong>Der Schaden ist nicht bloss kosmetisch.</strong> Der Duplikatcheck beim Upload fragt
 * neben {@code transactions} auch {@code import_jobs} nach einem <em>laufenden</em> Import
 * derselben Datei ({@code PdfImportService.isDuplicate}). Eine verwaiste {@code RUNNING}-Zeile
 * sperrt damit den erneuten Import genau der Datei, deren Import abgebrochen ist — mit 409, und
 * ohne diese Bereinigung bis zum nächsten Deploy. Der naheliegende Selbsthilfe-Versuch des
 * Nutzers, dieselbe Datei nochmals hochzuladen, ist also genau der, der scheitert.
 *
 * <h2>Die Schranke</h2>
 *
 * <p>Bereinigt wird nur, was <em>nicht mehr laufen kann</em>: Jobs, die älter sind als das
 * Zeitbudget des {@link ImportJobRunner} ({@code budgetbuddy.import.categorization-timeout-seconds})
 * plus eine Reserve ({@code budgetbuddy.import.stale-job-reserve-seconds}). Die Reserve deckt ab,
 * was der Watchdog im Runner selbst offenlässt: Der Zeitcheck steht <em>zwischen</em> den Bündeln,
 * ein laufender Claude-Call wird nie unterbrochen. Die reale Obergrenze eines gesunden Jobs ist
 * deshalb Zeitbudget plus ein vollständiges Bündel, nicht das Zeitbudget allein.
 *
 * <p>Ein jüngerer Job bleibt unangetastet. Das ist kein Detail, sondern die Bedingung dafür, dass
 * diese Komponente bei mehr als einer Instanz sicher bleibt: Sonst räumte die eine beim Hochfahren
 * die laufenden Jobs der anderen ab und der Nutzer verlöre einen Import, der gerade sauber lief.
 *
 * <h2>Zwei Auslöser</h2>
 *
 * <p>Beim Start deckt {@link ApplicationReadyEvent} den Deploy-Fall ab — den häufigsten. Er genügt
 * aber nicht: {@code ImportJobRunner.run} fängt zwar seit BE-PDF-11 auch {@link Error}, doch wenn
 * schon das Schreiben des {@code FAILED}-Status daran scheitert (nach einem
 * {@link OutOfMemoryError} ein realistischer Ausgang), bleibt die Zeile wieder stehen — bis zum
 * nächsten Neustart. Der periodische Lauf schliesst diese Lücke im laufenden Betrieb und ist damit
 * die letzte Verteidigungslinie hinter dem Catch im Runner.
 *
 * <p>Beide Auslöser rufen dieselbe Methode; sie ist idempotent und tut bei nichts zu tun nichts.
 *
 * <h2>Abschaltbar über {@code budgetbuddy.import.stale-job-cleanup.enabled}</h2>
 *
 * <p>Default {@code true}; der Schalter existiert für die Testausführung, wo ihn {@code pom.xml}
 * global auf {@code false} setzt — dieselbe Konstruktion und derselbe Grund wie beim
 * {@code AnthropicStartupHealthCheck}. Diese Klasse ist nach ihm die zweite, die beim blossen
 * Hochfahren eines Kontexts von sich aus etwas tut, und ein Dutzend Testkontexte registrieren ihre
 * Datenbank bewusst über {@code PostgresTestDatabase.registerWithoutFlyway} — dort existiert
 * {@code import_jobs} gar nicht. Der Startlauf lief dann in sein eigenes {@code try/catch} und
 * hinterliess in jedem dieser Kontexte eine ERROR-Zeile von Hibernate. Das Verhalten war korrekt,
 * die Meldung aber nicht: Sie beschreibt kein Problem, und Log-Rauschen auf ERROR-Niveau erzieht
 * dazu, ERROR zu überlesen.
 *
 * <p>Bewusst ein Property und kein {@code @Profile}: Nur eine Handvoll Testklassen aktiviert das
 * {@code test}-Profil, die betroffenen gehören nicht dazu. Die Abdeckung leidet nicht — beide
 * Testebenen rufen {@link #cleanUpStaleJobs()} und {@link #onApplicationReady()} direkt auf und
 * brauchen dafür kein Startereignis.
 */
@Component
@ConditionalOnProperty(
        name = "budgetbuddy.import.stale-job-cleanup.enabled",
        matchIfMissing = true)
public class StaleImportJobCleaner {

    private static final Logger log = LoggerFactory.getLogger(StaleImportJobCleaner.class);

    private final ImportJobRepository importJobRepository;
    private final Clock clock;

    /**
     * Zeitbudget des Kategorisierungslaufs plus Reserve — zusammen die Schranke, ab der ein
     * {@code RUNNING}-Job als verwaist gilt. Als Summe vorgehalten, weil nur sie je gebraucht wird.
     */
    private final Duration staleAfter;

    public StaleImportJobCleaner(
            ImportJobRepository importJobRepository,
            Clock clock,
            @Value("${budgetbuddy.import.categorization-timeout-seconds:300}")
                    long categorizationTimeoutSeconds,
            @Value("${budgetbuddy.import.stale-job-reserve-seconds:300}")
                    long staleJobReserveSeconds) {
        this.importJobRepository = importJobRepository;
        this.clock = clock;
        this.staleAfter =
                Duration.ofSeconds(categorizationTimeoutSeconds + staleJobReserveSeconds);
    }

    /**
     * Bereinigt beim Hochfahren, sobald der Kontext steht.
     *
     * <p>Synchron — der Zustand soll bereinigt sein, bevor der erste Upload durch den Duplikatcheck
     * geht. Die Abfrage ist dabei ein Sequential Scan und kein Indexzugriff: Die Indizes aus
     * {@code V05} führen alle mit {@code user_id}, das hier gar nicht im Spiel ist. Vertretbar
     * bleibt der synchrone Aufruf, weil {@code import_jobs} eine Zeile pro Upload wächst und nicht
     * pro Transaktion; der passende Teilindex ist als DB-10 (#270) erfasst. Aber in
     * {@code try/catch}: Diese
     * Bereinigung ist Aufräumarbeit, kein Startvorbehalt. Eine Datenbank, die im Moment des
     * Hochfahrens klemmt, darf die Anwendung nicht am Starten hindern — der periodische Lauf holt
     * die Bereinigung ohnehin nach.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            cleanUpStaleJobs();
        } catch (RuntimeException e) {
            log.warn("Bereinigung verwaister Import-Jobs beim Start fehlgeschlagen ({}) — "
                            + "der periodische Lauf versucht es erneut.",
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Bereinigt im laufenden Betrieb.
     *
     * <p>{@code initialDelay} entspricht dem Intervall: Der Start ist bereits durch
     * {@link #onApplicationReady()} abgedeckt, ohne diese Verzögerung liefe die Bereinigung beim
     * Hochfahren zweimal.
     *
     * <p>Kein eigenes {@code try/catch}: Springs Scheduler loggt eine geworfene Exception und ruft
     * beim nächsten Intervall erneut auf — anders als beim Start gibt es hier nichts zu schützen.
     */
    @Scheduled(
            fixedDelayString = "${budgetbuddy.import.stale-job-scan-interval-seconds:900}",
            initialDelayString = "${budgetbuddy.import.stale-job-scan-interval-seconds:900}",
            timeUnit = TimeUnit.SECONDS)
    public void cleanUpStaleJobsPeriodically() {
        cleanUpStaleJobs();
    }

    /**
     * Setzt alle verwaisten {@code RUNNING}-Jobs auf {@link ImportJobStatus#FAILED}.
     *
     * <p>{@code FAILED} sagt hier die Wahrheit: Der {@link ImportJobRunner} schreibt die
     * Transaktionen erst in seinem Abschlussblock, in einem Zug. Ein Lauf, der vorher stirbt, hat
     * tatsächlich nichts persistiert — anders als beim {@code degraded}-Abschluss, der trotz
     * Watchdog vollständig speichert und deshalb {@link ImportJobStatus#DONE} bleibt.
     *
     * <p>Der Zustandswechsel geht bewusst über {@link ImportJob#fail(Instant)} und nicht über ein
     * Bulk-{@code UPDATE}: Was ein abgebrochener Job ist, steht damit weiterhin an genau einer
     * Stelle. Die Menge ist klein genug, dass das nichts kostet — es sind die Jobs eines einzelnen
     * abgestürzten Laufs, nicht eine Tabelle.
     *
     * @return Anzahl der bereinigten Jobs; {@code 0}, wenn nichts zu tun war.
     */
    public int cleanUpStaleJobs() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(staleAfter);

        List<ImportJob> stale = importJobRepository.findByStatusAndCreatedAtBefore(
                ImportJobStatus.RUNNING, cutoff);
        if (stale.isEmpty()) {
            return 0;
        }

        stale.forEach(job -> job.fail(now));
        importJobRepository.saveAll(stale);

        // WARN, nicht INFO (BE-PDF-06: auffällige Zustände gehören ins Log): Ein bereinigter Job
        // heisst, dass ein Import eines Nutzers abgebrochen ist. Die IDs stehen dabei, weil ohne
        // sie die Zeile nicht mehr hergibt als eine Zahl — sie sind der Anknüpfungspunkt für jede
        // Nachfrage. Bewusst NUR die IDs: kein Buchungstext, kein Betrag, keine E-Mail-Adresse
        // (Render-Logs haben eine andere Zugriffskontrolle als die Datenbank).
        log.warn("{} verwaiste(r) Import-Job(s) auf FAILED gesetzt — älter als {}s und damit seit "
                        + "einem Neustart oder Absturz ohne laufenden Prozess. Job-IDs: {}.",
                stale.size(), staleAfter.toSeconds(), stale.stream().map(ImportJob::getId).toList());

        return stale.size();
    }
}
