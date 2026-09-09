package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Integrationstest von {@code GET /transactions/monthly-totals} (BE-STS-07, US-12) gegen echtes
 * PostgreSQL + Flyway.
 *
 * <p>Seeding über das {@link TransactionRepository} statt per Raw-SQL, damit das
 * {@link LocalDate}-Mapping identisch zum Lesepfad round-trippt — dieselbe Begründung wie in
 * {@link TransactionSummaryControllerIntegrationTest}. Eigene Datenbank auf dem gemeinsamen
 * Testcontainer und {@code @DirtiesContext} analog dazu.
 *
 * <p>Die Monate sind absolut gewählt (Juli/Juni/Mai 2026) und nicht relativ zu «heute»: ein
 * Fenster, das mit dem Kalender wandert, machte die erwarteten Zahlen vom Ausführungstag abhängig.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MonthlyTotalsControllerIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "monthly_totals");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ObjectMapper objectMapper;

    private long laraId;
    private long marcId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        laraId = createUser("lara@example.ch");
        marcId = createUser("marc@example.ch");

        // Juli: Gutschriften 5000, Belastungen 60 + 40 = 100 → Differenz 4900.
        save(laraId, "2026-07-25", "LOHN ARBEITGEBER", "5000.00", true, "Einkommen");
        save(laraId, "2026-07-03", "MIGROS BERN", "60.00", false, "Lebensmittel");
        save(laraId, "2026-07-20", "SBB CFF FFS", "40.00", false, "Transport");

        // Juni: Gutschriften 800, Belastungen 1250.50 → Differenz negativ (−450.50).
        save(laraId, "2026-06-25", "LOHN ARBEITGEBER", "800.00", true, "Einkommen");
        save(laraId, "2026-06-15", "MIETE", "1250.50", false, "Wohnen");

        // Mai: bewusst leer — die null-Zeile des Fensters.

        // Marcs Zahlen im selben Monat: dürfen in Laras Antwort nirgends auftauchen.
        save(marcId, "2026-07-10", "COOP ZUERICH", "999.99", false, "Lebensmittel");
        save(marcId, "2026-07-26", "LOHN MARC", "7777.77", true, "Einkommen");
    }

    private long createUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                email, "bcrypt-hash", new BigDecimal("4200.00"), true);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void save(
            long userId, String datum, String text, String betrag, boolean income, String category) {
        transactionRepository.save(new Transaction(
                userId, LocalDate.parse(datum), text, null, new BigDecimal(betrag), income,
                category, null));
    }

    private Cookie jwtCookie(long userId) {
        return new Cookie("jwt", jwtService.generateToken(userId));
    }

    @Test
    void returnsTheWindowNewestMonthFirst() throws Exception {
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-07")
                        .param("months", "3")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].month").value("2026-07"))
                .andExpect(jsonPath("$[0].income").value(5000.00))
                .andExpect(jsonPath("$[0].expenses").value(100.00))
                .andExpect(jsonPath("$[0].difference").value(4900.00))
                .andExpect(jsonPath("$[1].month").value("2026-06"))
                .andExpect(jsonPath("$[2].month").value("2026-05"));
    }

    @Test
    void reportsANegativeDifferenceWhenMoreWasSpentThanEarned() throws Exception {
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-06")
                        .param("months", "1")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].income").value(800.00))
                .andExpect(jsonPath("$[0].expenses").value(1250.50))
                .andExpect(jsonPath("$[0].difference").value(-450.50));
    }

    @Test
    void aMonthWithoutTransactionsCarriesNullsRatherThanZero() throws Exception {
        // Mai 2026 ist im Seed leer. 0.00 behauptete erfasste Nullbeträge; null hält den
        // Unterschied und ist die Grundlage für die «–»-Darstellung in FE-STS-04.
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-05")
                        .param("months", "1")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2026-05"))
                .andExpect(jsonPath("$[0].income").isEmpty())
                .andExpect(jsonPath("$[0].expenses").isEmpty())
                .andExpect(jsonPath("$[0].difference").isEmpty());
    }

    @Test
    void expensesMatchTheCategorySummaryTotalAmountOfTheSameMonth() throws Exception {
        // Die AC, die den Ausgabenbegriff festnagelt: nur Belastungen, ganzer Monat, KEIN Abzug der
        // per Dauerauftrag bezahlten Fixkosten (ADR-13 gehört allein in den Safe-to-Spend). Beide
        // Endpoints lesen dieselbe Query — der Test hält fest, dass das so bleibt.
        String totalsBody = mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-07")
                        .param("months", "1")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String summaryBody = mockMvc.perform(get("/api/transactions/summary")
                        .param("month", "2026-07")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode totals = objectMapper.readTree(totalsBody);
        JsonNode summary = objectMapper.readTree(summaryBody);

        assertThat(totals.get(0).get("expenses").decimalValue())
                .isEqualByComparingTo(summary.get("totalAmount").decimalValue());
    }

    @Test
    void doesNotExposeAnotherUsersFigures() throws Exception {
        // Marc hat im Juli 999.99 ausgegeben und 7777.77 erhalten. Laras Antwort trägt
        // ausschliesslich ihre eigenen Zahlen — beide Queries sind user-gebunden.
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-07")
                        .param("months", "1")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expenses").value(100.00))
                .andExpect(jsonPath("$[0].income").value(5000.00));

        // Gegenprobe aus Marcs Sicht: er sieht seine, nicht Laras.
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-07")
                        .param("months", "1")
                        .cookie(jwtCookie(marcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expenses").value(999.99))
                .andExpect(jsonPath("$[0].income").value(7777.77));
    }

    @Test
    void defaultsToThreeMonthsWhenMonthsIsOmitted() throws Exception {
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-07")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void answersAFutureMonthWithNullsInsteadOfRejectingIt() throws Exception {
        // Anders als GET /api/budget/safe-to-spend, das hier 400 liefert: dort hat weeksLeft für
        // einen künftigen Monat keine Bedeutung, hier gibt es einfach keine Buchungen.
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2099-12")
                        .param("months", "1")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2099-12"))
                .andExpect(jsonPath("$[0].income").isEmpty());
    }

    @Test
    void rejectsAMalformedMonthWithBadRequest() throws Exception {
        // Belegt zugleich, dass MonthlyTotalsController im TransactionExceptionHandler eingetragen
        // ist: ohne den Eintrag wäre das eine 500, nicht eine 400.
        for (String month : new String[] {"2026-13", "juli", "2026", ""}) {
            mockMvc.perform(get("/api/transactions/monthly-totals")
                            .param("month", month)
                            .cookie(jwtCookie(laraId)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsAMissingMonthWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/transactions/monthly-totals").cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnusableWindowSizeWithBadRequest() throws Exception {
        for (String months : new String[] {"0", "-1", "99"}) {
            mockMvc.perform(get("/api/transactions/monthly-totals")
                            .param("month", "2026-07")
                            .param("months", months)
                            .cookie(jwtCookie(laraId)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsANonNumericWindowSizeWithBadRequest() throws Exception {
        // months ist ein int; ein unbrauchbarer Wert darf nicht als 500 durchschlagen. Behandelt
        // wird das von Springs eigenem Resolver (MethodArgumentTypeMismatchException), nicht vom
        // TransactionExceptionHandler — der Test hält fest, dass das auch so bleibt.
        mockMvc.perform(get("/api/transactions/monthly-totals")
                        .param("month", "2026-07")
                        .param("months", "drei")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/transactions/monthly-totals").param("month", "2026-07"))
                .andExpect(status().isUnauthorized());
    }
}
