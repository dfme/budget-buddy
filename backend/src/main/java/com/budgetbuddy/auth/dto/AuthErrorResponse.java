package com.budgetbuddy.auth.dto;

/**
 * Body der 400-Antworten von {@code AuthController} und {@code UserController} (BE-AUTH-09).
 *
 * <p>Deckt sowohl das falsche {@code aktuellesPasswort} bei der Passwort-Änderung als auch die
 * Bean-Validation-Fehler von {@code RegisterRequest}, {@code LoginRequest},
 * {@code UpdateIncomeRequest} und {@code ChangePasswordRequest} ab — alle laufen durch denselben
 * {@code UserExceptionHandler} und tragen deshalb denselben Body.
 *
 * <p>{@code message} beschreibt die verletzte Regel und wiederholt nie eine Eingabe des Users, um
 * keinen Reflected-XSS-Pfad zu öffnen (gleiche Regel wie bei {@code FixedCostErrorResponse}).
 *
 * @param message feste, nicht-sensible Fehlermeldung.
 */
public record AuthErrorResponse(String message) {
}
