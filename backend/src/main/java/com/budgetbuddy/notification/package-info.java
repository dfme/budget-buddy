/**
 * Notification-Modul: Notification/NotificationRepository, NotificationService,
 * NotificationController.
 *
 * <p>Generisches In-App-Benachrichtigungs-Fundament (Vorlauf-Task für US-08, siehe
 * {@code docs/plans/us-08-09-12-breakdown.md}). Andere Module erzeugen Benachrichtigungen
 * ausschliesslich über den {@link com.budgetbuddy.notification.NotificationPort}, nie über
 * {@link com.budgetbuddy.notification.NotificationRepository} direkt (Modulgrenze, CLAUDE.md).
 *
 * <p>{@code type} ist in {@link com.budgetbuddy.notification.Notification} bewusst ein freier
 * String statt eines Java-Enums — kein Aufrufer des Ports existiert bislang, ein Enum jetzt hiesse,
 * Werte für einen noch nicht gebauten Consumer zu raten (z. B. die Abo-Erkennung aus US-08).
 *
 * <p>Die Kontolöschung (US-02, nDSG) räumt über den
 * {@link com.budgetbuddy.notification.NotificationCleanupPort} auf, den
 * {@code UserService.deleteUser} aus dem {@code auth}-Modul aufruft.
 */
package com.budgetbuddy.notification;
