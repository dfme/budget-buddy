package com.budgetbuddy.auth;

/**
 * Wird bei der Registrierung geworfen, wenn die E-Mail bereits vergeben ist (BE-AUTH-03).
 *
 * <p>Wird auf HTTP 409 (Conflict) abgebildet.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("E-Mail bereits registriert: " + email);
    }
}
