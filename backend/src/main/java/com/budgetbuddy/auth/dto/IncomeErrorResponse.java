package com.budgetbuddy.auth.dto;

/**
 * Body einer 400-Antwort von {@code PUT /api/users/me/income} (BE-AUTH-08).
 *
 * <p>Vier Regeln teilen sich diesen Status — fehlender Betrag, {@code <= 0}, mehr als zwei
 * Nachkommastellen und die Kapazitätsgrenze von {@code numeric(10,2)}. Ohne {@code message} wären
 * sie für den Client nicht unterscheidbar, und genau die Unterscheidung ist der Zweck dieses Tasks:
 * Vorher quittierte der Endpoint {@code 4200.004} mit {@code 200 OK} und liess PostgreSQL still auf
 * {@code 4200.00} runden.
 *
 * <p>Gleiche Bauart und gleicher Grund wie {@code FixedCostErrorResponse} im budget-Modul und
 * {@code ImportErrorResponse} im PDF-Import. {@code field} ist heute immer {@code "betrag"} — der
 * Endpoint hat nur eines. Es steht trotzdem im Body: so ist die Form über die Endpoints hinweg
 * dieselbe, und mit US-14 (Einkommen in den Einstellungen) steht das Feld neben anderen.
 *
 * <p><strong>Jeder 400er dieses Endpoints trägt diesen Body</strong> — auch der, den Jackson
 * auslöst, bevor der Controller läuft ({@code {"betrag": "abc"}} oder ein Komma-Betrag
 * {@code "12,50"} aus einem Schweizer Formular). Das {@code UserIncomeExceptionHandler}-Advice fängt
 * das eigens ab; ohne es sagte das OpenAPI-Dokument einen Body zu, den es in diesen Fällen nicht
 * gäbe.
 *
 * @param field Name des verletzten Feldes: {@code "betrag"} — oder {@code "request"}, wenn sich der
 *     Fehler keinem Feld zuordnen lässt (kein Body, abgeschnittenes JSON).
 * @param message Beschreibung der verletzten Regel. Wiederholt die Eingabe nicht: der Wert ginge
 *     sonst unverändert in die Antwort zurück, und das ist der kurze Weg zu Reflected-XSS im
 *     Client.
 */
public record IncomeErrorResponse(String field, String message) {}
