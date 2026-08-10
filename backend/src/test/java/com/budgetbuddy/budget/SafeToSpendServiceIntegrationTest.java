package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.budgetbuddy.budget.dto.FixedCostRequest;
import com.budgetbuddy.budget.dto.SafeToSpendResponse;
import com.budgetbuddy.support.PostgresTestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Integrationstest des {@link SafeToSpendService} gegen echtes PostgreSQL + Flyway (BE-STS-01).
 *
 * <p>Belegt zwei Dinge, die der Unit-Test mit gemockten Ports nicht belegen kann: die
 * <strong>Mandantentrennung</strong> aus Sicht eines fremden Users mit eigenen Fixkosten und
 * Transaktionen im selben Monat, und die Verdrahtung beider Modulkanten ({@code UserIncomePort} aus
 * {@code auth}, {@code MonthlyExpensePort} aus {@code transaction}) über echte Daten hinweg.
 *
 * <p>Die {@link Clock} ist als {@link MockitoBean} auf einen festen Zeitpunkt gestellt: sonst hinge
 * das Ergebnis am Kalendertag des CI-Laufs und der Test wäre an 11 von 12 Monaten grün und einmal
 * rot.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link FixedCostServiceIntegrationTest}. Test-User und Transaktionen werden per
 * {@link JdbcTemplate} eingefügt: ein Zugriff über {@code UserRepository} oder
 * {@code TransactionRepository} wäre genau der modulübergreifende Zugriff, den CLAUDE.md untersagt.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SafeToSpendServiceIntegrationTest {

    /** 11.08.2026, 12:00 Ortszeit — im August verbleiben ab hier 21 Tage, also 3 Wochen. */
    private static final Instant STICHTAG = LocalDate.of(2026, 8, 11)
            .atTime(12, 0)
            .atZone(ZoneId.of("Europe/Zurich"))
            .toInstant();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "safe_to_spend_service");
    }

    @Autowired private SafeToSpendService service;
    @Autowired private FixedCostService fixedCostService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private Clock clock;

    @BeforeEach
    void fixTheClock() {
        when(clock.instant()).thenReturn(STICHTAG);
    }

    @Test
    void calculatesFromIncomeFixedCostsAndTransactionsOfThisMonth() {
        long lara = insertUser("lara-sts@example.com", new BigDecimal("3000.00"));
        fixedCostService.create(lara,
                new FixedCostRequest("Miete", new BigDecimal("1200.00"), "monatlich"));
        fixedCostService.create(lara,
                new FixedCostRequest("Versicherung", new BigDecimal("1200.00"), "jaehrlich"));
        insertExpense(lara, LocalDate.of(2026, 8, 3), "COOP BERN", new BigDecimal("150.00"));
        insertExpense(lara, LocalDate.of(2026, 8, 9), "SBB", new BigDecimal("50.00"));

        SafeToSpendResponse result = service.calculate(lara);

        // Fixkosten monatlich: 1200.00 + (1200.00 ÷ 12) = 1300.00
        // (3000.00 − 1300.00 − 200.00) ÷ 3 = 500.00
        assertThat(result.amount()).isEqualByComparingTo("500.00");
        assertThat(result.weeksLeft()).isEqualTo(3);
        assertThat(result.isNegative()).isFalse();
        assertThat(result.noIncome()).isFalse();
    }

    @Test
    void ignoresCreditsAndTransactionsOfOtherMonths() {
        long lara = insertUser("lara-sts-scope@example.com", new BigDecimal("3000.00"));
        insertExpense(lara, LocalDate.of(2026, 8, 5), "MIGROS", new BigDecimal("300.00"));
        insertExpense(lara, LocalDate.of(2026, 7, 31), "VORMONAT", new BigDecimal("999.00"));
        insertExpense(lara, LocalDate.of(2026, 9, 1), "FOLGEMONAT", new BigDecimal("999.00"));
        insertIncome(lara, LocalDate.of(2026, 8, 25), "LOHN", new BigDecimal("3000.00"));

        // Nur die 300.00 aus dem August zählen: (3000.00 − 0 − 300.00) ÷ 3 = 900.00
        assertThat(service.calculate(lara).amount()).isEqualByComparingTo("900.00");
    }

    @Test
    void reportsNoIncomeWhenMonthlyIncomeIsNull() {
        long marc = insertUser("marc-sts-noincome@example.com", null);
        insertExpense(marc, LocalDate.of(2026, 8, 4), "SPOTIFY", new BigDecimal("12.95"));

        SafeToSpendResponse result = service.calculate(marc);

        assertThat(result.noIncome()).isTrue();
        assertThat(result.amount()).isNull();
        assertThat(result.weeksLeft()).isEqualTo(3);
    }

    @Test
    void reportsNegativeWhenTheMonthIsAlreadyOverspent() {
        long marc = insertUser("marc-sts-negative@example.com", new BigDecimal("2000.00"));
        fixedCostService.create(marc,
                new FixedCostRequest("Miete", new BigDecimal("1500.00"), "monatlich"));
        insertExpense(marc, LocalDate.of(2026, 8, 2), "SHOPPING", new BigDecimal("800.00"));

        SafeToSpendResponse result = service.calculate(marc);

        // (2000.00 − 1500.00 − 800.00) ÷ 3 = −100.00
        assertThat(result.amount()).isEqualByComparingTo("-100.00");
        assertThat(result.isNegative()).isTrue();
    }

    // --- Mandantentrennung: Gegenprobe mit einem zweiten User im selben Monat ---

    @Test
    void aForeignUsersFixedCostsAndTransactionsDoNotAffectTheResult() {
        long lara = insertUser("lara-sts-isolation@example.com", new BigDecimal("3000.00"));
        fixedCostService.create(lara,
                new FixedCostRequest("Miete", new BigDecimal("1200.00"), "monatlich"));
        insertExpense(lara, LocalDate.of(2026, 8, 3), "COOP", new BigDecimal("300.00"));

        SafeToSpendResponse allein = service.calculate(lara);

        // Marc bekommt im selben Monat deutlich höhere Fixkosten und Ausgaben. Würde eine der drei
        // Abfragen den User nicht einschränken, sänke Laras Betrag messbar.
        long marc = insertUser("marc-sts-isolation@example.com", new BigDecimal("9000.00"));
        fixedCostService.create(marc,
                new FixedCostRequest("Loft", new BigDecimal("4000.00"), "monatlich"));
        insertExpense(marc, LocalDate.of(2026, 8, 3), "DIGITEC", new BigDecimal("2500.00"));
        insertExpense(marc, LocalDate.of(2026, 8, 7), "RESTAURANT", new BigDecimal("400.00"));

        SafeToSpendResponse nachher = service.calculate(lara);

        // (3000.00 − 1200.00 − 300.00) ÷ 3 = 500.00 — vorher wie nachher.
        assertThat(allein.amount()).isEqualByComparingTo("500.00");
        assertThat(nachher.amount()).isEqualByComparingTo("500.00");

        // Gegenprobe in die andere Richtung: Marc sieht ausschliesslich seine eigenen Zahlen.
        // (9000.00 − 4000.00 − 2900.00) ÷ 3 = 700.00
        assertThat(service.calculate(marc).amount()).isEqualByComparingTo("700.00");
    }

    // --- Helfer ---

    private long insertUser(String email, BigDecimal monthlyIncome) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income) VALUES (?, ?, ?)",
                email, "$2a$10$test.only.not.a.real.hash", monthlyIncome);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertExpense(long userId, LocalDate datum, String text, BigDecimal betrag) {
        insertTransaction(userId, datum, text, betrag, false);
    }

    private void insertIncome(long userId, LocalDate datum, String text, BigDecimal betrag) {
        insertTransaction(userId, datum, text, betrag, true);
    }

    private void insertTransaction(
            long userId, LocalDate datum, String text, BigDecimal betrag, boolean income) {
        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, buchungsdatum, buchungstext, betrag, is_income) "
                        + "VALUES (?, ?, ?, ?, ?)",
                userId, datum, text, betrag, income);
    }
}
