package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest des {@link CategoryLearningService} gegen echte SQLite + Flyway (BE-CAT-04).
 * Prüft den Lerneffekt end-to-end: ein gelerntes Pattern wird persistiert und von der
 * {@link LookupTableService} anschliessend ohne Claude-Call gematcht; ein erneutes Lernen desselben
 * Patterns aktualisiert die Kategorie (Upsert auf dem PK).
 *
 * <p>Temp-File-DB statt {@code jdbc:sqlite::memory:} und {@code @DirtiesContext} analog zu
 * {@link LookupTableServiceIntegrationTest} (Begründung dort dokumentiert).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CategoryLearningServiceIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-cat-04-learning-it", ".db");
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

    @Autowired private CategoryLearningService learningService;
    @Autowired private LookupTableService lookupTableService;

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
    void blankPatternIsIgnored() {
        learningService.learn("   ", Category.LEBENSMITTEL);

        assertThat(lookupTableService.categorize("irgendein unbekannter text")).isEmpty();
    }
}
