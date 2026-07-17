package com.budgetbuddy.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest von {@code GET /transactions/summary} (BE-CAT-05) gegen echtes SQLite + Flyway.
 *
 * <p>Seeding der Transaktionen über das {@link TransactionRepository} (nicht per Raw-SQL), damit das
 * {@link LocalDate}-Mapping identisch zum Lesepfad round-trippt. Temp-File-DB statt
 * {@code jdbc:sqlite::memory:} und {@code @DirtiesContext} analog zu {@code UserControllerTest}
 * (Begründung dort dokumentiert).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionSummaryControllerIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-cat-05-summary-it", ".db");
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
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    private long userId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "lara@example.ch", "bcrypt-hash", new BigDecimal("4200.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'lara@example.ch'", Long.class);

        // Juli-Ausgaben: 60 + 40 = 100 CHF total → Lebensmittel 60 %, Transport 40 %.
        save("2026-07-03", "MIGROS BERN", "60.00", false, "Lebensmittel");
        save("2026-07-20", "SBB CFF FFS", "40.00", false, "Transport");
        // Einkommen im Juli → darf NICHT ins Ausgaben-Summary einfliessen.
        save("2026-07-25", "LOHN ARBEITGEBER", "3000.00", true, "Einkommen");
        // Ausgabe im Juni → anderer Monat, darf nicht erscheinen.
        save("2026-06-15", "MIETE", "1200.00", false, "Wohnen");
    }

    private void save(String datum, String text, String betrag, boolean income, String category) {
        transactionRepository.save(new Transaction(
                userId, LocalDate.parse(datum), text, new BigDecimal(betrag), income, category, null));
    }

    private Cookie jwtCookie() {
        return new Cookie("jwt", jwtService.generateToken(userId));
    }

    @Test
    void returnsExpenseSummaryForMonth() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("month", "2026-07").cookie(jwtCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-07"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.categories.length()").value(2))
                // Absteigend nach Betrag → Lebensmittel zuerst.
                .andExpect(jsonPath("$.categories[0].category").value("Lebensmittel"))
                .andExpect(jsonPath("$.categories[0].amount").value(60.00))
                .andExpect(jsonPath("$.categories[0].count").value(1))
                .andExpect(jsonPath("$.categories[0].percentage").value(60.00))
                .andExpect(jsonPath("$.categories[1].category").value("Transport"))
                .andExpect(jsonPath("$.categories[1].amount").value(40.00))
                .andExpect(jsonPath("$.categories[1].percentage").value(40.00));
    }

    @Test
    void excludesIncomeAndOtherMonths() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("month", "2026-07").cookie(jwtCookie()))
                .andExpect(status().isOk())
                // Weder Einkommen (Juli, Gutschrift) noch Wohnen (Juni) erscheinen.
                .andExpect(jsonPath("$.categories[?(@.category == 'Einkommen')]").isEmpty())
                .andExpect(jsonPath("$.categories[?(@.category == 'Wohnen')]").isEmpty());
    }

    @Test
    void emptyMonthReturnsEmptySummary() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("month", "2026-01").cookie(jwtCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    void invalidMonthReturns400() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("month", "2026-13").cookie(jwtCookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingMonthReturns400() throws Exception {
        mockMvc.perform(get("/transactions/summary").cookie(jwtCookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("month", "2026-07"))
                .andExpect(status().isUnauthorized());
    }
}
