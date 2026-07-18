package com.budgetbuddy.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das hochgeladene PDF wurde für diesen User bereits importiert (gleicher SHA-256, BE-PDF-02).
 *
 * <p>{@code @ResponseStatus(CONFLICT)} statt eines eigenen {@code @RestControllerAdvice}: Der
 * Import-Endpoint entsteht erst mit BE-PDF-03 (#18) — so erbt er das 409-Mapping, ohne dass hier
 * ein Advice ohne Controller herumliegt.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicatePdfImportException extends RuntimeException {

    public DuplicatePdfImportException(String pdfSha256) {
        super("PDF wurde bereits importiert (SHA-256: " + pdfSha256 + ")");
    }
}
