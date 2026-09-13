package com.budgetbuddy.recurring;

/**
 * Wird geworfen, wenn zur angefragten ID kein Eintrag des eingeloggten Users existiert — entweder
 * gibt es die ID nicht, oder sie gehört einem anderen User.
 *
 * <p>Beide Fälle werden bewusst gleich auf 404 abgebildet (nicht 403 für den fremden Eintrag),
 * damit ein User nicht per Statuscode die Existenz fremder IDs abfragen kann — gleiche
 * Begründung wie bei {@code NotificationNotFoundException}.
 */
public class RecurringExpenseNotFoundException extends RuntimeException {

    public RecurringExpenseNotFoundException(long userId, long recurringExpenseId) {
        super("Kein Abo-Eintrag mit ID " + recurringExpenseId + " für User " + userId);
    }
}
