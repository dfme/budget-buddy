package com.budgetbuddy.categorization;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erster Schritt der Hybrid-Kategorisierung (ADR-6): deterministischer DB-Lookup gegen die
 * globalen Seeds ({@code category_lookup}, V04 und V15) und die gelernten Patterns des Users
 * ({@code user_category_lookup}, V12).
 *
 * <p>Ein Transaktionstext wird einer Kategorie zugeordnet, wenn eines der bekannten Händler-Pattern
 * (case-insensitiv) darin enthalten ist. Unbekannte Texte liefern {@link Optional#empty()} — der
 * Aufrufer eskaliert dann an die nächste Stufe (Claude-API) bzw. den Fallback {@code Sonstiges}.
 *
 * <p><strong>Zwei Pools, eine Regel</strong> (BE-CAT-12, ADR-15): Das längste und damit
 * spezifischste Pattern gewinnt, egal woher es stammt; bei gleicher Länge das eigene. So kann ein
 * User {@code MIGROS} für sich anders einordnen als der Seed, ohne den Seed für alle zu ändern —
 * und ein längeres gelerntes Pattern ({@code MIGROS BANK ZINS}) schlägt den kürzeren Seed, so wie
 * es innerhalb eines Pools schon immer galt. Fremde Lerneinträge sieht die Query nie: die
 * Einschränkung auf {@code userId} steht in {@link UserCategoryLookupRepository#findMatching}.
 */
@Service
public class LookupTableService implements CategorizationPort {

    private final CategoryLookupRepository categoryLookupRepository;
    private final UserCategoryLookupRepository userCategoryLookupRepository;

    public LookupTableService(
            CategoryLookupRepository categoryLookupRepository,
            UserCategoryLookupRepository userCategoryLookupRepository) {
        this.categoryLookupRepository = categoryLookupRepository;
        this.userCategoryLookupRepository = userCategoryLookupRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategorizationResult> categorize(long userId, String transactionText) {
        if (transactionText == null || transactionText.isBlank()) {
            return Optional.empty();
        }

        // Beide Queries sortieren nach Pattern-Länge absteigend; verglichen wird nur der jeweils
        // spezifischste Treffer. Eigene zuerst, damit sie bei gleicher Länge gewinnen.
        List<UserCategoryLookup> own = userCategoryLookupRepository.findMatching(userId, transactionText);
        List<CategoryLookup> global = categoryLookupRepository.findMatching(transactionText);

        String category;
        if (own.isEmpty() && global.isEmpty()) {
            return Optional.empty();
        } else if (global.isEmpty()) {
            category = own.get(0).getCategory();
        } else if (own.isEmpty()) {
            category = global.get(0).getCategory();
        } else {
            category = own.get(0).getEmpfaengerPattern().length()
                    >= global.get(0).getEmpfaengerPattern().length()
                    ? own.get(0).getCategory()
                    : global.get(0).getCategory();
        }
        return Optional.of(new CategorizationResult(
                Category.fromLabel(category), CategorizationResult.Source.LOOKUP));
    }
}
