package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.support.PostgresTestDatabase;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
 * PostgreSQL-Datenbank — seit BE-CAT-17 zusammen mit V15, das die Seeds für Bargeldbezug und
 * Steuern nachreicht. Die Prüfungen laufen deshalb gegen die Tabelle nach allen Migrationen und
 * nicht gegen eine einzelne Datei.
 *
 * <p>Seit DB-05 (ADR-12) gegen Testcontainers-Postgres in derselben Major-Version wie Produktion,
 * mit einer eigenen Datenbank für diese Klasse (siehe {@link PostgresTestDatabase}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CategoryLookupMigrationTest {

    private static final String TABLE = "category_lookup";

    // Fixe Kategorienliste aus CLAUDE.md — Seed-Daten dürfen nur diese Werte verwenden.
    // Bewusst aus dem Enum abgeleitet statt hart kodiert: eine zweite Liste lief hier bis BE-CAT-10
    // unbemerkt auseinander, weil sie die Zahl «13» nirgends nennt und so von keiner Suche nach den
    // Kategorie-Spiegeln gefunden wurde.
    private static final Set<String> ALLOWED_CATEGORIES = Arrays.stream(Category.values())
            .map(Category::getLabel)
            .collect(Collectors.toUnmodifiableSet());

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "category_lookup_migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SchemaInspector schema() {
        return new SchemaInspector(jdbcTemplate);
    }

    @Test
    void migrationsRunSuccessfullyAfterV3() {
        // V01..V04 müssen alle erfolgreich gelaufen sein.
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(4);
    }

    @Test
    void categoryLookupTableHasExactlyExpectedColumns() {
        Map<String, String> typeByColumn = schema().columnTypes(TABLE);

        assertThat(typeByColumn).containsOnlyKeys("empfaenger_pattern", "category");
        assertThat(typeByColumn.get("empfaenger_pattern")).isEqualTo("text");
        assertThat(typeByColumn.get("category")).isEqualTo("text");
    }

    @Test
    void empfaengerPatternIsPrimaryKey() {
        assertThat(schema().primaryKeyColumns(TABLE)).containsExactly("empfaenger_pattern");
    }

    @Test
    void categoryColumnIsNotNull() {
        assertThat(schema().notNullFlags(TABLE).get("category")).isTrue();
    }

    @Test
    void seedDataContainsAtLeastTenMerchants() {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup", Integer.class);

        assertThat(rows).isGreaterThanOrEqualTo(10);
    }

    @Test
    void allSeededPatternsAreStoredInUpperCase() {
        // Trägt seit DB-05 die case-insensitive Zuordnung: SQLites COLLATE NOCASE hat unter
        // PostgreSQL keine Entsprechung. Stattdessen liegen Patterns ausschliesslich in
        // Grossschreibung vor (CategoryLearningService normalisiert vor dem Speichern), und das
        // Matching vergleicht über upper() auf beiden Seiten. Bricht diese Invariante, wären
        // 'migros' und 'MIGROS' zwei konkurrierende Zeilen.
        List<String> patterns = jdbcTemplate.queryForList(
                "SELECT empfaenger_pattern FROM category_lookup", String.class);

        // Locale.ROOT wie im Produktionscode (CategoryLearningService): mit der Default-Locale
        // prüfte der Test eine andere Abbildung, als er absichert — unter tr-TR wird aus "i" ein
        // "İ". Praktisch harmlos bei diesen Seeds, als Vorbild aber falsch.
        assertThat(patterns).isNotEmpty()
                .allSatisfy(pattern ->
                        assertThat(pattern).isEqualTo(pattern.toUpperCase(Locale.ROOT)));
    }

    @Test
    void lookupIsCaseInsensitive() {
        // AC aus DB-04: kleingeschriebene Eingabe findet den grossgeschriebenen Seed. Die
        // Gross-/Kleinschreib-Unabhängigkeit liegt in der Query (upper() auf beiden Seiten,
        // vgl. CategoryLookupRepository#findMatching), nicht mehr in der Spalten-Collation.
        String category = jdbcTemplate.queryForObject(
                "SELECT category FROM category_lookup WHERE upper(empfaenger_pattern) = upper(?)",
                String.class, "migros");

        assertThat(category).isEqualTo("Lebensmittel");
    }

    @Test
    void v15SeedsTheCashWithdrawalAndTaxCategories() {
        // BE-CAT-17. Die beiden Kategorien gibt es seit BE-CAT-10, Seeds bekamen sie erst mit V15
        // — bis dahin lief Stufe 1 (ADR-6) für sie leer und jeder Bancomat-Bezug ging an Claude.
        // Geprüft wird die Tabelle, nicht die Datei: Die Zusage ist, dass die Seeds nach allen
        // Migrationen dastehen, nicht, dass eine bestimmte Migration sie geschrieben hat.
        Map<String, String> categoryByPattern = jdbcTemplate.queryForList(
                        "SELECT empfaenger_pattern, category FROM category_lookup"
                                + " WHERE category IN ('Bargeldbezug', 'Steuern')")
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("empfaenger_pattern"),
                        row -> (String) row.get("category")));

        assertThat(categoryByPattern)
                .containsEntry("BANCOMAT", "Bargeldbezug")
                .containsEntry("POSTOMAT", "Bargeldbezug")
                .containsEntry("GELDAUTOMAT", "Bargeldbezug")
                .containsEntry("BARGELDBEZUG", "Bargeldbezug")
                .containsEntry("BARBEZUG", "Bargeldbezug")
                .containsEntry("STEUERVERWALTUNG", "Steuern")
                .containsEntry("STEUERAMT", "Steuern");

        // Bewusst nicht geseedet (Begründung in V15): Beides sind Substrings gewöhnlicher
        // Buchungstexte, und findMatching kennt seit BE-CAT-14 keine Wortgrenzen. Steht hier,
        // damit ein späterer Nachtrag eine Entscheidung ist und kein Versehen.
        assertThat(categoryByPattern).doesNotContainKeys("ATM", "STEUERN");
    }

    @Test
    void allSeededCategoriesAreFromAllowedList() {
        List<String> categories = jdbcTemplate.queryForList(
                "SELECT DISTINCT category FROM category_lookup", String.class);

        assertThat(categories).isNotEmpty().allMatch(ALLOWED_CATEGORIES::contains);
    }
}
