package com.budgetbuddy.transaction;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Lese-Port auf den aus den Transaktionen abgeleiteten Einkommens-Vorschlag — die zweite und
 * letzte Information aus dem {@code transaction}-Modul, die das {@code budget}-Modul für den
 * Safe-to-Spend aus US-06 braucht (BE-STS-02).
 *
 * <p>Über dieses Interface liest der {@code SafeToSpendService}, ohne direkt auf
 * {@link TransactionRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart wie
 * {@link MonthlyExpensePort} und {@code com.budgetbuddy.auth.UserIncomePort}: das Interface steht
 * im <em>liefernden</em> Modul, nicht im aufrufenden.
 *
 * <p>Bewusst schmal: über die Kante geht ein Betrag, nicht die erkannte Gutschriften-Gruppe. Der
 * Buchungstext, über den die Heuristik gruppiert, bleibt damit im transaction-Modul — das
 * budget-Modul hat mit Buchungstexten nichts zu tun (dieselbe Begründung wie bei
 * {@link MonthlyExpensePort}). Der Hinweistext aus US-06 («Regelmässige Gutschrift von X CHF
 * erkannt») braucht nur X.
 */
public interface IncomeSuggestionPort {

    /**
     * Leitet aus den Gutschriften des Users ein wahrscheinliches Monatseinkommen ab.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return der vorgeschlagene Betrag in CHF als {@link BigDecimal} mit Skala 2 (ADR-9), oder
     *     leer, wenn sich aus den vorhandenen Gutschriften kein wiederkehrendes Muster ableiten
     *     lässt. Leer ist der Normalfall für einen frisch angelegten User ohne Import — kein
     *     Fehler.
     */
    Optional<BigDecimal> suggestMonthlyIncome(long userId);
}
