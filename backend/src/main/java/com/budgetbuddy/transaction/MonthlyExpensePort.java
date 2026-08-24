package com.budgetbuddy.transaction;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Lese-Port auf die Belastungen eines Monats — die einzige Information aus dem
 * {@code transaction}-Modul, die das {@code budget}-Modul für den Safe-to-Spend aus US-06 braucht
 * (BE-STS-01).
 *
 * <p>Über dieses Interface liest der {@code SafeToSpendService}, ohne direkt auf
 * {@link TransactionRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart wie
 * {@code com.budgetbuddy.auth.UserIncomePort}: das Interface steht im <em>liefernden</em> Modul,
 * nicht im aufrufenden.
 *
 * <p>Bewusst schmal: über die Kante gehen <em>Beträge</em>, keine {@link Transaction}-Entities. Ein
 * breiterer Rückgabetyp nähme Buchungstexte und Kategorien ins budget-Modul mit, das mit beidem
 * nichts zu tun hat.
 *
 * <p><strong>Einzelbeträge statt Summe (BE-STS-04).</strong> Bis ADR-13 lieferte dieser Port die
 * fertige Monatssumme. Das reicht nicht mehr: das budget-Modul muss die per Dauerauftrag bezahlten
 * Fixkosten aus dem Summanden streichen, und dafür braucht es die einzelnen Belastungen. Die
 * Zuordnungsregel selbst bleibt drüben im budget-Modul, wo die Fixkosten liegen — hier ist sie
 * fachlich nicht zu Hause. Eine zweite Methode neben der Summe wäre die Alternative gewesen; sie
 * hätte zwei Wege auf dieselbe Zahl geschaffen, die auseinanderlaufen können.
 */
public interface MonthlyExpensePort {

    /**
     * Liefert die Beträge der <em>Ausgaben</em> ({@code is_income = false}) des Users im angegebenen
     * Monat. Gutschriften fliessen nicht ein.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param month Monat, dessen Belastungen geliefert werden.
     * @return je Belastung ein Betrag in CHF als {@link BigDecimal} mit Skala 2 (ADR-9); leere
     *     Liste, wenn der User in diesem Monat keine Ausgaben hat. Die Reihenfolge ist die der
     *     zugrunde liegenden Query und trägt keine Zusage — der Aufrufer summiert und vergleicht
     *     über Beträge, nicht über Positionen.
     */
    List<BigDecimal> expenseAmounts(long userId, YearMonth month);
}
