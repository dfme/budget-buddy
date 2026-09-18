package com.budgetbuddy.categorization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Einstiegspunkt der Hybrid-Kategorisierung (ADR-6): orchestriert die beiden Stufen der Kette —
 * zuerst der deterministische {@link LookupTableService}, und nur für dort unbekannte Texte die
 * {@link ClaudeCategorizationService}.
 *
 * <p>Die Reihenfolge ist der Kern von ADR-6: Der Lookup deckt ~70–80% der Transaktionen kostenlos
 * ab, sodass pro Import nur ~20–30% überhaupt einen API-Call auslösen.
 *
 * <p><strong>{@link #categorizeAll} hält die Stufen getrennt</strong> (ADR-14, BE-PDF-09): Erst
 * läuft der Lookup über <em>alle</em> Texte, dann geht der Rest in einem Zug an Claude. Würde
 * stattdessen der Default aus {@link CategorizationPort} greifen, liefe die Kette pro Text einmal
 * durch und jede unbekannte Transaktion löste wieder ihren eigenen Request aus — genau die
 * Sequenzialität, die #192 verursacht hat.
 *
 * <p>{@link Primary}, weil es drei {@link CategorizationPort}-Beans gibt: Aufrufer, die den Port
 * injizieren, sollen die vollständige Kette bekommen und nicht versehentlich eine Einzelstufe.
 *
 * <p>Als letzte Stufe der Kette liefert dieser Service für jede nicht-leere Eingabe eine Kategorie;
 * {@link Optional#empty()} kommt nur bei leerer Eingabe zurück, wo es nichts zu kategorisieren gibt.
 *
 * <p><strong>Der Lerneffekt setzt hier an, nicht erst bei der manuellen Korrektur</strong>
 * (BE-CAT-11, ADR-6 Schritt 4): Was Claude erfolgreich kategorisiert hat, geht über den
 * {@link CategoryLearningPort} zurück in die {@code category_lookup}-Tabelle. Ohne das löste
 * derselbe Händler bei jedem Import wieder einen Call aus, obwohl seine Kategorie längst einmal
 * ermittelt war — ein von Claude korrekt eingestufter, aber nie korrigierter Händler lernte nie.
 * Diese Stelle ist die richtige, weil hier beides vorliegt: der <em>rohe</em> Transaktionstext, an
 * dem die Lookup-Stufe später matcht, und die {@link CategorizationResult.Source}, an der ein
 * echtes Modell-Ergebnis von einem Ersatz-{@code Sonstiges} zu unterscheiden ist. Siehe
 * {@link #learnFromClaude} für die Frage, was <em>nicht</em> gelernt wird.
 */
@Service
@Primary
public class HybridCategorizationService implements CategorizationPort {

    private static final Logger log = LoggerFactory.getLogger(HybridCategorizationService.class);

    private final LookupTableService lookupTableService;
    private final ClaudeCategorizationService claudeCategorizationService;
    private final CategoryLearningPort categoryLearningPort;

    public HybridCategorizationService(
            LookupTableService lookupTableService,
            ClaudeCategorizationService claudeCategorizationService,
            CategoryLearningPort categoryLearningPort) {
        this.lookupTableService = lookupTableService;
        this.claudeCategorizationService = claudeCategorizationService;
        this.categoryLearningPort = categoryLearningPort;
    }

    @Override
    public Optional<CategorizationResult> categorize(String transactionText) {
        return categorizeAll(Collections.singletonList(transactionText)).get(0);
    }

    @Override
    public List<Optional<CategorizationResult>> categorizeAll(List<String> transactionTexts) {
        List<Optional<CategorizationResult>> results =
                new ArrayList<>(Collections.nCopies(transactionTexts.size(), Optional.empty()));

        // Stufe 1: bekannte Händler → fertig, kein API-Call.
        List<Integer> unknown = new ArrayList<>();
        for (int i = 0; i < transactionTexts.size(); i++) {
            String text = transactionTexts.get(i);
            if (text == null || text.isBlank()) {
                continue;
            }
            Optional<CategorizationResult> fromLookup = lookupTableService.categorize(text);
            if (fromLookup.isPresent()) {
                // Transaktionstext redigiert (BE-PDF-06): auch DEBUG darf keine Zahlungsdaten tragen.
                log.debug("{} via Lookup-Tabelle als '{}' kategorisiert.",
                        LogRedaction.redact(text), fromLookup.get().category().getLabel());
                results.set(i, fromLookup);
            } else {
                unknown.add(i);
            }
        }

        if (unknown.isEmpty()) {
            return results;
        }

        // Stufe 2: alles Unbekannte in einem Zug an Claude.
        List<String> unknownTexts = unknown.stream().map(transactionTexts::get).toList();
        List<Optional<CategorizationResult>> fromClaude = categorizeWithClaude(unknownTexts);
        for (int position = 0; position < unknown.size(); position++) {
            results.set(
                    unknown.get(position),
                    // Der Fallback hier deckt den Catch in categorizeWithClaude ab: ein Request
                    // ging hinaus (oder wurde versucht), nur kam nichts Brauchbares zurück —
                    // CLAUDE_FALLBACK, damit er nicht gelernt wird (BE-CAT-11).
                    Optional.of(fromClaude.get(position).orElse(
                            new CategorizationResult(
                                    Category.SONSTIGES,
                                    CategorizationResult.Source.CLAUDE_FALLBACK))));
        }

        // Stufe 3: was Claude wirklich beantwortet hat, wandert in die Lookup-Tabelle.
        learnFromClaude(transactionTexts, results, unknown);
        return results;
    }

    /**
     * Schreibt die erfolgreich von Claude kategorisierten Händler in die
     * {@code category_lookup}-Tabelle (BE-CAT-11, ADR-6 Schritt 4).
     *
     * <p><strong>Gelernt wird nur {@link CategorizationResult.Source#CLAUDE}</strong> — die
     * Transaktionen, für die ein Request hinausging <em>und</em> eine lesbare Antwort einen
     * gültigen Eintrag enthielt. Ein Ersatz-{@code Sonstiges} ({@code CLAUDE_FALLBACK},
     * {@code CLAUDE_SKIPPED}) bleibt draussen, und das ist der eigentliche Punkt dieses Tasks:
     * Würde es gelernt, fröre ein Netzwerkfehler oder ein offener Circuit Breaker den Händler
     * dauerhaft auf {@code Sonstiges} ein — die Lookup-Stufe fängt ihn ab dem nächsten Import vor
     * Claude ab, und er käme nie wieder zur Bewertung.
     *
     * <p><strong>Ein echtes {@code Sonstiges} vom Modell wird ebenfalls nicht gelernt.</strong>
     * Seine Einfrier-Wirkung ist dieselbe, nur ohne Fehler als Ursache, und es ist kein
     * Wissensgewinn gegenüber dem Fallback, den es für unbekannte Händler ohnehin gibt. Der Preis
     * ist ein Call pro unklarem Händler und Import — bewusst gezahlt, damit ein später besseres
     * Modell den Händler noch einmal sehen kann.
     *
     * <p>Gelernt wird der <strong>rohe</strong> Text, nicht die von {@link PromptSanitizer}
     * maskierte Fassung: Die Lookup-Stufe matcht gegen den rohen Text, und die Patterns aus der
     * manuellen Korrektur (BE-CAT-04) sind ebenfalls roh. Datenschutzfolge: Seit BE-CAT-11 landet
     * nicht mehr nur ein aktiv korrigierter, sondern jeder von Claude eingestufte Händlertext in
     * dieser Tabelle — sie hat keine {@code user_id} und überlebt die Kontolöschung (offene Lücke
     * #290, siehe {@code UserService#deleteUser}).
     *
     * <p><strong>Ein Fehler hier darf die Kategorisierung nicht kosten.</strong> Die Ergebnisse
     * stehen zu diesem Zeitpunkt vollständig in {@code results}; eine nicht erreichbare Datenbank
     * macht den Import nicht falsch, sondern nur den Lerneffekt wirkungslos — und der greift beim
     * nächsten Import erneut. Gezählt statt pro Eintrag geloggt: Fällt die DB aus, ist der Grund
     * für alle Einträge derselbe, und 20 identische Warnungen pro Bündel verdecken nur den Rest
     * des Import-Logs.
     *
     * @param positions Indizes in {@code texts}, die an Claude gingen — nur sie kommen infrage.
     */
    private void learnFromClaude(
            List<String> texts,
            List<Optional<CategorizationResult>> results,
            List<Integer> positions) {

        // Derselbe Händler steht auf einem Auszug oft mehrfach. Ohne diese Menge liefe pro
        // Vorkommen ein eigener Upsert auf denselben Primärschlüssel.
        Set<String> alreadyLearned = new HashSet<>();
        int failed = 0;
        RuntimeException lastFailure = null;

        for (int index : positions) {
            CategorizationResult result = results.get(index).orElse(null);
            if (result == null
                    || result.source() != CategorizationResult.Source.CLAUDE
                    || result.category() == Category.SONSTIGES) {
                continue;
            }
            String text = texts.get(index);
            if (!alreadyLearned.add(text)) {
                continue;
            }
            try {
                categoryLearningPort.learn(text, result.category());
            } catch (RuntimeException e) {
                failed++;
                lastFailure = e;
            }
        }

        if (lastFailure != null) {
            // Ohne Wortlaut der Exception (BE-PDF-06): Eine DB-Meldung kann das Pattern und damit
            // den Transaktionstext enthalten.
            log.warn("{} von Claude kategorisierte Händler konnten nicht in die Lookup-Tabelle "
                            + "geschrieben werden ({}) — die Kategorisierung selbst ist davon "
                            + "unberührt, der Lerneffekt greift beim nächsten Import erneut.",
                    failed, LogRedaction.describe(lastFailure));
        }
    }

    /**
     * {@link ClaudeCategorizationService} fängt {@link com.anthropic.errors.AnthropicException}
     * bereits selbst ab. Der Catch hier deckt alles darüber hinaus ab — ein unerwarteter
     * Laufzeitfehler aus dem SDK darf den synchronen Import-Flow nicht abbrechen (Churn-Risiko #1).
     */
    private List<Optional<CategorizationResult>> categorizeWithClaude(List<String> texts) {
        try {
            return claudeCategorizationService.categorizeAll(texts);
        } catch (RuntimeException e) {
            log.warn("Unerwarteter Fehler bei der Claude-Kategorisierung von {} Transaktion(en) — "
                    + "Fallback 'Sonstiges'.", texts.size(), e);
            return Collections.nCopies(texts.size(), Optional.empty());
        }
    }
}
