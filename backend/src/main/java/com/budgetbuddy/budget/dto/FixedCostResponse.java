package com.budgetbuddy.budget.dto;

import java.math.BigDecimal;

/**
 * Eine Fixkosten-Position in der API-Antwort (BE-FC-02, US-03).
 *
 * <p>{@code betrag} und {@code monatsbetrag} haben beide garantiert Skala 2. Das ist nicht
 * selbstverständlich: SQLite behandelt {@code DECIMAL(10,2)} als Affinität, gelesene Werte kommen
 * mit Skala 0 oder 1 zurück (#141). Ohne die Normalisierung hier lieferte JSON {@code 335} statt
 * {@code 335.00}. Der Fix gehört genau an diese Grenze — nicht in die Persistenzschicht.
 *
 * @param id ID der Position.
 * @param bezeichnung Anzeigename, z. B. {@code "Miete"}.
 * @param betrag Betrag in CHF <em>pro Intervall</em>, Skala 2.
 * @param intervall Intervall-Label, z. B. {@code "quartalsweise"}.
 * @param monatsbetrag auf einen Monat normalisierter Betrag in CHF, Skala 2 — {@code betrag} ÷ 1,
 *     ÷ 3 bzw. ÷ 12 je nach Intervall. Dies ist der Wert, der in die Safe-to-Spend-Rechnung
 *     eingeht (US-03).
 */
public record FixedCostResponse(
        Long id,
        String bezeichnung,
        BigDecimal betrag,
        String intervall,
        BigDecimal monatsbetrag) {}
