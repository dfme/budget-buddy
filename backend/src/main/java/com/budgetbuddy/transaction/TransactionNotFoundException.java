package com.budgetbuddy.transaction;

/**
 * Wird geworfen, wenn zur angefragten Transaktions-ID keine Transaktion des eingeloggten Users
 * existiert — entweder gibt es die ID nicht, oder sie gehört einem anderen User.
 *
 * <p>Beide Fälle werden bewusst gleich auf 404 abgebildet (nicht 403 für die fremde Transaktion),
 * damit ein User nicht per Statuscode die Existenz fremder Transaktions-IDs abfragen kann.
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(long userId, long transactionId) {
        super("Keine Transaktion mit ID " + transactionId + " für User " + userId);
    }
}
