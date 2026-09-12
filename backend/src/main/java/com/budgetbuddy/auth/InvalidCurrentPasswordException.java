package com.budgetbuddy.auth;

/**
 * Wird bei {@code PUT /users/me/password} (BE-AUTH-09) und {@code DELETE /users/me} (BE-AUTH-14)
 * geworfen, wenn das im Body mitgeschickte Passwort nicht mit dem gespeicherten Hash
 * übereinstimmt. Wird auf HTTP 400 abgebildet — anders als {@link InvalidCredentialsException}
 * beim Login (401): der User ist hier bereits authentifiziert, es geht nur um die Zusatzprüfung
 * vor der Passwort-Änderung bzw. der Kontolöschung.
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("Aktuelles Passwort falsch");
    }
}
