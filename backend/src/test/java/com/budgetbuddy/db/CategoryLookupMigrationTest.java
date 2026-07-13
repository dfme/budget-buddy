package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifiziert die Flyway-Migration V04 (category_lookup-Tabelle inkl. Seed-Daten) gegen eine echte
 * SQLite-Datenbank.
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
class CategoryLookupMigrationTest {

    // Fixe Kategorienliste aus CLAUDE.md — Seed-Daten dürfen nur diese Werte verwenden.
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "Wohnen", "Lebensmittel", "Transport", "Versicherung", "Telekom", "Gesundheit",
            "Freizeit", "Restaurant", "Shopping", "Bildung", "Einkommen", "Sparen", "Sonstiges");

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("db-04-migration-test", ".db");
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
    void migrationsRunSuccessfullyAfterV3() {
        // V01..V04 müssen alle erfolgreich gelaufen sein.
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(4);
    }

    @Test
    void categoryLookupTableHasExactlyExpectedColumns() {
        Map<String, String> typeByColumn = columnTypes();

        assertThat(typeByColumn).containsOnlyKeys("empfaenger_pattern", "category");
        assertThat(typeByColumn.get("empfaenger_pattern")).isEqualTo("VARCHAR");
        assertThat(typeByColumn.get("category")).isEqualTo("VARCHAR");
    }

    @Test
    void empfaengerPatternIsPrimaryKey() {
        // PRAGMA table_info: pk-Flag > 0 markiert die PK-Spalte(n).
        Map<String, Integer> pkFlagByColumn = tableInfo().stream()
                .collect(Collectors.toMap(
                        c -> ((String) c.get("name")),
                        c -> ((Number) c.get("pk")).intValue()));

        assertThat(pkFlagByColumn.get("empfaenger_pattern")).isGreaterThan(0);
        assertThat(pkFlagByColumn.get("category")).isEqualTo(0);
    }

    @Test
    void categoryColumnIsNotNull() {
        Map<String, Integer> notNullByColumn = tableInfo().stream()
                .collect(Collectors.toMap(
                        c -> ((String) c.get("name")),
                        c -> ((Number) c.get("notnull")).intValue()));

        assertThat(notNullByColumn.get("category")).isEqualTo(1);
    }

    @Test
    void seedDataContainsAtLeastTenMerchants() {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup", Integer.class);

        assertThat(rows).isGreaterThanOrEqualTo(10);
    }

    @Test
    void lookupIsCaseInsensitive() {
        // AC: case-insensitive Lookup — kleingeschriebenes Pattern matcht den grossgeschriebenen Seed.
        String category = jdbcTemplate.queryForObject(
                "SELECT category FROM category_lookup WHERE empfaenger_pattern = ?",
                String.class, "migros");

        assertThat(category).isEqualTo("Lebensmittel");
    }

    @Test
    void allSeededCategoriesAreFromAllowedList() {
        List<String> categories = jdbcTemplate.queryForList(
                "SELECT DISTINCT category FROM category_lookup", String.class);

        assertThat(categories).isNotEmpty().allMatch(ALLOWED_CATEGORIES::contains);
    }

    // PRAGMA table_info liefert pro Spalte u.a.: name, type, notnull, dflt_value, pk
    private Map<String, String> columnTypes() {
        return tableInfo().stream()
                .collect(Collectors.toMap(
                        c -> ((String) c.get("name")),
                        c -> ((String) c.get("type"))));
    }

    private List<Map<String, Object>> tableInfo() {
        return jdbcTemplate.queryForList("PRAGMA table_info(category_lookup)");
    }
}
