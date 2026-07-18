package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Test der Orchestrierung: Reihenfolge der beiden Stufen und Fehlerverhalten. Die Logik der
 * Stufen selbst ist in {@link LookupTableServiceTest} bzw. {@link ClaudeCategorizationServiceTest}
 * abgedeckt — hier sind beide gemockt.
 */
@ExtendWith(MockitoExtension.class)
class HybridCategorizationServiceTest {

    private static final String KNOWN = "COOP-2001 BERN";
    private static final String UNKNOWN = "DIGITEC GALAXUS AG 044 913 2323";

    @Mock private LookupTableService lookupTableService;
    @Mock private ClaudeCategorizationService claudeCategorizationService;

    @InjectMocks private HybridCategorizationService service;

    @Test
    void bekannterHaendlerWirdOhneClaudeCallKategorisiert() {
        when(lookupTableService.categorize(KNOWN)).thenReturn(Optional.of(Category.LEBENSMITTEL));

        assertThat(service.categorize(KNOWN)).contains(Category.LEBENSMITTEL);
        verifyNoInteractions(claudeCategorizationService);
    }

    @Test
    void unbekannterHaendlerWirdAnClaudeDelegiert() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorize(UNKNOWN))
                .thenReturn(Optional.of(Category.SHOPPING));

        assertThat(service.categorize(UNKNOWN)).contains(Category.SHOPPING);
        verify(claudeCategorizationService).categorize(UNKNOWN);
    }

    @Test
    void claudeFallbackSonstigesWirdDurchgereicht() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorize(UNKNOWN))
                .thenReturn(Optional.of(Category.SONSTIGES));

        assertThat(service.categorize(UNKNOWN)).contains(Category.SONSTIGES);
    }

    /**
     * Verteidigt AC 3: Claude-Fehler dürfen den Import-Flow nie abbrechen — auch nicht solche, die
     * {@link ClaudeCategorizationService} selbst nicht abfängt.
     */
    @Test
    void unerwarteterClaudeFehlerFuehrtZuSonstigesStattAbbruch() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorize(UNKNOWN))
                .thenThrow(new IllegalStateException("SDK kaputt"));

        assertThat(service.categorize(UNKNOWN)).contains(Category.SONSTIGES);
    }

    /** Defensiv: ein leeres Optional aus Stufe 2 darf nicht als "keine Kategorie" durchschlagen. */
    @Test
    void leeresErgebnisVonClaudeWirdZuSonstiges() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorize(UNKNOWN)).thenReturn(Optional.empty());

        assertThat(service.categorize(UNKNOWN)).contains(Category.SONSTIGES);
    }

    /** Ein DB-Fehler ist ein echter Fehler und wird nicht zu 'Sonstiges' geschluckt. */
    @Test
    void lookupFehlerPropagiert() {
        when(lookupTableService.categorize(KNOWN))
                .thenThrow(new IllegalStateException("DB nicht erreichbar"));

        assertThatThrownBy(() -> service.categorize(KNOWN))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(claudeCategorizationService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void leereEingabeLiefertEmptyOhneStufenAufzurufen(String transactionText) {
        assertThat(service.categorize(transactionText)).isEmpty();
        verifyNoInteractions(lookupTableService, claudeCategorizationService);
    }
}
