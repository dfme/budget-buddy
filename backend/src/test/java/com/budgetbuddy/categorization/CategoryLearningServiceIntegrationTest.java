package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest des {@link CategoryLearningService} gegen echtes PostgreSQL + Flyway
 * (BE-CAT-04, BE-CAT-12).
 * Prüft den Lerneffekt end-to-end: ein gelerntes Pattern wird persistiert und von der
 * {@link LookupTableService} anschliessend ohne Claude-Call gematcht; ein erneutes Lernen desselben
 * Patterns aktualisiert die Kategorie (Upsert auf {@code UNIQUE (user_id, empfaenger_pattern)}).
 * Seit BE-CAT-13 ist der gespeicherte Schlüssel das stabile Präfix des Textes — die variable
 * Mitteilung wird vor dem Speichern abgeschnitten ({@link LookupPatternExtractor}).
 *
 * <p>Seit BE-CAT-12 (ADR-15) ist der Lerneffekt mandantengebunden: Die Gegenprobe aus Sicht eines
 * zweiten Users gehört deshalb dazu — ein grüner Happy Path beweist die Trennung nicht.
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

    private long lara;
    private long marc;

    @BeforeEach
    void seedUsers() {
        jdbcTemplate.update("DELETE FROM user_category_lookup");
        jdbcTemplate.update("DELETE FROM users");
        lara = insertUser("lara@example.ch");
        marc = insertUser("marc@example.ch");
    }

    @Test
    void learnedPatternIsMatchedByLookupWithoutClaude() {
        learningService.learn(lara, "BAECKEREI MUELLER", Category.LEBENSMITTEL);

        // Realer PDF-Text enthält das gelernte Pattern als Substring.
        assertThat(lookupTableService.categorize(lara, "BAECKEREI MUELLER 12345"))
                .contains(new CategorizationResult(Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void learnedPatternIsInvisibleToAnotherUser() {
        learningService.learn(lara, "BAECKEREI MUELLER", Category.LEBENSMITTEL);

        // Mandantentrennung (BE-CAT-12): Laras Korrektur kategorisiert Marcs Auszug nicht.
        assertThat(lookupTableService.categorize(marc, "BAECKEREI MUELLER 12345")).isEmpty();
    }

    @Test
    void relearningSamePatternUpdatesCategory() {
        learningService.learn(lara, "COIFFEUR STUDIO X", Category.SONSTIGES);
        learningService.learn(lara, "COIFFEUR STUDIO X", Category.GESUNDHEIT);

        assertThat(lookupTableService.categorize(lara, "COIFFEUR STUDIO X ZUERICH"))
                .contains(new CategorizationResult(Category.GESUNDHEIT, CategorizationResult.Source.LOOKUP));
        assertThat(countLearnedRowsFor(lara, "COIFFEUR STUDIO X")).isEqualTo(1);
    }

    @Test
    void relearningSeededPatternInLowerCaseOverridesTheSeedForThisUserOnly() {
        // Ersatz für SQLites COLLATE NOCASE (DB-05, ADR-12): Die kleingeschriebene Eingabe muss
        // dieselbe (eigene) Zeile treffen wie eine grossgeschriebene — und bei gleicher Länge wie
        // der Seed 'MIGROS' gewinnt das eigene Pattern (ADR-15). Der Seed selbst bleibt
        // unverändert: Vor BE-CAT-12 überschrieb die Korrektur eines Users die Zeile für alle.
        learningService.learn(lara, "migros", Category.SONSTIGES);
        learningService.learn(lara, "MIGROS", Category.SONSTIGES);

        assertThat(lookupTableService.categorize(lara, "MIGROS BERN 044 913 2323"))
                .contains(new CategorizationResult(Category.SONSTIGES, CategorizationResult.Source.LOOKUP));
        assertThat(lookupTableService.categorize(marc, "MIGROS BERN 044 913 2323"))
                .contains(new CategorizationResult(Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));
        assertThat(countLearnedRowsFor(lara, "MIGROS")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT category FROM category_lookup WHERE empfaenger_pattern = 'MIGROS'", String.class))
                .isEqualTo("Lebensmittel");
    }

    @Test
    void learningNeverWritesTheGlobalTable() {
        int seedRowsBefore = countSeedRows();

        learningService.learn(lara, "NEUER HAENDLER", Category.SHOPPING);

        assertThat(countSeedRows()).isEqualTo(seedRowsBefore);
    }

    @Test
    void variableMessageIsCutOff_soTheNextMonthHitsTheLookup() {
        // BE-CAT-13: Der Januar-Text wird gelernt, der Februar-Text muss ohne Claude treffen.
        learningService.learn(lara, "GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025", Category.WOHNEN);

        assertThat(lookupTableService.categorize(lara, "GIRO POST MUSTER IMMOBILIEN AG MIETE FEBRUAR 2025"))
                .contains(new CategorizationResult(Category.WOHNEN, CategorizationResult.Source.LOOKUP));
        assertThat(countLearnedRowsContaining(lara, "GIRO POST MUSTER IMMOBILIEN AG MIETE")).isEqualTo(1);
    }

    @Test
    void twelveRentPaymentsBecomeOneRow_andTheCorrectionStillWins() {
        // Wächst pro Gegenpartei, nicht pro Transaktion (AC 2) — und beide Lernquellen treffen
        // weiterhin denselben Schlüssel: Die Korrektur nach der Claude-Einstufung ist ein Upsert.
        for (String month : new String[] {"JANUAR", "FEBRUAR", "MAERZ", "APRIL", "MAI", "JUNI",
                "JULI", "AUGUST", "SEPTEMBER", "OKTOBER", "NOVEMBER", "DEZEMBER"}) {
            learningService.learn(lara, "LASTSCHRIFT CSS VERSICHERUNG AG PRAEMIE " + month + " 2025",
                    Category.SONSTIGES);
        }
        assertThat(countLearnedRowsContaining(lara, "CSS VERSICHERUNG AG PRAEMIE")).isEqualTo(1);

        // Der User korrigiert die Juli-Prämie — dieselbe Zeile, kein zweiter Eintrag.
        learningService.learn(lara, "LASTSCHRIFT CSS VERSICHERUNG AG PRAEMIE JULI 2025",
                Category.VERSICHERUNG);

        assertThat(countLearnedRowsContaining(lara, "CSS VERSICHERUNG AG PRAEMIE")).isEqualTo(1);
        assertThat(lookupTableService.categorize(lara, "LASTSCHRIFT CSS VERSICHERUNG AG PRAEMIE AUGUST 2025"))
                .contains(new CategorizationResult(
                        Category.VERSICHERUNG, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void blankPatternIsIgnored() {
        learningService.learn(lara, "   ", Category.LEBENSMITTEL);

        assertThat(lookupTableService.categorize(lara, "irgendein unbekannter text")).isEmpty();
    }

    private Integer countLearnedRowsFor(long userId, String pattern) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_category_lookup "
                        + "WHERE user_id = ? AND upper(empfaenger_pattern) = upper(?)",
                Integer.class, userId, pattern);
    }

    private Integer countLearnedRowsContaining(long userId, String fragment) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_category_lookup "
                        + "WHERE user_id = ? AND empfaenger_pattern LIKE ?",
                Integer.class, userId, "%" + fragment + "%");
    }

    private int countSeedRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup", Integer.class);
        return count == null ? 0 : count;
    }

    private Long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)",
                email, "$2a$10$test.only.not.a.real.hash");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
