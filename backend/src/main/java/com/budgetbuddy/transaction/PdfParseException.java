package com.budgetbuddy.transaction;

/**
 * Wird geworfen, wenn ein PDF nicht gelesen oder verarbeitet werden kann — etwa weil die Datei
 * beschädigt ist oder kein gültiges PDF darstellt (BE-PDF-01, US-04).
 *
 * <p>Der Aufrufer übersetzt diese Exception in die nutzerseitige Meldung, dass nur PDF-Dateien von
 * Schweizer Banken unterstützt werden.
 */
public class PdfParseException extends RuntimeException {

  public PdfParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
