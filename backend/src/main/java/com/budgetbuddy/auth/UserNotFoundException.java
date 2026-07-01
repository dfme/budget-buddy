package com.budgetbuddy.auth;

/**
 * Wird geworfen, wenn zur authentifizierten User-ID kein {@link User} existiert.
 *
 * <p>Regulär tritt das nicht auf (ein gültiges JWT impliziert einen existierenden User); möglich
 * etwa, wenn das Konto nach Token-Ausstellung gelöscht wurde. Wird auf 404 abgebildet.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long userId) {
        super("Kein User mit ID " + userId);
    }
}
