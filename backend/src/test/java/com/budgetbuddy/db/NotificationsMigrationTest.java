package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifiziert die Flyway-Migration V08 (notifications-Tabelle) gegen eine echte
 * PostgreSQL-Datenbank.
 *
 * <p>Seit DB-05 (ADR-12) gegen Testcontainers-Postgres in derselben Major-Version wie Produktion,
 * mit einer eigenen Datenbank für diese Klasse (siehe {@link PostgresTestDatabase}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationsMigrationTest {

    private static final String TABLE = "notifications";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "notifications_migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SchemaInspector schema() {
        return new SchemaInspector(jdbcTemplate);
    }

    @Test
    void migrationsRunSuccessfullyThroughV08() {
        // V01 (users) ... V08 (notifications) müssen alle erfolgreich gelaufen sein.
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(8);
    }

    @Test
    void notificationsTableHasAllColumnsWithCorrectTypes() {
        Map<String, String> typeByColumn = schema().columnTypes(TABLE);

        assertThat(typeByColumn).containsOnlyKeys(
                "id", "user_id", "type", "reference_id", "message", "read_at", "created_at");

        assertThat(typeByColumn.get("id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("user_id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("type")).isEqualTo("text");
        assertThat(typeByColumn.get("reference_id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("message")).isEqualTo("text");
        assertThat(typeByColumn.get("read_at")).isEqualTo("timestamp with time zone");
        assertThat(typeByColumn.get("created_at")).isEqualTo("timestamp with time zone");
    }

    @Test
    void idIsIdentityPrimaryKey() {
        assertThat(schema().primaryKeyColumns(TABLE)).containsExactly("id");
        assertThat(schema().isIdentity(TABLE, "id")).isTrue();
    }

    @Test
    void foreignKeyToUsersIsDefined() {
        assertThat(schema().foreignKeys(TABLE)).anySatisfy(fk -> {
            assertThat(fk.get("column")).isEqualTo("user_id");
            assertThat(fk.get("referenced_table")).isEqualTo("users");
            assertThat(fk.get("referenced_column")).isEqualTo("id");
        });
    }

    @Test
    void requiredColumnsAreNotNull() {
        Map<String, Boolean> notNullByColumn = schema().notNullFlags(TABLE);

        assertThat(notNullByColumn.get("user_id")).isTrue();
        assertThat(notNullByColumn.get("type")).isTrue();
        assertThat(notNullByColumn.get("message")).isTrue();
        assertThat(notNullByColumn.get("created_at")).isTrue();
    }

    @Test
    void referenceIdAndReadAtAreNullable() {
        // reference_id: polymorpher Verweis, nicht jede Notification hat eine auslösende Zeile.
        // read_at: NULL = ungelesen, gesetzter Zeitstempel = gelesen (POST .../read, BE-NOTIF-01).
        Map<String, Boolean> notNullByColumn = schema().notNullFlags(TABLE);

        assertThat(notNullByColumn.get("reference_id")).isFalse();
        assertThat(notNullByColumn.get("read_at")).isFalse();
    }

    @Test
    void indexOnUserIdAndReadAtExists() {
        // Deckt den geplanten GET /api/notifications (ungelesen zuerst, pro Nutzer) aus
        // BE-NOTIF-01 ab.
        assertThat(schema().hasIndexNamed(TABLE, "idx_notifications_user_read")).isTrue();
    }
}
