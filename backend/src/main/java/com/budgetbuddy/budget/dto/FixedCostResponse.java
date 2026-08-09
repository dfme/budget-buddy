package com.budgetbuddy.budget.dto;

import java.math.BigDecimal;

/**
 * Eine Fixkosten-Position in der API-Antwort (BE-FC-02, US-03).
 *
 * <p>{@code betrag} und {@code monatsbetrag} haben beide garantiert Skala 2 — JSON zeigt also
 * {@code 335.00} und nicht {@code 335}. Seit DB-05 (ADR-12) liefert {@code numeric(10,2)} diese
 * Skala bereits selbst; unter SQLite war {@code DECIMAL} nur eine Affinität und der Wert kam mit
 * Skala 0 oder 1 zurück (#141). Die Zusage steht trotzdem hier und nicht in der Persistenzschicht:
 * so hängt sie am API-Contract und nicht daran, welche Datenbank gerade darunter liegt.
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
