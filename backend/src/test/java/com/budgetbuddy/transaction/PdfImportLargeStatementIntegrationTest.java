package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.support.PostgresTestDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Die erste Acceptance Criteria von BE-PDF-09 (#192) in ihrer eigenen Grössenordnung: «Ein Auszug
 * mit ~110 Transaktionen wird vollständig importiert, ohne ins Zeitbudget zu laufen».
 *
 * <p>Genau ein solcher Auszug liess den alten, synchronen Flow zweimal innerhalb einer Stunde
 * scheitern — und zwar so, dass <em>keine einzige</em> Transaktion ankam. Die übrigen Fixtures (28
 * bzw. 12 Buchungen) hätten das nie gezeigt: Bei ihnen greift weder die Bündelung sichtbar noch
 * ein Zeitbudget.
 *
 * <p>Die Fixture stammt wie alle anderen aus {@code backend/tools/generate_pdf_fixtures.py} und
 * ist vollständig synthetisch (fiktiver Inhaber «Peter Muster», Beispiel-IBAN). Sie ist zugleich
 * die erste Fixture für den <strong>generischen Raiffeisen-Zweig</strong> des Parsers, der bisher
 * nur über handgebaute PDFs im Test abgedeckt war.
 *
 * <p>Was dieser Test <em>nicht</em> belegt: die reale Laufzeit gegen die Claude-API. Die
 * Kategorisierung ist gemockt (kein Key in CI, keine Kosten pro Lauf, keine
 * Netz-Reproduzierbarkeit). Belegt sind Struktur und Vollständigkeit — 110 Buchungen geparst, in
 * Bündeln verarbeitet, alle persistiert — plus die Zahl der Aufrufe, an der die Laufzeit hängt.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportLargeStatementIntegrationTest {

    /** Buchungen der Fixture — {@code RAIFFEISEN_COUNT} im Generator-Skript. */
    private static final int TRANSACTION_COUNT = 110;

    /** {@code budgetbuddy.import.batch-size}; hier gespiegelt, um die Erwartung auszurechnen. */
    private static final int BATCH_SIZE = 20;

    /** Gedruckte Totale der Fixture — Kreuzprobe gegen die Saldokette des Generators. */
    private static final String PRINTED_DEBITS = "12471.25";
    private static final String PRINTED_CREDITS = "20400.00";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "pdf_import_large_statement");
    }

    @Autowired private PdfImportService pdfImportService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ImportJobRepository importJobRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** Ersetzt die Hybrid-Kette (den {@code @Primary}-{@link CategorizationPort}) im Kontext. */
    @MockitoBean(name = "hybridCategorizationService")
    private CategorizationPort categorizationPort;

    private long userId;

    private static byte[] fixture() {
        try (InputStream in = PdfImportLargeStatementIntegrationTest.class
                .getResourceAsStream("/pdf/Raiffeisen_Kontoauszug_110_Buchungen.pdf")) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        // Vor den Usern: import_jobs.user_id ist ein Fremdschlüssel auf users (Flyway V05).
        importJobRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "lara@example.ch", "bcrypt-hash", new BigDecimal("2200.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'lara@example.ch'", Long.class);

        when(categorizationPort.categorizeAll(any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return Collections.nCopies(texts.size(), Optional.of(new CategorizationResult(
                    Category.LEBENSMITTEL, CategorizationResult.Source.CLAUDE)));
        });
    }

    /**
     * Wartet, bis kein Job mehr läuft, bevor der nächste Test aufräumt.
     *
     * <p>Nicht Kosmetik: Scheitert eine Assertion <em>vor</em> {@link #awaitCompletion}, schreibt
     * der Hintergrundlauf weiter — und das {@code DELETE FROM users} des nächsten Tests scheitert
     * dann am Fremdschlüssel. Ein einzelner echter Fehler risse sonst die halbe Klasse mit und
     * verdeckte, welche Assertion zuerst gebrochen ist.
     */
    @AfterEach
    void awaitQuiescence() {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> importJobRepository.findAll().stream()
                        .noneMatch(job -> job.getStatus() == ImportJobStatus.RUNNING));
    }

    @Test
    void statementWithMoreThanAHundredTransactionsIsImportedCompletely() {
        ImportJob started = pdfImportService.startImport(userId, fixture(), false);

        // Das Parsen läuft synchron: Die Anzahl steht schon in der Antwort, bevor irgendetwas
        // kategorisiert ist. Daran hängt der Nenner der Fortschrittsanzeige.
        assertThat(started.getTotal()).isEqualTo(TRANSACTION_COUNT);
        assertThat(started.getStatus()).isEqualTo(ImportJobStatus.RUNNING);

        ImportJob finished = awaitCompletion(started);

        // Die tragende Assertion von AC1: alle 110 sind da. Der alte Flow lieferte hier 0.
        assertThat(transactionRepository.count()).isEqualTo(TRANSACTION_COUNT);
        assertThat(finished.getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(finished.getProcessed()).isEqualTo(TRANSACTION_COUNT);
        // Nicht degradiert: Das Zeitbudget wurde nicht einmal gestreift.
        assertThat(finished.isDegraded()).isFalse();

        // Jede Buchung hat eine Kategorie und den Hash des PDFs (AC BE-PDF-02).
        assertThat(transactionRepository.findAll()).allSatisfy(tx -> {
            assertThat(tx.getUserId()).isEqualTo(userId);
            assertThat(tx.getCategory()).isEqualTo(Category.LEBENSMITTEL.getLabel());
            assertThat(tx.getPdfSha256()).hasSize(64);
        });
    }

    /**
     * Kreuzprobe gegen die gedruckten Totale der Fixture (BigDecimal-exakt, ADR-9).
     *
     * <p>Ohne sie hiesse «110 importiert» nur, dass 110 <em>Zeilen</em> ankamen — nicht, dass sie
     * die richtigen Beträge und die richtige Richtung tragen. Die Richtung leitet der generische
     * Parser-Zweig aus der Saldodifferenz ab; sie ist damit die Eigenschaft, die bei einem
     * Auszug dieser Länge am ehesten still kippt.
     */
    @Test
    void amountsAndDirectionsMatchThePrintedTotals() {
        awaitCompletion(pdfImportService.startImport(userId, fixture(), false));

        List<Transaction> saved = transactionRepository.findAll();
        assertThat(sum(saved, false)).isEqualByComparingTo(PRINTED_DEBITS);
        assertThat(sum(saved, true)).isEqualByComparingTo(PRINTED_CREDITS);
    }

    /**
     * Die Zahl, an der die Laufzeit hängt: 110 Transaktionen kosten sechs Aufrufe, nicht 110.
     *
     * <p>Vor ADR-13 war das eins zu eins — bei ~1.14 s pro Claude-Call sind das ~2 min gegenüber
     * wenigen Sekunden. Die Rechnung aus #192 (~41 unbekannte Transaktionen ≈ 47 s) beruht auf
     * genau diesem Verhältnis, hier bei voller Auszugsgrösse gemessen statt hochgerechnet.
     */
    @Test
    void categorizationRunsInBatchesNotOnePerTransaction() {
        awaitCompletion(pdfImportService.startImport(userId, fixture(), false));

        int expectedBatches = (TRANSACTION_COUNT + BATCH_SIZE - 1) / BATCH_SIZE;
        assertThat(expectedBatches).isEqualTo(6);

        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(categorizationPort, times(expectedBatches)).categorizeAll(batches.capture());

        // Kein Bündel überschreitet die konfigurierte Grösse, und zusammen decken sie alles ab.
        assertThat(batches.getAllValues()).allSatisfy(
                batch -> assertThat(batch).hasSizeLessThanOrEqualTo(BATCH_SIZE));
        assertThat(batches.getAllValues().stream().mapToInt(List::size).sum())
                .isEqualTo(TRANSACTION_COUNT);
    }

    private ImportJob awaitCompletion(ImportJob started) {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> importJobRepository.findById(started.getId())
                        .map(job -> job.getStatus() != ImportJobStatus.RUNNING)
                        .orElse(false));
        return importJobRepository.findById(started.getId()).orElseThrow();
    }

    private static BigDecimal sum(List<Transaction> txns, boolean income) {
        return txns.stream()
                .filter(t -> t.isIncome() == income)
                .map(Transaction::getBetrag)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
