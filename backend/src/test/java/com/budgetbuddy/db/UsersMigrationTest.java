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
 * Verifiziert die Flyway-Migration V01 (users-Tabelle) gegen eine echte PostgreSQL-Datenbank.
 *
 * <p>Seit DB-05 (ADR-12) läuft der Test gegen einen Testcontainers-Postgres in derselben
 * Major-Version wie Produktion, mit einer eigenen Datenbank für diese Klasse (siehe
 * {@link PostgresTestDatabase}). Vorher war es eine SQLite-Temp-Datei — eine andere Engine als
 * Produktion und damit genau der Dialekt-Mismatch, den die Migration beseitigt hat.
 *
 * <p>{@code @DirtiesContext} schliesst den Kontext und damit den Hikari-Pool nach der Klasse, statt
 * die Verbindungen bis zum Ende des Testlaufs offen zu halten.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UsersMigrationTest {

    private static final String TABLE = "users";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "users_migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SchemaInspector schema() {
        return new SchemaInspector(jdbcTemplate);
    }

    @Test
    void migrationRunsSuccessfully() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(1);
    }

    @Test
    void usersTableHasAllColumnsWithCorrectTypes() {
        Map<String, String> typeByColumn = schema().columnTypes(TABLE);

        assertThat(typeByColumn).containsOnlyKeys(
                "id", "email", "password_hash", "monthly_income", "onboarding_completed");

        assertThat(typeByColumn.get("id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("email")).isEqualTo("text");
        assertThat(typeByColumn.get("password_hash")).isEqualTo("text");
        assertThat(typeByColumn.get("onboarding_completed")).isEqualTo("boolean");
    }

    @Test
    void idIsIdentityPrimaryKey() {
        // Ersatz für SQLites AUTOINCREMENT: ohne Identity-Spalte müsste jeder Insert die ID selbst
        // mitliefern, und GenerationType.IDENTITY in der User-Entity liefe ins Leere.
        assertThat(schema().primaryKeyColumns(TABLE)).containsExactly("id");
        assertThat(schema().isIdentity(TABLE, "id")).isTrue();
    }

    @Test
    void monthlyIncomeIsDecimalNotFloat() {
        assertThat(schema().columnTypes(TABLE).get("monthly_income"))
                .isEqualTo("numeric")
                .doesNotContainIgnoringCase("double")
                .doesNotContainIgnoringCase("real");

        assertThat(schema().numericPrecisionAndScale(TABLE, "monthly_income")).isEqualTo("10,2");
    }

    @Test
    void emailIsNotNullAndUnique() {
        assertThat(schema().notNullFlags(TABLE).get("email")).isTrue();
        assertThat(schema().hasUniqueConstraintOn(TABLE, "email")).isTrue();
    }
}
