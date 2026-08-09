package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest des {@link CategoryLearningService} gegen echtes PostgreSQL + Flyway
 * (BE-CAT-04).
 * Prüft den Lerneffekt end-to-end: ein gelerntes Pattern wird persistiert und von der
 * {@link LookupTableService} anschliessend ohne Claude-Call gematcht; ein erneutes Lernen desselben
 * Patterns aktualisiert die Kategorie (Upsert auf dem PK).
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link LookupTableServiceIntegrationTest} (Begründung in {@code PostgresTestDatabase}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CategoryLearningServiceIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "category_learning");
    }

    @Autowired private CategoryLearningService learningService;
    @Autowired private LookupTableService lookupTableService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void learnedPatternIsMatchedByLookupWithoutClaude() {
        learningService.learn("BAECKEREI MUELLER", Category.LEBENSMITTEL);

        // Realer PDF-Text enthält das gelernte Pattern als Substring.
        assertThat(lookupTableService.categorize("BAECKEREI MUELLER 12345"))
                .contains(Category.LEBENSMITTEL);
    }

    @Test
    void relearningSamePatternUpdatesCategory() {
        learningService.learn("COIFFEUR STUDIO X", Category.SONSTIGES);
        learningService.learn("COIFFEUR STUDIO X", Category.GESUNDHEIT);

        assertThat(lookupTableService.categorize("COIFFEUR STUDIO X ZUERICH"))
                .contains(Category.GESUNDHEIT);
    }

    @Test
    void relearningSeededPatternInLowerCaseUpdatesTheSameRow() {
        // Ersatz für SQLites COLLATE NOCASE (DB-05, ADR-12): Der Seed 'MIGROS' aus V04 und die
        // kleingeschriebene Eingabe müssen dieselbe Zeile treffen. Ohne die Normalisierung in
        // CategoryLearningService entstünden unter PostgreSQL zwei konkurrierende Zeilen, und
        // welche gewinnt, entschiede die Sortierung in findMatching — nicht die Korrektur des
        // Users.
        learningService.learn("migros", Category.SONSTIGES);

        assertThat(lookupTableService.categorize("MIGROS BERN 044 913 2323"))
                .contains(Category.SONSTIGES);
        assertThat(countLookupRowsFor("MIGROS")).isEqualTo(1);
    }

    @Test
    void blankPatternIsIgnored() {
        learningService.learn("   ", Category.LEBENSMITTEL);

        assertThat(lookupTableService.categorize("irgendein unbekannter text")).isEmpty();
    }

    private Integer countLookupRowsFor(String pattern) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup WHERE upper(empfaenger_pattern) = upper(?)",
                Integer.class, pattern);
    }
}
