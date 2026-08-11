package com.budgetbuddy.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.support.PostgresTestDatabase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
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
 * Integrationstest von {@code GET /transactions} (FE-CAT-03) gegen echtes PostgreSQL + Flyway.
 *
 * <p>Seeding über das {@link TransactionRepository} und eigene Datenbank auf dem gemeinsamen
 * Testcontainer, analog {@code TransactionSummaryControllerIntegrationTest} (Begründung in
 * {@code PostgresTestDatabase}).
 *
 * <p>Zwei User werden angelegt, nicht einer: die Mandantentrennung ist bei einem Endpoint, der
 * Transaktions-IDs herausgibt, der Punkt, der wirklich schiefgehen kann — ein grüner Happy Path
 * belegt sie nicht.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionListControllerIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "transaction_list");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    private long laraId;
    private long marcId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        laraId = createUser("lara@example.ch");
        marcId = createUser("marc@example.ch");

        // Laras Juli: zwei Ausgaben, eine davon noch nicht kategorisiert.
        save(laraId, "2026-07-03", "MIGROS BERN", "60.00", false, "Lebensmittel");
        save(laraId, "2026-07-20", "UNBEKANNT AG", "25.00", false, null);
        // Gutschrift im Juli → keine Ausgabe, darf nicht erscheinen.
        save(laraId, "2026-07-25", "LOHN ARBEITGEBER", "3000.00", true, "Einkommen");
        // Ausgabe im Juni → anderer Monat.
        save(laraId, "2026-06-15", "MIETE", "1200.00", false, "Wohnen");
        // Marcs Juli — dieselbe Periode, fremder User.
        save(marcId, "2026-07-10", "MARCS KAFFEE", "8.00", false, "Restaurant");
    }

    private long createUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                email, "bcrypt-hash", new BigDecimal("4200.00"), true);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void save(long userId, String datum, String text, String betrag, boolean income,
            String category) {
        transactionRepository.save(new Transaction(
                userId, LocalDate.parse(datum), text, new BigDecimal(betrag), income, category,
                null));
    }

    private Cookie jwtCookie(long userId) {
        return new Cookie("jwt", jwtService.generateToken(userId));
    }

    @Test
    void returnsExpensesOfTheMonthNewestFirst() throws Exception {
        mockMvc.perform(get("/transactions").param("month", "2026-07").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].buchungstext").value("UNBEKANNT AG"))
                .andExpect(jsonPath("$[0].buchungsdatum").value("2026-07-20"))
                .andExpect(jsonPath("$[0].betrag").value(25.00))
                .andExpect(jsonPath("$[0].income").value(false))
                // Nicht kategorisiert → 'Sonstiges', damit das Dropdown eine Vorauswahl hat.
                .andExpect(jsonPath("$[0].category").value("Sonstiges"))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[1].buchungstext").value("MIGROS BERN"))
                .andExpect(jsonPath("$[1].category").value("Lebensmittel"));
    }

    @Test
    void excludesIncomeAndOtherMonths() throws Exception {
        mockMvc.perform(get("/transactions").param("month", "2026-07").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.buchungstext == 'LOHN ARBEITGEBER')]").isEmpty())
                .andExpect(jsonPath("$[?(@.buchungstext == 'MIETE')]").isEmpty());
    }

    @Test
    void doesNotLeakTransactionsOfAnotherUser() throws Exception {
        // Marc fragt denselben Monat ab und sieht ausschliesslich seine eigene Buchung.
        mockMvc.perform(get("/transactions").param("month", "2026-07").cookie(jwtCookie(marcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buchungstext").value("MARCS KAFFEE"))
                .andExpect(jsonPath("$[?(@.buchungstext == 'MIGROS BERN')]").isEmpty());
    }

    @Test
    void filtersByCategory() throws Exception {
        mockMvc.perform(get("/transactions").param("month", "2026-07")
                        .param("category", "Lebensmittel").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buchungstext").value("MIGROS BERN"));
    }

    @Test
    void filterOnSonstigesMatchesUncategorizedTransactions() throws Exception {
        mockMvc.perform(get("/transactions").param("month", "2026-07")
                        .param("category", "Sonstiges").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buchungstext").value("UNBEKANNT AG"));
    }

    @Test
    void emptyMonthReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/transactions").param("month", "2026-01").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidMonthReturns400() throws Exception {
        mockMvc.perform(get("/transactions").param("month", "2026-13").cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingMonthReturns400() throws Exception {
        mockMvc.perform(get("/transactions").cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unmatchedCategoryReturnsEmptyList() throws Exception {
        // Der Filter validiert das Vokabular bewusst nicht — sonst liessen sich genau die Zeilen
        // nicht aufklappen, die mit einem unerwarteten Label in der Übersicht stehen.
        mockMvc.perform(get("/transactions").param("month", "2026-07")
                        .param("category", "Lebensmitel").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void withoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/transactions").param("month", "2026-07"))
                .andExpect(status().isUnauthorized());
    }
}
