package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.categorization.LookupTableService;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Integrationstest von {@code PUT /transactions/{id}/category} (BE-CAT-04) gegen echtes SQLite +
 * Flyway. Deckt die Acceptance Criteria end-to-end ab: transactions-Zeile aktualisiert,
 * category_lookup geschrieben, nächste Transaktion desselben Händlers ohne Claude via Lookup.
 *
 * <p>Temp-File-DB statt {@code jdbc:sqlite::memory:} und {@code @DirtiesContext} analog zu
 * {@link TransactionSummaryControllerIntegrationTest} (Begründung dort dokumentiert).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionCategoryControllerIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-cat-04-category-it", ".db");
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

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private LookupTableService lookupTableService;

    private long userId;
    private long otherUserId;
    private long transactionId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        // Gelernte Patterns aus vorherigen Tests entfernen, Seed-Daten bleiben unberührt.
        jdbcTemplate.update("DELETE FROM category_lookup WHERE empfaenger_pattern = 'BAECKEREI MUELLER'");

        userId = insertUser("lara@example.ch");
        otherUserId = insertUser("marc@example.ch");

        // Startkategorie Sonstiges → wird auf Lebensmittel korrigiert.
        transactionId = transactionRepository.save(new Transaction(
                userId, LocalDate.of(2026, 7, 3), "BAECKEREI MUELLER",
                new BigDecimal("12.50"), false, "Sonstiges", null)).getId();
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
        mockMvc.perform(put("/transactions/" + transactionId + "/category")
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
                .contains(Category.LEBENSMITTEL);
    }

    @Test
    void foreignTransactionReturns404() throws Exception {
        mockMvc.perform(put("/transactions/" + transactionId + "/category")
                        .cookie(jwtCookie(otherUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownTransactionReturns404() throws Exception {
        mockMvc.perform(put("/transactions/999999/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCategoryReturns400() throws Exception {
        mockMvc.perform(put("/transactions/" + transactionId + "/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Foobar")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankCategoryReturns400() throws Exception {
        mockMvc.perform(put("/transactions/" + transactionId + "/category")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutJwtReturns401() throws Exception {
        mockMvc.perform(put("/transactions/" + transactionId + "/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Lebensmittel")))
                .andExpect(status().isUnauthorized());
    }
}
