package com.budgetbuddy.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.transaction.ParsedTransaction;
import com.budgetbuddy.transaction.SwissBankStatementParser;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest der Abo-Erkennung (BE-REC-01, US-08) gegen echtes PostgreSQL + Flyway.
 *
 * <p>Belegt, was der Unit-Test mit gemockten Ports nicht belegen kann: dass die Kette
 * {@code TransactionRepository → ExpenseHistoryPort → RecurringExpenseService →
 * recurring_expenses/notifications} im echten Schema zusammenhält — die Ableitung von
 * {@code findByUserIdAndIncomeFalse}, das Schreiben gegen die {@code CHECK}- und
 * {@code UNIQUE}-Constraints aus V11, die Notification mit {@code reference_id} auf die neue Zeile
 * — und die <strong>Mandantentrennung</strong> aus Sicht eines zweiten Users.
 *
 * <p>Die Daten kommen aus dem echten Jahresauszug {@code Post_Kontoauszug_2025_240_Buchungen.pdf},
 * über den {@link SwissBankStatementParser} wie im Import. Dort stehen fünf Abo-Positionen zwölfmal
 * mit identischem Betrag ({@code SwissBankStatementParserFixtureTest#recurringFixedCosts…}). Die
 * Regel aus US-08 trifft darüber hinaus drei weitere Empfänger, und zwar zu Recht nach ihrem
 * Wortlaut: die monatliche Kontoführungsgebühr, den monatlichen Bargeldbezug am selben Postomat
 * über denselben Betrag, und die Stadtwerke-Rechnung, deren schwankende Beträge in genau einem
 * Folgemonatspaar (März→April, 74.90→73.65) innerhalb der ±2 % liegen. Der Test schreibt alle acht
 * fest — eine Verschärfung der Regel würde hier sichtbar, nicht still.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecurringExpenseDetectionIntegrationTest {

    private static final String POST_JAHR = "/pdf/Post_Kontoauszug_2025_240_Buchungen.pdf";

    /** Ein erwarteter Eintrag: Betrag des jüngsten Paars und erster Monat der Reihe. */
    private record Erwartung(String amount, String firstMonth) {}

    /**
     * Alles, was die Regel in der Fixture trifft (siehe Klassen-Javadoc). Die fünf Abos beginnen
     * im Januar 2025, dem ersten Monat des Auszugs; die Stadtwerke-Reihe erst im März, weil nur
     * das Paar März→April innerhalb der Toleranz liegt — gespeichert wird dessen späterer Betrag.
     */
    private static final Map<String, Erwartung> ERKANNT = Map.of(
            "CSS VERSICHERUNG AG", new Erwartung("320.50", "2025-01"),
            "SWISSCOM (SCHWEIZ) AG", new Erwartung("65.00", "2025-01"),
            "NETFLIX INTERNATIONAL BV", new Erwartung("20.90", "2025-01"),
            "SPOTIFY AB", new Erwartung("12.95", "2025-01"),
            "MUSTER IMMOBILIEN AG", new Erwartung("1250.00", "2025-01"),
            "KONTOFÜHRUNG", new Erwartung("5.00", "2025-01"),
            "POSTOMAT BAHNHOF BERN", new Erwartung("200.00", "2025-01"),
            "STADTWERKE BERN", new Erwartung("73.65", "2025-03"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "recurring_expense_detection");
    }

    @Autowired private RecurringExpenseService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final SwissBankStatementParser parser = new SwissBankStatementParser();

    private long lara;
    private long marc;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM recurring_expenses");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM users");
        lara = insertUser("lara-rec@example.ch");
        marc = insertUser("marc-rec@example.ch");
    }

    @Test
    void detectsTheRecurringPositionsOfTheYearlyStatement() {
        importStatement(lara, POST_JAHR);

        service.detect(lara);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT payee_key, amount, status, first_detected_month FROM recurring_expenses"
                        + " WHERE user_id = ? ORDER BY payee_key", lara);
        assertThat(rows).extracting(r -> r.get("payee_key"))
                .containsExactlyInAnyOrderElementsOf(ERKANNT.keySet());
        assertThat(rows).allSatisfy(r -> {
            Erwartung erwartet = ERKANNT.get((String) r.get("payee_key"));
            assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo(erwartet.amount());
            assertThat(r.get("status")).isEqualTo("DETECTED");
            assertThat(r.get("first_detected_month")).isEqualTo(erwartet.firstMonth());
        });
    }

    /** AC 4: je Abo eine Notification vom Typ RECURRING_EXPENSE_DETECTED, verknüpft über reference_id. */
    @Test
    void notifiesOncePerDetectedPayee_withTheRowIdAsReference() {
        importStatement(lara, POST_JAHR);

        service.detect(lara);

        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "SELECT n.type, n.reference_id, n.message, n.read_at, r.payee_key"
                        + " FROM notifications n"
                        + " JOIN recurring_expenses r ON r.id = n.reference_id"
                        + " WHERE n.user_id = ?", lara);
        assertThat(notifications).hasSize(ERKANNT.size());
        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.get("type")).isEqualTo("RECURRING_EXPENSE_DETECTED");
            assertThat(n.get("read_at")).isNull();
            assertThat((String) n.get("message")).contains((String) n.get("payee_key"));
        });
    }

    /** Ein zweiter Lauf über denselben Bestand — der nächste Import — schreibt nichts Neues. */
    @Test
    void secondRunOverTheSameHistory_isIdempotent() {
        importStatement(lara, POST_JAHR);
        service.detect(lara);

        service.detect(lara);

        assertThat(count("recurring_expenses", lara)).isEqualTo(ERKANNT.size());
        assertThat(count("notifications", lara)).isEqualTo(ERKANNT.size());
    }

    /** AC 5 im echten Schema: eine DISMISSED-Zeile bleibt die einzige Zeile dieses Empfängers. */
    @Test
    void dismissedPayee_staysDismissedAndGetsNoNotification() {
        importStatement(lara, POST_JAHR);
        jdbcTemplate.update(
                "INSERT INTO recurring_expenses"
                        + " (user_id, payee_key, amount, status, first_detected_month)"
                        + " VALUES (?, ?, ?, 'DISMISSED', '2024-11')",
                lara, "NETFLIX INTERNATIONAL BV", new BigDecimal("20.90"));

        service.detect(lara);

        List<Map<String, Object>> netflix = jdbcTemplate.queryForList(
                "SELECT status FROM recurring_expenses WHERE user_id = ? AND payee_key = ?",
                lara, "NETFLIX INTERNATIONAL BV");
        assertThat(netflix).hasSize(1);
        assertThat(netflix.getFirst().get("status")).isEqualTo("DISMISSED");
        assertThat(count("recurring_expenses", lara)).isEqualTo(ERKANNT.size());
        assertThat(count("notifications", lara)).isEqualTo(ERKANNT.size() - 1);
    }

    /**
     * Mandantentrennung: Marcs Erkennung sieht Laras Auszug nicht — obwohl beide dieselbe
     * Netflix-Belastung haben. Marc hat sie nur in einem Monat; ohne Laras Zeilen bleibt sie
     * ein Einzelfall.
     */
    @Test
    void anotherUsersTransactions_doNotLeakIntoDetection() {
        importStatement(lara, POST_JAHR);
        insertExpense(marc, LocalDate.of(2025, 2, 5), "LASTSCHRIFT", "NETFLIX INTERNATIONAL BV",
                new BigDecimal("20.90"));

        service.detect(marc);

        assertThat(count("recurring_expenses", marc)).isZero();
        assertThat(count("notifications", marc)).isZero();
        // Und umgekehrt: Laras Lauf schreibt nichts auf Marcs Konto.
        service.detect(lara);
        assertThat(count("recurring_expenses", marc)).isZero();
        assertThat(count("recurring_expenses", lara)).isEqualTo(ERKANNT.size());
    }

    // --- Helfer ---

    /** Persistiert einen Auszug so, wie {@code ImportJobRunner} es tut — Buchungstext und Details getrennt. */
    private void importStatement(long userId, String fixture) {
        for (ParsedTransaction tx : parser.parse(bytes(fixture))) {
            jdbcTemplate.update(
                    "INSERT INTO transactions"
                            + " (user_id, buchungsdatum, buchungstext, buchungsdetails, betrag,"
                            + " is_income, category, pdf_sha256)"
                            + " VALUES (?, ?, ?, ?, ?, ?, 'Sonstiges', 'fixture')",
                    userId, tx.buchungsdatum(), tx.buchungstext(), tx.detailsAsText(),
                    tx.betrag(), tx.isIncome());
        }
    }

    private void insertExpense(long userId, LocalDate datum, String text, String details,
            BigDecimal betrag) {
        jdbcTemplate.update(
                "INSERT INTO transactions"
                        + " (user_id, buchungsdatum, buchungstext, buchungsdetails, betrag, is_income)"
                        + " VALUES (?, ?, ?, ?, ?, false)",
                userId, datum, text, details, betrag);
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income) VALUES (?, ?, ?)",
                email, "$2a$10$test.only.not.a.real.hash", null);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private int count(String table, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private static byte[] bytes(String classpathResource) {
        try (InputStream in = RecurringExpenseDetectionIntegrationTest.class
                .getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath: " + classpathResource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
