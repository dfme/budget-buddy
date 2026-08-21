package com.budgetbuddy.transaction.dto;

import com.budgetbuddy.transaction.ImportJob;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Antwort-DTO des PDF-Upload-Endpoints {@code POST /api/import/pdf} (BE-PDF-09, ADR-13).
 *
 * <p>Seit ADR-13 ist der Upload nicht mehr das Ende des Imports, sondern sein Anfang: Das PDF ist
 * geparst, die Kategorisierung läuft im Hintergrund. Deshalb trägt die Antwort die Job-ID zum
 * Weiterverfolgen statt einer Endzahl — und {@code total} als Nenner, damit der
 * Fortschrittsbalken schon vor dem ersten Status-Poll etwas anzeigen kann.
 *
 * @param jobId ID des Import-Jobs für {@code GET /api/import/{jobId}/status}.
 * @param total Anzahl im PDF erkannter Transaktionen.
 */
@Schema(description = "Bestätigung, dass der Import gestartet wurde")
public record ImportStartedResponse(
        @Schema(description = "ID des Import-Jobs zum Abfragen des Fortschritts", example = "42")
                Long jobId,
        @Schema(description = "Anzahl im PDF erkannter Transaktionen", example = "108")
                int total) {

    public static ImportStartedResponse from(ImportJob job) {
        return new ImportStartedResponse(job.getId(), job.getTotal());
    }
}
