package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.FixedCostResponse;
import com.budgetbuddy.money.ChfAmounts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 * <p><strong>Erkannte Abos (FE-FC-05).</strong> Ein erkanntes, nicht verneintes Abo (US-08) ist
 * eine bekannte Verpflichtung wie eine Fixkosten-Position: es mindert den Safe-to-Spend von
 * Monatsbeginn an — nicht erst, wenn seine Abbuchung im Auszug steht — und seine Abbuchung fällt
 * mit derselben Regel aus dem Ausgaben-Summanden. Die Abos gehen deshalb in dasselbe Multiset wie
 * die Fixkosten-Positionen, je Abo höchstens eine Belastung.
 *
 * <p>Ein Abo, das der User <em>zusätzlich</em> als Fixkosten-Position erfasst hat — Handy, Krankenkasse,
 * Streaming sind die typischen Fälle, die sowohl im Wizard eingetragen als auch aus dem Auszug
 * erkannt werden —, darf nicht zweimal zählen. Woran das System die Überschneidung erkennt, ist
 * dieselbe Frage wie in ADR-13, und die Antwort ist dieselbe: der Betrag. {@link
 * #uncoveredRecurringExpenses} bildet die Multiset-Differenz der Abo-Beträge gegen die
 * Fixkosten-Beträge; nur der Rest wirkt zusätzlich. Ein Abo über 59.00 neben einer Position über
 * 59.00 ist damit «bereits erfasst» — die Position zählt, das Abo nicht. Zwei Abos über 59.00 neben
 * einer Position über 59.00 lassen eines zusätzlich stehen.
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
 * in jedem Monat. ADR-13 wägt das gegeneinander ab. Die Abo-Deduplizierung hat dieselbe
 * Fehlerrichtung: ein Abo, das nur zufällig den Betrag einer fremden Fixkosten-Position trifft,
 * zählt nicht — der Safe-to-Spend ist um diesen Betrag zu hoch.
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
     * Die erkannten Abos, die <em>nicht</em> bereits als Fixkosten-Position erfasst sind
     * (FE-FC-05) — Multiset-Differenz der Abo-Beträge gegen {@link FixedCostResponse#betrag()}.
     *
     * <p>Das Ergebnis ist die Liste, die auf der Fixkosten-Seite zusätzlich zur Monatssumme
     * abgezogen wird <em>und</em> die {@link #variableExpenses} als {@code abos} bekommt. Beide
     * Stellen müssen dieselbe Menge sehen — sonst würde ein abgedecktes Abo zwar nicht abgezogen,
     * seine Abbuchung aber trotzdem ein zweites Mal gestrichen.
     *
     * @param abos Beträge der erkannten, nicht verneinten Abos des Users.
     * @param fixkosten Fixkosten-Positionen des Users — verglichen wird
     *     {@link FixedCostResponse#betrag()}.
     * @return die Beträge der Abos ohne betragsgleiche Position, auf Rappen normalisiert; leer,
     *     wenn es keine Abos gibt oder jedes von einer Position abgedeckt ist. Die Reihenfolge
     *     ist die der Eingabe.
     */
    static List<BigDecimal> uncoveredRecurringExpenses(
            List<BigDecimal> abos, List<FixedCostResponse> fixkosten) {
        Map<BigDecimal, Integer> erfasst = new HashMap<>();
        for (FixedCostResponse position : fixkosten) {
            erfasst.merge(rappen(position.betrag()), 1, Integer::sum);
        }

        List<BigDecimal> uncovered = new ArrayList<>();
        for (BigDecimal abo : abos) {
            BigDecimal schluessel = rappen(abo);
            if (!consume(erfasst, schluessel)) {
                uncovered.add(schluessel);
            }
        }
        return List.copyOf(uncovered);
    }

    /**
     * Summiert die Belastungen des Monats <em>ohne</em> die als Fixkosten- oder Abo-Zahlung
     * erkannten.
     *
     * @param belastungen Beträge aller Belastungen des Monats, jeder Wert positiv. Die Reihenfolge
     *     ist unerheblich: gestrichen wird über Betragsgleichheit, nicht über Position.
     * @param fixkosten Fixkosten-Positionen des Users — verglichen wird
     *     {@link FixedCostResponse#betrag()}.
     * @param abos Beträge der erkannten Abos, die nicht bereits als Position erfasst sind — das
     *     Ergebnis von {@link #uncoveredRecurringExpenses}, nie die rohe Liste vom Port. Je Abo
     *     wird höchstens eine betragsgleiche Belastung gestrichen.
     * @return Summe der verbleibenden Belastungen in CHF, Skala 2; {@code 0.00}, wenn nichts übrig
     *     bleibt oder die Liste leer war. Nie negativ — es wird gestrichen, nicht subtrahiert.
     */
    static BigDecimal variableExpenses(
            List<BigDecimal> belastungen, List<FixedCostResponse> fixkosten, List<BigDecimal> abos) {
        BigDecimal summe = BigDecimal.ZERO.setScale(RAPPEN_SCALE);
        if (belastungen.isEmpty()) {
            return summe;
        }

        // Offene Streichungen als Multiset: Betrag → wie viele Belastungen dieser Höhe noch
        // gestrichen werden dürfen. Der Schlüssel ist der auf Rappen normalisierte Betrag —
        // BigDecimal.equals() unterscheidet sonst 1200 (Skala 0) von 1200.00 (Skala 2), und die
        // Seiten kommen aus verschiedenen Schreibpfaden. Fixkosten und Abos landen im selben
        // Multiset: für die Streichung ist gleichgültig, woher die Verpflichtung stammt.
        Map<BigDecimal, Integer> offen = new HashMap<>();
        for (FixedCostResponse position : fixkosten) {
            offen.merge(rappen(position.betrag()), 1, Integer::sum);
        }
        for (BigDecimal abo : abos) {
            offen.merge(rappen(abo), 1, Integer::sum);
        }

        for (BigDecimal belastung : belastungen) {
            BigDecimal schluessel = rappen(belastung);
            if (consume(offen, schluessel)) {
                // Treffer: diese Belastung ist die Zahlung einer Position und fällt aus dem
                // Summanden. Der Zähler ist gesunken, damit dieselbe Position nicht ein zweites
                // Mal streicht.
                continue;
            }
            summe = summe.add(schluessel);
        }
        return summe;
    }

    /**
     * Verbraucht einen Eintrag des Multisets, falls einer für {@code schluessel} offen ist.
     *
     * @return {@code true}, wenn ein Eintrag verbraucht wurde; {@code false}, wenn für diesen
     *     Betrag nichts (mehr) offen war.
     */
    private static boolean consume(Map<BigDecimal, Integer> multiset, BigDecimal schluessel) {
        Integer verbleibend = multiset.get(schluessel);
        if (verbleibend == null) {
            return false;
        }
        if (verbleibend == 1) {
            multiset.remove(schluessel);
        } else {
            multiset.put(schluessel, verbleibend - 1);
        }
        return true;
    }

    /**
     * Normalisiert einen Betrag auf Rappen — als Vergleichsschlüssel und als Summand.
     *
     * <p>Alle Seiten liefern heute bereits Skala 2 — {@code FixedCostService.toResponse(...)},
     * {@link com.budgetbuddy.transaction.MonthlyExpensePort#expenseAmounts(long, java.time.YearMonth)}
     * und {@link com.budgetbuddy.recurring.RecurringExpenseAmountPort#detectedAmounts(long)} sagen
     * sie zu. Die Normalisierung hier verlässt sich nicht darauf: {@link BigDecimal#equals}
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
