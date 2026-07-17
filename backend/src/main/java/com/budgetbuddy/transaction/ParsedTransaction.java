package com.budgetbuddy.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Eine aus einem Bank-PDF extrahierte Transaktionszeile (BE-PDF-01).
 *
 * <p>Bewusst entkoppelt von der JPA-{@code Transaction}-Entity (BE-PDF-02): der Parser kennt keine
 * Persistenz und keinen User. Das Mapping auf die Entity erfolgt im PdfImportService.
 *
 * @param buchungsdatum Buchungsdatum der Transaktion.
 * @param buchungstext Beschreibungstext (Händler/Empfänger), ggf. aus mehreren PDF-Zeilen
 *     zusammengesetzt.
 * @param betrag Betrag als positiver {@link BigDecimal} (Magnitude, Skala 2) — niemals
 *     {@code double}/{@code float} (ADR-9). Die Richtung steht in {@code isIncome}.
 * @param isIncome {@code true} für Gutschriften (Einkommen), {@code false} für Belastungen.
 */
public record ParsedTransaction(
    LocalDate buchungsdatum, String buchungstext, BigDecimal betrag, boolean isIncome) {}
