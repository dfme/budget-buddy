/**
 * Transaction-Modul: TransactionController, PdfImportService, Transaction-Entity.
 *
 * <p>PDF-Upload synchron mit Timeout + Fallback; CHF-Beträge als {@code BigDecimal}.
 *
 * <p>Nach aussen stellt das Modul den {@link com.budgetbuddy.transaction.MonthlyExpensePort}
 * bereit: das budget-Modul bezieht darüber die Ausgabensumme eines Monats für den Safe-to-Spend
 * (US-06), ohne auf {@code TransactionRepository} zuzugreifen (Modulgrenze, CLAUDE.md).
 */
package com.budgetbuddy.transaction;
