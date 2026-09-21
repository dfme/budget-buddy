package com.budgetbuddy.transaction;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Lese-Port auf die Ausgaben-Historie eines Users — die Information aus dem
 * {@code transaction}-Modul, die das {@code recurring}-Modul für die Abo-Erkennung aus US-08
 * braucht (BE-REC-01).
 *
 * <p>Über dieses Interface liest der {@code RecurringExpenseService}, ohne direkt auf
 * {@link TransactionRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart wie
 * {@link MonthlyExpensePort}: das Interface steht im <em>liefernden</em> Modul, nicht im
 * aufrufenden.
 *
 * <p><strong>Über die Kante geht der bereits normalisierte Empfänger</strong>, nicht die
 * {@link Transaction}-Entity. Wo der Empfänger steht — erste Zeile von {@code buchungsdetails} bei
 * PostFinance, der Buchungstext bei UBS/Raiffeisen (BE-PDF-07) — ist Wissen über das Format der
 * Bank-PDFs und gehört in dieses Modul. Das recurring-Modul gruppiert nur noch, es interpretiert
 * keine Buchungstexte.
 *
 * <p><strong>Ohne Zeitfenster</strong>, anders als {@code IncomeSuggestionService}: die Erkennung
 * läuft nach jedem Import, und der kann einen Jahresauszug von 2025 nachreichen. Ein Fenster ab
 * «heute» liesse dessen Abos unerkannt. Die Menge ist die Ausgaben-Historie eines einzelnen Users
 * — einige hundert Zeilen pro importiertem Jahr.
 */
public interface ExpenseHistoryPort {

    /**
     * Eine Ausgabe, reduziert auf das, was die Abo-Erkennung vergleicht.
     *
     * @param payeeKey normalisierter Empfänger: Grossschreibung, ohne Ziffernfolgen, nie
     *     leer. Zwei Buchungen mit gleichem Schlüssel gelten als derselbe Empfänger.
     * @param amount Betrag der Belastung in CHF, positiv, Skala 2 (ADR-9).
     * @param month Kalendermonat des Buchungsdatums.
     */
    record ExpenseEntry(String payeeKey, BigDecimal amount, YearMonth month) {}

    /**
     * Liefert alle <em>Ausgaben</em> ({@code is_income = false}) des Users, je eine pro
     * Transaktion. Gutschriften fliessen nicht ein.
     *
     * @param userId ID des Users, dessen Import gerade lief.
     * @return je Belastung ein Eintrag; leere Liste, wenn der User keine Ausgaben hat. Die
     *     Reihenfolge trägt keine Zusage.
     */
    List<ExpenseEntry> expenseHistory(long userId);

    /**
     * Wie {@link #expenseHistory(long)}, aber auf das Fenster {@code [from, to]} begrenzt — beide
     * Monate inklusive. Für die Aktivitätsprüfung der Abos im Safe-to-Spend
     * ({@code RecurringExpenseAmountPort.detectedAmounts}, FE-FC-05): dort zählt nur, ob ein
     * Empfänger in den jüngsten Monaten noch abgebucht hat, und der Dashboard-Pfad soll dafür
     * nicht die ganze Historie laden. Die Erkennung selbst bleibt beim ungefensterten Aufruf.
     *
     * @param userId ID des eingeloggten Users.
     * @param from erster Monat des Fensters.
     * @param to letzter Monat des Fensters.
     * @return je Belastung im Fenster ein Eintrag; leere Liste, wenn keine vorliegt. Die
     *     Reihenfolge trägt keine Zusage.
     */
    List<ExpenseEntry> expenseHistory(long userId, YearMonth from, YearMonth to);
}
