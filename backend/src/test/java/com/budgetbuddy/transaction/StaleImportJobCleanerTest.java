package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.budgetbuddy.support.ThreadScopedLogAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * Unit-Test der Bereinigung verwaister Import-Jobs (BE-PDF-11, #197).
 *
 * <p>Geprüft wird hier die <em>Entscheidung</em> — welche Jobs die Schranke fängt, welche nicht,
 * und was ins Log geht. Dass die abgeleitete Query {@code findByStatusAndCreatedAtBefore} in
 * PostgreSQL überhaupt auflösbar ist, kann ein Mock grundsätzlich nicht zeigen: Er bestätigt nur
 * den erfundenen Methodennamen. Diesen Teil deckt {@link StaleImportJobCleanerIntegrationTest} ab.
 *
 * <p>Die Uhr ist fest ({@link #NOW}), die Schranke damit exakt bekannt — der Grenzfall lässt sich
 * so auf die Sekunde prüfen statt ungefähr.
 */
class StaleImportJobCleanerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final long CATEGORIZATION_TIMEOUT_SECONDS = 300L;
    private static final long RESERVE_SECONDS = 300L;

    /** Alles vor diesem Zeitpunkt gilt als verwaist: NOW − (300 s + 300 s). */
    private static final Instant CUTOFF =
            NOW.minus(Duration.ofSeconds(CATEGORIZATION_TIMEOUT_SECONDS + RESERVE_SECONDS));

    private final ImportJobRepository repository = mock(ImportJobRepository.class);

    private final StaleImportJobCleaner cleaner = new StaleImportJobCleaner(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofSeconds(CATEGORIZATION_TIMEOUT_SECONDS),
            Duration.ofSeconds(RESERVE_SECONDS),
            true);

    private final Logger observedLogger =
            (Logger) LoggerFactory.getLogger(StaleImportJobCleaner.class);
    private ThreadScopedLogAppender appender;

    @BeforeEach
    void attachAppender() {
        // ThreadScopedLogAppender statt ListAppender (BE-CAT-07): Der Logger ist prozessweit
        // geteilt, ein nackter Appender sähe auch Zeilen fremder Threads aus anderen Testklassen.
        appender = new ThreadScopedLogAppender();
        appender.start();
        observedLogger.setLevel(Level.INFO);
        observedLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        observedLogger.detachAppender(appender);
        observedLogger.setLevel(null);
    }

    /** AC1: Ein Job jenseits der Schranke kann nicht mehr laufen und wird bereinigt. */
    @Test
    void setsStaleRunningJobToFailed() {
        ImportJob stale = runningJobCreatedAt(CUTOFF.minusSeconds(1));
        whenRepositoryReturns(stale);

        int cleaned = cleaner.cleanUpStaleJobs();

        assertThat(cleaned).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(stale.getFinishedAt()).isEqualTo(NOW);
        verify(repository).saveAll(List.of(stale));
    }

    /**
     * AC2: Ein junger Job bleibt unangetastet.
     *
     * <p>Der Nachweis sitzt an der Abfrage, nicht am Ergebnis: Der Cleaner fragt gar nicht erst
     * nach Jobs jenseits der Schranke. Deshalb wird hier die übergebene Schranke geprüft — käme
     * ein Vorzeichenfehler in die Rechnung, fiele genau das hier auf und nicht am Mock-Rückgabewert.
     */
    @Test
    void asksOnlyForJobsOlderThanTheCutoff() {
        when(repository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        int cleaned = cleaner.cleanUpStaleJobs();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository)
                .findByStatusAndCreatedAtBefore(eq(ImportJobStatus.RUNNING), cutoff.capture());

        assertThat(cutoff.getValue()).isEqualTo(CUTOFF);
        // Ein Job, der eine Sekunde jünger ist als die Schranke, liegt damit ausserhalb.
        assertThat(CUTOFF.plusSeconds(1)).isAfter(cutoff.getValue());
        assertThat(cleaned).isZero();
    }

    /**
     * Grenzfall: ein Job exakt auf der Schranke.
     *
     * <p>{@code findByStatusAndCreatedAtBefore} ist echt kleiner — der Job auf der Sekunde genau
     * bleibt also stehen. Das ist die sichere Richtung: Im Zweifel lieber einen toten Job eine
     * Runde länger stehen lassen als einen lebenden abräumen (AC2 wiegt schwerer als AC1).
     */
    @Test
    void treatsTheCutoffItselfAsStillRunning() {
        when(repository.findByStatusAndCreatedAtBefore(ImportJobStatus.RUNNING, CUTOFF))
                .thenReturn(List.of());

        assertThat(cleaner.cleanUpStaleJobs()).isZero();
        verify(repository, never()).saveAll(anyList());
    }

    /** AC3: Die Logzeile nennt die Anzahl — und die IDs, ohne die sie nichts hergibt. */
    @Test
    void logsHowManyJobsWereCleanedUp() {
        ImportJob first = runningJobCreatedAt(CUTOFF.minusSeconds(60));
        ImportJob second = runningJobCreatedAt(CUTOFF.minusSeconds(90));
        whenRepositoryReturns(first, second);

        cleaner.cleanUpStaleJobs();

        assertThat(warnMessages()).anySatisfy(message ->
                assertThat(message).contains("2 verwaiste(r) Import-Job(s)"));
    }

    /**
     * Kein Fund, keine Zeile.
     *
     * <p>Der Normalfall im Betrieb ist, dass nichts zu tun ist. Eine WARN-Zeile bei jedem Lauf
     * alle 15 Minuten wäre Rauschen, das die echten Fälle zudeckt.
     */
    @Test
    void staysSilentWhenNothingIsStale() {
        when(repository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        assertThat(cleaner.cleanUpStaleJobs()).isZero();

        assertThat(appender.list).isEmpty();
        verify(repository, never()).saveAll(anyList());
    }

    /**
     * Ein DB-Fehler beim Start bleibt folgenlos für den Start.
     *
     * <p>Die Bereinigung ist Aufräumarbeit. Eine Datenbank, die im Moment des Hochfahrens klemmt,
     * darf die Anwendung nicht am Starten hindern — ohne dieses Verhalten wäre aus einem
     * kosmetischen Problem ein Verfügbarkeitsproblem geworden.
     */
    @Test
    void startupCleanupNeverPropagatesFailures() {
        when(repository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenThrow(new IllegalStateException("DB nicht erreichbar"));

        cleaner.onApplicationReady();

        assertThat(warnMessages()).anySatisfy(message ->
                assertThat(message).contains("beim Start fehlgeschlagen"));
    }

    /**
     * Der periodische Lauf schluckt dagegen nichts: Springs Scheduler loggt die Exception und
     * ruft beim nächsten Intervall erneut auf. Ein eigener Catch verstümmelte nur den Stacktrace.
     */
    @Test
    void scheduledCleanupLetsFailuresReachTheScheduler() {
        when(repository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenThrow(new IllegalStateException("DB nicht erreichbar"));

        assertThatThrownBy(cleaner::cleanUpStaleJobsPeriodically)
                .isInstanceOf(IllegalStateException.class);
    }

    // --- cleanUpIfStale: der Pfad, den der Upload nimmt (BE-PDF-11) ---

    /**
     * Der Kern des Lazy-Cleanups: Ein verwaister Job wird beim Upload abgeräumt, statt den
     * erneuten Import derselben Datei mit 409 zu sperren.
     */
    @Test
    void cleanUpIfStale_failsAnOrphanedJobAndReportsIt() {
        ImportJob stale = runningJobCreatedAt(CUTOFF.minusSeconds(1));

        assertThat(cleaner.cleanUpIfStale(stale)).isTrue();

        assertThat(stale.getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(stale.getFinishedAt()).isEqualTo(NOW);
        verify(repository).save(stale);
    }

    /** Ein Job, der noch laufen kann, sperrt den Upload zu Recht — und bleibt unberührt. */
    @Test
    void cleanUpIfStale_leavesARunningJobAlone() {
        ImportJob fresh = runningJobCreatedAt(CUTOFF.plusSeconds(1));

        assertThat(cleaner.cleanUpIfStale(fresh)).isFalse();

        assertThat(fresh.getStatus()).isEqualTo(ImportJobStatus.RUNNING);
        verify(repository, never()).save(any(ImportJob.class));
    }

    /**
     * Ein bereits abgeschlossener Job ist kein Fall für die Bereinigung, egal wie alt er ist.
     * Ohne die Statusprüfung setzte dieser Pfad ein {@code DONE} auf {@code FAILED} zurück und
     * behauptete damit, ein erfolgreicher Import sei gescheitert.
     */
    @Test
    void cleanUpIfStale_neverTouchesAFinishedJob() {
        ImportJob done = runningJobCreatedAt(CUTOFF.minusSeconds(3600));
        done.finishSuccessfully(false, NOW);

        assertThat(cleaner.cleanUpIfStale(done)).isFalse();

        assertThat(done.getStatus()).isEqualTo(ImportJobStatus.DONE);
        verify(repository, never()).save(any(ImportJob.class));
    }

    // --- Der Schalter gatet die automatischen Läufe, nicht den Upload-Pfad ---

    /**
     * {@code stale-job-cleanup.enabled=false} stellt Start- und periodischen Lauf ruhig — das ist
     * die Einstellung, die {@code pom.xml} für die gesamte Testausführung setzt.
     */
    @Test
    void disabled_silencesBothAutomaticTriggers() {
        StaleImportJobCleaner disabled = cleanerWithAutomaticCleanup(false);

        disabled.onApplicationReady();
        disabled.cleanUpStaleJobsPeriodically();

        verifyNoInteractions(repository);
    }

    /**
     * Derselbe Schalter darf den Upload-Pfad <strong>nicht</strong> treffen.
     *
     * <p>Sonst verhielte sich der Duplikatcheck im Test anders als in Produktion, und zwar genau
     * in dem Punkt, den der Nutzer bemerkt. Der Schalter existiert, damit Testkontexte ohne
     * Flyway-Schema keine ERROR-Zeilen produzieren — nicht, um Verhalten abzuschalten.
     */
    @Test
    void disabled_stillCleansUpOnTheUploadPath() {
        StaleImportJobCleaner disabled = cleanerWithAutomaticCleanup(false);
        ImportJob stale = runningJobCreatedAt(CUTOFF.minusSeconds(1));

        assertThat(disabled.cleanUpIfStale(stale)).isTrue();

        assertThat(stale.getStatus()).isEqualTo(ImportJobStatus.FAILED);
        verify(repository).save(stale);
    }

    private StaleImportJobCleaner cleanerWithAutomaticCleanup(boolean enabled) {
        return new StaleImportJobCleaner(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(CATEGORIZATION_TIMEOUT_SECONDS),
                Duration.ofSeconds(RESERVE_SECONDS),
                enabled);
    }

    private void whenRepositoryReturns(ImportJob... jobs) {
        when(repository.findByStatusAndCreatedAtBefore(ImportJobStatus.RUNNING, CUTOFF))
                .thenReturn(List.of(jobs));
    }

    private static ImportJob runningJobCreatedAt(Instant createdAt) {
        return new ImportJob(42L, "abc123", 20, createdAt);
    }

    private List<String> warnMessages() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
