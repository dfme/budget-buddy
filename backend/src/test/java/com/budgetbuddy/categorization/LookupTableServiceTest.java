package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
 * Unit-Test der {@link LookupTableService}-Logik mit gemockten Repositories: Mapping DB-String →
 * {@link Category}, leeres Optional bei unbekanntem Text, Kurzschluss bei leerer Eingabe und —
 * seit BE-CAT-12 — die Regel, nach der eigene und globale Treffer gegeneinander antreten.
 * Das eigentliche Substring-/Case-insensitive-Matching gegen echte Seed-Daten prüft
 * {@link LookupTableServiceIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class LookupTableServiceTest {

    private static final long USER_ID = 42L;

    @Mock private CategoryLookupRepository categoryLookupRepository;
    @Mock private UserCategoryLookupRepository userCategoryLookupRepository;

    @InjectMocks private LookupTableService lookupTableService;

    @Test
    void mapsKnownMerchantToCategory() {
        when(categoryLookupRepository.findMatching("MIGROS BERN"))
                .thenReturn(List.of(new CategoryLookup("MIGROS", "Lebensmittel")));
        when(userCategoryLookupRepository.findMatching(USER_ID, "MIGROS BERN")).thenReturn(List.of());

        Optional<CategorizationResult> result = lookupTableService.categorize(USER_ID, "MIGROS BERN");

        assertThat(result).contains(new CategorizationResult(
                Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void returnsEmptyForUnknownMerchant() {
        when(categoryLookupRepository.findMatching("UNBEKANNTER LADEN")).thenReturn(List.of());
        when(userCategoryLookupRepository.findMatching(USER_ID, "UNBEKANNTER LADEN"))
                .thenReturn(List.of());

        Optional<CategorizationResult> result =
                lookupTableService.categorize(USER_ID, "UNBEKANNTER LADEN");

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
        when(userCategoryLookupRepository.findMatching(USER_ID, "SWISS PASS ABO"))
                .thenReturn(List.of());

        Optional<CategorizationResult> result = lookupTableService.categorize(USER_ID, "SWISS PASS ABO");

        assertThat(result).contains(new CategorizationResult(
                Category.TRANSPORT, CategorizationResult.Source.LOOKUP));
    }

    // --- Eigene gegen globale Patterns (BE-CAT-12, ADR-15) ---

    @Test
    void ownPatternAloneIsEnough() {
        when(categoryLookupRepository.findMatching("BAECKEREI MUELLER 12345")).thenReturn(List.of());
        when(userCategoryLookupRepository.findMatching(USER_ID, "BAECKEREI MUELLER 12345"))
                .thenReturn(List.of(new UserCategoryLookup(USER_ID, "BAECKEREI MUELLER", "Lebensmittel")));

        assertThat(lookupTableService.categorize(USER_ID, "BAECKEREI MUELLER 12345"))
                .contains(new CategorizationResult(
                        Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void longerOwnPatternBeatsShorterSeed() {
        // MIGROS BANK ZINS ist Finanzen, auch wenn der Seed MIGROS Lebensmittel sagt — dieselbe
        // Längenregel, die innerhalb eines Pools schon immer galt.
        when(categoryLookupRepository.findMatching("MIGROS BANK ZINS 2026"))
                .thenReturn(List.of(new CategoryLookup("MIGROS", "Lebensmittel")));
        when(userCategoryLookupRepository.findMatching(USER_ID, "MIGROS BANK ZINS 2026"))
                .thenReturn(List.of(new UserCategoryLookup(USER_ID, "MIGROS BANK ZINS", "Sparen")));

        assertThat(lookupTableService.categorize(USER_ID, "MIGROS BANK ZINS 2026"))
                .contains(new CategorizationResult(Category.SPAREN, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void longerSeedBeatsShorterOwnPattern() {
        when(categoryLookupRepository.findMatching("SWISS PASS ABO"))
                .thenReturn(List.of(new CategoryLookup("SWISS PASS", "Transport")));
        when(userCategoryLookupRepository.findMatching(USER_ID, "SWISS PASS ABO"))
                .thenReturn(List.of(new UserCategoryLookup(USER_ID, "SWISS", "Freizeit")));

        assertThat(lookupTableService.categorize(USER_ID, "SWISS PASS ABO"))
                .contains(new CategorizationResult(
                        Category.TRANSPORT, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void onEqualLengthTheOwnPatternWins() {
        // Ein User darf MIGROS für sich anders einordnen als der Seed — ohne den Seed für alle zu
        // ändern. Vor BE-CAT-12 überschrieb seine Korrektur die globale Zeile.
        when(categoryLookupRepository.findMatching("MIGROS BERN"))
                .thenReturn(List.of(new CategoryLookup("MIGROS", "Lebensmittel")));
        when(userCategoryLookupRepository.findMatching(USER_ID, "MIGROS BERN"))
                .thenReturn(List.of(new UserCategoryLookup(USER_ID, "MIGROS", "Sonstiges")));

        assertThat(lookupTableService.categorize(USER_ID, "MIGROS BERN"))
                .contains(new CategorizationResult(
                        Category.SONSTIGES, CategorizationResult.Source.LOOKUP));
    }

    @Test
    void returnsEmptyForNullInputWithoutHittingRepositories() {
        Optional<CategorizationResult> result = lookupTableService.categorize(USER_ID, null);

        assertThat(result).isEmpty();
        verify(categoryLookupRepository, never()).findMatching(any());
        verify(userCategoryLookupRepository, never()).findMatching(anyLong(), any());
    }

    @Test
    void returnsEmptyForBlankInputWithoutHittingRepositories() {
        Optional<CategorizationResult> result = lookupTableService.categorize(USER_ID, "   ");

        assertThat(result).isEmpty();
        verify(categoryLookupRepository, never()).findMatching(any());
        verify(userCategoryLookupRepository, never()).findMatching(anyLong(), any());
    }

    @Test
    void throwsWhenDatabaseContainsUnknownCategoryLabel() {
        // Inkonsistente Seed-Daten dürfen nicht stillschweigend als Sonstiges durchgehen.
        when(categoryLookupRepository.findMatching("KAPUTT"))
                .thenReturn(List.of(new CategoryLookup("KAPUTT", "GibtsNicht")));
        when(userCategoryLookupRepository.findMatching(USER_ID, "KAPUTT")).thenReturn(List.of());

        assertThatThrownBy(() -> lookupTableService.categorize(USER_ID, "KAPUTT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
