package com.budgetbuddy.transaction.dto;

import com.budgetbuddy.transaction.ImportJob;
import com.budgetbuddy.transaction.ImportJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Antwort-DTO von {@code GET /api/import/{jobId}/status} (BE-PDF-09, ADR-13) — die Quelle der
 * Fortschrittsanzeige im Frontend.
 *
 * <p>Bewusst ohne Zeitstempel und ohne den PDF-Hash: Das Frontend braucht zum Anzeigen nur
 * Zähler, Nenner und Ausgang. Der Hash ist ein interner Duplikat-Schlüssel und wird nicht nach
 * aussen gegeben (dieselbe Begründung wie beim früheren {@code ImportResponse}).
 *
 * @param status {@code RUNNING}, {@code DONE} oder {@code FAILED}.
 * @param total Anzahl erkannter Transaktionen — der Nenner der Anzeige.
 * @param processed Anzahl bereits kategorisierter Transaktionen — der Zähler.
 * @param degraded {@code true}, wenn das Zeitbudget überschritten wurde und ein Teil ohne
 *     Claude-Call als {@code Sonstiges} gespeichert ist. Der Import ist trotzdem vollständig.
 */
@Schema(description = "Fortschritt und Ausgang eines Import-Jobs")
public record ImportJobStatusResponse(
        @Schema(description = "Lebenszyklus des Jobs") ImportJobStatus status,
        @Schema(description = "Anzahl erkannter Transaktionen", example = "108") int total,
        @Schema(description = "Anzahl bereits kategorisierter Transaktionen", example = "60")
                int processed,
        @Schema(description = "Zeitbudget überschritten; Rest als 'Sonstiges' gespeichert")
                boolean degraded) {

    public static ImportJobStatusResponse from(ImportJob job) {
        return new ImportJobStatusResponse(
                job.getStatus(), job.getTotal(), job.getProcessed(), job.isDegraded());
    }
}
