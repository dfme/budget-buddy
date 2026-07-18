package com.budgetbuddy.transaction;

/**
 * Ergebnis eines PDF-Imports (BE-PDF-02).
 *
 * <p>Bewusst schlank: Die HTTP-Antwortform (BE-PDF-03, FE-PDF-02) entsteht erst am Endpoint;
 * hier steht nur, was der Service selbst weiss.
 *
 * @param pdfSha256 SHA-256 (Hex) des importierten PDFs — der Duplikat-Schlüssel.
 * @param transactionCount Anzahl importierter (und kategorisierter) Transaktionen.
 */
public record ImportResult(String pdfSha256, int transactionCount) {}
