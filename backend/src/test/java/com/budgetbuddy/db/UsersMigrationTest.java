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
 * Verifiziert die Flyway-Migration V01 (users-Tabelle) gegen eine echte SQLite-Datenbank.
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
class UsersMigrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("db-01-migration-test", ".db");
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
    void migrationRunsSuccessfully() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(1);
    }

    @Test
    void usersTableHasAllColumnsWithCorrectTypes() {
        Map<String, String> typeByColumn = columnTypes();

        assertThat(typeByColumn).containsOnlyKeys(
                "id", "email", "password_hash", "monthly_income", "onboarding_completed");

        assertThat(typeByColumn.get("id")).isEqualTo("INTEGER");
        assertThat(typeByColumn.get("email")).isEqualTo("TEXT");
        assertThat(typeByColumn.get("password_hash")).isEqualTo("TEXT");
        assertThat(typeByColumn.get("onboarding_completed")).isEqualTo("BOOLEAN");
    }

    @Test
    void monthlyIncomeIsDecimalNotFloat() {
        assertThat(columnTypes().get("monthly_income"))
                .isEqualTo("DECIMAL(10,2)")
                .doesNotContainIgnoringCase("float")
                .doesNotContainIgnoringCase("real");
    }

    @Test
    void emailIsNotNullAndUnique() {
        Integer emailNotNull = jdbcTemplate.queryForObject(
                "SELECT \"notnull\" FROM pragma_table_info('users') WHERE name = 'email'",
                Integer.class);
        assertThat(emailNotNull).isEqualTo(1);

        // pragma_index_list: 'unique' = 1 für einen UNIQUE-Index; mind. ein Index muss die
        // Spalte email abdecken (von der UNIQUE-Constraint automatisch erzeugt).
        boolean emailHasUniqueIndex = jdbcTemplate.queryForList("PRAGMA index_list('users')")
                .stream()
                .filter(idx -> ((Number) idx.get("unique")).intValue() == 1)
                .anyMatch(idx -> {
                    String indexName = (String) idx.get("name");
                    return jdbcTemplate.queryForList("PRAGMA index_info('" + indexName + "')")
                            .stream()
                            .anyMatch(col -> "email".equals(col.get("name")));
                });

        assertThat(emailHasUniqueIndex).isTrue();
    }

    // PRAGMA table_info liefert pro Spalte u.a.: name, type, notnull, dflt_value, pk
    private Map<String, String> columnTypes() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(users)");
        return columns.stream()
                .collect(Collectors.toMap(
                        c -> ((String) c.get("name")),
                        c -> ((String) c.get("type"))));
    }
}
