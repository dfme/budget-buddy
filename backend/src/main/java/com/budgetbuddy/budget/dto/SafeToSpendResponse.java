package com.budgetbuddy.budget.dto;

import java.math.BigDecimal;

/**
 * Ergebnis der Safe-to-Spend-Berechnung eines Users (BE-STS-01, US-06).
 *
 * <p>BE-STS-03 serviert dieses Record unverändert als Antwort von {@code GET /budget/safe-to-spend};
 * alle fünf dort geforderten Felder stehen bereits hier. {@code incomeSuggestion} kam mit der
 * Einkommens-Heuristik (BE-STS-02) dazu, weil deren AC verlangt, dass die Heuristik bei jedem
 * Safe-to-Spend-Aufruf läuft — das Feld ohne den Aufruf zu haben, hiesse es immer {@code null} zu
 * lassen.
 *
 * <p>Die Feldnamen sind zugleich das Wire-Format: Jackson serialisiert Record-Komponenten unter
 * ihrem Namen. Booleans stehen deshalb ohne {@code is}-Präfix, wie
 * {@link FixedCostSummaryResponse#exceedsIncome()} und {@code noIncome} — sonst stünden im selben
 * Objekt zwei Konventionen nebeneinander.
 *
 * @param amount wöchentlich verfügbarer Betrag in CHF, Skala 2 ({@link BigDecimal}, ADR-9). Kann
 *     negativ sein — dann ist {@code negative} gesetzt. {@code null} genau dann, wenn
 *     {@code noIncome} gilt: US-06 verlangt in diesem Fall ausdrücklich, dass keine Division
 *     ausgeführt wird. {@code null} ist damit für den Client von «Budget aufgebraucht»
 *     ({@code 0.00}) unterscheidbar.
 * @param weeksLeft verbleibende Wochen im laufenden Monat, immer mindestens {@code 1}. Auch ohne
 *     erfasstes Einkommen gesetzt — der Wert hängt allein vom Datum ab.
 * @param negative {@code true}, wenn {@code amount < 0}: der User hat sein Monatsbudget bereits
 *     überzogen und US-06 verlangt das Warn-Banner. Bei exakt {@code 0.00} und ohne Einkommen
 *     {@code false}.
 * @param noIncome {@code true}, wenn der User kein Monatseinkommen erfasst hat
 *     ({@code users.monthly_income IS NULL}). Der Client zeigt dann den Hinweis «Bitte erfasse dein
 *     Monatseinkommen in den Einstellungen» statt eines Betrags.
 * @param incomeSuggestion aus den Gutschriften abgeleiteter Einkommens-Vorschlag in CHF, Skala 2
 *     (BE-STS-02) — der Betrag für den Hinweis «Regelmässige Gutschrift von X CHF erkannt». Nur
 *     gesetzt, wenn {@code noIncome} gilt <em>und</em> sich ein wiederkehrendes Muster finden liess;
 *     sonst {@code null}. Bei erfasstem Einkommen immer {@code null}: US-06 lässt die manuelle
 *     Eingabe die Schätzung überschreiben, ein Vorschlag daneben wäre nur verwirrend.
 */
public record SafeToSpendResponse(
        BigDecimal amount,
        int weeksLeft,
        boolean negative,
        boolean noIncome,
        BigDecimal incomeSuggestion) {}
