/**
 * Antwort von `POST /api/import/pdf` (BE-PDF-03) — bewusst schlank, spiegelt
 * `ImportResponse.java`: nur die Anzahl importierter Transaktionen.
 */
export interface ImportResponse {
  count: number;
}
