package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Test der {@link LookupTableService}-Logik mit gemocktem Repository: Mapping DB-String →
 * {@link Category}, leeres Optional bei unbekanntem Text und Kurzschluss bei leerer Eingabe.
 * Das eigentliche Substring-/Case-insensitive-Matching gegen echte Seed-Daten prüft
 * {@link LookupTableServiceIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class LookupTableServiceTest {

    @Mock private CategoryLookupRepository categoryLookupRepository;

    @InjectMocks private LookupTableService lookupTableService;

    @Test
    void mapsKnownMerchantToCategory() {
        when(categoryLookupRepository.findMatching("MIGROS BERN"))
                .thenReturn(List.of(new CategoryLookup("MIGROS", "Lebensmittel")));

        Optional<Category> result = lookupTableService.categorize("MIGROS BERN");

        assertThat(result).contains(Category.LEBENSMITTEL);
    }

    @Test
    void returnsEmptyForUnknownMerchant() {
        when(categoryLookupRepository.findMatching("UNBEKANNTER LADEN")).thenReturn(List.of());

        Optional<Category> result = lookupTableService.categorize("UNBEKANNTER LADEN");

        assertThat(result).isEmpty();
    }

    @Test
    void picksMostSpecificMatchFirst() {
        // Repository liefert bereits nach Spezifität sortiert; der Service nimmt den ersten Treffer.
        when(categoryLookupRepository.findMatching("SWISS PASS ABO"))
                .thenReturn(
                        List.of(
                                new CategoryLookup("SWISS PASS", "Transport"),
                                new CategoryLookup("SWISS", "Sonstiges")));

        Optional<Category> result = lookupTableService.categorize("SWISS PASS ABO");

        assertThat(result).contains(Category.TRANSPORT);
    }

    @Test
    void returnsEmptyForNullInputWithoutHittingRepository() {
        Optional<Category> result = lookupTableService.categorize(null);

        assertThat(result).isEmpty();
        verify(categoryLookupRepository, never()).findMatching(any());
    }

    @Test
    void returnsEmptyForBlankInputWithoutHittingRepository() {
        Optional<Category> result = lookupTableService.categorize("   ");

        assertThat(result).isEmpty();
        verify(categoryLookupRepository, never()).findMatching(any());
    }

    @Test
    void throwsWhenDatabaseContainsUnknownCategoryLabel() {
        // Inkonsistente Seed-Daten dürfen nicht stillschweigend als Sonstiges durchgehen.
        when(categoryLookupRepository.findMatching("KAPUTT"))
                .thenReturn(List.of(new CategoryLookup("KAPUTT", "GibtsNicht")));

        assertThatThrownBy(() -> lookupTableService.categorize("KAPUTT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
