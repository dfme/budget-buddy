package com.budgetbuddy.categorization;

import java.util.Optional;

/**
 * Port für die Transaktions-Kategorisierung (ADR-6, Hybrid-Ansatz).
 *
 * <p>Kapselt die Kategorisierungsquelle hinter einem Interface, damit sie in Tests mockbar und
 * ohne Refactoring austauschbar ist (Lookup-Tabelle → Claude-API → …). Der erste Schritt der
 * Hybrid-Kategorisierung ist {@link LookupTableService}.
 */
public interface CategorizationPort {

    /**
     * Ordnet einen Transaktionstext einer {@link Category} zu.
     *
     * @param transactionText Freitext der Transaktion (z. B. {@code "DIGITEC GALAXUS AG 044 913
     *     2323"}), typischerweise aus dem PDF-Import.
     * @return die erkannte Kategorie samt liefernder Stufe ({@link CategorizationResult.Source},
     *     BE-PDF-06: Basis für das Lookup-/Claude-Verhältnis im Import-Log), oder
     *     {@link Optional#empty()}, wenn diese Quelle den Text nicht zuordnen kann (der Aufrufer
     *     eskaliert dann an die nächste Stufe bzw. den Fallback {@code Sonstiges}).
     */
    Optional<CategorizationResult> categorize(String transactionText);
}
