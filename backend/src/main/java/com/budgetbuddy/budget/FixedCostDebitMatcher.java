package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.FixedCostResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Streicht die per Dauerauftrag bezahlten Fixkosten aus den Monatsbelastungen (BE-STS-04, ADR-13).
 *
 * <p><strong>Das Problem.</strong> Die Formel aus US-06 zieht die Fixkosten-Monatssumme ab
 * <em>und</em> die Ausgaben des laufenden Monats. Eine Miete, die per Dauerauftrag abgeht, steht
 * nach dem PDF-Import in beiden Summanden und mindert den Safe-to-Spend zweimal.
 *
 * <p><strong>Die Regel.</strong> Je Fixkosten-Position wird höchstens <em>eine</em> betragsgleiche
 * Belastung des Monats aus dem Summanden gestrichen; die Fixkosten-Seite bleibt unverändert. Die
 * Position mindert den Betrag damit genau einmal. Das Matching ist eine
 * <strong>Multiset-Schnittmenge</strong>: zwei Positionen zu je 59.00 streichen zwei Belastungen
 * über 59.00, eine einzelne Position niemals zwei.
 *
 * <p><strong>Verglichen wird gegen {@link FixedCostResponse#betrag()}, nicht gegen
 * {@link FixedCostResponse#monatsbetrag()}</strong> — gegen die tatsächliche Abbuchung also, nicht
 * gegen den normalisierten Monatsanteil. Nur so stimmt der Nicht-Monats-Fall: eine jährliche
 * Versicherung über 1'200 wird im Zahlungsmonat als 1'200 gestrichen, während auf der
 * Fixkosten-Seite in jedem der zwölf Monate 100 stehen. Über das Jahr ergibt das exakt 1'200. Bei
 * {@code monatlich} sind beide Werte ohnehin identisch.
 *
 * <p><strong>Warum der Betrag und nicht der Empfänger.</strong> Ein Dauerauftrag trägt in
 * {@code transactions.buchungstext} nur den Buchungs<em>typ</em> — bei Post-Auszügen etwa
 * {@code GIRO POST}. Der eigentliche Gegenpart steht in den Detailzeilen, die der Import heute
 * verwirft (#159, BE-PDF-07). Ein Textvergleich gegen die Bezeichnung der Fixkosten-Position hätte
 * also nichts, woran er greifen könnte. Sobald #159 den Empfänger persistiert, ist er das
 * trennschärfere Kriterium — der Upgrade-Pfad steht in ADR-13.
 *
 * <p><strong>Falsch-positiver Treffer.</strong> Eine echte Ausgabe, die zufällig den Betrag einer
 * Fixkosten-Position trifft, wird mitgestrichen; der Safe-to-Spend fällt dann um diesen Betrag zu
 * hoch aus. Der Fehler ist auf eine Position begrenzt und tritt nur bei rappengenauer Gleichheit
 * auf. Der heutige Zustand ohne Matching ist systematisch falsch — um die volle Fixkosten-Summe und
 * in jedem Monat. ADR-13 wägt das gegeneinander ab.
 *
 * <p>Sämtliche Beträge sind {@link BigDecimal} (ADR-9) — nie {@code double}/{@code float}.
 *
 * <p>Reine Rechenlogik ohne Zustand und ohne Repository: die Mandantentrennung liegt beim Aufrufer,
 * der beide Listen bereits user-gebunden geladen hat ({@link SafeToSpendService}).
 */
final class FixedCostDebitMatcher {

    /** Rappen — Zielskala aller Beträge nach aussen. */
    private static final int RAPPEN_SCALE = 2;

    private FixedCostDebitMatcher() {
        // Utility
    }

    /**
     * Summiert die Belastungen des Monats <em>ohne</em> die als Fixkosten-Zahlung erkannten.
     *
     * @param belastungen Beträge aller Belastungen des Monats, jeder Wert positiv. Die Reihenfolge
     *     ist unerheblich: gestrichen wird über Betragsgleichheit, nicht über Position.
     * @param fixkosten Fixkosten-Positionen des Users — verglichen wird
     *     {@link FixedCostResponse#betrag()}.
     * @return Summe der verbleibenden Belastungen in CHF, Skala 2; {@code 0.00}, wenn nichts übrig
     *     bleibt oder die Liste leer war. Nie negativ — es wird gestrichen, nicht subtrahiert.
     */
    static BigDecimal variableExpenses(
            List<BigDecimal> belastungen, List<FixedCostResponse> fixkosten) {
        BigDecimal summe = BigDecimal.ZERO.setScale(RAPPEN_SCALE);
        if (belastungen.isEmpty()) {
            return summe;
        }

        // Offene Streichungen als Multiset: Betrag → wie viele Belastungen dieser Höhe noch
        // gestrichen werden dürfen. Der Schlüssel ist der auf Rappen normalisierte Betrag —
        // BigDecimal.equals() unterscheidet sonst 1200 (Skala 0) von 1200.00 (Skala 2), und die
        // beiden Seiten kommen aus verschiedenen Schreibpfaden.
        Map<BigDecimal, Integer> offen = new HashMap<>();
        for (FixedCostResponse position : fixkosten) {
            offen.merge(rappen(position.betrag()), 1, Integer::sum);
        }

        for (BigDecimal belastung : belastungen) {
            BigDecimal schluessel = rappen(belastung);
            Integer verbleibend = offen.get(schluessel);
            if (verbleibend != null) {
                // Treffer: diese Belastung ist die Zahlung einer Fixkosten-Position und fällt aus
                // dem Summanden. Der Zähler sinkt, damit dieselbe Position nicht ein zweites Mal
                // streicht.
                if (verbleibend == 1) {
                    offen.remove(schluessel);
                } else {
                    offen.put(schluessel, verbleibend - 1);
                }
                continue;
            }
            summe = summe.add(schluessel);
        }
        return summe;
    }

    /**
     * Normalisiert einen Betrag auf Rappen — als Vergleichsschlüssel und als Summand.
     *
     * <p>Beide Seiten liefern heute bereits Skala 2 — {@code FixedCostService.toResponse(...)} und
     * {@link com.budgetbuddy.transaction.MonthlyExpensePort#expenseAmounts(long, java.time.YearMonth)}
     * sagen sie zu. Die Normalisierung hier verlässt sich nicht darauf: {@link BigDecimal#equals}
     * unterscheidet {@code 1200} (Skala 0) von {@code 1200.00} (Skala 2), und ein Vergleich, der an
     * der Skala einer anderen Klasse hängt, bricht lautlos, wenn dort etwas geändert wird. Ein
     * stiller Fehltreffer ist hier teurer als eine redundante Zeile.
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
