package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest der {@link LookupTableService} gegen eine echte PostgreSQL-DB mit den
 * Flyway-V04-Seed-Daten. Prüft den Happy Path (DoD) end-to-end: Substring-Matching gegen realen
 * Transaktionstext und case-insensitives Matching.
 *
 * <p>Dazu die Metazeichen-Fälle aus BE-CAT-14 direkt gegen die beiden {@code findMatching}-Queries:
 * Ein Pattern mit {@code %} oder {@code _} darf nicht als Wildcard wirken. Das Verhalten liegt in
 * der Query, nicht im Java-Code — ein Mockito-Test würde es nicht berühren, deshalb gehört es
 * hierher und nicht in {@link LookupTableServiceTest}. Die Fälle laufen absichtlich in dieser
 * Klasse mit statt in einer eigenen: jede weitere {@code @SpringBootTest}-Klasse ist ein weiterer
 * Spring-Kontext mit eigenem Connection-Pool, und der Kontext-Cache ist bei über fünfzig
 * Integrationstestklassen der knappere Posten (Begründung in {@code PostgresTestDatabase}).
 *
 * <p>Der Schwerpunkt liegt seit BE-CAT-12 (ADR-15) auf {@code user_category_lookup}: Dort landet
 * alles Gelernte, also roher Buchungstext mit Metazeichen. Die globale {@code category_lookup}
 * hält nur noch kuratierte Seeds ohne {@code %} oder {@code _} — ihre Query wird trotzdem geprüft,
 * damit die Zusage für beide Pools gilt und nicht nur für den, der sie heute braucht.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link com.budgetbuddy.db.CategoryLookupMigrationTest} (Begründung in
 * {@code PostgresTestDatabase}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LookupTableServiceIntegrationTest {

    /** Pattern, das dieser Test selbst in die globale Tabelle legt und vorher wieder abräumt. */
    private static final String GLOBAL_TEST_PATTERN = "RABATT 20% ZZGLOBAL";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "lookup_table");
    }

    @Autowired private LookupTableService lookupTableService;

    // Gelernt wird über den echten Lernpfad (BE-CAT-11 / BE-CAT-04), nicht über repository.save():
    // so deckt der Test dieselbe Normalisierung ab, die in Produktion das Pattern erzeugt.
    @Autowired private CategoryLearningService categoryLearningService;

    @Autowired private CategoryLookupRepository categoryLookupRepository;
    @Autowired private UserCategoryLookupRepository userCategoryLookupRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Seit BE-CAT-12 hängt {@code user_category_lookup} per Fremdschlüssel an {@code users} — ein
     * frei gewählter Wert wie {@code 1L} genügt zum Lernen nicht mehr.
     */
    private long userId;

    @BeforeEach
    void seedUser() {
        jdbcTemplate.update("DELETE FROM user_category_lookup");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM category_lookup WHERE empfaenger_pattern = ?", GLOBAL_TEST_PATTERN);
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)",
                "lookup@example.ch", "$2a$10$test.only.not.a.real.hash");
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "lookup@example.ch");
    }

    @Test
    void matchesMerchantAsSubstringOfTransactionText() {
        // Realer PDF-Text enthält das Seed-Pattern MIGROS plus Zusatz-Tokens.
        Optional<CategorizationResult> result = lookupTableService.categorize(userId, "MIGROS BERN 044 913 2323");

        assertThat(result).contains(new CategorizationResult(Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void matchesCaseInsensitively() {
        Optional<CategorizationResult> result = lookupTableService.categorize(userId, "digitec galaxus ag");

        assertThat(result).contains(new CategorizationResult(Category.SHOPPING, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void returnsEmptyForUnknownMerchant() {
        Optional<CategorizationResult> result = lookupTableService.categorize(userId, "BAECKEREI MUELLER 12345");

        assertThat(result).isEmpty();
    }

    @Test
    void percentSignInLearnedPatternMatchesLiterally() {
        categoryLearningService.learn(userId, "RABATT 20% MIGROS", Category.LEBENSMITTEL);

        assertThat(learnedPatterns("RABATT 20% MIGROS BERN")).contains("RABATT 20% MIGROS");
    }

    @Test
    void percentSignInLearnedPatternIsNoWildcard() {
        categoryLearningService.learn(userId, "RABATT 20% MIGROS", Category.LEBENSMITTEL);

        // Das Prozentzeichen darf nicht für " CHF " einspringen. Geprüft wird die Pattern-Liste
        // statt isEmpty(): so bleibt die Aussage dieselbe, falls der Pool später mehr enthält.
        assertThat(learnedPatterns("RABATT 20 CHF MIGROS")).doesNotContain("RABATT 20% MIGROS");
    }

    @Test
    void underscoreInLearnedPatternMatchesLiterally() {
        categoryLearningService.learn(userId, "SHOP_X", Category.SHOPPING);

        assertThat(learnedPatterns("SHOP_X FILIALE 3")).contains("SHOP_X");
    }

    @Test
    void underscoreInLearnedPatternIsNoWildcard() {
        categoryLearningService.learn(userId, "SHOP_X", Category.SHOPPING);

        // Der Unterstrich darf nicht für das zweite P einspringen.
        assertThat(learnedPatterns("SHOPPX FILIALE 3")).doesNotContain("SHOP_X");
    }

    @Test
    void percentSignInSeededPatternIsNoWildcard() {
        // Die Seeds tragen heute kein Metazeichen; die Zusage der globalen Query soll trotzdem
        // nicht davon abhängen, dass das so bleibt. Deshalb direkt in die Tabelle statt über den
        // Lernpfad — dorthin schreibt seit ADR-15 nur noch die kuratierte Pflege.
        jdbcTemplate.update(
                "INSERT INTO category_lookup (empfaenger_pattern, category) VALUES (?, ?)",
                GLOBAL_TEST_PATTERN, Category.LEBENSMITTEL.getLabel());

        assertThat(seededPatterns("RABATT 20% ZZGLOBAL BERN")).contains(GLOBAL_TEST_PATTERN);
        assertThat(seededPatterns("RABATT 20 CHF ZZGLOBAL")).doesNotContain(GLOBAL_TEST_PATTERN);
    }

    @Test
    void doesNotCategorizeViaPercentWildcard() {
        // Der eigentliche Schaden aus #322, auf Service-Ebene: eine falsche Kategorie über Stufe 1.
        // Händler ohne Seed-Eintrag, damit das leere Optional eindeutig auf das Pattern zurückgeht.
        categoryLearningService.learn(userId, "RABATT 20% FOOBAR", Category.SHOPPING);

        assertThat(lookupTableService.categorize(userId, "RABATT 20% FOOBAR BERN"))
                .contains(new CategorizationResult(Category.SHOPPING, CategorizationResult.Source.LOOKUP));
        assertThat(lookupTableService.categorize(userId, "RABATT 20 CHF FOOBAR")).isEmpty();
    }

    /** Nur die Pattern der Treffer aus dem Lern-Pool — die Kategorie ist hier nicht die Aussage. */
    private List<String> learnedPatterns(String text) {
        return userCategoryLookupRepository.findMatching(userId, text).stream()
                .map(UserCategoryLookup::getEmpfaengerPattern)
                .toList();
    }

    /** Dasselbe für die globalen Seeds. */
    private List<String> seededPatterns(String text) {
        return categoryLookupRepository.findMatching(text).stream()
                .map(CategoryLookup::getEmpfaengerPattern)
                .toList();
    }
}
