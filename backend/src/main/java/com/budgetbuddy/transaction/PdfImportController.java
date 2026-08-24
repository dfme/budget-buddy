package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.ImportErrorResponse;
import com.budgetbuddy.transaction.dto.ImportJobStatusResponse;
import com.budgetbuddy.transaction.dto.ImportStartedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF-Upload-Endpoint für den Kontoauszug-Import (BE-PDF-03, US-04).
 *
 * <p>Multipart-Upload einer einzelnen PDF-Datei; delegiert an {@link PdfImportService}, der
 * Duplikatcheck und Parse synchron erledigt und die Kategorisierung dann an einen Hintergrund-Job
 * übergibt (ADR-14, BE-PDF-09). Der Upload antwortet deshalb mit {@code 202 Accepted} und der
 * Job-ID statt mit einer Endzahl; den Fortschritt holt das Frontend über
 * {@link #importStatus(Long, Long)} ab und zeigt ihn als Balken an.
 *
 * <p>Vor ADR-14 lief der ganze Flow im Request und lief damit bei ~110 Transaktionen
 * reproduzierbar in ein 30-Sekunden-Zeitbudget, das den gesamten Import verwarf (#192). Auf den
 * Hintergrundlauf wartet kein Request mehr — dieses Fehlerbild existiert nicht mehr.
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird.
 *
 * <p>Fehlerabbildung (siehe {@link PdfImportExceptionHandler}): ungültiges/passwortgeschütztes PDF
 * → 400, Duplikat → 409 ({@code @ResponseStatus} auf {@link DuplicatePdfImportException}), Timeout
 * → 408 ({@link PdfImportTimeoutException}), Überschreitung des serverseitigen 10-MB-Limits → 413.
 *
 * <p>Der optionale Parameter {@code force} ist die Gegenseite des 409: Bestätigt der User im
 * Duplikat-Dialog «Trotzdem importieren» (FE-PDF-03, US-04), wiederholt der Client denselben
 * Upload mit {@code force=true} und ersetzt damit den früheren Import. Default ist {@code false} —
 * ohne ausdrückliche Bestätigung bleibt es beim Duplikatschutz.
 */
@RestController
@RequestMapping("/api/import")
@Tag(name = "Import", description = "PDF-Import von Kontoauszügen für den eingeloggten User")
public class PdfImportController {

    private final PdfImportService pdfImportService;

    public PdfImportController(PdfImportService pdfImportService) {
        this.pdfImportService = pdfImportService;
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Kontoauszug-PDF hochladen und Import starten",
            description = "Lädt eine PDF-Datei (max. 10 MB) hoch: Duplikatcheck über den SHA-256 "
                    + "des PDFs und Parsing des Schweizer-Bank-Layouts laufen synchron, die "
                    + "Kategorisierung und Persistierung danach im Hintergrund. Die Antwort trägt "
                    + "die Job-ID, über die der Fortschritt abgefragt wird. Die PDF-Binärdaten "
                    + "werden nicht gespeichert.")
    @ApiResponses({
        @ApiResponse(responseCode = "202",
                description = "Import gestartet; Job-ID und Anzahl erkannter Transaktionen"),
        @ApiResponse(responseCode = "400",
                description = "Ungültiges, gescanntes (kein Text-Layer) oder passwortgeschütztes "
                        + "PDF; der Body unterscheidet die Fälle über den maschinenlesbaren reason",
                content = @Content(schema = @Schema(implementation = ImportErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = @Content),
        @ApiResponse(responseCode = "408",
                description = "Zeitbudget beim Parsen überschritten", content = @Content),
        @ApiResponse(responseCode = "409",
                description = "Dieses PDF wurde bereits importiert; mit force=true erneut "
                        + "aufrufen, um den früheren Import zu ersetzen", content = @Content),
        @ApiResponse(responseCode = "413",
                description = "PDF überschreitet das 10-MB-Limit", content = @Content)
    })
    public ImportStartedResponse importPdf(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Die hochzuladende PDF-Datei (max. 10 MB)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Überspringt den Duplikatcheck und ersetzt einen früheren "
                    + "Import desselben PDFs. Nur nach ausdrücklicher Bestätigung des Users im "
                    + "Duplikat-Dialog setzen.")
            @RequestParam(name = "force", defaultValue = "false") boolean force) {
        return ImportStartedResponse.from(
                pdfImportService.startImport(userId, readBytes(file), force));
    }

    @GetMapping("/{jobId}/status")
    @Operation(summary = "Fortschritt eines Import-Jobs abfragen",
            description = "Liefert Zähler, Nenner und Ausgang des Jobs. Das Frontend pollt diesen "
                    + "Endpoint, solange der Status RUNNING ist, und zeigt processed/total als "
                    + "Fortschrittsbalken an.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aktueller Stand des Jobs"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = @Content),
        @ApiResponse(responseCode = "404",
                description = "Kein Job dieser ID für den eingeloggten User", content = @Content)
    })
    public ImportJobStatusResponse importStatus(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Job-ID aus der Upload-Antwort") @PathVariable Long jobId) {
        // Die Abfrage geht über findByIdAndUserId, nicht über findById: Job-IDs sind fortlaufend
        // und damit ratbar. Ein fremder Job ist deshalb nicht «verboten», sondern nicht vorhanden
        // — ein 403 würde verraten, dass unter dieser ID ein fremder Import läuft.
        return pdfImportService.findJob(userId, jobId)
                .map(ImportJobStatusResponse::from)
                .orElseThrow(ImportJobNotFoundException::new);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            // Der Multipart-Stream ist beim Argument-Binding bereits vollständig gepuffert;
            // ein IOException hier ist ein Infrastrukturfehler, kein fachlicher PDF-Fehler.
            throw new UncheckedIOException("Hochgeladene Datei konnte nicht gelesen werden", e);
        }
    }
}
