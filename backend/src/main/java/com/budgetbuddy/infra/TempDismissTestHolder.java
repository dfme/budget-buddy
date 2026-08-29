package com.budgetbuddy.infra;

import java.math.BigDecimal;

/**
 * WEGWERF für INFRA-35 (#224): der ADR-9-Verstoss aus dem ersten Commit ist hier behoben
 * (BigDecimal statt double), damit der zweite review-pr-Lauf keine Blocker mehr findet und den
 * neuen Self-Dismiss-Mechanismus (Variante C) auslöst. Wird nicht gemerged.
 */
public class TempDismissTestHolder {

    private BigDecimal amountChf;

    public BigDecimal getAmountChf() {
        return amountChf;
    }

    public void setAmountChf(BigDecimal amountChf) {
        this.amountChf = amountChf;
    }
}
