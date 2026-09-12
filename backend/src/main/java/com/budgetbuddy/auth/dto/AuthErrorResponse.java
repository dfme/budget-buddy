package com.budgetbuddy.auth.dto;

/**
 * Body der 400-Antworten von {@code AuthController} und {@code UserController} (BE-AUTH-09).
 *
 * <p>Deckt sowohl das falsche Passwort bei der Passwort-Änderung und der Kontolöschung als auch
 * die Bean-Validation-Fehler von {@code RegisterRequest}, {@code LoginRequest},
 * {@code UpdateIncomeRequest}, {@code ChangePasswordRequest} und {@code DeleteAccountRequest} ab —
 * alle laufen durch denselben {@code UserExceptionHandler} und tragen deshalb denselben Body.
 *
 * <p>{@code message} beschreibt die verletzte Regel und wiederholt nie eine Eingabe des Users, um
 * keinen Reflected-XSS-Pfad zu öffnen (gleiche Regel wie bei {@code FixedCostErrorResponse}).
 *
 * @param message feste, nicht-sensible Fehlermeldung.
 */
public record AuthErrorResponse(String message) {
}
