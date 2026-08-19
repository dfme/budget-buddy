package com.budgetbuddy.transaction.dto;

/**
 * Fehler-Body der 400-Antworten des PDF-Upload-Endpoints {@code POST /import/pdf} (FE-PDF-02,
 * US-04).
 *
 * <p>Alle drei 400-Fälle — passwortgeschützt, gescannt ohne Text-Layer, unbekanntes Layout —
 * tragen denselben HTTP-Status; erst der {@code reason} macht sie für das Frontend
 * unterscheidbar, das daraus getrennte Nutzermeldungen formuliert (BE-PDF-08: der Scan-Fall war
 * bis dahin fälschlich im generischen {@code UNSUPPORTED_FORMAT} untergegangen). Die übrigen
 * Fehlerstatus (408/409/413) tragen keinen {@code reason}, weil der Status dort bereits eindeutig
 * ist (408/409 antworten über Springs ERROR-Dispatch mit dem Standard-Fehlerbody, 413 ohne Body —
 * das Frontend wertet nur den Status aus).
 *
 * @param reason maschinenlesbarer Fehlergrund des 400ers.
 */
public record ImportErrorResponse(Reason reason) {

    /** Fehlergründe, die hinter einem 400 stecken können. */
    public enum Reason {
        /** Das PDF ist verschlüsselt und kann ohne Passwort nicht gelesen werden. */
        PASSWORD_PROTECTED,
        /** Das PDF enthält keinen Text-Layer (vermutlich ein Scan). */
        MISSING_TEXT_LAYER,
        /** Das PDF ist kein lesbarer Kontoauszug (unbekanntes Layout, defekt). */
        UNSUPPORTED_FORMAT
    }
}
