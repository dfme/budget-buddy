package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
 * Integrationstest der Einkommens-Heuristik gegen echtes PostgreSQL + Flyway (BE-STS-02).
 *
 * <p>Belegt drei Dinge, die der Unit-Test mit gemocktem Repository nicht belegen kann: dass die
 * Ableitung des Gutschriften-Filters (<code>is_income = true</code>) im echten Schema greift, dass
 * das 12-Monats-Fenster tatsächlich in der Query wirkt, und die <strong>Mandantentrennung</strong>
 * aus Sicht eines fremden Users mit eigenen wiederkehrenden Gutschriften.
 *
 * <p>Die {@link Clock} ist als {@link MockitoBean} auf einen festen Zeitpunkt gestellt — sonst
 * verschöbe sich das Fenster mit jedem CI-Lauf und der Test würde irgendwann von selbst rot.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zum
 * {@code SafeToSpendServiceIntegrationTest}.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IncomeSuggestionServiceIntegrationTest {

    /** 11.08.2026, 12:00 Ortszeit — das Fenster reicht damit zurück bis zum 11.08.2025. */
    private static final Instant STICHTAG = LocalDate.of(2026, 8, 11)
            .atTime(12, 0)
            .atZone(ZoneId.of("Europe/Zurich"))
            .toInstant();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "income_suggestion_service");
    }

    @Autowired private IncomeSuggestionService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private Clock clock;

    @BeforeEach
    void fixTheClock() {
        when(clock.instant()).thenReturn(STICHTAG);
    }

    @Test
    void recognisesTheRecurringSalaryAcrossMonths() {
        // Beträge und Buchungstext aus dem UBS-Fixture.
        long lara = insertUser("lara-inc@example.com");
        insertIncome(lara, LocalDate.of(2026, 5, 25), "Saläreingang", new BigDecimal("6800.00"));
        insertIncome(lara, LocalDate.of(2026, 6, 25), "Saläreingang", new BigDecimal("6800.00"));
        insertIncome(lara, LocalDate.of(2026, 7, 25), "Saläreingang", new BigDecimal("6800.00"));

        assertThat(service.suggestMonthlyIncome(lara)).hasValue(new BigDecimal("6800.00"));
    }

    @Test
    void groupsAcrossChangingMonthNamesInTheBookingText() {
        // Post-Fixture: «GUTSCHRIFT LOHN <Monat>» — drei verschiedene Texte, eine Gruppe.
        long lara = insertUser("lara-inc-month@example.com");
        insertIncome(lara, LocalDate.of(2026, 5, 30), "GUTSCHRIFT LOHN MAI", new BigDecimal("5500.00"));
        insertIncome(lara, LocalDate.of(2026, 6, 30), "GUTSCHRIFT LOHN JUNI", new BigDecimal("5500.00"));
        insertIncome(lara, LocalDate.of(2026, 7, 30), "GUTSCHRIFT LOHN JULI", new BigDecimal("5500.00"));

        assertThat(service.suggestMonthlyIncome(lara)).hasValue(new BigDecimal("5500.00"));
    }

    @Test
    void recurringExpensesNeverBecomeAnIncomeSuggestion() {
        // Dieselbe Regelmässigkeit, aber als Belastung gebucht: eine Miete ist kein Einkommen.
        // Belegt den is_income-Filter im echten Schema — im Unit-Test liefert das Mock einfach,
        // was man ihm gibt.
        long marc = insertUser("marc-inc-expenses@example.com");
        insertExpense(marc, LocalDate.of(2026, 5, 1), "GIRO MIETE", new BigDecimal("1500.00"));
        insertExpense(marc, LocalDate.of(2026, 6, 1), "GIRO MIETE", new BigDecimal("1500.00"));
        insertExpense(marc, LocalDate.of(2026, 7, 1), "GIRO MIETE", new BigDecimal("1500.00"));

        assertThat(service.suggestMonthlyIncome(marc)).isEmpty();
    }

    @Test
    void creditsOlderThanTwelveMonthsDoNotWin() {
        // Der alte Lohn ist höher und käme ohne das Fenster als Vorschlag heraus — die Auswahlregel
        // «höchster Median» würde ihn wählen. Der Test ist damit auch ein Fenster-Nachweis und
        // nicht nur eine Aussage über den Rückgabewert.
        long lara = insertUser("lara-inc-window@example.com");
        insertIncome(lara, LocalDate.of(2024, 3, 25), "Altlohn", new BigDecimal("9999.00"));
        insertIncome(lara, LocalDate.of(2024, 4, 25), "Altlohn", new BigDecimal("9999.00"));
        insertIncome(lara, LocalDate.of(2026, 6, 25), "Saläreingang", new BigDecimal("6800.00"));
        insertIncome(lara, LocalDate.of(2026, 7, 25), "Saläreingang", new BigDecimal("6800.00"));

        assertThat(service.suggestMonthlyIncome(lara)).hasValue(new BigDecimal("6800.00"));
    }

    @Test
    void withoutAnyCreditsThereIsNoSuggestion() {
        long marc = insertUser("marc-inc-empty@example.com");

        assertThat(service.suggestMonthlyIncome(marc)).isEmpty();
    }

    // --- Mandantentrennung: Gegenprobe mit einem zweiten User im selben Zeitraum ---

    @Test
    void aForeignUsersCreditsDoNotAffectTheSuggestion() {
        long lara = insertUser("lara-inc-isolation@example.com");
        insertIncome(lara, LocalDate.of(2026, 6, 25), "Saläreingang", new BigDecimal("4200.00"));
        insertIncome(lara, LocalDate.of(2026, 7, 25), "Saläreingang", new BigDecimal("4200.00"));

        var allein = service.suggestMonthlyIncome(lara);

        // Marc hat im selben Zeitraum eine höhere wiederkehrende Gutschrift. Würde die Query den
        // User nicht einschränken, gewänne sein Betrag über die Regel «höchster Median» — Laras
        // Vorschlag spränge sichtbar von 4200.00 auf 9100.00.
        long marc = insertUser("marc-inc-isolation@example.com");
        insertIncome(marc, LocalDate.of(2026, 6, 25), "Saläreingang", new BigDecimal("9100.00"));
        insertIncome(marc, LocalDate.of(2026, 7, 25), "Saläreingang", new BigDecimal("9100.00"));

        assertThat(allein).hasValue(new BigDecimal("4200.00"));
        assertThat(service.suggestMonthlyIncome(lara)).hasValue(new BigDecimal("4200.00"));

        // Gegenprobe in die andere Richtung: Marc sieht ausschliesslich seine eigenen Gutschriften.
        assertThat(service.suggestMonthlyIncome(marc)).hasValue(new BigDecimal("9100.00"));
    }

    // --- Helfer ---

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income) VALUES (?, ?, ?)",
                email, "$2a$10$test.only.not.a.real.hash", null);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertIncome(long userId, LocalDate datum, String text, BigDecimal betrag) {
        insertTransaction(userId, datum, text, betrag, true);
    }

    private void insertExpense(long userId, LocalDate datum, String text, BigDecimal betrag) {
        insertTransaction(userId, datum, text, betrag, false);
    }

    private void insertTransaction(
            long userId, LocalDate datum, String text, BigDecimal betrag, boolean income) {
        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, buchungsdatum, buchungstext, betrag, is_income) "
                        + "VALUES (?, ?, ?, ?, ?)",
                userId, datum, text, betrag, income);
    }
}
