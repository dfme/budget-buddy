package com.budgetbuddy.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.notification.NotificationService;
import com.budgetbuddy.recurring.dto.RecurringExpenseResponse;
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
 * {@code UNIQUE}-Constraints aus V11, die eine Bündel-Notification pro Lauf mit
 * {@code notification_id} auf jeder neuen Zeile (V14, FE-NOTIF-04) — und die
 * <strong>Mandantentrennung</strong> aus Sicht eines zweiten Users.
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

    private static final String NETFLIX = "NETFLIX INTERNATIONAL BV";

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
    @Autowired private NotificationService notificationService;
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

    /**
     * AC 4 (US-08) im Zuschnitt von FE-NOTIF-04 (#336): <em>eine</em> Notification vom Typ
     * RECURRING_EXPENSE_DETECTED pro Lauf, an der alle acht Zeilen über {@code notification_id}
     * hängen. Vorher waren es acht Notifications, verknüpft über {@code reference_id}.
     */
    @Test
    void notifiesOncePerRun_andEveryRowPointsToThatNotification() {
        importStatement(lara, POST_JAHR);

        service.detect(lara);

        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "SELECT id, type, reference_id, message, read_at FROM notifications WHERE user_id = ?",
                lara);
        assertThat(notifications).hasSize(1);
        Map<String, Object> bundle = notifications.getFirst();
        assertThat(bundle.get("type")).isEqualTo("RECURRING_EXPENSE_DETECTED");
        assertThat(bundle.get("reference_id")).isNull();
        assertThat(bundle.get("read_at")).isNull();
        assertThat(bundle.get("message")).isEqualTo(
                "8 neue Abos erkannt: CSS VERSICHERUNG AG, KONTOFÜHRUNG, MUSTER IMMOBILIEN AG"
                        + " und 5 weitere");

        List<Long> notificationIds = jdbcTemplate.queryForList(
                "SELECT notification_id FROM recurring_expenses WHERE user_id = ?", Long.class, lara);
        assertThat(notificationIds).hasSize(ERKANNT.size()).containsOnly((Long) bundle.get("id"));
    }

    /**
     * AC 1 von #336 über die ganze Kette: nach einem Import mit acht erkannten Abos genügt
     * <em>eine</em> Kenntnisnahme — die der Bündel-Notification —, und kein Eintrag ist mehr «Neu».
     */
    @Test
    void readingTheBundleNotificationClearsNewOnEveryDetectedEntry() {
        importStatement(lara, POST_JAHR);
        service.detect(lara);
        assertThat(service.list(lara)).hasSize(ERKANNT.size()).allMatch(RecurringExpenseResponse::isNew);
        long bundleId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ?", Long.class, lara);

        notificationService.markAsRead(lara, bundleId);

        assertThat(service.list(lara)).hasSize(ERKANNT.size()).noneMatch(RecurringExpenseResponse::isNew);
    }

    /** Ein zweiter Lauf über denselben Bestand — der nächste Import — schreibt nichts Neues. */
    @Test
    void secondRunOverTheSameHistory_isIdempotent() {
        importStatement(lara, POST_JAHR);
        service.detect(lara);

        service.detect(lara);

        assertThat(count("recurring_expenses", lara)).isEqualTo(ERKANNT.size());
        // Ein Lauf ohne Treffer meldet nichts — es bleibt beim einen Bündel des ersten Laufs.
        assertThat(count("notifications", lara)).isEqualTo(1);
    }

    /** AC 5 im echten Schema: eine DISMISSED-Zeile bleibt die einzige Zeile dieses Empfängers. */
    @Test
    void dismissedPayee_staysDismissedAndGetsNoNotification() {
        importStatement(lara, POST_JAHR);
        jdbcTemplate.update(
                "INSERT INTO recurring_expenses"
                        + " (user_id, payee_key, amount, status, first_detected_month)"
                        + " VALUES (?, ?, ?, 'DISMISSED', '2024-11')",
                lara, NETFLIX, new BigDecimal("20.90"));

        service.detect(lara);

        List<Map<String, Object>> netflix = jdbcTemplate.queryForList(
                "SELECT status FROM recurring_expenses WHERE user_id = ? AND payee_key = ?",
                lara, NETFLIX);
        assertThat(netflix).hasSize(1);
        assertThat(netflix.getFirst().get("status")).isEqualTo("DISMISSED");
        assertThat(count("recurring_expenses", lara)).isEqualTo(ERKANNT.size());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM notifications WHERE user_id = ?", String.class, lara))
                .startsWith("7 neue Abos erkannt: ");
    }

    // --- BE-REC-04: der zweite Import bewertet die Zeilen neu ---

    /**
     * AC 1 im echten Schema: Netflix steigt nach dem Jahresauszug von 20.90 auf 25.90. Der zweite
     * Lauf zieht Betrag und Erstmonat nach — ohne zweite Zeile ({@code UNIQUE (user_id, payee_key)})
     * und ohne zweites Bündel.
     *
     * <p>Der Sprung liegt ausserhalb der ±2 %, das Paar über ihn hinweg qualifiziert also nicht und
     * die Reihe beginnt im Januar 2026 neu — genau der Fall, der bis BE-REC-04 im Safe-to-Spend
     * doppelt zählte (ADR-13-Nachtrag, «Abbuchung ausserhalb des Bands»).
     */
    @Test
    void aPriceJumpAfterTheFirstRun_updatesTheRowWithoutASecondNotification() {
        importStatement(lara, POST_JAHR);
        service.detect(lara);

        insertExpense(lara, LocalDate.of(2026, 1, 3), "LASTSCHRIFT", NETFLIX, new BigDecimal("25.90"));
        insertExpense(lara, LocalDate.of(2026, 2, 3), "LASTSCHRIFT", NETFLIX, new BigDecimal("25.90"));

        service.detect(lara);

        Map<String, Object> netflix = jdbcTemplate.queryForMap(
                "SELECT amount, status, first_detected_month, notification_id"
                        + " FROM recurring_expenses WHERE user_id = ? AND payee_key = ?",
                lara, NETFLIX);
        assertThat((BigDecimal) netflix.get("amount")).isEqualByComparingTo("25.90");
        assertThat(netflix.get("first_detected_month")).isEqualTo("2026-01");
        assertThat(netflix.get("status")).isEqualTo("DETECTED");
        assertThat(count("recurring_expenses", lara)).isEqualTo(ERKANNT.size());
        assertThat(count("notifications", lara)).isEqualTo(1);
    }

    /**
     * AC 2 im echten Schema: nach dem Jahresauszug 2025 folgen nur noch fremde Belastungen. Die
     * acht Reihen haben im Fenster der drei jüngsten Monate keine Abbuchung mehr und laufen aus —
     * sie mindern damit den Safe-to-Spend nicht mehr, ohne dass jemand «Kein Abo» klicken müsste.
     */
    @Test
    void payeesThatStopDebiting_becomeEndedAndDropOutOfDetectedAmounts() {
        importStatement(lara, POST_JAHR);
        service.detect(lara);
        assertThat(service.detectedAmounts(lara)).hasSize(ERKANNT.size());

        insertExpense(lara, LocalDate.of(2026, 4, 8), "LASTSCHRIFT", "COOP BERN",
                new BigDecimal("45.60"));

        service.detect(lara);

        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM recurring_expenses WHERE user_id = ?", String.class, lara);
        assertThat(status).hasSize(ERKANNT.size()).containsOnly("ENDED");
        assertThat(service.detectedAmounts(lara)).isEmpty();
        // Keine neue Meldung für das Auslaufen — der «Neu»-Hinweis gilt dem Fund (AC 4).
        assertThat(count("notifications", lara)).isEqualTo(1);
    }

    /** Bucht der Empfänger wieder ab, läuft die Zeile wieder — lautlos und ohne neue Zeile. */
    @Test
    void anEndedPayeeThatDebitsAgain_returnsToDetected() {
        importStatement(lara, POST_JAHR);
        service.detect(lara);
        insertExpense(lara, LocalDate.of(2026, 4, 8), "LASTSCHRIFT", "COOP BERN",
                new BigDecimal("45.60"));
        service.detect(lara);

        insertExpense(lara, LocalDate.of(2026, 5, 3), "LASTSCHRIFT", NETFLIX, new BigDecimal("20.90"));
        insertExpense(lara, LocalDate.of(2026, 6, 3), "LASTSCHRIFT", NETFLIX, new BigDecimal("20.90"));

        service.detect(lara);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM recurring_expenses WHERE user_id = ? AND payee_key = ?",
                String.class, lara, NETFLIX)).isEqualTo("DETECTED");
        assertThat(count("recurring_expenses", lara)).isEqualTo(ERKANNT.size());
        assertThat(count("notifications", lara)).isEqualTo(1);
    }

    /**
     * Mandantentrennung der Aktivitätsprüfung: Marc bucht NETFLIX weiter, Lara nicht. Griffe die
     * Prüfung über den User hinweg, hielte Marcs Abbuchung Laras Zeile am Leben und minderte
     * ihren Safe-to-Spend um ein Abo, das sie gekündigt hat.
     *
     * <p>Diese Prüfung lag bis BE-REC-04 im Lesepfad des budget-Moduls und ist mit dem
     * Aktivitätsfenster hierher gewandert.
     */
    @Test
    void aForeignUsersDebitsDoNotKeepAnOwnPayeeActive() {
        importStatement(lara, POST_JAHR);
        service.detect(lara);
        insertExpense(lara, LocalDate.of(2026, 4, 8), "LASTSCHRIFT", "COOP BERN",
                new BigDecimal("45.60"));
        insertExpense(marc, LocalDate.of(2026, 4, 3), "LASTSCHRIFT", NETFLIX, new BigDecimal("20.90"));
        insertExpense(marc, LocalDate.of(2026, 5, 3), "LASTSCHRIFT", NETFLIX, new BigDecimal("20.90"));

        service.detect(lara);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM recurring_expenses WHERE user_id = ? AND payee_key = ?",
                String.class, lara, NETFLIX)).isEqualTo("ENDED");
        assertThat(service.detectedAmounts(lara)).isEmpty();
    }

    /**
     * Mandantentrennung: Marcs Erkennung sieht Laras Auszug nicht — obwohl beide dieselbe
     * Netflix-Belastung haben. Marc hat sie nur in einem Monat; ohne Laras Zeilen bleibt sie
     * ein Einzelfall.
     */
    @Test
    void anotherUsersTransactions_doNotLeakIntoDetection() {
        importStatement(lara, POST_JAHR);
        insertExpense(marc, LocalDate.of(2025, 2, 5), "LASTSCHRIFT", NETFLIX,
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
