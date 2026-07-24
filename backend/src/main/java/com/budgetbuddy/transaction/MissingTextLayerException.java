package com.budgetbuddy.transaction;

/**
 * Wird geworfen, wenn ein PDF keinen extrahierbaren Textlayer enthält — typischerweise ein
 * eingescannter Kontoauszug, dessen Seiten nur aus Bildern bestehen (BE-PDF-04, US-04).
 *
 * <p>Bewusst getrennt von {@link UnsupportedStatementFormatException}: Bei einem Scan ist die
 * hilfreichere nutzerseitige Meldung „bitte lade das PDF aus dem E-Banking herunter, statt es
 * zu scannen" — nicht „Format nicht unterstützt".
 *
 * <p>Subklasse von {@link PdfParseException}, damit bestehende Aufrufer-Verträge gültig
 * bleiben und der Upload-Endpoint (BE-PDF-03) per Subtyp differenzieren kann.
 */
public class MissingTextLayerException extends PdfParseException {

  public MissingTextLayerException() {
    super("Das PDF enthält keinen Textlayer (vermutlich ein Scan) und kann nicht"
        + " verarbeitet werden");
  }
}
