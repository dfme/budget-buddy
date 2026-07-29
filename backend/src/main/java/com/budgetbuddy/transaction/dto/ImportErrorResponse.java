package com.budgetbuddy.transaction.dto;

/**
 * Fehler-Body der 400-Antworten des PDF-Upload-Endpoints {@code POST /import/pdf} (FE-PDF-02,
 * US-04).
 *
 * <p>Beide 400-Fälle — passwortgeschütztes und nicht lesbares PDF — tragen denselben HTTP-Status;
 * erst der {@code reason} macht sie für das Frontend unterscheidbar, das daraus zwei getrennte
 * Nutzermeldungen formuliert. Die übrigen Fehlerstatus (408/409/413) bleiben body-los, weil der
 * Status dort bereits eindeutig ist.
 *
 * @param reason maschinenlesbarer Fehlergrund des 400ers.
 */
public record ImportErrorResponse(Reason reason) {

    /** Fehlergründe, die hinter einem 400 stecken können. */
    public enum Reason {
        /** Das PDF ist verschlüsselt und kann ohne Passwort nicht gelesen werden. */
        PASSWORD_PROTECTED,
        /** Das PDF ist kein lesbarer Kontoauszug (kein Text-Layer, unbekanntes Layout, defekt). */
        UNSUPPORTED_FORMAT
    }
}
