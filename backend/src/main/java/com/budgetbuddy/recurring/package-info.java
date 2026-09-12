/**
 * Recurring-Modul: RecurringExpense/RecurringExpenseRepository, RecurringExpenseService.
 *
 * <p>Abo-Erkennung aus US-08 (BE-REC-01, siehe {@code docs/plans/us-08-09-12-breakdown.md}):
 * Ein Empfänger, der in zwei aufeinanderfolgenden Monaten einen Betrag innerhalb von ±2 %
 * belastet, wird als wiederkehrende Ausgabe persistiert und dem Nutzer gemeldet. Die Endpoints
 * der Abo-Übersicht (BE-REC-02) folgen in diesem Modul.
 *
 * <p>Drei Kanten zu anderen Modulen, alle über Interfaces (Modulgrenze, CLAUDE.md):
 *
 * <ul>
 *   <li>{@code ImportJobRunner} (transaction) stösst die Erkennung über den
 *       {@link com.budgetbuddy.recurring.RecurringExpenseDetectionPort} an — am Ende jedes
 *       erfolgreichen Imports, kein Scheduler.
 *   <li>Die Ausgaben-Historie kommt über {@code com.budgetbuddy.transaction.ExpenseHistoryPort}
 *       herein, mit bereits normalisiertem Empfänger. Der Zyklus transaction ↔ recurring besteht
 *       nur aus diesen beiden Interfaces, wie heute schon auth ↔ budget.
 *   <li>Benachrichtigt wird über {@code com.budgetbuddy.notification.NotificationPort} mit dem Typ
 *       {@link com.budgetbuddy.recurring.RecurringExpenseService#NOTIFICATION_TYPE}.
 * </ul>
 *
 * <p>Die Kontolöschung (US-02, nDSG) räumt über den
 * {@link com.budgetbuddy.recurring.RecurringExpenseCleanupPort} auf, den
 * {@code UserService.deleteUser} aus dem {@code auth}-Modul aufruft — der Fremdschlüssel in V11
 * steht bewusst ohne {@code ON DELETE CASCADE}.
 */
package com.budgetbuddy.recurring;
