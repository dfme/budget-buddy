package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Mock private CategoryLearningPort categoryLearningPort;

    @InjectMocks private HybridCategorizationService service;

    @Test
    void bekannterHaendlerWirdOhneClaudeCallKategorisiert() {
        when(lookupTableService.categorize(KNOWN)).thenReturn(Optional.of(lookup(Category.LEBENSMITTEL)));

        assertThat(service.categorize(KNOWN)).contains(lookup(Category.LEBENSMITTEL));
        verifyNoInteractions(claudeCategorizationService);
        // Ein Lookup-Treffer steht bereits in der Tabelle — ihn zurückzuschreiben wäre ein
        // Upsert auf sich selbst und pro Import eine Schreiblast ohne jeden Gewinn.
        verifyNoInteractions(categoryLearningPort);
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

    /**
     * Ein {@code Sonstiges}, das Claude selbst geliefert hat, kommt unverändert beim Aufrufer an —
     * einschliesslich seiner {@code Source}. Gelernt wird es trotzdem nicht, siehe
     * {@link #echtesSonstigesWirdNichtGelernt}.
     */
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

        // CLAUDE_FALLBACK, nicht CLAUDE: Ein Ergebnis, das nur aus dem Fehler entstanden ist,
        // darf nicht gelernt werden (BE-CAT-11).
        assertThat(service.categorize(UNKNOWN)).contains(fallback());
        verifyNoInteractions(categoryLearningPort);
    }

    /** Defensiv: ein leeres Optional aus Stufe 2 darf nicht als "keine Kategorie" durchschlagen. */
    @Test
    void leeresErgebnisVonClaudeWirdZuSonstiges() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.empty()));

        assertThat(service.categorize(UNKNOWN)).contains(fallback());
        verifyNoInteractions(categoryLearningPort);
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

    // --- Lerneffekt aus erfolgreicher Claude-Kategorisierung (BE-CAT-11, ADR-6 Schritt 4) ---

    /** AC 1: Was Claude wirklich beantwortet hat, geht als Händler-Pattern in die Tabelle. */
    @Test
    void lerntNachErfolgreicherClaudeKategorisierung() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(claude(Category.SHOPPING))));

        service.categorize(UNKNOWN);

        // Der rohe Text, nicht die maskierte Fassung: Die Lookup-Stufe matcht gegen den rohen
        // Text, und die Patterns aus der manuellen Korrektur (BE-CAT-04) sind ebenfalls roh.
        verify(categoryLearningPort).learn(UNKNOWN, Category.SHOPPING);
    }

    /**
     * AC 4 — der eigentliche Zweck des Tasks: Ein zweiter Import desselben Händlertexts kostet
     * keinen Claude-Call mehr.
     *
     * <p>Die Lookup-Tabelle ist hier eine echte {@link Map}, in die der Lern-Port schreibt und aus
     * der die Lookup-Stufe liest. Zwei fest einprogrammierte Rückgaben täten es auch — aber sie
     * prüften nur, dass der Test selbst die zweite Antwort kennt. So hängt der Lookup-Treffer im
     * zweiten Lauf tatsächlich am {@code learn}-Aufruf des ersten.
     */
    @Test
    void zweiterImportTrifftDieLookupTabelleOhneClaudeCall() {
        Map<String, Category> lookupTable = new HashMap<>();
        when(lookupTableService.categorize(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(lookupTable.get(invocation.getArgument(0, String.class)))
                        .map(HybridCategorizationServiceTest::lookup));
        doAnswer(invocation -> lookupTable.put(
                        invocation.getArgument(0), invocation.getArgument(1)))
                .when(categoryLearningPort).learn(anyString(), any());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(claude(Category.SHOPPING))));

        assertThat(service.categorize(UNKNOWN)).contains(claude(Category.SHOPPING));
        assertThat(service.categorize(UNKNOWN)).contains(lookup(Category.SHOPPING));

        verify(claudeCategorizationService, times(1)).categorizeAll(List.of(UNKNOWN));
    }

    /**
     * AC 5: Der offene Circuit Breaker liefert {@code Sonstiges} ohne Request. Würde das gelernt,
     * fröre ein Ausfall der Claude-API jeden Händler des laufenden Imports dauerhaft auf
     * {@code Sonstiges} ein — die Lookup-Stufe finge ihn ab dem nächsten Import vor Claude ab.
     */
    @Test
    void offenerBreakerSchreibtKeinenLookupEintrag() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(skipped())));

        assertThat(service.categorize(UNKNOWN)).contains(skipped());
        verifyNoInteractions(categoryLearningPort);
    }

    /** AC 2, die Gegenprobe mit Request: fehlgeschlagener oder unlesbarer Call. */
    @Test
    void fehlgeschlagenerCallSchreibtKeinenLookupEintrag() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(fallback())));

        assertThat(service.categorize(UNKNOWN)).contains(fallback());
        verifyNoInteractions(categoryLearningPort);
    }

    /**
     * Auch ein <em>echtes</em> {@code Sonstiges} vom Modell wird nicht gelernt: Seine
     * Einfrier-Wirkung ist dieselbe wie beim Fehler-Fallback, nur ohne Fehler als Ursache, und es
     * ist kein Wissensgewinn gegenüber dem Fallback, den unbekannte Händler ohnehin bekommen.
     */
    @Test
    void echtesSonstigesWirdNichtGelernt() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(claude(Category.SONSTIGES))));

        assertThat(service.categorize(UNKNOWN)).contains(claude(Category.SONSTIGES));
        verifyNoInteractions(categoryLearningPort);
    }

    /**
     * Derselbe Händler steht auf einem Auszug oft mehrfach. Ohne Deduplizierung liefe pro
     * Vorkommen ein eigener Upsert auf denselben Primärschlüssel — bei einem Auszug voller
     * TWINT-Zahlungen an dieselbe Bäckerei wären das zwanzig identische Schreibvorgänge.
     */
    @Test
    void derselbeHaendlerImBuendelWirdNurEinmalGelernt() {
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN, UNKNOWN, UNKNOWN)))
                .thenReturn(List.of(
                        Optional.of(claude(Category.SHOPPING)),
                        Optional.of(claude(Category.SHOPPING)),
                        Optional.of(claude(Category.SHOPPING))));

        service.categorizeAll(List.of(UNKNOWN, UNKNOWN, UNKNOWN));

        verify(categoryLearningPort, times(1)).learn(UNKNOWN, Category.SHOPPING);
    }

    /**
     * Der Lerneffekt ist ein Nebeneffekt, keine Vorbedingung: Eine nicht erreichbare Datenbank
     * macht den Import nicht falsch, sondern nur den Lerneffekt wirkungslos — und der greift beim
     * nächsten Import erneut. Ohne diese Isolation nähme ein Schreibfehler die bereits
     * kategorisierten Transaktionen mit in den Abbruch.
     */
    @Test
    void fehlerBeimLernenBrichtDieKategorisierungNichtAb() {
        when(lookupTableService.categorize(KNOWN))
                .thenReturn(Optional.of(lookup(Category.LEBENSMITTEL)));
        when(lookupTableService.categorize(UNKNOWN)).thenReturn(Optional.empty());
        when(claudeCategorizationService.categorizeAll(List.of(UNKNOWN)))
                .thenReturn(List.of(Optional.of(claude(Category.SHOPPING))));
        doThrow(new IllegalStateException("DB nicht erreichbar"))
                .when(categoryLearningPort).learn(anyString(), any());

        assertThat(service.categorizeAll(List.of(KNOWN, UNKNOWN))).containsExactly(
                Optional.of(lookup(Category.LEBENSMITTEL)),
                Optional.of(claude(Category.SHOPPING)));
    }

    private static CategorizationResult lookup(Category category) {
        return new CategorizationResult(category, CategorizationResult.Source.LOOKUP);
    }

    private static CategorizationResult claude(Category category) {
        return new CategorizationResult(category, CategorizationResult.Source.CLAUDE);
    }

    /** Request ging hinaus, brauchbares Ergebnis kam keines zurück (BE-CAT-11). */
    private static CategorizationResult fallback() {
        return new CategorizationResult(
                Category.SONSTIGES, CategorizationResult.Source.CLAUDE_FALLBACK);
    }

    /** Claude-Stufe erreicht, aber ohne HTTP-Request auf {@code Sonstiges} gefallen. */
    private static CategorizationResult skipped() {
        return new CategorizationResult(
                Category.SONSTIGES, CategorizationResult.Source.CLAUDE_SKIPPED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void leereEingabeLiefertEmptyOhneStufenAufzurufen(String transactionText) {
        assertThat(service.categorize(transactionText)).isEmpty();
        verifyNoInteractions(lookupTableService, claudeCategorizationService);
    }
}
