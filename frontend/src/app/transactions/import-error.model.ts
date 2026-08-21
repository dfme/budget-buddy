/**
 * Fehler-Body der 400-Antworten von `POST /api/import/pdf` — spiegelt
 * `ImportErrorResponse.java` (FE-PDF-02, US-04; MISSING_TEXT_LAYER seit BE-PDF-08).
 *
 * Der `reason` unterscheidet die drei 400-Fälle, die sich denselben
 * HTTP-Status teilen; die übrigen Fehlerstatus (408/409/413) kommen body-los.
 */
export interface ImportErrorResponse {
  reason: 'PASSWORD_PROTECTED' | 'MISSING_TEXT_LAYER' | 'UNSUPPORTED_FORMAT';
}
