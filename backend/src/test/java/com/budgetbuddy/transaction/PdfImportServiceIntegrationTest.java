package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.Category;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
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
 * Integrationstest des PDF-Import-Flows (BE-PDF-02) gegen echtes PostgreSQL + Flyway und ein echtes
 * Fixture-PDF (UBS, 28 Transaktionen): Parse → Kategorisierung → Persistierung inkl.
 * Duplikatcheck.
 *
 * <p>Die Kategorisierung ist per {@link MockitoBean} auf dem {@code hybridCategorizationService}
 * (dem {@code @Primary}-Port) gemockt — kein Claude-Call im Test; die Kette selbst ist in
 * {@code HybridCategorizationServiceTest} abgedeckt. Eigene Datenbank auf dem gemeinsamen
 * Testcontainer + {@code @DirtiesContext} analog {@code TransactionSummaryControllerIntegrationTest}.
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
        ImportResult result = pdfImportService.importPdf(userId, fixture(), false);

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
        pdfImportService.importPdf(userId, fixture(), false);

        assertThatThrownBy(() -> pdfImportService.importPdf(userId, fixture(), false))
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

        pdfImportService.importPdf(userId, fixture(), false);
        ImportResult second = pdfImportService.importPdf(otherUserId, fixture(), false);

        assertThat(second.transactionCount()).isEqualTo(28);
        assertThat(transactionRepository.count()).isEqualTo(56);
    }

    @Test
    void pdfBinaryIsNotStoredInDatabase() {
        pdfImportService.importPdf(userId, fixture(), false);

        // AC: keine PDF-Binärdaten in der DB — das Schema (Flyway V02) hat keine Blob-Spalte,
        // und die einzige PDF-Spur ist der 64-Zeichen-Hash.
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'transactions'
                """, String.class);
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
