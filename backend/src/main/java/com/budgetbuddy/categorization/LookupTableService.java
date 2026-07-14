package com.budgetbuddy.categorization;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erster Schritt der Hybrid-Kategorisierung (ADR-6): deterministischer DB-Lookup gegen die
 * {@code category_lookup}-Tabelle.
 *
 * <p>Ein Transaktionstext wird einer Kategorie zugeordnet, wenn eines der bekannten Händler-Pattern
 * (case-insensitiv) darin enthalten ist. Unbekannte Texte liefern {@link Optional#empty()} — der
 * Aufrufer eskaliert dann an die nächste Stufe (Claude-API) bzw. den Fallback {@code Sonstiges}.
 */
@Service
public class LookupTableService implements CategorizationPort {

    private final CategoryLookupRepository categoryLookupRepository;

    public LookupTableService(CategoryLookupRepository categoryLookupRepository) {
        this.categoryLookupRepository = categoryLookupRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> categorize(String transactionText) {
        if (transactionText == null || transactionText.isBlank()) {
            return Optional.empty();
        }

        List<CategoryLookup> matches = categoryLookupRepository.findMatching(transactionText);
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        // Spezifischster Treffer zuerst (Query sortiert nach Pattern-Länge absteigend).
        return Optional.of(Category.fromLabel(matches.get(0).getCategory()));
    }
}
