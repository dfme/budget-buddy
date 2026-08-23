package com.budgetbuddy.auth.dto;

/**
 * Body der 400-Antwort von {@code PUT /users/me/password}, wenn {@code aktuellesPasswort} nicht
 * stimmt (BE-AUTH-09).
 *
 * <p>{@code message} ist immer die feste Meldung „Aktuelles Passwort falsch" — nie eine
 * Eingabe des Users, um keinen Reflected-XSS-Pfad zu öffnen (gleiche Regel wie bei
 * {@code FixedCostErrorResponse}).
 *
 * @param message feste, nicht-sensible Fehlermeldung.
 */
public record PasswordErrorResponse(String message) {
}
