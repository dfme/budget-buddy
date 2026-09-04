package com.budgetbuddy.transaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Zeitbudget des {@link ImportJobRunner} ({@code budgetbuddy.import.categorization-timeout})
 * plus eine Reserve ({@code budgetbuddy.import.stale-job-reserve}). Die Reserve deckt ab,
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
 * <h2>Dritter Auslöser: der Upload selbst</h2>
 *
 * <p>{@link #cleanUpIfStale(ImportJob)} räumt einen einzelnen Job ab und wird vom Duplikatcheck in
 * {@code PdfImportService} gerufen. Das ist der Auslöser, der den Nutzer tatsächlich erreicht: Er
 * greift genau in dem Moment, in dem die verwaiste Zeile weh tut — beim erneuten Upload derselben
 * Datei — und kostet dabei <strong>keinen einzigen zusätzlichen Weckvorgang</strong>, weil der
 * Duplikatcheck ohnehin läuft und die Datenbank in diesem Moment ohnehin wach ist.
 *
 * <h2>Die automatischen Läufe sind abschaltbar</h2>
 *
 * <p>{@code budgetbuddy.import.stale-job-cleanup.enabled} (Default {@code true}) gatet
 * <strong>nur die beiden automatischen Auslöser</strong>, nicht die Klasse und nicht
 * {@link #cleanUpIfStale(ImportJob)}. {@code pom.xml} setzt ihn für die gesamte Testausführung auf
 * {@code false} — derselbe Grund wie beim {@code AnthropicStartupHealthCheck}: Ein gutes Dutzend
 * Testkontexte registriert seine Datenbank bewusst über
 * {@code PostgresTestDatabase.registerWithoutFlyway}, dort existiert {@code import_jobs} gar nicht,
 * und der Startlauf hinterliess in jedem davon eine ERROR-Zeile von Hibernate. Das Verhalten war
 * korrekt, die Meldung nicht: Sie beschrieb kein Problem, und Log-Rauschen auf ERROR-Niveau erzieht
 * dazu, ERROR zu überlesen.
 *
 * <p>Der Schalter war zuerst ein {@code @ConditionalOnProperty} an der Klasse. Das ging nicht mehr,
 * als {@code PdfImportService} diese Bean für den Upload-Pfad brauchte: Ohne Bean scheiterte jeder
 * Testkontext, der den Service baut. Ein Schalter, der eine Komponente <em>verschwinden</em> lässt,
 * ist eben etwas anderes als einer, der sie ruhigstellt — gemeint war immer das Zweite.
 */
@Component
public class StaleImportJobCleaner {

    private static final Logger log = LoggerFactory.getLogger(StaleImportJobCleaner.class);

    private final ImportJobRepository importJobRepository;
    private final Clock clock;

    /**
     * Zeitbudget des Kategorisierungslaufs plus Reserve — zusammen die Schranke, ab der ein
     * {@code RUNNING}-Job als verwaist gilt. Als Summe vorgehalten, weil nur sie je gebraucht wird.
     */
    private final Duration staleAfter;

    /** Gatet die beiden automatischen Auslöser — nie {@link #cleanUpIfStale(ImportJob)}. */
    private final boolean automaticCleanupEnabled;

    public StaleImportJobCleaner(
            ImportJobRepository importJobRepository,
            Clock clock,
            @Value("${budgetbuddy.import.categorization-timeout:300s}")
                    Duration categorizationTimeout,
            @Value("${budgetbuddy.import.stale-job-reserve:300s}") Duration staleJobReserve,
            @Value("${budgetbuddy.import.stale-job-cleanup.enabled:true}")
                    boolean automaticCleanupEnabled) {
        this.importJobRepository = importJobRepository;
        this.clock = clock;
        this.staleAfter = categorizationTimeout.plus(staleJobReserve);
        this.automaticCleanupEnabled = automaticCleanupEnabled;
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
        if (!automaticCleanupEnabled) {
            return;
        }
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
     * <p><strong>Bewusst selten (alle 6 h).</strong> Jeder Lauf weckt Neons Compute für eine
     * triviale Abfrage, und danach bleibt er noch rund fünf Minuten wach, bevor Scale-to-Zero
     * wieder greift. Ein 15-Minuten-Takt hielte die Datenbank damit etwa ein Drittel des Tages
     * wach — rund 60 der 100 CU-h des Monatskontingents, für eine Abfrage, die fast immer nichts
     * findet. Genau dieses Muster hat INFRA-28 schon einmal gekostet, dort über den Health-Check.
     *
     * <p>Der Preis der Seltenheit ist gering, weil dieser Lauf ohnehin nur ein Restfall ist:
     * Stirbt der Prozess, räumt der Startlauf beim Neustart sofort auf; und der Upload-Pfad über
     * {@link #cleanUpIfStale(ImportJob)} löst die für den Nutzer spürbare Sperre sofort. Übrig
     * bleibt der Job, der verwaist, während der Prozess weiterlebt — ein {@link Error}, bei dem
     * auch noch der FAILED-Write scheitert.
     *
     * <p>Kein eigenes {@code try/catch}: Springs Scheduler loggt eine geworfene Exception und ruft
     * beim nächsten Intervall erneut auf — anders als beim Start gibt es hier nichts zu schützen.
     */
    @Scheduled(
            fixedDelayString = "${budgetbuddy.import.stale-job-scan-interval:6h}",
            initialDelayString = "${budgetbuddy.import.stale-job-scan-interval:6h}")
    public void cleanUpStaleJobsPeriodically() {
        if (!automaticCleanupEnabled) {
            return;
        }
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

    /**
     * Räumt <em>einen</em> Job ab, falls er verwaist ist — der Pfad, den der Duplikatcheck beim
     * Upload nimmt ({@code PdfImportService.isDuplicate}).
     *
     * <p>Ohne ihn sperrte eine verwaiste {@code RUNNING}-Zeile den erneuten Upload genau der Datei,
     * deren Import abgebrochen ist, mit 409 — bis zum nächsten automatischen Lauf. Das ist die
     * einzige Auswirkung, die ein Nutzer überhaupt bemerkt, und hier wird sie in dem Moment
     * aufgelöst, in dem sie entsteht. Dass das nichts kostet, ist der eigentliche Witz daran: Der
     * Duplikatcheck fragt diese Zeile ohnehin ab, die Datenbank ist also wach, und es kommt kein
     * einziger zusätzlicher Weckvorgang dazu.
     *
     * <p>Bewusst <strong>nicht</strong> vom Schalter {@code stale-job-cleanup.enabled} gegatet: Der
     * stellt die automatischen Läufe ruhig, damit Testkontexte ohne Schema keine ERROR-Zeilen
     * produzieren. Dieser Pfad läuft dagegen innerhalb eines echten Requests, in dem das Schema
     * garantiert existiert — ihn mit abzuschalten hiesse, das Verhalten im Test von dem in
     * Produktion abweichen zu lassen, und zwar genau im Punkt, der den Nutzer betrifft.
     *
     * @param job ein Job, den der Aufrufer bereits geladen hat.
     * @return {@code true}, wenn er verwaist war und jetzt auf {@code FAILED} steht; {@code false},
     *     wenn er noch laufen kann und den Upload zu Recht sperrt.
     */
    public boolean cleanUpIfStale(ImportJob job) {
        Instant now = clock.instant();
        if (job.getStatus() != ImportJobStatus.RUNNING || !isStale(job, now)) {
            return false;
        }

        job.fail(now);
        importJobRepository.save(job);
        log.warn("Import-Job {} beim Upload als verwaist erkannt und auf FAILED gesetzt — älter "
                        + "als {}s. Der erneute Import derselben Datei ist damit wieder möglich.",
                job.getId(), staleAfter.toSeconds());
        return true;
    }

    /**
     * Kann dieser Job unmöglich noch laufen?
     *
     * <p>Echt kleiner, nicht kleiner-gleich — dieselbe Grenze wie
     * {@code findByStatusAndCreatedAtBefore}, damit beide Wege denselben Job gleich beurteilen.
     * Im Zweifel bleibt ein toter Job eine Runde länger stehen, statt dass ein lebender abgeräumt
     * wird.
     */
    private boolean isStale(ImportJob job, Instant now) {
        return job.getCreatedAt().isBefore(now.minus(staleAfter));
    }
}
