package com.budgetbuddy.transaction.dto;

import com.budgetbuddy.transaction.ImportJobStatus;

/**
 * Fehler-Body des 409 von {@code GET /import/{jobId}/transactions} (BE-PDF-14, US-04).
 *
 * <p>Trägt den Stand des Jobs, weil die beiden Fälle hinter dem Status unterschiedliche
 * Reaktionen verlangen: Bei {@code RUNNING} lohnt sich Warten und ein erneuter Versuch, bei
 * {@code FAILED} nicht — dort ist der Import endgültig gescheitert und es wird nie Buchungen
 * geben. Ohne dieses Feld müsste der Client die Unterscheidung über einen zweiten Aufruf von
 * {@code GET /import/{jobId}/status} nachholen.
 *
 * <p>Anders als die 409-Antwort des Uploads (Duplikat), die laut {@link ImportErrorResponse}
 * bewusst ohne eigenen Body auskommt: Dort ist der Status für sich eindeutig.
 *
 * @param status aktueller Stand des Jobs; nie {@link ImportJobStatus#DONE} — dann antwortet der
 *     Endpoint mit der Liste.
 */
public record ImportNotCompleteResponse(ImportJobStatus status) {
}
