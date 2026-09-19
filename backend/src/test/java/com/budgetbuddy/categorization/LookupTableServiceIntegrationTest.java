package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest der {@link LookupTableService} gegen eine echte PostgreSQL-DB mit den
 * Flyway-V04-Seed-Daten. Prüft den Happy Path (DoD) end-to-end: Substring-Matching gegen realen
 * Transaktionstext und case-insensitives Matching.
 *
 * <p>Dazu die Metazeichen-Fälle aus BE-CAT-14 direkt gegen
 * {@link CategoryLookupRepository#findMatching}: Ein gelerntes Pattern mit {@code %} oder {@code _}
 * darf nicht als Wildcard wirken. Das Verhalten liegt in der Query, nicht im Java-Code — ein
 * Mockito-Test würde es nicht berühren, deshalb gehört es hierher und nicht in
 * {@link LookupTableServiceTest}. Die Fälle laufen absichtlich in dieser Klasse mit statt in einer
 * eigenen: jede weitere {@code @SpringBootTest}-Klasse ist ein weiterer Spring-Kontext mit eigenem
 * Connection-Pool, und der Kontext-Cache ist bei über fünfzig Integrationstestklassen der
 * knappere Posten (Begründung in {@code PostgresTestDatabase}).
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link com.budgetbuddy.db.CategoryLookupMigrationTest} (Begründung in
 * {@code PostgresTestDatabase}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LookupTableServiceIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "lookup_table");
    }

    @Autowired private LookupTableService lookupTableService;

    // Gelernt wird über den echten Lernpfad (BE-CAT-11 / BE-CAT-04), nicht über repository.save():
    // so deckt der Test dieselbe Normalisierung ab, die in Produktion das Pattern erzeugt.
    @Autowired private CategoryLearningService categoryLearningService;

    @Autowired private CategoryLookupRepository categoryLookupRepository;

    @Test
    void matchesMerchantAsSubstringOfTransactionText() {
        // Realer PDF-Text enthält das Seed-Pattern MIGROS plus Zusatz-Tokens.
        Optional<CategorizationResult> result = lookupTableService.categorize("MIGROS BERN 044 913 2323");

        assertThat(result).contains(new CategorizationResult(Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void matchesCaseInsensitively() {
        Optional<CategorizationResult> result = lookupTableService.categorize("digitec galaxus ag");

        assertThat(result).contains(new CategorizationResult(Category.SHOPPING, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void returnsEmptyForUnknownMerchant() {
        Optional<CategorizationResult> result = lookupTableService.categorize("BAECKEREI MUELLER 12345");

        assertThat(result).isEmpty();
    }

    @Test
    void percentSignInPatternMatchesLiterally() {
        categoryLearningService.learn("RABATT 20% MIGROS", Category.LEBENSMITTEL);

        assertThat(matchedPatterns("RABATT 20% MIGROS BERN")).contains("RABATT 20% MIGROS");
    }

    @Test
    void percentSignInPatternIsNoWildcard() {
        categoryLearningService.learn("RABATT 20% MIGROS", Category.LEBENSMITTEL);

        // Nicht auf isEmpty() prüfen: der Seed MIGROS matcht diesen Text zu Recht weiter. Die
        // Aussage ist, dass das Prozentzeichen nicht für " CHF " einspringt.
        assertThat(matchedPatterns("RABATT 20 CHF MIGROS")).doesNotContain("RABATT 20% MIGROS");
    }

    @Test
    void underscoreInPatternMatchesLiterally() {
        categoryLearningService.learn("SHOP_X", Category.SHOPPING);

        assertThat(matchedPatterns("SHOP_X FILIALE 3")).contains("SHOP_X");
    }

    @Test
    void underscoreInPatternIsNoWildcard() {
        categoryLearningService.learn("SHOP_X", Category.SHOPPING);

        // Der Unterstrich darf nicht für das zweite P einspringen.
        assertThat(matchedPatterns("SHOPPX FILIALE 3")).doesNotContain("SHOP_X");
    }

    @Test
    void doesNotCategorizeViaPercentWildcard() {
        // Der eigentliche Schaden aus #322, auf Service-Ebene: eine falsche Kategorie über Stufe 1.
        // Händler ohne Seed-Eintrag, damit das leere Optional eindeutig auf das Pattern zurückgeht.
        categoryLearningService.learn("RABATT 20% FOOBAR", Category.SHOPPING);

        assertThat(lookupTableService.categorize("RABATT 20% FOOBAR BERN"))
                .contains(new CategorizationResult(Category.SHOPPING, CategorizationResult.Source.LOOKUP));
        assertThat(lookupTableService.categorize("RABATT 20 CHF FOOBAR")).isEmpty();
    }

    /** Nur die Pattern der Treffer — die Kategorie ist hier nicht die Aussage. */
    private List<String> matchedPatterns(String text) {
        return categoryLookupRepository.findMatching(text).stream()
                .map(CategoryLookup::getEmpfaengerPattern)
                .toList();
    }
}
