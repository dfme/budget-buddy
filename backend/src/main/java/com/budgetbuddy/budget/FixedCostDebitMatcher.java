package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.FixedCostResponse;
import com.budgetbuddy.money.ChfAmounts;
import com.budgetbuddy.recurring.RecurringExpenseAmountPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Streicht die per Dauerauftrag bezahlten Fixkosten und die Abbuchungen erkannter Abos aus den
 * Monatsbelastungen (BE-STS-04, ADR-13; Abos seit FE-FC-05, ADR-13-Nachtrag).
 *
 * <p><strong>Das Problem.</strong> Die Formel aus US-06 zieht die Fixkosten-Monatssumme ab
 * <em>und</em> die Ausgaben des laufenden Monats. Eine Miete, die per Dauerauftrag abgeht, steht
 * nach dem PDF-Import in beiden Summanden und mindert den Safe-to-Spend zweimal.
 *
 * <p><strong>Die Regel für Fixkosten-Positionen.</strong> Je Position wird höchstens <em>eine</em>
 * betragsgleiche Belastung des Monats aus dem Summanden gestrichen; die Fixkosten-Seite bleibt
 * unverändert. Die Position mindert den Betrag damit genau einmal. Das Matching ist eine
 * <strong>Multiset-Schnittmenge</strong>: zwei Positionen zu je 59.00 streichen zwei Belastungen
 * über 59.00, eine einzelne Position niemals zwei. Verglichen wird <em>rappengenau</em>: ein
 * Dauerauftrag geht immer gleich ab, und eine Toleranz vergrösserte nur das Falsch-Positiv-Risiko.
 *
 * <p><strong>Verglichen wird gegen {@link FixedCostResponse#betrag()}, nicht gegen
 * {@link FixedCostResponse#monatsbetrag()}</strong> — gegen die tatsächliche Abbuchung also, nicht
 * gegen den normalisierten Monatsanteil. Nur so stimmt der Nicht-Monats-Fall: eine jährliche
 * Versicherung über 1'200 wird im Zahlungsmonat als 1'200 gestrichen, während auf der
 * Fixkosten-Seite in jedem der zwölf Monate 100 stehen. Über das Jahr ergibt das exakt 1'200. Bei
 * {@code monatlich} sind beide Werte ohnehin identisch.
 *
 * <p><strong>Die Regel für erkannte Abos (FE-FC-05).</strong> Ein erkanntes, nicht verneintes Abo
 * (US-08) ist eine bekannte Verpflichtung wie eine Fixkosten-Position: es mindert den
 * Safe-to-Spend von Monatsbeginn an — nicht erst, wenn seine Abbuchung im Auszug steht —, und
 * seine Abbuchung fällt aus dem Ausgaben-Summanden. Zwei Dinge sind anders als bei den Positionen,
 * beide aus demselben Grund: der gelieferte Abo-Betrag stammt aus der Erkennung, die zwischen zwei
 * Monaten ±{@value RecurringExpenseAmountPort#TOLERANCE_PERCENT}&nbsp;% Abweichung zulässt — das
 * Abo bucht deshalb regelmässig nicht rappengenau diesen Betrag ab
 * ({@link RecurringExpenseAmountPort}).
 *
 * <ul>
 *   <li><strong>Gestrichen wird mit der Toleranz der Erkennung</strong>
 *       ({@link RecurringExpenseAmountPort#withinTolerance}), nicht rappengenau. Ein Handy-Abo,
 *       erkannt mit 59.00 und diesen Monat mit 59.90 abgebucht, ist genau die Klasse von Abos,
 *       für die die Toleranz existiert; rappengenau bliebe die Abbuchung als variable Ausgabe
 *       stehen und das Abo zählte doppelt (Review PR #345). Trifft mehr als eine Belastung ins
 *       Band, nimmt das Abo die nächstliegende.
 *   <li><strong>Auf der Fixkosten-Seite zählt der gestrichene Betrag</strong>, nicht der
 *       gelieferte: wurde 59.90 gestrichen, wird 59.90 abgezogen. Nur ohne Abbuchung im Monat —
 *       das Abo ist noch nicht fällig — zählt der gelieferte Betrag als Erwartung.
 * </ul>
 *
 * <p><strong>Ein Abo, das der User zusätzlich als Fixkosten-Position erfasst hat</strong> — Handy,
 * Krankenkasse, Streaming sind die typischen Fälle, die sowohl im Wizard eingetragen als auch aus
 * dem Auszug erkannt werden —, darf nicht zweimal zählen. Woran das System die Überschneidung
 * erkennt, ist dieselbe Frage wie in ADR-13, und die Antwort ist dieselbe: der Betrag, hier mit
 * derselben Toleranz, weil der Wizard-Betrag gerundet sein kann («Krankenkasse 350», real
 * 351.20). Je Position wird höchstens ein Abo als abgedeckt gewertet — die Position zählt, das
 * Abo nicht. Zwei Abos über 59.00 neben einer Position über 59.00 lassen eines zusätzlich stehen.
 *
 * <p><strong>Warum der Betrag und nicht der Empfänger.</strong> Ein Dauerauftrag trägt in
 * {@code transactions.buchungstext} nur den Buchungs<em>typ</em> — bei Post-Auszügen etwa
 * {@code GIRO POST}. Der eigentliche Gegenpart steht in den Detailzeilen, die der Import heute
 * verwirft (#159, BE-PDF-07). Ein Textvergleich gegen die Bezeichnung der Fixkosten-Position hätte
 * also nichts, woran er greifen könnte. Sobald #159 den Empfänger persistiert, ist er das
 * trennschärfere Kriterium — der Upgrade-Pfad steht in ADR-13. Für die Abos gilt dasselbe: eine
 * Fixkosten-Position hat keinen Empfänger, gegen den sich der {@code payee_key} vergleichen liesse.
 *
 * <p><strong>Falsch-positiver Treffer.</strong> Eine echte Ausgabe, die zufällig den Betrag einer
 * Fixkosten-Position trifft, wird mitgestrichen; der Safe-to-Spend fällt dann um diesen Betrag zu
 * hoch aus. Der Fehler ist auf eine Position begrenzt und tritt nur bei rappengenauer Gleichheit
 * auf. Der heutige Zustand ohne Matching ist systematisch falsch — um die volle Fixkosten-Summe und
 * in jedem Monat. ADR-13 wägt das gegeneinander ab. Bei den Abos ist das Band breiter (±2 %),
 * die Fehlerrichtung dieselbe: eine fremde Ausgabe im Band eines Abos wird als dessen Abbuchung
 * gewertet, und ein fremdes Abo im Band einer Position gilt als abgedeckt — beides macht den
 * Safe-to-Spend um den Betrag zu hoch. Die Gegenrichtung — Abbuchung ausserhalb des Bands, etwa
 * nach einem Preissprung — zählt doppelt, bis die Erkennung die Zeile neu bewertet (BE-REC-04).
 *
 * <p><strong>Deterministisch.</strong> Abos werden aufsteigend nach Betrag verarbeitet, Belastungen
 * aufsteigend durchsucht: welches Abo welche Belastung nimmt, hängt damit nicht an der
 * Zeilenreihenfolge zweier Queries, die keine Zusage tragen.
 *
 * <p>Sämtliche Beträge sind {@link BigDecimal} (ADR-9) — nie {@code double}/{@code float}.
 *
 * <p>Reine Rechenlogik ohne Zustand und ohne Repository: die Mandantentrennung liegt beim Aufrufer,
 * der alle Listen bereits user-gebunden geladen hat ({@link SafeToSpendService}).
 */
final class FixedCostDebitMatcher {

    /** Rappen — Zielskala aller Beträge nach aussen (ADR-9). */
    private static final int RAPPEN_SCALE = ChfAmounts.RAPPEN_SCALE;

    private FixedCostDebitMatcher() {
        // Utility
    }

    /**
     * Die beiden Summanden, die aus der Zuordnung hervorgehen — zusammen, weil sie aus
     * <em>einem</em> Durchgang stammen und nur zusammen stimmen: was hier als Abo abgezogen wird,
     * ist genau das, was dort aus den Belastungen gestrichen wurde.
     *
     * @param variableExpenses Summe der verbleibenden Belastungen in CHF, Skala 2; {@code 0.00},
     *     wenn nichts übrig bleibt. Nie negativ — es wird gestrichen, nicht subtrahiert.
     * @param recurringExpenses Summe der Abo-Verpflichtungen in CHF, Skala 2 — je nicht
     *     abgedecktem Abo der gestrichene Betrag, ohne Abbuchung der gelieferte; {@code 0.00}
     *     ohne Abos.
     */
    record Result(BigDecimal variableExpenses, BigDecimal recurringExpenses) {}

    /**
     * Ordnet die Belastungen des Monats den Fixkosten-Positionen und den erkannten Abos zu.
     *
     * @param belastungen Beträge aller Belastungen des Monats, jeder Wert positiv. Die Reihenfolge
     *     ist unerheblich.
     * @param fixkosten Fixkosten-Positionen des Users — verglichen wird
     *     {@link FixedCostResponse#betrag()}.
     * @param abos Beträge der erkannten, nicht verneinten und noch laufenden Abos, wie
     *     {@link RecurringExpenseAmountPort#detectedAmounts} sie liefert.
     * @return beide Summanden, siehe {@link Result}.
     */
    static Result match(
            List<BigDecimal> belastungen, List<FixedCostResponse> fixkosten, List<BigDecimal> abos) {
        List<BigDecimal> offen = new ArrayList<>(belastungen.stream().map(FixedCostDebitMatcher::rappen)
                .sorted().toList());
        List<BigDecimal> positionen = fixkosten.stream()
                .map(position -> rappen(position.betrag())).sorted().toList();

        strikeFixedCostDebits(offen, positionen);

        BigDecimal recurringExpenses = BigDecimal.ZERO.setScale(RAPPEN_SCALE);
        List<BigDecimal> unbelegtePositionen = new ArrayList<>(positionen);
        for (BigDecimal abo : abos.stream().map(FixedCostDebitMatcher::rappen).sorted().toList()) {
            // Bereits als Position erfasst? Dann zählt die Position (Monatssumme) und nicht das
            // Abo — und die Abbuchung hat die Position oben schon gestrichen, falls sie
            // rappengenau war.
            if (takeClosestWithinTolerance(unbelegtePositionen, abo) != null) {
                continue;
            }
            BigDecimal abbuchung = takeClosestWithinTolerance(offen, abo);
            recurringExpenses = recurringExpenses.add(abbuchung != null ? abbuchung : abo);
        }

        BigDecimal variableExpenses = BigDecimal.ZERO.setScale(RAPPEN_SCALE);
        for (BigDecimal belastung : offen) {
            variableExpenses = variableExpenses.add(belastung);
        }
        return new Result(variableExpenses, recurringExpenses);
    }

    /**
     * Streicht je Position höchstens eine rappengenau gleiche Belastung (ADR-13).
     *
     * <p>Offene Streichungen als Multiset: Betrag → wie viele Belastungen dieser Höhe noch
     * gestrichen werden dürfen. Der Schlüssel ist der auf Rappen normalisierte Betrag —
     * {@code BigDecimal.equals()} unterscheidet sonst 1200 (Skala 0) von 1200.00 (Skala 2), und
     * die Seiten kommen aus verschiedenen Schreibpfaden.
     */
    private static void strikeFixedCostDebits(List<BigDecimal> offen, List<BigDecimal> positionen) {
        Map<BigDecimal, Integer> streichungen = new HashMap<>();
        for (BigDecimal position : positionen) {
            streichungen.merge(position, 1, Integer::sum);
        }
        offen.removeIf(belastung -> {
            Integer verbleibend = streichungen.get(belastung);
            if (verbleibend == null) {
                return false;
            }
            // Treffer: diese Belastung ist die Zahlung einer Position und fällt aus dem
            // Summanden. Der Zähler sinkt, damit dieselbe Position nicht ein zweites Mal streicht.
            if (verbleibend == 1) {
                streichungen.remove(belastung);
            } else {
                streichungen.put(belastung, verbleibend - 1);
            }
            return true;
        });
    }

    /**
     * Entnimmt aus {@code kandidaten} den Betrag, der {@code referenz} am nächsten liegt und
     * innerhalb der Abo-Toleranz ist — oder {@code null}, wenn keiner im Band liegt. Bei
     * Gleichstand der Abstände gewinnt der kleinere Betrag, weil die Liste aufsteigend sortiert
     * ist und ein späterer Kandidat nur bei echt kleinerem Abstand übernimmt.
     */
    private static BigDecimal takeClosestWithinTolerance(List<BigDecimal> kandidaten, BigDecimal referenz) {
        int bester = -1;
        BigDecimal besterAbstand = null;
        for (int i = 0; i < kandidaten.size(); i++) {
            BigDecimal kandidat = kandidaten.get(i);
            if (!RecurringExpenseAmountPort.withinTolerance(referenz, kandidat)) {
                continue;
            }
            BigDecimal abstand = kandidat.subtract(referenz).abs();
            if (besterAbstand == null || abstand.compareTo(besterAbstand) < 0) {
                bester = i;
                besterAbstand = abstand;
            }
        }
        return bester < 0 ? null : kandidaten.remove(bester);
    }

    /**
     * Normalisiert einen Betrag auf Rappen — als Vergleichsschlüssel und als Summand.
     *
     * <p>Alle Seiten liefern heute bereits Skala 2 — {@code FixedCostService.toResponse(...)},
     * {@link com.budgetbuddy.transaction.MonthlyExpensePort#expenseAmounts(long, java.time.YearMonth)}
     * und {@link RecurringExpenseAmountPort#detectedAmounts(long)} sagen sie zu. Die
     * Normalisierung hier verlässt sich nicht darauf: {@link BigDecimal#equals} unterscheidet
     * {@code 1200} (Skala 0) von {@code 1200.00} (Skala 2), und ein Vergleich, der an der Skala
     * einer anderen Klasse hängt, bricht lautlos, wenn dort etwas geändert wird. Ein stiller
     * Fehltreffer ist hier teurer als eine redundante Zeile.
     *
     * <p>{@link RoundingMode#HALF_UP} und nicht {@link RoundingMode#UNNECESSARY}: dies ist ein
     * Lesepfad, der eine HTTP-Antwort trägt. Er soll bei einem unerwarteten Wert einen leicht
     * gerundeten Betrag liefern und nicht das Dashboard mit einer {@code ArithmeticException}
     * umbringen — dieselbe Abwägung wie beim Einkommen in {@code FixedCostService.list(...)}.
     */
    private static BigDecimal rappen(BigDecimal betrag) {
        return betrag.setScale(RAPPEN_SCALE, RoundingMode.HALF_UP);
    }
}
