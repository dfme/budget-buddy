package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifiziert die Flyway-Migration V14 ({@code recurring_expenses.notification_id}, FE-NOTIF-04,
 * #336) gegen eine echte PostgreSQL-Datenbank — samt Backfill.
 *
 * <p>Anders als die übrigen Migrationstests läuft Flyway hier <em>nicht</em> beim Kontextstart
 * ({@link PostgresTestDatabase#registerWithoutFlyway}), sondern von Hand in zwei Schritten: erst
 * bis V13, dann werden Bestandsdaten im alten Zuschnitt eingefügt — eine Zeile mit ihrer
 * Einzel-Notification über {@code reference_id} —, dann V14. Nur so ist der Backfill belegt; auf
 * einer leeren Tabelle wäre das {@code UPDATE} ein No-op und der Test sagte nichts.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecurringExpensesNotificationIdMigrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "recurring_expenses_notification_id");
    }

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void backfillLinksLegacyRowsToTheirSingleNotification_andLeavesOrphansNull() {
        migrateTo("13");
        long lara = insertUser("lara-v14@example.ch");
        long marc = insertUser("marc-v14@example.ch");
        // Laras Netflix-Zeile mit Einzel-Notification (BE-REC-01 vor #336) — muss verknüpft werden.
        long netflix = insertExpense(lara, "NETFLIX INTERNATIONAL BV");
        long netflixNotification = insertNotification(lara, "RECURRING_EXPENSE_DETECTED", netflix);
        // Laras Spotify-Zeile, deren Notification schon weg ist — bleibt ohne Bündel.
        long spotify = insertExpense(lara, "SPOTIFY AB");
        // Marcs Notification zeigt zufällig auf Laras Zeilen-ID (reference_id ist FK-los) — der
        // Backfill ist über user_id gebunden und darf sie nicht verwenden.
        insertNotification(marc, "RECURRING_EXPENSE_DETECTED", netflix);
        // Eine Notification anderen Typs mit derselben reference_id zählt ebenfalls nicht.
        insertNotification(lara, "MONTHLY_REPORT_READY", spotify);

        migrateTo("14");

        assertThat(notificationIdOf(netflix)).isEqualTo(netflixNotification);
        assertThat(notificationIdOf(spotify)).isNull();

        Map<String, String> types = new SchemaInspector(jdbcTemplate).columnTypes("recurring_expenses");
        assertThat(types).containsEntry("notification_id", "bigint");
        assertThat(new SchemaInspector(jdbcTemplate).notNullFlags("recurring_expenses"))
                .containsEntry("notification_id", false);
    }

    // --- Helfer ---

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)", email, "bcrypt-hash");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertExpense(long userId, String payeeKey) {
        jdbcTemplate.update(
                "INSERT INTO recurring_expenses"
                        + " (user_id, payee_key, amount, status, first_detected_month)"
                        + " VALUES (?, ?, ?, 'DETECTED', '2026-06')",
                userId, payeeKey, new BigDecimal("20.90"));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM recurring_expenses WHERE user_id = ? AND payee_key = ?",
                Long.class, userId, payeeKey);
    }

    private long insertNotification(long userId, String type, long referenceId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO notifications (user_id, type, reference_id, message)"
                        + " VALUES (?, ?, ?, 'Abo erkannt') RETURNING id",
                Long.class, userId, type, referenceId);
    }

    private Long notificationIdOf(long expenseId) {
        return jdbcTemplate.queryForObject(
                "SELECT notification_id FROM recurring_expenses WHERE id = ?", Long.class, expenseId);
    }
}
