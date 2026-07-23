package com.budgetbuddy.transaction;

/**
 * Wird geworfen, wenn ein PDF zwar einen Textlayer enthält, daraus aber keine einzige
 * Buchungszeile extrahiert werden konnte — das Layout passt zu keinem der unterstützten
 * Formate (BE-PDF-04, US-04).
 *
 * <p>Ohne diese Exception sähe ein solcher Upload wie „erfolgreich, 0 Transaktionen" aus —
 * ein stiller Fehlschlag beim ersten PDF-Upload ist Churn-Risiko #1. Der Aufrufer übersetzt
 * sie in die nutzerseitige Meldung „Dieses Kontoauszug-Format können wir noch nicht lesen".
 *
 * <p>Subklasse von {@link PdfParseException}, damit bestehende Aufrufer-Verträge gültig
 * bleiben und der Upload-Endpoint (BE-PDF-03) per Subtyp differenzieren kann.
 */
public class UnsupportedStatementFormatException extends PdfParseException {

  public UnsupportedStatementFormatException() {
    super("Aus dem PDF konnte keine Transaktion extrahiert werden — das Kontoauszug-Format"
        + " wird nicht unterstützt");
  }
}
