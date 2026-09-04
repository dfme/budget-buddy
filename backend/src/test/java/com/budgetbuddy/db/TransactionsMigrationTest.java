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
 * Verifiziert die Flyway-Migrationen der transactions-Tabelle gegen eine echte
 * PostgreSQL-Datenbank: V02 (Anlage, DB-02), V06 (Spalte {@code buchungsdetails}, BE-PDF-07) und
 * V08 (Spalte {@code direction_uncertain}, BE-PDF-10).
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
                "id", "user_id", "buchungsdatum", "buchungstext", "buchungsdetails", "betrag",
                "is_income", "direction_uncertain", "category", "pdf_sha256");

        assertThat(typeByColumn.get("id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("user_id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("buchungsdatum")).isEqualTo("date");
        assertThat(typeByColumn.get("buchungstext")).isEqualTo("text");
        assertThat(typeByColumn.get("buchungsdetails")).isEqualTo("text");
        assertThat(typeByColumn.get("is_income")).isEqualTo("boolean");
        assertThat(typeByColumn.get("direction_uncertain")).isEqualTo("boolean");
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

        // AC BE-PDF-07 «bestehende Importe brechen nicht»: Vor V06 geschriebene Zeilen haben
        // keinen Wert für diese Spalte und sind per Reimport auch nicht nachzuziehen — die
        // Detailzeilen stehen nur im Quell-PDF. NOT NULL hätte die Migration auf jeder Datenbank
        // mit Bestand scheitern lassen.
        assertThat(notNullByColumn.get("buchungsdetails")).isFalse();

        // BE-PDF-10: direction_uncertain ist NOT NULL. Anders als bei buchungsdetails gibt es
        // hier keinen dritten Zustand — «unbekannt, ob die Richtung geraten war» müsste in der
        // Oberfläche entweder als Hinweis enden (falsch-positiv für den ganzen Altbestand) oder
        // als sicher, also wie FALSE. Die Migration trägt den Default deshalb selbst nach.
        assertThat(notNullByColumn.get("direction_uncertain")).isTrue();
    }

    /**
     * V08 fügt an, statt die Tabelle neu zu bauen: Der Bestand muss die Migration überleben.
     *
     * <p>Anders als bei {@code buchungsdetails} (V06) trägt diese Spalte ein {@code DEFAULT}, und
     * das ist hier die richtige Wahl: Vor V08 geschriebene Zeilen sind nicht als «geraten»
     * erkennbar, und sie rückwirkend zu markieren stellte Buchungen zur Prüfung, die der Nutzer
     * längst gesehen und für richtig gehalten hat.
     */
    @Test
    void rowsWrittenWithoutDirectionUncertainDefaultToCertain() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "bestand-v08@example.com", "bcrypt-hash", new java.math.BigDecimal("4200.00"),
                true);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "bestand-v08@example.com");

        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, buchungsdatum, buchungstext, betrag, is_income)"
                        + " VALUES (?, DATE '2026-07-03', 'LASTSCHRIFT', 42.50, FALSE)",
                userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT direction_uncertain FROM transactions WHERE user_id = ?", Boolean.class,
                userId))
                .isFalse();
    }

    /**
     * V06 fügt an, statt die Tabelle neu zu bauen: Der Bestand muss die Migration überleben.
     *
     * <p>Der Test schreibt die Zeile über reines SQL ohne {@code buchungsdetails} — so, wie eine
     * vor BE-PDF-07 importierte Zeile aussieht — und liest sie zurück. Ein {@code DEFAULT} an der
     * Spalte würde hier auffallen: Es unterschiede «hatte keine Detailzeilen» nicht mehr von
     * «stammt aus einem Import vor V06», und genau diese Trennung begründet die Nullbarkeit.
     */
    @Test
    void rowsWrittenWithoutBuchungsdetailsKeepNull() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "altbestand@example.com", "bcrypt-hash", new java.math.BigDecimal("4200.00"), true);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "altbestand@example.com");

        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, buchungsdatum, buchungstext, betrag, is_income)"
                        + " VALUES (?, DATE '2026-07-03', 'LASTSCHRIFT', 42.50, FALSE)",
                userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT buchungsdetails FROM transactions WHERE user_id = ?", String.class, userId))
                .isNull();
    }
}
