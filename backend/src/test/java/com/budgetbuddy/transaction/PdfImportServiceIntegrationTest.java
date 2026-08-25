package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integrationstest des PDF-Import-Flows (BE-PDF-02) gegen echtes PostgreSQL + Flyway und ein echtes
 * Fixture-PDF (UBS, 28 Transaktionen): Parse → Kategorisierung → Persistierung inkl.
 * Duplikatcheck.
 *
 * <p>Die Kategorisierung ist per {@link MockitoBean} auf dem {@code hybridCategorizationService}
 * (dem {@code @Primary}-Port) gemockt — kein Claude-Call im Test; die Kette selbst ist in
 * {@code HybridCategorizationServiceTest} abgedeckt. Eigene Datenbank auf dem gemeinsamen
 * Testcontainer + {@code @DirtiesContext} analog {@code TransactionSummaryControllerIntegrationTest}.
 *
 * <p><strong>Wartet auf den Job</strong> (ADR-14, BE-PDF-09): Seit BE-PDF-09 kehrt
 * {@code startImport} zurück, bevor persistiert ist. {@link #importAndAwait} pollt deshalb den
 * Job-Status — genau wie das Frontend. Das macht diesen Test zugleich zum Nachweis, dass der
 * {@code @Async}-Proxy im echten Kontext greift: Liefe der Lauf entgegen der Absicht synchron,
 * stünde der Job beim ersten Poll bereits auf {@code DONE} und der Test bliebe grün — deshalb
 * prüft {@link #importStartsBeforeItFinishes} zusätzlich den Zwischenzustand.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportServiceIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "pdf_import_service");
    }

    @Autowired
    private PdfImportService pdfImportService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ImportJobRepository importJobRepository;

    /** Ersetzt die Hybrid-Kette (den {@code @Primary}-{@link CategorizationPort}) im Kontext. */
    @MockitoBean(name = "hybridCategorizationService")
    private CategorizationPort categorizationPort;

    private long userId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        // Vor den Usern: import_jobs.user_id ist ein Fremdschlüssel auf users (Flyway V05).
        importJobRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "peter.muster@example.ch", "bcrypt-hash", new BigDecimal("6800.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'peter.muster@example.ch'", Long.class);
        // Seit ADR-14 fragt der Import gebündelt ab: categorizeAll, nicht categorize.
        when(categorizationPort.categorizeAll(any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return Collections.nCopies(texts.size(), Optional.of(new CategorizationResult(
                    Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP)));
        });
    }

    /**
     * Startet den Import und wartet, bis der Hintergrundlauf fertig ist — die Testvariante dessen,
     * was das Frontend mit {@code GET /api/import/{jobId}/status} tut.
     *
     * @return der abgeschlossene Job.
     */
    private ImportJob importAndAwait(long forUserId, byte[] pdf, boolean force) {
        ImportJob started = pdfImportService.startImport(forUserId, pdf, force);
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> importJobRepository.findById(started.getId())
                        .map(job -> job.getStatus() != ImportJobStatus.RUNNING)
                        .orElse(false));
        return importJobRepository.findById(started.getId()).orElseThrow();
    }

    private static byte[] fixture() {
        return fixture("/pdf/UBS_Konto_Bewegungen_2021_Juli.pdf");
    }

    /**
     * Der layouttreue PostFinance-Auszug. Anders als die UBS-Fixture trägt er Detailzeilen — dort
     * steht der Händler nicht in der Buchungszeile, sondern darunter.
     */
    private static byte[] postFinanceFixture() {
        return fixture("/pdf/Post_Kontoauszug_2026_Juli_20_Buchungen.pdf");
    }

    private static byte[] fixture(String resource) {
        try (InputStream in = PdfImportServiceIntegrationTest.class
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * BE-PDF-07, DoD «Detailzeilen werden beim Import persistiert» — über den echten Import statt
     * über Mocks.
     *
     * <p>Bewusst an der PostFinance-Fixture: In der UBS-Fixture steht der Händler bereits in der
     * Buchungszeile, sie hat gar keine Detailzeilen und könnte den Pfad deshalb nicht belegen.
     * Genau daran hängt der Nutzen des Tickets — vor BE-PDF-07 landete bei diesem Layout nur die
     * Zahlungsart in der Datenbank.
     */
    @Test
    void postFinanceImport_persistsCounterpartyFromDetailLines() {
        importAndAwait(userId, postFinanceFixture(), false);

        List<Transaction> saved = transactionRepository.findAll();
        assertThat(saved).isNotEmpty();

        // Der Händler ist nur über die Detailspalte auffindbar: In keinem buchungstext dieses
        // Auszugs steht er. Fiele die Spalte weg, wäre der erste Teil der Prüfung leer.
        assertThat(saved)
                .filteredOn(tx -> tx.getBuchungsdetails() != null
                        && tx.getBuchungsdetails().contains("ZALANDO SE"))
                .isNotEmpty();
        assertThat(saved).noneSatisfy(
                tx -> assertThat(tx.getBuchungstext()).contains("ZALANDO SE"));

        // Und die Trennung überlebt das Persistieren: Buchungstext und Detailzeilen stehen in
        // eigenen Spalten, nicht zusammengeschrieben (US-08 braucht sie einzeln).
        assertThat(saved)
                .allSatisfy(tx -> assertThat(tx.getBuchungsdetails())
                        .satisfiesAnyOf(
                                details -> assertThat(details).isNull(),
                                details -> assertThat(details)
                                        .doesNotContain(tx.getBuchungstext())));
    }

    @Test
    void importsAllFixtureTransactions_withCategoryAndHash() {
        ImportJob job = importAndAwait(userId, fixture(), false);

        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(job.getTotal()).isEqualTo(28);
        assertThat(job.getProcessed()).isEqualTo(28);
        assertThat(job.isDegraded()).isFalse();

        List<Transaction> saved = transactionRepository.findAll();
        assertThat(saved).hasSize(28);
        // AC: Alle importierten Transaktionen erhalten eine Kategorie; Hash gesetzt.
        assertThat(saved).allSatisfy(tx -> {
            assertThat(tx.getUserId()).isEqualTo(userId);
            assertThat(tx.getCategory()).isEqualTo("Lebensmittel");
            assertThat(tx.getPdfSha256()).hasSize(64); // SHA-256 als Hex
        });
        // Kreuzprobe gegen die gedruckte Umsatztotal-Zeile des Fixtures (BigDecimal-exakt).
        BigDecimal expenses = sum(saved, false);
        BigDecimal income = sum(saved, true);
        assertThat(expenses).isEqualByComparingTo("26970.40");
        assertThat(income).isEqualByComparingTo("40950.00");
    }

    /**
     * Der Nachweis, dass der Lauf wirklich asynchron ist: {@code startImport} kehrt zurück,
     * während die Kategorisierung noch hängt. Genau das macht die Fortschrittsanzeige möglich —
     * und genau das war vor ADR-14 nicht so, weshalb der Request 30 s lang blockierte (#192).
     *
     * <p>Der Latch macht die Aussage deterministisch. Ohne ihn könnte der Hintergrundlauf mit
     * einem sofort antwortenden Mock schon fertig sein, bevor die Assertion greift — der Test
     * wäre dann grün, ohne etwas zu belegen.
     */
    @Test
    void importStartsBeforeItFinishes() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reached = new CountDownLatch(1);
        // doAnswer statt when(...).thenAnswer(...): Bei einer bereits gestubbten Methode würde
        // das Argument von when() den vorhandenen Answer mit null aufrufen.
        org.mockito.Mockito.doAnswer(invocation -> {
            reached.countDown();
            release.await(30, TimeUnit.SECONDS);
            List<String> texts = invocation.getArgument(0);
            return Collections.nCopies(texts.size(), Optional.of(new CategorizationResult(
                    Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP)));
        }).when(categorizationPort).categorizeAll(any());

        ImportJob started = pdfImportService.startImport(userId, fixture(), false);

        assertThat(reached.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(importJobRepository.findById(started.getId()).orElseThrow().getStatus())
                .isEqualTo(ImportJobStatus.RUNNING);
        assertThat(transactionRepository.count()).isZero();

        release.countDown();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> transactionRepository.count() == 28);
    }

    @Test
    void secondImportOfSamePdf_throwsDuplicateAndPersistsNothingNew() {
        importAndAwait(userId, fixture(), false);

        assertThatThrownBy(() -> pdfImportService.startImport(userId, fixture(), false))
                .isInstanceOf(DuplicatePdfImportException.class);

        assertThat(transactionRepository.count()).isEqualTo(28);
    }

    @Test
    void samePdfForDifferentUser_importsWithoutDuplicateConflict() {
        // Der Duplikatcheck ist per User gescoped (Repository-Javadoc): dasselbe PDF darf von
        // einem anderen User importiert werden — z. B. Partner mit Gemeinschaftskonto.
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "lara.beispiel@example.ch", "bcrypt-hash", new BigDecimal("2200.00"), true);
        long otherUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'lara.beispiel@example.ch'", Long.class);

        importAndAwait(userId, fixture(), false);
        ImportJob second = importAndAwait(otherUserId, fixture(), false);

        assertThat(second.getTotal()).isEqualTo(28);
        assertThat(transactionRepository.count()).isEqualTo(56);
    }

    @Test
    void pdfBinaryIsNotStoredInDatabase() {
        importAndAwait(userId, fixture(), false);

        // AC: keine PDF-Binärdaten in der DB — das Schema hat keine Blob-Spalte, und die einzige
        // PDF-Spur ist der 64-Zeichen-Hash.
        //
        // Die Liste ist bewusst abschliessend und nicht bloss ein «enthält kein bytea»: Sie zwingt
        // jede neue Spalte auf diesem Tisch zu einer Begründung. buchungsdetails (V06, BE-PDF-07)
        // ist eine solche — sie hält die geparsten Detailzeilen als Text, also Gegenpartei und
        // Verwendungszweck. Das ist Inhalt des Auszugs, nicht die Datei; ihre Länge deckelt der
        // Parser auf 3 Zeilen à 40 Zeichen (SwissBankStatementParser). In dieser UBS-Fixture ist
        // die Spalte durchgängig NULL — UBS schreibt den Händler in die Buchungszeile selbst und
        // hat gar keine Detailzeilen (SwissBankStatementParserFixtureTest#pageBreak_...).
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'transactions'
                """, String.class);
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "user_id", "buchungsdatum", "buchungstext", "buchungsdetails", "betrag",
                "is_income", "category", "pdf_sha256");
        Integer maxLen = jdbcTemplate.queryForObject(
                "SELECT MAX(LENGTH(pdf_sha256)) FROM transactions", Integer.class);
        assertThat(maxLen).isEqualTo(64);
    }

    private static BigDecimal sum(List<Transaction> txns, boolean income) {
        return txns.stream()
                .filter(t -> t.isIncome() == income)
                .map(Transaction::getBetrag)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
