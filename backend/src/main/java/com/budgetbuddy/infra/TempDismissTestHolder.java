package com.budgetbuddy.infra;

/**
 * WEGWERF für INFRA-35 (#224): erzeugt absichtlich einen ADR-9-Verstoss (double statt
 * BigDecimal für CHF-Beträge), damit der automatische review-pr-Lauf einen echten
 * blockierenden Befund postet. Wird nicht gemerged.
 */
public class TempDismissTestHolder {

    private double amountChf;

    public double getAmountChf() {
        return amountChf;
    }

    public void setAmountChf(double amountChf) {
        this.amountChf = amountChf;
    }
}
