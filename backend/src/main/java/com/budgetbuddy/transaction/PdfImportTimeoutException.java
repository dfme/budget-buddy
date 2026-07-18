package com.budgetbuddy.transaction;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Import-Flow hat das Zeitbudget überschritten (BE-PDF-02, AC "Timeout → 408").
 *
 * <p>Wird vor dem Persistieren geworfen — dank {@code @Transactional} im
 * {@link PdfImportService} bleibt die DB ohne Partial-Import. {@code @ResponseStatus} analog
 * {@link DuplicatePdfImportException}: der Endpoint (BE-PDF-03) erbt das 408-Mapping.
 */
@ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
public class PdfImportTimeoutException extends RuntimeException {

    public PdfImportTimeoutException(Duration timeout) {
        super("PDF-Import nach " + timeout.toSeconds() + "s abgebrochen");
    }
}
