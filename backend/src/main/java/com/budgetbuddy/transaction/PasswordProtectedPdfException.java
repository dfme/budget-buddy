package com.budgetbuddy.transaction;

/**
 * Wird geworfen, wenn ein hochgeladenes PDF passwortgeschützt (verschlüsselt) ist und daher nicht
 * gelesen werden kann (BE-PDF-01, US-04).
 *
 * <p>Der Aufrufer übersetzt diese Exception in die nutzerseitige Meldung „Das PDF ist
 * passwortgeschützt — bitte entferne den Schutz vor dem Upload".
 */
public class PasswordProtectedPdfException extends RuntimeException {

  public PasswordProtectedPdfException(Throwable cause) {
    super("Das PDF ist passwortgeschützt und kann nicht gelesen werden", cause);
  }
}
