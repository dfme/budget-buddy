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
 * @param field Name des verletzten Feldes: {@code "bezeichnung"}, {@code "betrag"},
 *     {@code "intervall"} — oder {@code "request"}, wenn gar kein Body ankam.
 * @param message Beschreibung der verletzten Regel. Wiederholt die Eingabe nicht: der Wert ginge
 *     sonst unverändert in die Antwort zurück, und das ist der kurze Weg zu Reflected-XSS im
 *     Client.
 */
public record FixedCostErrorResponse(String field, String message) {}
