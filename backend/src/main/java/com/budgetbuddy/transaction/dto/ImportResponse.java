package com.budgetbuddy.transaction.dto;

import com.budgetbuddy.transaction.ImportResult;

/**
 * Antwort-DTO des PDF-Upload-Endpoints {@code POST /import/pdf} (BE-PDF-03, US-04).
 *
 * <p>Bewusst nur die Anzahl importierter Transaktionen — der SHA-256 aus {@link ImportResult}
 * ist ein interner Duplikat-Schlüssel und wird nicht nach aussen gegeben.
 *
 * @param count Anzahl importierter (und kategorisierter) Transaktionen.
 */
public record ImportResponse(int count) {

    public static ImportResponse from(ImportResult result) {
        return new ImportResponse(result.transactionCount());
    }
}
