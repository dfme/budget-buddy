package com.budgetbuddy.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetbuddy.money.ChfAmounts.Violation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit-Test der geteilten CHF-Betragsregel (BE-FC-04).
 *
 * <p>Der Test steht bewusst an der Regel selbst und nicht nur an den beiden Services: Er ist ab
 * jetzt die eine Stelle, die «grösser als 0, höchstens zwei Nachkommastellen, höchstens
 * {@code 99'999'999.99}» festhält. {@code UserServiceTest} und {@code FixedCostServiceTest} prüfen
 * daneben weiterhin, dass ihre eigene Exception und ihr eigener Meldungstext ankommen — geteilt
 * wird die Prüfung, nicht der Text.
 */
class ChfAmountsTest {

    // --- gültige Beträge ---

    @Test
    void acceptsATypicalAmount() {
        assertThat(ChfAmounts.check(new BigDecimal("1200.50"))).isEmpty();
    }

    @Test
    void acceptsTrailingZerosBeyondTheRappenScale() {
        // Der Grund, warum @Digits(fraction = 2) hier nicht genügt: es zählt scale() ohne
        // stripTrailingZeros() und lehnte 100.000 ab, obwohl das derselbe Wert wie 100.00 ist.
        assertThat(ChfAmounts.check(new BigDecimal("100.000"))).isEmpty();
        assertThat(ChfAmounts.check(new BigDecimal("100.0000000"))).isEmpty();
    }

    @Test
    void acceptsAnAmountWithoutADecimalPoint() {
        assertThat(ChfAmounts.check(new BigDecimal("42"))).isEmpty();
    }

    @Test
    void acceptsTheSmallestPositiveAmount() {
        assertThat(ChfAmounts.check(new BigDecimal("0.01"))).isEmpty();
    }

    // --- Grenzen ---

    @Test
    void acceptsTheColumnCapacityItself() {
        // 99999999.99 ist das Maximum von DECIMAL(10,2) und muss durchkommen, nicht abgelehnt
        // werden — die Grenze ist inklusiv.
        assertThat(ChfAmounts.check(ChfAmounts.MAX)).isEmpty();
    }

    @Test
    void rejectsOneRappenAboveTheColumnCapacity() {
        assertThat(ChfAmounts.check(ChfAmounts.MAX.add(new BigDecimal("0.01"))))
                .contains(Violation.UEBER_MAXIMUM);
    }

    @Test
    void maxMatchesTheDecimalColumnDefinitionOfTheMigrations() {
        // V01__create_users_table.sql:10, V02__create_transactions_table.sql:11 und
        // V03__create_fixed_costs_table.sql:8 definieren alle DECIMAL(10,2): 10 Stellen gesamt,
        // davon 2 nach dem Komma. Ändert sich die Spaltenbreite, muss dieser Test brechen.
        assertThat(ChfAmounts.MAX).isEqualByComparingTo("99999999.99");
        assertThat(ChfAmounts.MAX.precision()).isEqualTo(10);
        assertThat(ChfAmounts.MAX.scale()).isEqualTo(ChfAmounts.RAPPEN_SCALE);
    }

    @Test
    void maxFormattedIsTheSameNumberInSwissNotation() {
        // Die Meldungstexte beider Module bauen auf MAX_FORMATTED auf. Läuft es gegen MAX
        // auseinander, nennt die Fehlermeldung eine andere Grenze als die, die geprüft wird.
        assertThat(new BigDecimal(ChfAmounts.MAX_FORMATTED.replace("'", "")))
                .isEqualByComparingTo(ChfAmounts.MAX);
    }

    // --- verletzte Regeln ---

    @Test
    void reportsAMissingAmountInsteadOfThrowing() {
        assertThat(ChfAmounts.check(null)).contains(Violation.FEHLT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-1200.50"})
    void rejectsZeroAndNegativeAmounts(String betrag) {
        assertThat(ChfAmounts.check(new BigDecimal(betrag))).contains(Violation.NICHT_POSITIV);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.345", "4200.004", "0.001"})
    void rejectsMoreThanTwoSignificantDecimals(String betrag) {
        assertThat(ChfAmounts.check(new BigDecimal(betrag)))
                .contains(Violation.ZU_VIELE_NACHKOMMASTELLEN);
    }

    // --- Reihenfolge der Prüfung ---

    @Test
    void checksTheSignBeforeTheScale() {
        // -0.001 verletzt beides. Welche Meldung der User sieht, hängt an dieser Reihenfolge —
        // sie ist deshalb sichtbares Verhalten und nicht bloss Implementierungsdetail.
        assertThat(ChfAmounts.check(new BigDecimal("-0.001"))).contains(Violation.NICHT_POSITIV);
    }

    @Test
    void checksTheScaleBeforeTheMaximum() {
        assertThat(ChfAmounts.check(new BigDecimal("100000000.001")))
                .contains(Violation.ZU_VIELE_NACHKOMMASTELLEN);
    }

    // --- Normalisierung ---

    @Test
    void normalisesToTheRappenScale() {
        assertThat(ChfAmounts.toRappen(new BigDecimal("42"))).isEqualTo(new BigDecimal("42.00"));
        assertThat(ChfAmounts.toRappen(new BigDecimal("100.000")))
                .isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void normalisationKeepsTheValueUnchanged() {
        assertThat(ChfAmounts.toRappen(new BigDecimal("1200.5")))
                .isEqualByComparingTo("1200.50")
                .satisfies(betrag -> assertThat(betrag.scale()).isEqualTo(2));
    }

    @Test
    void normalisationFailsLoudlyOnAnUncheckedAmount() {
        // RoundingMode.UNNECESSARY ist Absicht: Wer toRappen() ohne check() aufruft, soll nicht
        // still gerundete Rappen bekommen. Genau dieses stille Runden war der Defekt aus #148.
        assertThatThrownBy(() -> ChfAmounts.toRappen(new BigDecimal("4200.004")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void normalisationAcceptsEveryAmountThatPassedTheCheck() {
        for (String betrag : new String[] {"0.01", "42", "100.000", "1200.50", "99999999.99"}) {
            BigDecimal wert = new BigDecimal(betrag);
            assertThat(ChfAmounts.check(wert)).isEmpty();
            assertThatCode(() -> ChfAmounts.toRappen(wert)).doesNotThrowAnyException();
        }
    }
}
