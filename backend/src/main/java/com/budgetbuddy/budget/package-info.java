/**
 * Budget-Modul: FixedCost/FixedCostRepository, FixedCostService, BudgetController,
 * SafeToSpendService, SavingsGoalService.
 *
 * <p>Fixkosten (US-03), wöchentlicher Safe-to-Spend-Betrag und Sparziele; alle CHF-Beträge als
 * {@code BigDecimal}. Fixkosten liegen hier, weil sie ein Eingabewert der
 * Safe-to-Spend-Berechnung sind.
 *
 * <p>Das monatliche Einkommen, das der {@code FixedCostService} für die Warnung «Fixkosten ≥
 * Einkommen» braucht, liest er über {@code com.budgetbuddy.auth.UserIncomePort} — nicht über das
 * {@code UserRepository} (Modulgrenze, CLAUDE.md).
 */
package com.budgetbuddy.budget;
