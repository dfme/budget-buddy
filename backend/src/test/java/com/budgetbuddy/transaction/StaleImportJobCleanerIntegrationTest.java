package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest der Bereinigung verwaister Import-Jobs (BE-PDF-11, #197) gegen echtes
 * PostgreSQL + Flyway.
 *
 * <p><strong>Warum zusätzlich zum Unit-Test:</strong> {@code findByStatusAndCreatedAtBefore} ist
 * eine <em>abgeleitete</em> Query — Spring Data baut sie aus dem Methodennamen. Ein Tippfehler oder
 * ein Feldname, den es am {@link ImportJob} nicht gibt, fällt deshalb weder beim Kompilieren auf
 * noch gegen einen Mock, der den erfundenen Namen bereitwillig bestätigt: Er fällt beim Aufbau des
 * Kontexts auf, und damit nur hier. Dasselbe gilt für das {@code TIMESTAMPTZ}-Mapping der
 * {@code created_at}-Spalte, an dem der ganze Vergleich hängt.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StaleImportJobCleanerIntegrationTest {

    private static final long TIMEOUT_SECONDS = 300L;
    private static final long RESERVE_SECONDS = 300L;

    /** Ein Job älter als 600 s gilt als verwaist. */
    private static final Duration STALE_AFTER =
            Duration.ofSeconds(TIMEOUT_SECONDS + RESERVE_SECONDS);

    private static final String SHA = "aaaa1111bbbb2222cccc3333dddd4444";

    /**
     * Die Schranke wird hier ausdrücklich gesetzt statt aus {@code application.properties} geerbt.
     * Der Test soll seine eigene Annahme mitbringen: Sonst verschöbe eine spätere Änderung an den
     * Produktions-Defaults still die Grenze, gegen die er prüft.
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "stale_import_jobs");
        registry.add("budgetbuddy.import.categorization-timeout", () -> TIMEOUT_SECONDS + "s");
        registry.add("budgetbuddy.import.stale-job-reserve", () -> RESERVE_SECONDS + "s");
        // pom.xml schaltet den Cleaner für die gesamte Testausführung ab (Begründung dort). Diese
        // Klasse ist die eine, die ihn als Bean braucht, und holt ihn sich deshalb zurück.
        registry.add("budgetbuddy.import.stale-job-cleanup.enabled", () -> true);
    }

    @Autowired
    private StaleImportJobCleaner cleaner;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;

    private long otherUserId;

    @BeforeEach
    void seed() {
        importJobRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "lara@example.ch", "bcrypt-hash", new BigDecimal("4200.00"), true);
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "marc@example.ch", "bcrypt-hash", new BigDecimal("6100.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'lara@example.ch'", Long.class);
        otherUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'marc@example.ch'", Long.class);
    }

    /** AC1 und AC2 in einem Lauf: Der alte Job fällt, der junge bleibt. */
    @Test
    void cleansUpOnlyTheStaleJob() {
        Instant now = Instant.now();
        ImportJob stale = runningJobCreatedAt(now.minus(STALE_AFTER).minusSeconds(60));
        ImportJob fresh = runningJobCreatedAt(now.minusSeconds(10));

        int cleaned = cleaner.cleanUpStaleJobs();

        assertThat(cleaned).isEqualTo(1);
        assertThat(statusOf(stale)).isEqualTo(ImportJobStatus.FAILED);
        assertThat(statusOf(fresh)).isEqualTo(ImportJobStatus.RUNNING);
    }

    /**
     * {@code finished_at} wird mitgeschrieben.
     *
     * <p>Ohne diesen Zeitstempel wäre ein bereinigter Job in der Tabelle nicht von einem
     * regulär gescheiterten zu unterscheiden — und jede Auswertung über die Laufdauer eines
     * Imports stiesse auf eine {@code FAILED}-Zeile ohne Ende.
     */
    @Test
    void recordsWhenTheJobWasGivenUpOn() {
        ImportJob stale = runningJobCreatedAt(Instant.now().minus(STALE_AFTER).minusSeconds(60));

        cleaner.cleanUpStaleJobs();

        assertThat(reload(stale).getFinishedAt()).isNotNull();
    }

    /**
     * Der eigentliche Nutzerschaden: Die Bereinigung gibt den Upload derselben Datei wieder frei.
     *
     * <p>{@code PdfImportService.isDuplicate} fragt neben {@code transactions} auch nach einem
     * <em>laufenden</em> Job desselben PDFs. Eine verwaiste {@code RUNNING}-Zeile beantwortet
     * damit jeden erneuten Upload dieser Datei mit 409 — also genau den Versuch, mit dem ein
     * Nutzer auf einen abgebrochenen Import reagiert. Ohne diesen Test bliebe die Bereinigung eine
     * Aussage über eine Tabellenspalte; mit ihm ist sie eine über den Nutzer.
     */
    @Test
    void unblocksReimportOfTheSameFile() {
        runningJobCreatedAt(Instant.now().minus(STALE_AFTER).minusSeconds(60));

        assertThat(isBlockedAsDuplicate()).isTrue();

        cleaner.cleanUpStaleJobs();

        assertThat(isBlockedAsDuplicate()).isFalse();
    }

    /**
     * Derselbe Nutzerschaden am echten Upload-Pfad, ohne auf einen periodischen Lauf zu warten.
     *
     * <p>Das ist der Unterschied zwischen «die Tabelle stimmt irgendwann wieder» und «der Nutzer
     * kommt weiter»: Der Duplikatcheck räumt die verwaiste Zeile im Vorbeigehen ab, und zwar in
     * genau der Sekunde, in der sie ihn sonst mit 409 abgewiesen hätte.
     */
    @Test
    void uploadPathClearsTheOrphanItself() {
        ImportJob orphaned =
                runningJobCreatedAt(Instant.now().minus(STALE_AFTER).minusSeconds(60));

        assertThat(cleaner.cleanUpIfStale(orphaned)).isTrue();

        assertThat(statusOf(orphaned)).isEqualTo(ImportJobStatus.FAILED);
        assertThat(isBlockedAsDuplicate()).isFalse();
    }

    /** Abgeschlossene Jobs sind kein Fall für die Bereinigung, egal wie alt sie sind. */
    @Test
    void leavesFinishedJobsAlone() {
        ImportJob done = runningJobCreatedAt(Instant.now().minus(STALE_AFTER).minusSeconds(3600));
        done.finishSuccessfully(false, Instant.now());
        importJobRepository.save(done);

        assertThat(cleaner.cleanUpStaleJobs()).isZero();
        assertThat(statusOf(done)).isEqualTo(ImportJobStatus.DONE);
    }

    /**
     * AC1 und AC4 (BE-PDF-16): Der bereinigte Job erzeugt genau eine {@code IMPORT_FAILED}-Zeile,
     * beim richtigen User und mit der Job-ID als {@code reference_id}.
     *
     * <p>Diesen Nachweis kann {@link StaleImportJobCleanerTest} nicht führen: Dort ist der Job nie
     * persistiert, {@code getId()} also {@code null} — ein {@code verify(..., eq(job.getId()))}
     * bestätigt dann nur, dass zweimal {@code null} dasselbe ist. Erst hier steht eine echte ID
     * dahinter, und erst hier ist belegt, dass der {@code NotificationPort} über den Kontext
     * tatsächlich verdrahtet ist und die Zeile schreibt.
     *
     * <p>Der zweite Job gehört bewusst einem anderen User: Ein Aufruf, der die User-ID nicht aus
     * dem jeweiligen Job zöge, schickte Laras Fehlschlag an Marc — mit nur einem User wäre das
     * nicht zu sehen.
     */
    @Test
    void notifiesEachOwnerAboutTheirOwnCleanedUpJob() {
        Instant longAgo = Instant.now().minus(STALE_AFTER).minusSeconds(60);
        ImportJob laras = runningJobCreatedAt(longAgo);
        ImportJob marcs = importJobRepository.save(
                new ImportJob(otherUserId, "ffff9999eeee8888dddd7777cccc6666", 20, longAgo));

        assertThat(cleaner.cleanUpStaleJobs()).isEqualTo(2);

        assertThat(failureNotifications()).containsExactlyInAnyOrder(
                Map.entry(userId, laras.getId()), Map.entry(otherUserId, marcs.getId()));
    }

    /** Derselbe Nachweis am Upload-Pfad — eine Zeile, für den Besitzer des verwaisten Jobs. */
    @Test
    void uploadPathNotifiesTheOwnerToo() {
        ImportJob orphaned =
                runningJobCreatedAt(Instant.now().minus(STALE_AFTER).minusSeconds(60));

        assertThat(cleaner.cleanUpIfStale(orphaned)).isTrue();

        assertThat(failureNotifications())
                .containsExactly(Map.entry(userId, orphaned.getId()));
    }

    /** Ein Job, der noch laufen kann, erzeugt keine Benachrichtigung. */
    @Test
    void notifiesNobodyAboutAJobThatMayStillBeRunning() {
        runningJobCreatedAt(Instant.now().minusSeconds(10));

        assertThat(cleaner.cleanUpStaleJobs()).isZero();

        assertThat(failureNotifications()).isEmpty();
    }

    /** {@code (user_id, reference_id)} aller {@code IMPORT_FAILED}-Zeilen in der Tabelle. */
    private List<Map.Entry<Long, Long>> failureNotifications() {
        return jdbcTemplate.query(
                "SELECT user_id, reference_id FROM notifications WHERE type = ?",
                (rs, rowNum) -> Map.entry(rs.getLong("user_id"), rs.getLong("reference_id")),
                ImportFailureNotifier.NOTIFICATION_TYPE_FAILED);
    }

    private boolean isBlockedAsDuplicate() {
        return !importJobRepository
                .findByUserIdAndPdfSha256AndStatus(userId, SHA, ImportJobStatus.RUNNING)
                .isEmpty();
    }

    private ImportJob runningJobCreatedAt(Instant createdAt) {
        return importJobRepository.save(new ImportJob(userId, SHA, 20, createdAt));
    }

    private ImportJobStatus statusOf(ImportJob job) {
        return reload(job).getStatus();
    }

    /** Liest den Job frisch aus der Datenbank — nicht aus dem Persistence-Context der Testmethode. */
    private ImportJob reload(ImportJob job) {
        return importJobRepository.findByIdAndUserId(job.getId(), userId).orElseThrow();
    }
}
