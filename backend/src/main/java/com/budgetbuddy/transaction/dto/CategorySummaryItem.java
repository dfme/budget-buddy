package com.budgetbuddy.transaction.dto;

import java.math.BigDecimal;

/**
 * Ein Kategorie-Eintrag im Ausgaben-Summary (BE-CAT-05).
 *
 * @param category Kategorie-Label (deutsch, wie in {@link com.budgetbuddy.categorization.Category}).
 * @param amount Summe der Ausgaben dieser Kategorie in CHF ({@link BigDecimal}, Skala 2).
 * @param count Anzahl Transaktionen dieser Kategorie im Monat.
 * @param percentage Prozentanteil an den Gesamtausgaben ({@link BigDecimal}, Skala 2). Die Summe
 *     aller {@code percentage} über alle Einträge ergibt exakt 100.00 (Largest-Remainder-Rundung).
 */
public record CategorySummaryItem(
        String category, BigDecimal amount, int count, BigDecimal percentage) {}
