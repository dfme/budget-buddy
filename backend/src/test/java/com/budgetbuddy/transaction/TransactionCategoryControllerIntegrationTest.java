package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.categorization.CategoryLearningPort;
import com.budgetbuddy.categorization.LookupTableService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest von {@code PUT /transactions/{id}/category} (BE-CAT-04) gegen echtes
 * PostgreSQL + Flyway. Deckt die Acceptance Criteria end-to-end ab: transactions-Zeile aktualisiert,
 * category_lookup geschrieben, nächste Transaktion desselben Händlers ohne Claude via Lookup.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link TransactionSummaryControllerIntegrationTest} (Begründung in
 * {@code PostgresTestDatabase}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionCategoryControllerIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "transaction_category");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private LookupTableService lookupTableService;
    @Autowired private CategoryLearningPort learningPort;

    /**
     * Der Schlüssel, unter dem die Claude-Stufe die Buchung mit Detailzeilen lernen würde —
     * {@code ParsedTransaction.fullText()} derselben Buchung ({@code ImportJobRunner:153}).
     */
    private static final String CLAUDE_LEARNED_PATTERN =
            "KAUF/DIENSTLEISTUNG BAECKEREI HUBER BERN KARTE 1234";

    private long userId;
    private long otherUserId;
    private long transactionId;
    private long detailedTransactionId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        // Gelernte Patterns aus vorherigen Tests entfernen, Seed-Daten bleiben unberührt.
        jdbcTemplate.update("DELETE FROM category_lookup WHERE empfaenger_pattern IN (?, ?, ?)",
                "BAECKEREI MUELLER", CLAUDE_LEARNED_PATTERN, "KAUF/DIENSTLEISTUNG");

        userId = insertUser("lara@example.ch");
        otherUserId = insertUser("marc@example.ch");

        // Startkategorie Sonstiges → wird auf Lebensmittel korrigiert.
        transactionId = transactionRepository.save(new Transaction(
                userId, LocalDate.of(2026, 7, 3), "BAECKEREI MUELLER", null,
                new BigDecimal("12.50"), false, "Sonstiges", null)).getId();

        // Layout mit Detailzeilen (PostFinance): buchungstext trägt nur die Zahlungsart.
        detailedTransactionId = transactionRepository.save(new Transaction(
                userId, LocalDate.of(2026, 7, 4), "KAUF/DIENSTLEISTUNG",
                "BAECKEREI HUBER BERN\nKARTE 1234", new BigDecimal("8.20"), false,
                "Sonstiges", null)).getId();
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                email, "bcrypt-hash", new BigDecimal("4200.00"), true);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Cookie jwtCookie(long uid) {
        return new Cookie("jwt", jwtService.generateToken(uid));
    }

    private String body(String category) {
        return "{\"category\":\"" + category + "\"}";
    }

    @Test
    void updatesCategoryPersistsLookupAndEnablesClaudelessCategorization() throws Exception {
        mockMvc.perform(put("/api/transactions/" + transactionId + "/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.category").value("Lebensmittel"));

        // AC 1: transactions-Zeile aktualisiert.
        assertThat(transactionRepository.findById(transactionId))
                .get().extracting(Transaction::getCategory).isEqualTo("Lebensmittel");

        // AC 2: Händler-Pattern in category_lookup eingetragen.
        String learned = jdbcTemplate.queryForObject(
                "SELECT category FROM category_lookup WHERE empfaenger_pattern = 'BAECKEREI MUELLER'",
                String.class);
        assertThat(learned).isEqualTo("Lebensmittel");

        // AC 3: nächste Transaktion desselben Händlers wird ohne Claude via Lookup kategorisiert.
        assertThat(lookupTableService.categorize("BAECKEREI MUELLER FILIALE BERN"))
                .contains(new CategorizationResult(
                        Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void foreignTransactionReturns404() throws Exception {
        mockMvc.perform(put("/api/transactions/" + transactionId + "/category")
                        .cookie(jwtCookie(otherUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownTransactionReturns404() throws Exception {
        mockMvc.perform(put("/api/transactions/999999/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCategoryReturns400() throws Exception {
        mockMvc.perform(put("/api/transactions/" + transactionId + "/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Foobar")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankCategoryReturns400() throws Exception {
        mockMvc.perform(put("/api/transactions/" + transactionId + "/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutJwtReturns401() throws Exception {
        mockMvc.perform(put("/api/transactions/" + transactionId + "/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * AC 3, die «überschreibt»-Hälfte — und der Regressionstest zum blockierenden Befund aus
     * PR #320.
     *
     * <p>Bewusst gegen die echte Query statt gegen eine {@code Map}: Der Fehler bestand nicht
     * darin, dass ein falscher Wert geschrieben wurde, sondern darin, dass <em>zwei</em> Zeilen
     * entstanden — eine von Claude unter dem vollen Text, eine von der Korrektur unter dem blossen
     * {@code buchungstext}. Ein Map-Stub mit exaktem Schlüssel kann das nicht zeigen; es braucht
     * die LIKE-Substring-Semantik und die Sortierung nach Pattern-Länge aus
     * {@code CategoryLookupRepository#findMatching}, weil genau sie den längeren Claude-Eintrag
     * gewinnen liess.
     */
    @Test
    void manualCorrectionOverwritesWhatClaudeLearnedForTheSameTransaction() throws Exception {
        // Import im Juli: Claude stuft den Händler ein und lernt ihn unter dem vollen Text.
        learningPort.learn(CLAUDE_LEARNED_PATTERN, Category.RESTAURANT);
        assertThat(lookupTableService.categorize(CLAUDE_LEARNED_PATTERN))
                .contains(new CategorizationResult(
                        Category.RESTAURANT, CategorizationResult.Source.LOOKUP));

        // Der User widerspricht: es ist eine Bäckerei, keine Restaurantrechnung.
        mockMvc.perform(put("/api/transactions/" + detailedTransactionId + "/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isOk());

        // Upsert statt zweiter Zeile: derselbe Primärschlüssel, keine Konkurrenz um die Länge.
        assertThat(countLookupRowsContaining("BAECKEREI HUBER")).isEqualTo(1);

        // Import im August, derselbe Text: die Korrektur des Users gewinnt.
        assertThat(lookupTableService.categorize(CLAUDE_LEARNED_PATTERN))
                .contains(new CategorizationResult(
                        Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));

        // Und der generische Buchungstext wurde nicht als eigenes Pattern gelernt — sonst
        // kategorisierte er jeden Kartenkauf des Kontos als Lebensmittel.
        assertThat(countLookupRowsFor("KAUF/DIENSTLEISTUNG")).isZero();
    }

    /** Wie viele Zeilen den Händler überhaupt tragen — eine zweite wäre der Befund aus #320. */
    private Integer countLookupRowsContaining(String patternFragment) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup WHERE empfaenger_pattern LIKE ?",
                Integer.class, "%" + patternFragment + "%");
    }

    private Integer countLookupRowsFor(String pattern) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup WHERE empfaenger_pattern = ?",
                Integer.class, pattern);
    }
}
