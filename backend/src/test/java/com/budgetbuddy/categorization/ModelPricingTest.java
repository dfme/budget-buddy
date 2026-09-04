package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit-Test der Kostenformel (BE-CAT-09).
 *
 * <p>Isoliert geprüft, weil sie die einzige Rechnung im Kosten-Logging ist und ihr Ergebnis in
 * einer Log-Zeile landet, die niemand gegenrechnet. Ein Faktor 1000 daneben fiele sonst erst auf,
 * wenn jemand die Render-Logs mit der Anthropic-Rechnung vergleicht.
 */
class ModelPricingTest {

    /** Haiku 4.5, Stand 02.09.2026: 1.00 USD Input, 5.00 USD Output je 1 Mio. Tokens. */
    private static final ModelPricing HAIKU =
            new ModelPricing(new BigDecimal("1.00"), new BigDecimal("5.00"));

    @Test
    void computesCostFromInputAndOutputTokens() {
        // 1'000'000 × 1.00 + 1'000'000 × 5.00 = 6.00 USD — der Preis ist per Definition der
        // Betrag für je eine Million Tokens, was die Formel hier direkt sichtbar macht.
        assertThat(HAIKU.costFor(1_000_000L, 1_000_000L))
                .isEqualByComparingTo(new BigDecimal("6.00"));
    }

    /** Ein realistisches Bündel: 412 Input-, 147 Output-Tokens. */
    @Test
    void computesCostOfATypicalBatch() {
        assertThat(HAIKU.costFor(412L, 147L))
                .isEqualByComparingTo(new BigDecimal("0.001147"));
    }

    /**
     * Der Grund für sechs Nachkommastellen: Ein einzelner Call kostet Bruchteile eines Cents. Auf
     * zwei Stellen gerundet wäre jede Zeile 0.00 und die Summe über einen Import ebenfalls.
     */
    @Test
    void keepsSubCentAmountsVisible() {
        assertThat(HAIKU.costFor(50L, 3L))
                .isEqualByComparingTo(new BigDecimal("0.000065"))
                .isNotEqualByComparingTo(BigDecimal.ZERO);
        assertThat(HAIKU.costFor(50L, 3L).scale()).isEqualTo(ModelPricing.SCALE);
    }

    @Test
    void roundsHalfUpAtTheLastKeptDigit() {
        // 0.5 Einheiten der letzten gehaltenen Stelle: 3 Tokens × 5.00 / 1e6 = 0.000015 exakt,
        // 1 Token × 4.50 / 1e6 = 0.0000045 → HALF_UP hebt auf 0.000005.
        ModelPricing pricing = new ModelPricing(new BigDecimal("4.50"), BigDecimal.ZERO);

        assertThat(pricing.costFor(1L, 0L)).isEqualByComparingTo(new BigDecimal("0.000005"));
    }

    @Test
    void aZeroTokenCallCostsNothing() {
        assertThat(HAIKU.costFor(0L, 0L)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsNullOrNegativePrices() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ModelPricing(null, BigDecimal.ONE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ModelPricing(BigDecimal.ONE, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ModelPricing(new BigDecimal("-1"), BigDecimal.ONE));
    }
}
