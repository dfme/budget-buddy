package com.budgetbuddy.recurring;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lese-Port auf die Beträge der erkannten, nicht verneinten und noch laufenden Abos eines Users —
 * die Information aus dem {@code recurring}-Modul, die das {@code budget}-Modul für den
 * Safe-to-Spend braucht (FE-FC-05, US-06/US-08).
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
 *
 * <p><strong>Die Beträge sind aktuell, aber nicht rappengenau.</strong>
 * {@code recurring_expenses.amount} ist der Betrag des jüngsten qualifizierenden Monatspaars;
 * seit BE-REC-04 (#350) zieht ihn jeder Import nach, ein Preissprung über die Toleranz hinaus
 * eingeschlossen. Die Erkennung lässt zwischen zwei Monaten {@value #TOLERANCE_PERCENT}&nbsp;%
 * Abweichung zu — genau die Klasse von Abos, für die diese Toleranz existiert (Handy mit
 * Verbrauchsanteil, Prämienanpassung), bucht deshalb regelmässig <em>nicht</em> rappengenau den
 * gelieferten Betrag ab. Wer einen gelieferten Betrag gegen eine Belastung vergleicht, muss
 * dieselbe Toleranz anwenden: {@link #withinTolerance(BigDecimal, BigDecimal)} ist diese eine
 * Regel, für die Erkennung wie für den Safe-to-Spend (Review PR #345).
 *
 * <p><strong>Ob ein Abo noch läuft, steht im Status.</strong> Die Erkennung setzt eine Zeile ohne
 * Abbuchung in den jüngsten Monaten der Historie auf {@code ENDED}; dieser Port liefert sie dann
 * nicht mehr. Bis BE-REC-04 prüfte er das selbst über ein Aktivitätsfenster
 * ({@code ACTIVE_WINDOW_MONTHS}), weil eine Zeile nie neu bewertet wurde — mit der Neubewertung
 * ist das Fenster entfallen, und damit auch der {@code month}-Parameter dieser Methode: er
 * beantwortete nur noch eine Frage, die niemand mehr stellt.
 */
public interface RecurringExpenseAmountPort {

    /** ±2 % — die Toleranz aus US-08, Basis ist der bekannte Betrag. */
    int TOLERANCE_PERCENT = 2;

    /** {@link #TOLERANCE_PERCENT} als Faktor: {@code 0.02}. */
    BigDecimal TOLERANCE = BigDecimal.valueOf(TOLERANCE_PERCENT).movePointLeft(2);

    /**
     * {@code |candidate − reference| ≤ reference × 2 %}. Der Referenzbetrag ist die Basis, weil er
     * der bekannte Vergleichswert ist — bei der Erkennung der frühere Monat, beim Safe-to-Spend der
     * gelieferte Abo-Betrag.
     *
     * <p>Ein nicht positiver Referenzbetrag qualifiziert nie: bei {@code 0.00} wäre das Band leer,
     * und ein Paar aus Nullbuchungen hiesse «Abo über CHF 0.00».
     */
    static boolean withinTolerance(BigDecimal reference, BigDecimal candidate) {
        if (reference.signum() <= 0) {
            return false;
        }
        BigDecimal maxAbweichung = reference.multiply(TOLERANCE);
        return candidate.subtract(reference).abs().compareTo(maxAbweichung) <= 0;
    }

    /**
     * Liefert die Beträge der laufenden Abos des Users — der Zeilen im Status
     * {@link RecurringExpenseStatus#DETECTED}.
     *
     * <p>Die beiden anderen Status fliessen nicht ein, aus verschiedenen Gründen:
     * {@code DISMISSED} ist die Aussage des Nutzers, dass dies kein Abo ist (US-08 AC3), und
     * {@code ENDED} die Feststellung der Erkennung, dass die Reihe ausgelaufen ist (BE-REC-04) —
     * ein gekündigtes Abo darf den Safe-to-Spend nicht weiter mindern.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return je laufendem Abo ein Betrag in CHF als {@link BigDecimal} mit Skala 2 (ADR-9),
     *     positiv; leere Liste, wenn keines läuft. Die Reihenfolge trägt keine Zusage — der
     *     Aufrufer summiert und vergleicht über Beträge, nicht über Positionen.
     */
    List<BigDecimal> detectedAmounts(long userId);
}
