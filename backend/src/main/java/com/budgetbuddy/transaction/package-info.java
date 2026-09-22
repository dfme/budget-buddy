/**
 * Transaction-Modul: TransactionController, PdfImportService, ImportJobRunner,
 * Transaction-Entity.
 *
 * <p>PDF-Upload zweistufig (ADR-14): Duplikatcheck und Parse synchron im Request mit Zeitbudget,
 * Kategorisierung und Persistierung danach als {@code @Async}-Job mit Fortschritts-Polling.
 * Timeout + Fallback auf {@code Sonstiges} gelten weiterhin; CHF-Beträge als {@code BigDecimal}.
 *
 * <p>Nach aussen stellt das Modul den {@link com.budgetbuddy.transaction.MonthlyExpensePort}
 * bereit: das budget-Modul bezieht darüber die Belastungen eines Monats für den Safe-to-Spend
 * (US-06), ohne auf {@code TransactionRepository} zuzugreifen (Modulgrenze, CLAUDE.md). Über die
 * Kante gehen nur Beträge — welche davon eine Fixkosten-Zahlung ist, entscheidet drüben das
 * budget-Modul (BE-STS-04, ADR-13).
 *
 * <p>Zweiter Lese-Port ist der {@link com.budgetbuddy.transaction.ExpenseHistoryPort}: das
 * recurring-Modul bezieht darüber die Ausgaben-Historie für die Abo-Erkennung (US-08, BE-REC-01),
 * mit bereits normalisiertem Empfänger — wo der im Bank-PDF steht, bleibt Wissen dieses Moduls.
 * In der Gegenrichtung stösst {@code ImportJobRunner} die Erkennung über
 * {@code com.budgetbuddy.recurring.RecurringExpenseDetectionPort} an, nach dem Persistieren.
 *
 * <p>Dritte ausgehende Kante ist {@code com.budgetbuddy.notification.NotificationPort}: Nach dem
 * Endstatus eines Import-Jobs legt {@code ImportJobRunner} darüber die Abschluss-Benachrichtigung
 * für die Glocke an (BE-PDF-15) — Typen {@code IMPORT_COMPLETED}, {@code IMPORT_DEGRADED},
 * {@code IMPORT_FAILED}, als {@code reference_id} die Job-ID. Wie bei der Abo-Erkennung darf ein
 * Fehler auf dieser Kante den Import nicht mehr beeinflussen.
 *
 * <p>{@code IMPORT_FAILED} hat seit BE-PDF-16 zwei Erzeuger: den Runner für den abgebrochenen Lauf
 * und den {@code StaleImportJobCleaner} für den Job, dessen Prozess weg ist. Beide gehen über
 * {@code ImportFailureNotifier}, in dem Typ, Text und das Schlucken des Fehlers <em>einmal</em>
 * stehen. Sie schliessen sich gegenseitig aus — der Cleaner fasst nur an, was noch auf
 * {@code RUNNING} steht.
 */
package com.budgetbuddy.transaction;
