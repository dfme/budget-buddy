package com.budgetbuddy.categorization;

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
    public Optional<Category> categorize(String transactionText) {
        if (transactionText == null || transactionText.isBlank()) {
            return Optional.empty();
        }

        // Stufe 1: bekannter Händler → fertig, kein API-Call.
        Optional<Category> fromLookup = lookupTableService.categorize(transactionText);
        if (fromLookup.isPresent()) {
            log.debug("'{}' via Lookup-Tabelle als '{}' kategorisiert.",
                    transactionText, fromLookup.get().getLabel());
            return fromLookup;
        }

        // Stufe 2: unbekannt → Claude.
        return Optional.of(categorizeWithClaude(transactionText));
    }

    /**
     * {@link ClaudeCategorizationService} fängt {@link com.anthropic.errors.AnthropicException}
     * bereits selbst ab. Der Catch hier deckt alles darüber hinaus ab — ein unerwarteter
     * Laufzeitfehler aus dem SDK darf den synchronen Import-Flow nicht abbrechen (Churn-Risiko #1).
     */
    private Category categorizeWithClaude(String transactionText) {
        try {
            return claudeCategorizationService
                    .categorize(transactionText)
                    .orElse(Category.SONSTIGES);
        } catch (RuntimeException e) {
            log.warn("Unerwarteter Fehler bei der Claude-Kategorisierung von '{}' — Fallback "
                    + "'Sonstiges'.", transactionText, e);
            return Category.SONSTIGES;
        }
    }
}
