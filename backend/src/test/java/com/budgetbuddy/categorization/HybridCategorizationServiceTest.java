package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
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
 *
 * <p>Seit ADR-14 orchestriert die Kette zusätzlich <em>gebündelt</em>: Der Lookup läuft über alle
 * Texte, und nur was er nicht kennt, geht in einem Zug an Claude. Die Stufe 2 wird deshalb über
 * {@code categorizeAll} gemockt — {@code categorize} delegiert intern dorthin.
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
        when(lookupTableService.categorize(KNOWN)).thenReturn(Optional.of(lookup(Category.LEBENSMITTEL)));

        assertThat(service.categorize(KNOWN)).contains(lookup(Category.LEBENSMITTEL));
        verifyNoInteractions(claudeCategorizationService);
    }

    @Test
    void unbekannterHaendlerWirdAnClaudeDelegiert() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(claude(Category.SHOPPING))));

        assertThat(service.categorize(UNKNOWN)).contains(claude(Category.SHOPPING));
        verify(claudeCategorizationService).categorizeAll(List.of(UNKNOWN));
    }

    /**
     * Der Kern von ADR-14 auf Ketten-Ebene: Bekannte Händler kosten keinen Call, und alles
     * Unbekannte geht in <em>einem</em> Aufruf hinaus — nicht in einem pro Transaktion. Genau
     * diese Trennung verhindert, dass der Default aus {@link CategorizationPort} greift und die
     * Kette wieder pro Text durchläuft (die Sequenzialität aus #192).
     */
    @Test
    void categorizeAll_fragtNurDieUnbekanntenUndZwarGebuendelt() {
        when(lookupTableService.categorize(KNOWN)).thenReturn(Optional.of(lookup(Category.LEBENSMITTEL)));
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(lookupTableService.categorize("SBB")).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN, "SBB")))
                .thenReturn(List.of(
                        Optional.of(claude(Category.SHOPPING)),
                        Optional.of(claude(Category.TRANSPORT))));

        List<Optional<CategorizationResult>> results =
                service.categorizeAll(List.of(KNOWN, UNKNOWN, "SBB"));

        assertThat(results).containsExactly(
                Optional.of(lookup(Category.LEBENSMITTEL)),
                Optional.of(claude(Category.SHOPPING)),
                Optional.of(claude(Category.TRANSPORT)));
        verify(claudeCategorizationService).categorizeAll(List.of(UNKNOWN, "SBB"));
    }

    /**
     * Positionsgleichheit ist der Vertrag von {@link CategorizationPort#categorizeAll} — im Import
     * hängt daran, welche Kategorie an welcher Buchung landet. Der leere Text in der Mitte ist der
     * heikle Fall: Er geht an keine der beiden Stufen und darf den Rest nicht verschieben.
     */
    @Test
    void categorizeAll_haeltDieReihenfolgeAuchMitLeeremText() {
        when(lookupTableService.categorize(KNOWN)).thenReturn(Optional.of(lookup(Category.LEBENSMITTEL)));
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(claude(Category.SHOPPING))));

        assertThat(service.categorizeAll(Arrays.asList(KNOWN, "  ", UNKNOWN))).containsExactly(
                Optional.of(lookup(Category.LEBENSMITTEL)),
                Optional.empty(),
                Optional.of(claude(Category.SHOPPING)));
    }

    @Test
    void claudeFallbackSonstigesWirdDurchgereicht() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(claude(Category.SONSTIGES))));

        assertThat(service.categorize(UNKNOWN)).contains(claude(Category.SONSTIGES));
    }

    /**
     * Verteidigt AC 3: Claude-Fehler dürfen den Import-Flow nie abbrechen — auch nicht solche, die
     * {@link ClaudeCategorizationService} selbst nicht abfängt.
     */
    @Test
    void unerwarteterClaudeFehlerFuehrtZuSonstigesStattAbbruch() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenThrow(new IllegalStateException("SDK kaputt"));

        assertThat(service.categorize(UNKNOWN)).contains(claude(Category.SONSTIGES));
    }

    /** Defensiv: ein leeres Optional aus Stufe 2 darf nicht als "keine Kategorie" durchschlagen. */
    @Test
    void leeresErgebnisVonClaudeWirdZuSonstiges() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.empty()));

        assertThat(service.categorize(UNKNOWN)).contains(claude(Category.SONSTIGES));
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

    private static CategorizationResult lookup(Category category) {
        return new CategorizationResult(category, CategorizationResult.Source.LOOKUP);
    }

    private static CategorizationResult claude(Category category) {
        return new CategorizationResult(category, CategorizationResult.Source.CLAUDE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void leereEingabeLiefertEmptyOhneStufenAufzurufen(String transactionText) {
        assertThat(service.categorize(transactionText)).isEmpty();
        verifyNoInteractions(lookupTableService, claudeCategorizationService);
    }
}
