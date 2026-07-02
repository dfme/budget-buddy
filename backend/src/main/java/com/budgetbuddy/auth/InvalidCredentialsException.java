package com.budgetbuddy.auth;

/**
 * Wird beim Login geworfen, wenn E-Mail oder Passwort nicht stimmen (BE-AUTH-03).
 *
 * <p>Bewusst identisch für „E-Mail existiert nicht" und „Passwort falsch": die Antwort (401) darf
 * keine Auskunft geben, ob eine E-Mail registriert ist (User-Enumeration-Schutz).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Ungültige Anmeldedaten");
    }
}
