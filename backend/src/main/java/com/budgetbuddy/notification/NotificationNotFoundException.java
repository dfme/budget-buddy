package com.budgetbuddy.notification;

/**
 * Wird geworfen, wenn zur angefragten ID keine Benachrichtigung des eingeloggten Users existiert —
 * entweder gibt es die ID nicht, oder sie gehört einem anderen User.
 *
 * <p>Beide Fälle werden bewusst gleich auf 404 abgebildet (nicht 403 für die fremde
 * Benachrichtigung), damit ein User nicht per Statuscode die Existenz fremder IDs abfragen kann —
 * gleiche Begründung wie bei {@code FixedCostNotFoundException}.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(long userId, long notificationId) {
        super("Keine Benachrichtigung mit ID " + notificationId + " für User " + userId);
    }
}
