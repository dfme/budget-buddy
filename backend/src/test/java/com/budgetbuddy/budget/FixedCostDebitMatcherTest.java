package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.budget.FixedCostDebitMatcher.Result;
import com.budgetbuddy.budget.dto.FixedCostResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit-Test des {@link FixedCostDebitMatcher} (BE-STS-04, ADR-13; Abos seit FE-FC-05).
 *
 * <p>Getestet wird die Streich-Regel isoliert — ohne Clock, Einkommen und Ports. Die Wirkung auf den
 * Safe-to-Spend selbst liegt im {@link SafeToSpendServiceTest}, der Weg über echte Daten im
 * {@link SafeToSpendServiceIntegrationTest}. Die Fälle ohne Abos übergeben eine leere Abo-Liste
 * und lesen nur {@link Result#variableExpenses()}: die Regel für Fixkosten-Positionen allein muss
 * unverändert gelten — rappengenau, während die Abos mit der Toleranz der Erkennung gestrichen
 * werden.
 *
 * <p>Die Grenzen sind hier das Interessante: dass eine Position <em>höchstens</em> eine Belastung
 * streicht und <em>mindestens</em> eine, sobald es eine betragsgleiche gibt. Ein Test, der nur den
 * geraden Fall «eine Position, eine passende Belastung» prüft, würde eine Implementierung
 * durchlassen, die alle betragsgleichen Belastungen streicht.
 */
class FixedCostDebitMatcherTest {

    @Nested
    @DisplayName("Streicht die Zahlung einer Fixkosten-Position")
    class Streicht {

        @Test
        void dieBetragsgleicheBelastung() {
            // Miete 1'200 als Position + Dauerauftrag über 1'200 → die Belastung fällt weg.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("1200.00", "45.60"), List.of(fixkosten("1200.00", "monatlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("45.60");
        }

        @Test
        void jeGleichHoherPositionGenauEine() {
            // Zwei Abos zu je 59.00 und drei Belastungen über 59.00: zwei werden gestrichen,
            // die dritte ist eine echte variable Ausgabe.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("59.00", "59.00", "59.00"),
                    List.of(fixkosten("59.00", "monatlich"), fixkosten("59.00", "monatlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("59.00");
        }

        @Test
        void auchBeiUnterschiedlicherSkalaAufBeidenSeiten() {
            // fixed_costs.betrag und transactions.betrag kommen aus verschiedenen Schreibpfaden.
            // BigDecimal.equals() unterschiede 1200 (Skala 0) von 1200.00 (Skala 2) — der Matcher
            // vergleicht deshalb auf Rappen normalisiert.
            BigDecimal result = FixedCostDebitMatcher.match(
                    List.of(new BigDecimal("1200")), List.of(fixkosten("1200.00", "monatlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("0.00");
        }

        @Test
        void denErfasstenBetragUndNichtDenMonatsbetrag() {
            // Versicherung 1'200 jährlich → monatsbetrag 100.00, Abbuchung aber 1'200 im März.
            // Gestrichen wird die Abbuchung; die 100.00 stehen unabhängig davon auf der
            // Fixkosten-Seite und werden hier nicht berührt.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("1200.00", "80.00"), List.of(fixkosten("1200.00", "jaehrlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("80.00");
        }
    }

    @Nested
    @DisplayName("Streicht nicht")
    class StreichtNicht {

        @Test
        void wennKeineBelastungDenBetragTrifft() {
            // Die Position wurde in diesem Monat nicht abgebucht — nichts fällt weg, und sie
            // zählt weiterhin genau einmal über die Fixkosten-Seite.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("45.60", "12.40"), List.of(fixkosten("1200.00", "monatlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("58.00");
        }

        @Test
        void dieselbePositionEinZweitesMal() {
            // Nachzahlung: dieselbe Miete geht im selben Monat zweimal ab. Die zweite Abbuchung
            // ist eine zusätzliche Belastung des Kontos und bleibt eine variable Ausgabe.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("1200.00", "1200.00"), List.of(fixkosten("1200.00", "monatlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("1200.00");
        }

        @Test
        void beiRappenAbweichung() {
            // 1'200.05 ist nicht 1'200.00. Exakte Gleichheit ist die Regel — eine Toleranz würde
            // das Falsch-Positiv-Risiko aus ADR-13 vergrössern, ohne einen Dauerauftrag besser zu
            // treffen: der geht rappengenau immer gleich ab.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("1200.05"), List.of(fixkosten("1200.00", "monatlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("1200.05");
        }
    }

    @Nested
    @DisplayName("Randfälle")
    class Randfaelle {

        @Test
        void ohneBelastungenNullKomma() {
            BigDecimal result = FixedCostDebitMatcher.match(
                    List.of(), List.of(fixkosten("1200.00", "monatlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("0.00");
            assertThat(result.scale()).isEqualTo(2);
        }

        @Test
        void ohneFixkostenBleibtDieVolleSumme() {
            // Der Zustand eines Users, der den Wizard noch nicht ausgefüllt hat: die Regel darf
            // dann nichts verändern.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("45.60", "12.40", "1200.00"), List.of(), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("1258.00");
        }

        @Test
        void mehrPositionenAlsBelastungen() {
            // Vier erfasste Positionen, eine einzige passende Belastung — es darf nichts
            // Negatives entstehen, gestrichen wird und nicht subtrahiert.
            BigDecimal result = FixedCostDebitMatcher.match(
                    betraege("1200.00"),
                    List.of(
                            fixkosten("1200.00", "monatlich"),
                            fixkosten("350.00", "monatlich"),
                            fixkosten("59.00", "monatlich"),
                            fixkosten("1200.00", "jaehrlich")), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("0.00");
            assertThat(result.signum()).isNotNegative();
        }

        @Test
        void lieferstImmerSkalaZwei() {
            // Die Zusage «Skala 2 nach aussen» hängt nicht daran, was die Eingabe mitbringt.
            BigDecimal result = FixedCostDebitMatcher.match(
                    List.of(new BigDecimal("10"), new BigDecimal("0.5")), List.of(), List.of()).variableExpenses();

            assertThat(result).isEqualByComparingTo("10.50");
            assertThat(result.scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Erkannte Abos (FE-FC-05)")
    class Abos {

        @Test
        void einAboOhnePositionZaehltAufDerFixkostenSeite() {
            // Netflix 17.90 erkannt, keine Fixkosten-Position dazu, diesen Monat nicht abgebucht:
            // das Abo zählt als Erwartung, die Belastungen bleiben unberührt.
            Result result = FixedCostDebitMatcher.match(
                    betraege("45.60"), List.of(fixkosten("1200.00", "monatlich")), betraege("17.90"));

            assertThat(result.recurringExpenses()).isEqualByComparingTo("17.90");
            assertThat(result.variableExpenses()).isEqualByComparingTo("45.60");
        }

        @Test
        void streichtDieAbbuchungEinesAbosUndZaehltSieEinmal() {
            // Netflix 17.90 ist im Auszug abgegangen: die Belastung fällt weg, das Abo zählt in
            // Höhe der Abbuchung — genau einmal, die 45.60 bleiben variable Ausgabe.
            Result result = FixedCostDebitMatcher.match(
                    betraege("17.90", "45.60"), List.of(), betraege("17.90"));

            assertThat(result.variableExpenses()).isEqualByComparingTo("45.60");
            assertThat(result.recurringExpenses()).isEqualByComparingTo("17.90");
        }

        @Test
        void streichtMitDerToleranzDerErkennungUndZaehltDenGestrichenenBetrag() {
            // Review PR #345: Abo SALT erkannt mit 59.00, Rechnung dieses Monats 59.90 — innerhalb
            // der ±2 %, deshalb überhaupt als Abo erkannt. Rappengenau bliebe 59.90 als variable
            // Ausgabe stehen und 59.00 zählte dazu: 118.90 statt 59.90. Gestrichen wird im Band,
            // gezählt der gestrichene Betrag.
            Result result = FixedCostDebitMatcher.match(
                    betraege("59.90", "45.60"), List.of(), betraege("59.00"));

            assertThat(result.variableExpenses()).isEqualByComparingTo("45.60");
            assertThat(result.recurringExpenses()).isEqualByComparingTo("59.90");
            assertThat(result.variableExpenses().add(result.recurringExpenses()))
                    .as("die Abbuchung zählt genau einmal")
                    .isEqualByComparingTo("105.50");
        }

        @Test
        void streichtNichtAusserhalbDesBands() {
            // 61.00 liegt mehr als 2 % über 59.00 (Band bis 60.18): kein Abo-Treffer. Das Abo
            // zählt als Erwartung, die 61.00 bleiben variable Ausgabe — die bekannte Grenze aus
            // dem ADR-13-Nachtrag, bis die Erkennung die Zeile neu bewertet.
            Result result = FixedCostDebitMatcher.match(
                    betraege("61.00"), List.of(), betraege("59.00"));

            assertThat(result.variableExpenses()).isEqualByComparingTo("61.00");
            assertThat(result.recurringExpenses()).isEqualByComparingTo("59.00");
        }

        @Test
        void nimmtBeiMehrerenBelastungenImBandDieNaechstliegende() {
            // 58.50 und 59.10 liegen beide im Band von 59.00; genommen wird 59.10 (Abstand 0.10),
            // 58.50 bleibt variable Ausgabe.
            Result result = FixedCostDebitMatcher.match(
                    betraege("58.50", "59.10"), List.of(), betraege("59.00"));

            assertThat(result.recurringExpenses()).isEqualByComparingTo("59.10");
            assertThat(result.variableExpenses()).isEqualByComparingTo("58.50");
        }

        @Test
        void streichtJeAboHoechstensEineBelastung() {
            // Zwei Belastungen über 17.90, ein Abo: die zweite ist eine echte zusätzliche Ausgabe.
            Result result = FixedCostDebitMatcher.match(
                    betraege("17.90", "17.90"), List.of(), betraege("17.90"));

            assertThat(result.variableExpenses()).isEqualByComparingTo("17.90");
            assertThat(result.recurringExpenses()).isEqualByComparingTo("17.90");
        }

        @Test
        void einBetragsgleichesAboGiltAlsBereitsErfasst() {
            // Handy 59.00 als Position erfasst UND SWISSCOM 59.00 erkannt, dazu die Abbuchung:
            // die Position zählt (über die Monatssumme) und streicht die Abbuchung; das Abo
            // zählt nicht — sonst stünde dieselbe Verpflichtung zweimal auf der Fixkosten-Seite.
            Result result = FixedCostDebitMatcher.match(
                    betraege("59.00", "300.00"),
                    List.of(fixkosten("59.00", "monatlich")),
                    betraege("59.00"));

            assertThat(result.recurringExpenses()).isEqualByComparingTo("0.00");
            assertThat(result.variableExpenses()).isEqualByComparingTo("300.00");
        }

        @Test
        void einAboImToleranzbandEinerPositionGiltAlsBereitsErfasst() {
            // Krankenkasse im Wizard gerundet mit 350.00 erfasst, erkannt mit 351.20 (Review PR
            // #345, Punkt 1): innerhalb der 2 % — dieselbe Verpflichtung, das Abo zählt nicht.
            // Die Abbuchung 351.20 streicht die Position nicht (rappengenau, ADR-13) — sie bleibt
            // im Abbuchungsmonat variable Ausgabe, wie schon vor FE-FC-05.
            Result result = FixedCostDebitMatcher.match(
                    betraege("351.20"), List.of(fixkosten("350.00", "monatlich")), betraege("351.20"));

            assertThat(result.recurringExpenses()).isEqualByComparingTo("0.00");
            assertThat(result.variableExpenses()).isEqualByComparingTo("351.20");
        }

        @Test
        void jePositionDecktGenauEinAboAb() {
            // Multiset: eine Position über 59.00, zwei Abos über 59.00 — eines bleibt zusätzlich.
            Result result = FixedCostDebitMatcher.match(
                    betraege("59.00", "59.00", "59.00"),
                    List.of(fixkosten("59.00", "monatlich")),
                    betraege("59.00", "59.00"));

            // Position streicht eine 59.00, das unabgedeckte Abo eine zweite; die dritte bleibt.
            assertThat(result.recurringExpenses()).isEqualByComparingTo("59.00");
            assertThat(result.variableExpenses()).isEqualByComparingTo("59.00");
        }

        @Test
        void verglichenWirdGegenDenErfasstenBetragNichtDenMonatsbetrag() {
            // Versicherung 1'200 jährlich → monatsbetrag 100.00. Ein Abo über 100.00 ist damit
            // NICHT abgedeckt: die Abbuchung der Position ist 1'200, nicht 100 — dieselbe Regel
            // wie beim Streichen (ADR-13, Festlegung 2).
            Result result = FixedCostDebitMatcher.match(
                    List.of(), List.of(fixkosten("1200.00", "jaehrlich")), betraege("100.00"));

            assertThat(result.recurringExpenses()).isEqualByComparingTo("100.00");
        }

        @Test
        void unterschiedlicheSkalaAufBeidenSeitenDecktTrotzdemAb() {
            // recurring_expenses.amount und fixed_costs.betrag kommen aus verschiedenen
            // Schreibpfaden — 59 (Skala 0) und 59.00 (Skala 2) sind derselbe Betrag.
            Result result = FixedCostDebitMatcher.match(
                    List.of(), List.of(fixkosten("59.00", "monatlich")), List.of(new BigDecimal("59")));

            assertThat(result.recurringExpenses()).isEqualByComparingTo("0.00");
        }

        @Test
        void dasErgebnisHaengtNichtAnDerReihenfolgeDerEingaben() {
            // Zwei Abos mit überlappenden Bändern (59.00 → [57.82, 60.18]; 60.00 → [58.80, 61.20])
            // und zwei Belastungen darin: egal, in welcher Reihenfolge Port und Query liefern,
            // die Summen sind dieselben.
            Result vorwaerts = FixedCostDebitMatcher.match(
                    betraege("59.50", "60.50"), List.of(), betraege("59.00", "60.00"));
            Result rueckwaerts = FixedCostDebitMatcher.match(
                    betraege("60.50", "59.50"), List.of(), betraege("60.00", "59.00"));

            assertThat(vorwaerts).isEqualTo(rueckwaerts);
            assertThat(vorwaerts.variableExpenses()).isEqualByComparingTo("0.00");
            assertThat(vorwaerts.recurringExpenses()).isEqualByComparingTo("120.00");
        }

        @Test
        void liefertBeideSummandenMitSkalaZwei() {
            Result result = FixedCostDebitMatcher.match(
                    List.of(new BigDecimal("10")), List.of(), List.of(new BigDecimal("5")));

            assertThat(result.variableExpenses().scale()).isEqualTo(2);
            assertThat(result.recurringExpenses().scale()).isEqualTo(2);
            assertThat(result.recurringExpenses()).isEqualByComparingTo("5.00");
        }
    }

    /** Fixkosten-Position, wie {@code FixedCostService.list(...)} sie liefert. */
    private static FixedCostResponse fixkosten(String betrag, String intervall) {
        Intervall enumWert = Intervall.fromLabel(intervall);
        BigDecimal wert = new BigDecimal(betrag);
        BigDecimal monatsbetrag = switch (enumWert) {
            case MONATLICH -> wert;
            case QUARTALSWEISE -> wert.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            case JAEHRLICH -> wert.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        };
        return new FixedCostResponse(1L, "Position", wert, intervall, monatsbetrag);
    }

    private static List<BigDecimal> betraege(String... werte) {
        return Arrays.stream(werte).map(BigDecimal::new).toList();
    }
}
