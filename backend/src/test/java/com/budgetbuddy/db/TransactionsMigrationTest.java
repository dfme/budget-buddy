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
 * Verifiziert die Flyway-Migration V02 (transactions-Tabelle) gegen eine echte
 * PostgreSQL-Datenbank.
 *
 * <p>Seit DB-05 (ADR-12) gegen Testcontainers-Postgres in derselben Major-Version wie Produktion,
 * mit einer eigenen Datenbank für diese Klasse (siehe {@link PostgresTestDatabase}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionsMigrationTest {

    private static final String TABLE = "transactions";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "transactions_migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SchemaInspector schema() {
        return new SchemaInspector(jdbcTemplate);
    }

    @Test
    void migrationsRunSuccessfullyAfterV1() {
        // V01 (users) + V02 (transactions) müssen beide erfolgreich gelaufen sein.
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(2);
    }

    @Test
    void transactionsTableHasAllColumnsWithCorrectTypes() {
        Map<String, String> typeByColumn = schema().columnTypes(TABLE);

        assertThat(typeByColumn).containsOnlyKeys(
                "id", "user_id", "buchungsdatum", "buchungstext", "betrag",
                "is_income", "category", "pdf_sha256");

        assertThat(typeByColumn.get("id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("user_id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("buchungsdatum")).isEqualTo("date");
        assertThat(typeByColumn.get("buchungstext")).isEqualTo("text");
        assertThat(typeByColumn.get("is_income")).isEqualTo("boolean");
        assertThat(typeByColumn.get("category")).isEqualTo("text");
        assertThat(typeByColumn.get("pdf_sha256")).isEqualTo("text");
    }

    @Test
    void idIsIdentityPrimaryKey() {
        assertThat(schema().primaryKeyColumns(TABLE)).containsExactly("id");
        assertThat(schema().isIdentity(TABLE, "id")).isTrue();
    }

    @Test
    void betragIsDecimalNotFloat() {
        assertThat(schema().columnTypes(TABLE).get("betrag"))
                .isEqualTo("numeric")
                .doesNotContainIgnoringCase("double")
                .doesNotContainIgnoringCase("real");

        assertThat(schema().numericPrecisionAndScale(TABLE, "betrag")).isEqualTo("10,2");
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
    void requiredColumnsAreNotNullAndPdfSha256IsNullable() {
        Map<String, Boolean> notNullByColumn = schema().notNullFlags(TABLE);

        assertThat(notNullByColumn.get("user_id")).isTrue();
        assertThat(notNullByColumn.get("buchungsdatum")).isTrue();
        assertThat(notNullByColumn.get("buchungstext")).isTrue();
        assertThat(notNullByColumn.get("betrag")).isTrue();
        assertThat(notNullByColumn.get("is_income")).isTrue();

        // AC: pdf_sha256 erlaubt NULL; category wird erst später gesetzt.
        assertThat(notNullByColumn.get("pdf_sha256")).isFalse();
        assertThat(notNullByColumn.get("category")).isFalse();
    }
}
