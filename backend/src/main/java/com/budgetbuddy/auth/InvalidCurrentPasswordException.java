package com.budgetbuddy.auth;

/**
 * Wird bei {@code PUT /users/me/password} geworfen, wenn {@code aktuellesPasswort} nicht mit dem
 * gespeicherten Hash übereinstimmt (BE-AUTH-09). Wird auf HTTP 400 abgebildet — anders als
 * {@link InvalidCredentialsException} beim Login (401): der User ist hier bereits authentifiziert,
 * es geht nur um die Zusatzprüfung vor der Passwort-Änderung.
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("Aktuelles Passwort falsch");
    }
}
