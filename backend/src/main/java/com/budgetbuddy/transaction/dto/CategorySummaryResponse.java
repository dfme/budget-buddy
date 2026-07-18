package com.budgetbuddy.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Antwort für {@code GET /transactions/summary?month=YYYY-MM} (BE-CAT-05).
 *
 * <p>Enthält die Ausgaben eines Monats aggregiert pro Kategorie. Es erscheinen nur Kategorien, die
 * im Monat tatsächlich Ausgaben haben; nicht kategorisierte Transaktionen ({@code category = null})
 * zählen als {@code Sonstiges}. Einkommen (Gutschriften) fliesst nicht ein.
 *
 * @param month der abgefragte Monat im Format {@code YYYY-MM}.
 * @param totalAmount Summe aller Ausgaben des Monats in CHF ({@link BigDecimal}, Skala 2).
 * @param totalCount Gesamtzahl der Ausgaben-Transaktionen im Monat.
 * @param categories Kategorie-Einträge, absteigend nach Betrag sortiert. Leer, wenn der Monat keine
 *     Ausgaben enthält.
 */
public record CategorySummaryResponse(
        String month, BigDecimal totalAmount, int totalCount, List<CategorySummaryItem> categories) {}
