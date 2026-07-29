package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.ImportErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Bildet die Fehlerfälle des PDF-Upload-Endpoints (BE-PDF-03) auf HTTP-Status ab.
 *
 * <p>Nur die hier fehlenden Mappings: {@link PdfParseException} (inkl. der Subtypen
 * {@link MissingTextLayerException} und {@link UnsupportedStatementFormatException}) und
 * {@link PasswordProtectedPdfException} → 400; Überschreitung des serverseitigen 10-MB-Limits
 * ({@link MaxUploadSizeExceededException}, geworfen beim Multipart-Parsing) → 413.
 *
 * <p>Die restlichen Fälle brauchen keinen Handler: {@link DuplicatePdfImportException} (409) und
 * {@link PdfImportTimeoutException} (408) tragen ihr Status-Mapping bereits als
 * {@code @ResponseStatus}, ein fehlender {@code file}-Part liefert Spring als 400.
 *
 * <p>Die beiden 400er tragen einen {@link ImportErrorResponse}-Body mit maschinenlesbarem
 * {@code reason}: Passwort- und Format-Fehler teilen sich den Status und wären sonst für das
 * Frontend nicht unterscheidbar, das daraus zwei getrennte Nutzermeldungen formuliert (FE-PDF-02).
 * 408/409/413 bleiben body-los (analog {@link TransactionExceptionHandler}) — dort ist der Status
 * allein eindeutig.
 */
@RestControllerAdvice(assignableTypes = PdfImportController.class)
public class PdfImportExceptionHandler {

    @ExceptionHandler(PdfParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ImportErrorResponse handlePdfParse(PdfParseException ex) {
        // 400 für ein nicht lesbares/unbekanntes PDF-Format (inkl. Subtypen).
        return new ImportErrorResponse(ImportErrorResponse.Reason.UNSUPPORTED_FORMAT);
    }

    @ExceptionHandler(PasswordProtectedPdfException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ImportErrorResponse handlePasswordProtected(PasswordProtectedPdfException ex) {
        // 400 für ein verschlüsseltes PDF.
        return new ImportErrorResponse(ImportErrorResponse.Reason.PASSWORD_PROTECTED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public void handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // Kein Body: 413, sobald das serverseitige 10-MB-Multipart-Limit überschritten wird.
    }
}
