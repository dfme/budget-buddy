package com.budgetbuddy.recurring;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lese-Port auf die Beträge der erkannten, nicht verneinten Abos eines Users — die Information
 * aus dem {@code recurring}-Modul, die das {@code budget}-Modul für den Safe-to-Spend braucht
 * (FE-FC-05, US-06/US-08).
 *
 * <p>Über dieses Interface liest der {@code SafeToSpendService}, ohne direkt auf
 * {@link RecurringExpenseRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart
 * wie {@code com.budgetbuddy.transaction.MonthlyExpensePort}: das Interface steht im
 * <em>liefernden</em> Modul, nicht im aufrufenden.
 *
 * <p>Bewusst schmal: über die Kante gehen <em>Beträge</em>, keine {@link RecurringExpense}-Entities.
 * Der Safe-to-Spend vergleicht und summiert über Beträge — den Empfänger braucht er nicht, und
 * das «Neu»-Flag geht ihn nichts an. Die Zuordnungsregel — welche Belastung des Monats die
 * Abbuchung eines Abos ist — bleibt drüben im budget-Modul, wo dieselbe Regel schon für die
 * Fixkosten-Positionen gilt (ADR-13).
 */
public interface RecurringExpenseAmountPort {

    /**
     * Liefert die Beträge aller Abos des Users im Status {@link RecurringExpenseStatus#DETECTED}.
     * Per «Kein Abo» verneinte Einträge ({@code DISMISSED}) fliessen nicht ein — sie sind kein
     * Abo und dürfen den Safe-to-Spend nicht mindern.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return je Abo ein Betrag in CHF als {@link BigDecimal} mit Skala 2 (ADR-9), positiv; leere
     *     Liste, wenn keines erkannt ist. Die Reihenfolge trägt keine Zusage — der Aufrufer
     *     summiert und vergleicht über Beträge, nicht über Positionen.
     */
    List<BigDecimal> detectedAmounts(long userId);
}
