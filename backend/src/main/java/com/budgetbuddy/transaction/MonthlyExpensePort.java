package com.budgetbuddy.transaction;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Lese-Port auf die Ausgabensumme eines Monats — die einzige Information aus dem
 * {@code transaction}-Modul, die das {@code budget}-Modul für den Safe-to-Spend aus US-06 braucht
 * (BE-STS-01).
 *
 * <p>Über dieses Interface liest der {@code SafeToSpendService}, ohne direkt auf
 * {@link TransactionRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart wie
 * {@code com.budgetbuddy.auth.UserIncomePort}: das Interface steht im <em>liefernden</em> Modul,
 * nicht im aufrufenden.
 *
 * <p>Bewusst schmal: über die Kante geht eine Summe, keine {@link Transaction}-Entities. Ein
 * breiterer Rückgabetyp nähme Buchungstexte und Kategorien ins budget-Modul mit, das mit beidem
 * nichts zu tun hat.
 */
public interface MonthlyExpensePort {

    /**
     * Summiert die <em>Ausgaben</em> ({@code is_income = false}) des Users im angegebenen Monat.
     * Gutschriften fliessen nicht ein.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param month Monat, dessen Belastungen summiert werden.
     * @return Summe in CHF als {@link BigDecimal} mit Skala 2 (ADR-9); {@code 0.00}, wenn der User
     *     in diesem Monat keine Ausgaben hat.
     */
    BigDecimal sumExpenses(long userId, YearMonth month);
}
