/**
 * Budget-Modul: FixedCost/FixedCostRepository, FixedCostService, FixedCostController,
 * SafeToSpendService, SavingsGoalService.
 *
 * <p>Fixkosten (US-03), wöchentlicher Safe-to-Spend-Betrag und Sparziele; alle CHF-Beträge als
 * {@code BigDecimal}. Fixkosten liegen hier, weil sie ein Eingabewert der
 * Safe-to-Spend-Berechnung sind.
 *
 * <p>Das monatliche Einkommen, das der {@code FixedCostService} für die Warnung «Fixkosten ≥
 * Einkommen» braucht, liest er über {@code com.budgetbuddy.auth.UserIncomePort} — nicht über das
 * {@code UserRepository} (Modulgrenze, CLAUDE.md).
 *
 * <p>Der {@code SafeToSpendService} liest über dieselbe Kante das Einkommen und über
 * {@code com.budgetbuddy.transaction.MonthlyExpensePort} die Belastungen des laufenden Monats —
 * kein Zugriff auf das {@code TransactionRepository}. Die Fixkosten bezieht er modul-intern über
 * den {@code FixedCostService}, damit dort und hier dieselbe gerundete Monatssumme gilt.
 *
 * <p>Welche dieser Belastungen die Zahlung einer Fixkosten-Position ist, entscheidet der
 * {@code FixedCostDebitMatcher} — bewusst hier und nicht im transaction-Modul: die Regel vergleicht
 * gegen Fixkosten-Beträge und ist damit Fachlogik dieses Moduls (BE-STS-04, ADR-13).
 *
 * <p>Seit FE-FC-05 gehen erkannte Abos (US-08) denselben Weg: der {@code SafeToSpendService} liest
 * ihre Beträge über {@code com.budgetbuddy.recurring.RecurringExpenseAmountPort} — kein Zugriff
 * auf das {@code RecurringExpenseRepository} — und der {@code FixedCostDebitMatcher} behandelt sie
 * wie Positionen, abzüglich derer, die der User bereits als Fixkosten-Position erfasst hat
 * (ADR-13-Nachtrag).
 */
package com.budgetbuddy.budget;
