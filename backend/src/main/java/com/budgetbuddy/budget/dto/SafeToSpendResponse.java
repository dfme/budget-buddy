package com.budgetbuddy.budget.dto;

import java.math.BigDecimal;

/**
 * Ergebnis der Safe-to-Spend-Berechnung eines Users (BE-STS-01, US-06).
 *
 * <p>BE-STS-03 serviert dieses Record als Antwort von {@code GET /budget/safe-to-spend} und ergänzt
 * dabei das dort geforderte fünfte Feld {@code incomeSuggestion} aus der Einkommens-Heuristik
 * (BE-STS-02). Solange die Heuristik nicht existiert, wäre das Feld hier immer {@code null} — ein
 * Feld ohne Wert dokumentiert nichts.
 *
 * @param amount wöchentlich verfügbarer Betrag in CHF, Skala 2 ({@link BigDecimal}, ADR-9). Kann
 *     negativ sein — dann ist {@code isNegative} gesetzt. {@code null} genau dann, wenn
 *     {@code noIncome} gilt: US-06 verlangt in diesem Fall ausdrücklich, dass keine Division
 *     ausgeführt wird. {@code null} ist damit für den Client von «Budget aufgebraucht»
 *     ({@code 0.00}) unterscheidbar.
 * @param weeksLeft verbleibende Wochen im laufenden Monat, immer mindestens {@code 1}. Auch ohne
 *     erfasstes Einkommen gesetzt — der Wert hängt allein vom Datum ab.
 * @param isNegative {@code true}, wenn {@code amount < 0}: der User hat sein Monatsbudget bereits
 *     überzogen und US-06 verlangt das Warn-Banner. Bei exakt {@code 0.00} und ohne Einkommen
 *     {@code false}.
 * @param noIncome {@code true}, wenn der User kein Monatseinkommen erfasst hat
 *     ({@code users.monthly_income IS NULL}). Der Client zeigt dann den Hinweis «Bitte erfasse dein
 *     Monatseinkommen in den Einstellungen» statt eines Betrags.
 */
public record SafeToSpendResponse(
        BigDecimal amount,
        int weeksLeft,
        boolean isNegative,
        boolean noIncome) {}
