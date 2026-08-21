/**
 * Transaction-Modul: TransactionController, PdfImportService, Transaction-Entity.
 *
 * <p>PDF-Upload synchron mit Timeout + Fallback; CHF-Beträge als {@code BigDecimal}.
 *
 * <p>Nach aussen stellt das Modul den {@link com.budgetbuddy.transaction.MonthlyExpensePort}
 * bereit: das budget-Modul bezieht darüber die Belastungen eines Monats für den Safe-to-Spend
 * (US-06), ohne auf {@code TransactionRepository} zuzugreifen (Modulgrenze, CLAUDE.md). Über die
 * Kante gehen nur Beträge — welche davon eine Fixkosten-Zahlung ist, entscheidet drüben das
 * budget-Modul (BE-STS-04, ADR-13).
 */
package com.budgetbuddy.transaction;
