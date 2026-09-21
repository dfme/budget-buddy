package com.budgetbuddy.recurring;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Lese-Port auf die Beträge der erkannten, nicht verneinten und noch aktiven Abos eines Users —
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
 * <p><strong>Die Beträge sind Momentaufnahmen mit Toleranz.</strong> {@code recurring_expenses.amount}
 * ist der Betrag des jüngsten qualifizierenden Monatspaars zum Zeitpunkt der Erkennung und wird
 * danach nicht aktualisiert (BE-REC-01: bekannte Empfänger werden übersprungen). Die Erkennung
 * lässt zwischen zwei Monaten {@value #TOLERANCE_PERCENT}&nbsp;% Abweichung zu — genau die Klasse
 * von Abos, für die diese Toleranz existiert (Handy mit Verbrauchsanteil, Prämienanpassung),
 * bucht deshalb regelmässig <em>nicht</em> rappengenau den gelieferten Betrag ab. Wer einen
 * gelieferten Betrag gegen eine Belastung vergleicht, muss dieselbe Toleranz anwenden:
 * {@link #withinTolerance(BigDecimal, BigDecimal)} ist diese eine Regel, für die Erkennung wie
 * für den Safe-to-Spend (Review PR #345).
 */
public interface RecurringExpenseAmountPort {

    /** ±2 % — die Toleranz aus US-08, Basis ist der bekannte Betrag. */
    int TOLERANCE_PERCENT = 2;

    /** {@link #TOLERANCE_PERCENT} als Faktor: {@code 0.02}. */
    BigDecimal TOLERANCE = BigDecimal.valueOf(TOLERANCE_PERCENT).movePointLeft(2);

    /**
     * Wie viele Monate ein Abo ohne Abbuchung bleiben darf, bevor es aus
     * {@link #detectedAmounts} fällt — den angefragten Monat eingeschlossen. Drei, nicht zwei:
     * Auszüge kommen rückdatiert, der Auszug des Vormonats liegt in den ersten Tagen des Monats
     * oft noch nicht vor, und ein Abo, das im Vor-Vormonat zuletzt sichtbar war, ist dann noch
     * kein Indiz für eine Kündigung.
     */
    int ACTIVE_WINDOW_MONTHS = 3;

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
     * Liefert die Beträge der Abos des Users im Status {@link RecurringExpenseStatus#DETECTED},
     * die in {@code month} oder den {@value #ACTIVE_WINDOW_MONTHS}&nbsp;−&nbsp;1 Monaten davor
     * mindestens einmal abgebucht wurden.
     *
     * <p>Per «Kein Abo» verneinte Einträge ({@code DISMISSED}) fliessen nicht ein — sie sind kein
     * Abo und dürfen den Safe-to-Spend nicht mindern. Ebenso wenig ein Abo ohne Abbuchung im
     * Fenster: die Zeile verfällt nie von selbst (Review PR #345), ein gekündigtes Abo bliebe
     * sonst dauerhaft ein Abzug. Das Fenster ist die Grenze, ab der die Zeile als beendet gilt;
     * der Nachlauf von bis zu zwei Monaten ist im ADR-13-Nachtrag festgehalten.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param month der Monat, für den der Safe-to-Spend gerechnet wird — das obere Ende des
     *     Fensters.
     * @return je aktivem Abo ein Betrag in CHF als {@link BigDecimal} mit Skala 2 (ADR-9),
     *     positiv; leere Liste, wenn keines erkannt oder keines im Fenster abgebucht ist. Die
     *     Reihenfolge trägt keine Zusage — der Aufrufer summiert und vergleicht über Beträge,
     *     nicht über Positionen.
     */
    List<BigDecimal> detectedAmounts(long userId, YearMonth month);
}
