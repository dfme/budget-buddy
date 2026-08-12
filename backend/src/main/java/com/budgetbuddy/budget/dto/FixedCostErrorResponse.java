package com.budgetbuddy.budget.dto;

/**
 * Body einer 400-Antwort der Fixkosten-Endpoints (BE-FC-03, US-03).
 *
 * <p>US-03 verlangt eine <em>feldspezifische</em> Fehlermeldung — der Wizard markiert genau die
 * Zeile, die der User korrigieren muss. Drei Pflichtfelder teilen sich denselben Statuscode; ohne
 * {@code field} wären sie für den Client nicht unterscheidbar. Gleiche Bauart und gleicher Grund
 * wie bei {@code ImportErrorResponse} im PDF-Import, wo sich Passwort- und Formatfehler den
 * Status 400 teilen.
 *
 * <p>404 trägt bewusst <em>keinen</em> Body: dort ist der Status eindeutig, und jede Zusatzauskunft
 * verriete, ob eine fremde ID existiert.
 *
 * <p><strong>Jeder 400er dieses Controllers trägt diesen Body</strong> — auch die, die Jackson
 * auslöst, bevor der Controller läuft (fehlender Body, kaputtes JSON, Typfehler). Der
 * {@code FixedCostExceptionHandler} fängt sie eigens ab; ohne ihn sagte das OpenAPI-Dokument den
 * Body zu, den es in diesen Fällen nicht gäbe.
 *
 * @param field Name des verletzten Feldes: {@code "bezeichnung"}, {@code "betrag"},
 *     {@code "intervall"} — oder {@code "request"}, wenn sich der Fehler keinem einzelnen Feld
 *     zuordnen lässt (kein Body, abgeschnittenes JSON).
 * @param message Beschreibung der verletzten Regel. Wiederholt die Eingabe nicht: der Wert ginge
 *     sonst unverändert in die Antwort zurück, und das ist der kurze Weg zu Reflected-XSS im
 *     Client.
 */
public record FixedCostErrorResponse(String field, String message) {}
