package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifiziert die Flyway-Migration V03 (fixed_costs-Tabelle) gegen eine echte SQLite-Datenbank.
 *
 * <p>Bewusst eine Temp-File-DB statt {@code jdbc:sqlite::memory:}: In-Memory-SQLite legt pro
 * Connection eine eigene DB an, wodurch die Flyway-Connection und die Test-Query-Connection
 * unterschiedliche Datenbanken sähen. Eine Datei wird vom gesamten Connection-Pool geteilt.
 *
 * <p>{@code @DirtiesContext} schliesst den Kontext (und damit den Hikari-Pool) nach der Klasse,
 * sodass das SQLite-File-Handle freigegeben wird, bevor {@code deleteOnExit} die Datei entfernt
 * (auf Windows hält ein offener Pool die Datei sonst gesperrt).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FixedCostsMigrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("db-03-migration-test", ".db");
            Files.deleteIfExists(file); // SQLite/Flyway legt die Datei selbst frisch an
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsRunSuccessfullyAfterV2() {
        // V01 (users) + V02 (transactions) + V03 (fixed_costs) müssen alle erfolgreich gelaufen sein.
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(3);
    }

    @Test
    void fixedCostsTableHasAllColumnsWithCorrectTypes() {
        Map<String, String> typeByColumn = columnTypes();

        assertThat(typeByColumn).containsOnlyKeys(
                "id", "user_id", "bezeichnung", "betrag", "intervall");

        assertThat(typeByColumn.get("id")).isEqualTo("INTEGER");
        assertThat(typeByColumn.get("user_id")).isEqualTo("INTEGER");
        assertThat(typeByColumn.get("bezeichnung")).isEqualTo("VARCHAR");
        assertThat(typeByColumn.get("intervall")).isEqualTo("VARCHAR");
    }

    @Test
    void betragIsDecimalNotFloat() {
        assertThat(columnTypes().get("betrag"))
                .isEqualTo("DECIMAL(10,2)")
                .doesNotContainIgnoringCase("float")
                .doesNotContainIgnoringCase("real");
    }

    @Test
    void foreignKeyToUsersIsDefined() {
        List<Map<String, Object>> foreignKeys =
                jdbcTemplate.queryForList("PRAGMA foreign_key_list('fixed_costs')");

        assertThat(foreignKeys).anySatisfy(fk -> {
            assertThat(fk.get("table")).isEqualTo("users");
            assertThat(fk.get("from")).isEqualTo("user_id");
            assertThat(fk.get("to")).isEqualTo("id");
        });
    }

    @Test
    void allColumnsAreNotNull() {
        Map<String, Integer> notNullByColumn = notNullFlags();

        assertThat(notNullByColumn.get("user_id")).isEqualTo(1);
        assertThat(notNullByColumn.get("bezeichnung")).isEqualTo(1);
        assertThat(notNullByColumn.get("betrag")).isEqualTo(1);
        assertThat(notNullByColumn.get("intervall")).isEqualTo(1);
    }

    // PRAGMA table_info liefert pro Spalte u.a.: name, type, notnull, dflt_value, pk
    private Map<String, String> columnTypes() {
        return tableInfo().stream()
                .collect(Collectors.toMap(
                        c -> ((String) c.get("name")),
                        c -> ((String) c.get("type"))));
    }

    private Map<String, Integer> notNullFlags() {
        return tableInfo().stream()
                .collect(Collectors.toMap(
                        c -> ((String) c.get("name")),
                        c -> ((Number) c.get("notnull")).intValue()));
    }

    private List<Map<String, Object>> tableInfo() {
        return jdbcTemplate.queryForList("PRAGMA table_info(fixed_costs)");
    }
}
