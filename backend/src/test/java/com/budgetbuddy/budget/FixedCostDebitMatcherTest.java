package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;

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
 * {@link SafeToSpendServiceIntegrationTest}. Die Fälle ohne Abos übergeben eine leere Abo-Liste:
 * die Regel für Fixkosten-Positionen allein muss unverändert gelten.
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
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("1200.00", "45.60"), List.of(fixkosten("1200.00", "monatlich")), List.of());

            assertThat(result).isEqualByComparingTo("45.60");
        }

        @Test
        void jeGleichHoherPositionGenauEine() {
            // Zwei Abos zu je 59.00 und drei Belastungen über 59.00: zwei werden gestrichen,
            // die dritte ist eine echte variable Ausgabe.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("59.00", "59.00", "59.00"),
                    List.of(fixkosten("59.00", "monatlich"), fixkosten("59.00", "monatlich")), List.of());

            assertThat(result).isEqualByComparingTo("59.00");
        }

        @Test
        void auchBeiUnterschiedlicherSkalaAufBeidenSeiten() {
            // fixed_costs.betrag und transactions.betrag kommen aus verschiedenen Schreibpfaden.
            // BigDecimal.equals() unterschiede 1200 (Skala 0) von 1200.00 (Skala 2) — der Matcher
            // vergleicht deshalb auf Rappen normalisiert.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    List.of(new BigDecimal("1200")), List.of(fixkosten("1200.00", "monatlich")), List.of());

            assertThat(result).isEqualByComparingTo("0.00");
        }

        @Test
        void denErfasstenBetragUndNichtDenMonatsbetrag() {
            // Versicherung 1'200 jährlich → monatsbetrag 100.00, Abbuchung aber 1'200 im März.
            // Gestrichen wird die Abbuchung; die 100.00 stehen unabhängig davon auf der
            // Fixkosten-Seite und werden hier nicht berührt.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("1200.00", "80.00"), List.of(fixkosten("1200.00", "jaehrlich")), List.of());

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
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("45.60", "12.40"), List.of(fixkosten("1200.00", "monatlich")), List.of());

            assertThat(result).isEqualByComparingTo("58.00");
        }

        @Test
        void dieselbePositionEinZweitesMal() {
            // Nachzahlung: dieselbe Miete geht im selben Monat zweimal ab. Die zweite Abbuchung
            // ist eine zusätzliche Belastung des Kontos und bleibt eine variable Ausgabe.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("1200.00", "1200.00"), List.of(fixkosten("1200.00", "monatlich")), List.of());

            assertThat(result).isEqualByComparingTo("1200.00");
        }

        @Test
        void beiRappenAbweichung() {
            // 1'200.05 ist nicht 1'200.00. Exakte Gleichheit ist die Regel — eine Toleranz würde
            // das Falsch-Positiv-Risiko aus ADR-13 vergrössern, ohne einen Dauerauftrag besser zu
            // treffen: der geht rappengenau immer gleich ab.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("1200.05"), List.of(fixkosten("1200.00", "monatlich")), List.of());

            assertThat(result).isEqualByComparingTo("1200.05");
        }
    }

    @Nested
    @DisplayName("Randfälle")
    class Randfaelle {

        @Test
        void ohneBelastungenNullKomma() {
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    List.of(), List.of(fixkosten("1200.00", "monatlich")), List.of());

            assertThat(result).isEqualByComparingTo("0.00");
            assertThat(result.scale()).isEqualTo(2);
        }

        @Test
        void ohneFixkostenBleibtDieVolleSumme() {
            // Der Zustand eines Users, der den Wizard noch nicht ausgefüllt hat: die Regel darf
            // dann nichts verändern.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("45.60", "12.40", "1200.00"), List.of(), List.of());

            assertThat(result).isEqualByComparingTo("1258.00");
        }

        @Test
        void mehrPositionenAlsBelastungen() {
            // Vier erfasste Positionen, eine einzige passende Belastung — es darf nichts
            // Negatives entstehen, gestrichen wird und nicht subtrahiert.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("1200.00"),
                    List.of(
                            fixkosten("1200.00", "monatlich"),
                            fixkosten("350.00", "monatlich"),
                            fixkosten("59.00", "monatlich"),
                            fixkosten("1200.00", "jaehrlich")), List.of());

            assertThat(result).isEqualByComparingTo("0.00");
            assertThat(result.signum()).isNotNegative();
        }

        @Test
        void lieferstImmerSkalaZwei() {
            // Die Zusage «Skala 2 nach aussen» hängt nicht daran, was die Eingabe mitbringt.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    List.of(new BigDecimal("10"), new BigDecimal("0.5")), List.of(), List.of());

            assertThat(result).isEqualByComparingTo("10.50");
            assertThat(result.scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Erkannte Abos (FE-FC-05)")
    class Abos {

        @Test
        void einAboOhnePositionBleibtUnabgedeckt() {
            // Netflix 17.90 erkannt, keine Fixkosten-Position dazu — das Abo zählt zusätzlich.
            List<BigDecimal> result = FixedCostDebitMatcher.uncoveredRecurringExpenses(
                    betraege("17.90"), List.of(fixkosten("1200.00", "monatlich")));

            assertThat(result).containsExactly(new BigDecimal("17.90"));
        }

        @Test
        void einBetragsgleichesAboGiltAlsBereitsErfasst() {
            // Handy 59.00 als Position erfasst UND SWISSCOM 59.00 erkannt: dieselbe Verpflichtung.
            // Die Position zählt, das Abo nicht — sonst stünde sie zweimal auf der Fixkosten-Seite.
            List<BigDecimal> result = FixedCostDebitMatcher.uncoveredRecurringExpenses(
                    betraege("59.00"), List.of(fixkosten("59.00", "monatlich")));

            assertThat(result).isEmpty();
        }

        @Test
        void jePositionDecktGenauEinAboAb() {
            // Multiset: eine Position über 59.00, zwei Abos über 59.00 — eines bleibt zusätzlich.
            List<BigDecimal> result = FixedCostDebitMatcher.uncoveredRecurringExpenses(
                    betraege("59.00", "59.00"), List.of(fixkosten("59.00", "monatlich")));

            assertThat(result).containsExactly(new BigDecimal("59.00"));
        }

        @Test
        void verglichenWirdGegenDenErfasstenBetragNichtDenMonatsbetrag() {
            // Versicherung 1'200 jährlich → monatsbetrag 100.00. Ein Abo über 100.00 ist damit
            // NICHT abgedeckt: die Abbuchung der Position ist 1'200, nicht 100 — dieselbe Regel
            // wie beim Streichen (ADR-13, Festlegung 2).
            List<BigDecimal> result = FixedCostDebitMatcher.uncoveredRecurringExpenses(
                    betraege("100.00"), List.of(fixkosten("1200.00", "jaehrlich")));

            assertThat(result).containsExactly(new BigDecimal("100.00"));
        }

        @Test
        void unterschiedlicheSkalaAufBeidenSeitenDecktTrotzdemAb() {
            // recurring_expenses.amount und fixed_costs.betrag kommen aus verschiedenen
            // Schreibpfaden — 59 (Skala 0) und 59.00 (Skala 2) sind derselbe Betrag.
            List<BigDecimal> result = FixedCostDebitMatcher.uncoveredRecurringExpenses(
                    List.of(new BigDecimal("59")), List.of(fixkosten("59.00", "monatlich")));

            assertThat(result).isEmpty();
        }

        @Test
        void ohneFixkostenBleibenAlleAbosUnabgedecktUndNormalisiert() {
            List<BigDecimal> result = FixedCostDebitMatcher.uncoveredRecurringExpenses(
                    List.of(new BigDecimal("17.9"), new BigDecimal("13")), List.of());

            assertThat(result).containsExactly(new BigDecimal("17.90"), new BigDecimal("13.00"));
            assertThat(result).allSatisfy(betrag -> assertThat(betrag.scale()).isEqualTo(2));
        }

        @Test
        void streichtDieAbbuchungEinesAbosAusDenBelastungen() {
            // Netflix 17.90 ist im Auszug abgegangen: die Belastung fällt weg wie bei einer
            // Fixkosten-Position, die 45.60 bleiben variable Ausgabe.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("17.90", "45.60"), List.of(), betraege("17.90"));

            assertThat(result).isEqualByComparingTo("45.60");
        }

        @Test
        void streichtJeAboHoechstensEineBelastung() {
            // Zwei Belastungen über 17.90, ein Abo: die zweite ist eine echte zusätzliche Ausgabe.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("17.90", "17.90"), List.of(), betraege("17.90"));

            assertThat(result).isEqualByComparingTo("17.90");
        }

        @Test
        void fixkostenUndAbosTeilenSichEinMultiset() {
            // Position 59.00 plus unabgedecktes Abo 59.00 (das zweite von zweien) streichen
            // zusammen zwei Belastungen über 59.00 — nicht drei, nicht eine.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("59.00", "59.00", "59.00"),
                    List.of(fixkosten("59.00", "monatlich")),
                    betraege("59.00"));

            assertThat(result).isEqualByComparingTo("59.00");
        }

        @Test
        void ohneAbbuchungImMonatStreichtDasAboNichts() {
            // Das Abo wurde in diesem Monat (noch) nicht abgebucht — nichts fällt weg, das Abo
            // zählt trotzdem genau einmal über die Fixkosten-Seite.
            BigDecimal result = FixedCostDebitMatcher.variableExpenses(
                    betraege("45.60"), List.of(), betraege("17.90"));

            assertThat(result).isEqualByComparingTo("45.60");
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
