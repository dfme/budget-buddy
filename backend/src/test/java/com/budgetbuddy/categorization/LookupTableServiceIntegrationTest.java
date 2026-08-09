package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
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

    @Test
    void matchesMerchantAsSubstringOfTransactionText() {
        // Realer PDF-Text enthält das Seed-Pattern MIGROS plus Zusatz-Tokens.
        Optional<Category> result = lookupTableService.categorize("MIGROS BERN 044 913 2323");

        assertThat(result).contains(Category.LEBENSMITTEL);
    }

    @Test
    void matchesCaseInsensitively() {
        Optional<Category> result = lookupTableService.categorize("digitec galaxus ag");

        assertThat(result).contains(Category.SHOPPING);
    }

    @Test
    void returnsEmptyForUnknownMerchant() {
        Optional<Category> result = lookupTableService.categorize("BAECKEREI MUELLER 12345");

        assertThat(result).isEmpty();
    }
}
