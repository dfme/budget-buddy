package com.budgetbuddy.budget;

/**
 * Wird geworfen, wenn zur angefragten ID keine Fixkosten-Position des eingeloggten Users existiert
 * — entweder gibt es die ID nicht, oder sie gehört einem anderen User.
 *
 * <p>Beide Fälle werden bewusst gleich auf 404 abgebildet (nicht 403 für die fremde Position),
 * damit ein User nicht per Statuscode die Existenz fremder IDs abfragen kann. Gleiche Begründung
 * wie bei {@code TransactionNotFoundException}; das {@link FixedCostRepository} liefert die beiden
 * Fälle aus demselben Grund bereits ununterscheidbar zurück.
 */
public class FixedCostNotFoundException extends RuntimeException {

    public FixedCostNotFoundException(long userId, long fixedCostId) {
        super("Keine Fixkosten-Position mit ID " + fixedCostId + " für User " + userId);
    }
}
