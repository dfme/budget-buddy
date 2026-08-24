package com.budgetbuddy.categorization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
 */
@Service
@Primary
public class HybridCategorizationService implements CategorizationPort {

    private static final Logger log = LoggerFactory.getLogger(HybridCategorizationService.class);

    private final LookupTableService lookupTableService;
    private final ClaudeCategorizationService claudeCategorizationService;

    public HybridCategorizationService(
            LookupTableService lookupTableService,
            ClaudeCategorizationService claudeCategorizationService) {
        this.lookupTableService = lookupTableService;
        this.claudeCategorizationService = claudeCategorizationService;
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
                    Optional.of(fromClaude.get(position).orElse(
                            new CategorizationResult(
                                    Category.SONSTIGES, CategorizationResult.Source.CLAUDE))));
        }
        return results;
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
