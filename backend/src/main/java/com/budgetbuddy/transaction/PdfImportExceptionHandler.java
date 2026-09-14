package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.ImportErrorResponse;
import com.budgetbuddy.transaction.dto.ImportNotCompleteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Bildet die Fehlerfälle des PDF-Upload-Endpoints (BE-PDF-03) auf HTTP-Status ab.
 *
 * <p>Nur die hier fehlenden Mappings: {@link MissingTextLayerException},
 * {@link PdfParseException} (inkl. des verbleibenden Subtyps
 * {@link UnsupportedStatementFormatException}) und {@link PasswordProtectedPdfException} → 400;
 * Überschreitung des serverseitigen 10-MB-Limits ({@link MaxUploadSizeExceededException},
 * geworfen beim Multipart-Parsing) → 413.
 *
 * <p>{@link MissingTextLayerException} bekommt einen eigenen Handler, obwohl sie eine
 * {@link PdfParseException} ist (BE-PDF-08): Spring wählt bei mehreren passenden
 * {@code @ExceptionHandler}-Methoden automatisch den spezifischeren Typ, unabhängig von der
 * Deklarationsreihenfolge — ohne den eigenen Handler wäre der Scan-Fall im generischen
 * {@code UNSUPPORTED_FORMAT} untergegangen, obwohl die Exception selbst eine andere,
 * hilfreichere Nutzermeldung dokumentiert.
 *
 * <p>Seit BE-PDF-14 kommt {@link ImportJobNotCompleteException} → 409 dazu, mit einem
 * {@link ImportNotCompleteResponse}-Body. <strong>Dieser 409 läuft bewusst über einen Handler
 * und nicht über {@code @ResponseStatus}</strong> wie der Duplikat-409 daneben — zwei
 * nachgemessene Gründe:
 *
 * <ul>
 *   <li>{@code server.error.include-message} ist nirgends gesetzt, Spring Boots Default ist
 *       {@code never}. Ein {@code @ResponseStatus(CONFLICT, reason = "…")} lieferte damit einen
 *       Body <em>ohne</em> die Meldung, und der Status des Jobs — der eigentliche Hinweis —
 *       käme beim Client nie an.
 *   <li>{@code @ResponseStatus} antwortet über {@code sendError()} und damit über den
 *       ERROR-Dispatch auf {@code /error}, den MockMvc nicht ausführt. Genau daran waren 408 und
 *       409 unter MockMvc grün und kamen real als 401 an (siehe die Anmerkung in
 *       {@code PdfImportControllerIntegrationTest} und {@code PdfImportErrorDispatchIntegrationTest}).
 *       Ein Handler antwortet direkt und ist dort prüfbar, wo der Endpoint getestet wird.
 * </ul>
 *
 * <p>Die restlichen Fälle brauchen keinen Handler: {@link DuplicatePdfImportException} (409) und
 * {@link PdfImportTimeoutException} (408) tragen ihr Status-Mapping bereits als
 * {@code @ResponseStatus}, ein fehlender {@code file}-Part liefert Spring als 400.
 *
 * <p>Alle drei 400er tragen einen {@link ImportErrorResponse}-Body mit maschinenlesbarem
 * {@code reason}: Passwort-, Scan- und Format-Fehler teilen sich den Status und wären sonst für
 * das Frontend nicht unterscheidbar, das daraus getrennte Nutzermeldungen formuliert (FE-PDF-02).
 * 408/409/413 tragen keinen {@code reason} — dort ist der Status allein eindeutig. Body-los sind
 * sie deshalb aber nicht alle: 408/409 laufen via {@code @ResponseStatus} über Springs
 * ERROR-Dispatch und antworten mit dem Standard-Fehlerbody ({@code timestamp}/{@code status}/…);
 * nur 413 aus dem {@code void}-Handler unten bleibt tatsächlich ohne Body. Der Client-Contract
 * hängt allein am Status plus — bei 400 — am {@code reason}.
 */
@RestControllerAdvice(assignableTypes = PdfImportController.class)
public class PdfImportExceptionHandler {

    @ExceptionHandler(MissingTextLayerException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ImportErrorResponse handleMissingTextLayer(MissingTextLayerException ex) {
        // 400 für ein gescanntes PDF ohne Text-Layer — eigener reason statt UNSUPPORTED_FORMAT.
        return new ImportErrorResponse(ImportErrorResponse.Reason.MISSING_TEXT_LAYER);
    }

    @ExceptionHandler(PdfParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ImportErrorResponse handlePdfParse(PdfParseException ex) {
        // 400 für ein nicht lesbares/unbekanntes PDF-Format (verbleibende Subtypen).
        return new ImportErrorResponse(ImportErrorResponse.Reason.UNSUPPORTED_FORMAT);
    }

    @ExceptionHandler(PasswordProtectedPdfException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ImportErrorResponse handlePasswordProtected(PasswordProtectedPdfException ex) {
        // 400 für ein verschlüsseltes PDF.
        return new ImportErrorResponse(ImportErrorResponse.Reason.PASSWORD_PROTECTED);
    }

    @ExceptionHandler(ImportJobNotCompleteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ImportNotCompleteResponse handleJobNotComplete(ImportJobNotCompleteException ex) {
        // 409 statt einer leeren Liste: Der Job existiert und gehört dem User, seine Buchungen
        // stehen nur noch nicht (RUNNING) oder werden nie stehen (FAILED). Der Status im Body
        // sagt dem Client, welches von beidem — und damit, ob sich ein zweiter Versuch lohnt.
        return new ImportNotCompleteResponse(ex.getStatus());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public void handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // Kein Body: 413, sobald das serverseitige 10-MB-Multipart-Limit überschritten wird.
    }
}
