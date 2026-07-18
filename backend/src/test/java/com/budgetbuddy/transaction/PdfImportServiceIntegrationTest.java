package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.Category;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
 * Integrationstest des PDF-Import-Flows (BE-PDF-02) gegen echtes SQLite + Flyway und ein echtes
 * Fixture-PDF (UBS, 28 Transaktionen): Parse → Kategorisierung → Persistierung inkl.
 * Duplikatcheck.
 *
 * <p>Die Kategorisierung ist per {@link MockitoBean} auf dem {@code hybridCategorizationService}
 * (dem {@code @Primary}-Port) gemockt — kein Claude-Call im Test; die Kette selbst ist in
 * {@code HybridCategorizationServiceTest} abgedeckt. Temp-File-DB + {@code @DirtiesContext}
 * analog {@code TransactionSummaryControllerIntegrationTest}.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportServiceIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-pdf-02-import-it", ".db");
            Files.deleteIfExists(file); // Flyway/SQLite legt die Datei selbst an
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private PdfImportService pdfImportService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Ersetzt die Hybrid-Kette (den {@code @Primary}-{@link CategorizationPort}) im Kontext. */
    @MockitoBean(name = "hybridCategorizationService")
    private CategorizationPort categorizationPort;

    private long userId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "peter.muster@example.ch", "bcrypt-hash", new BigDecimal("6800.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'peter.muster@example.ch'", Long.class);
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(Category.LEBENSMITTEL));
    }

    private static byte[] fixture() {
        try (InputStream in = PdfImportServiceIntegrationTest.class
                .getResourceAsStream("/pdf/UBS_Konto_Bewegungen_2021_Juli.pdf")) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void importsAllFixtureTransactions_withCategoryAndHash() {
        ImportResult result = pdfImportService.importPdf(userId, fixture());

        assertThat(result.transactionCount()).isEqualTo(28);
        assertThat(result.pdfSha256()).hasSize(64); // SHA-256 als Hex

        List<Transaction> saved = transactionRepository.findAll();
        assertThat(saved).hasSize(28);
        // AC: Alle importierten Transaktionen erhalten eine Kategorie; Hash gesetzt.
        assertThat(saved).allSatisfy(tx -> {
            assertThat(tx.getUserId()).isEqualTo(userId);
            assertThat(tx.getCategory()).isEqualTo("Lebensmittel");
            assertThat(tx.getPdfSha256()).isEqualTo(result.pdfSha256());
        });
        // Kreuzprobe gegen die gedruckte Umsatztotal-Zeile des Fixtures (BigDecimal-exakt).
        BigDecimal expenses = sum(saved, false);
        BigDecimal income = sum(saved, true);
        assertThat(expenses).isEqualByComparingTo("26970.40");
        assertThat(income).isEqualByComparingTo("40950.00");
    }

    @Test
    void secondImportOfSamePdf_throwsDuplicateAndPersistsNothingNew() {
        pdfImportService.importPdf(userId, fixture());

        assertThatThrownBy(() -> pdfImportService.importPdf(userId, fixture()))
                .isInstanceOf(DuplicatePdfImportException.class);

        assertThat(transactionRepository.count()).isEqualTo(28);
    }

    @Test
    void pdfBinaryIsNotStoredInDatabase() {
        pdfImportService.importPdf(userId, fixture());

        // AC: keine PDF-Binärdaten in der DB — das Schema (Flyway V02) hat keine Blob-Spalte,
        // und die einzige PDF-Spur ist der 64-Zeichen-Hash.
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT name FROM pragma_table_info('transactions')", String.class);
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "user_id", "buchungsdatum", "buchungstext", "betrag", "is_income",
                "category", "pdf_sha256");
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
