package com.budgetbuddy.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request-Body für {@code DELETE /users/me} (BE-AUTH-14, US-02).
 *
 * <p>{@code passwort} nur {@link NotBlank} (keine Längenprüfung): ob es stimmt, prüft der
 * {@link com.budgetbuddy.auth.UserService} über den {@code PasswordEncoder} — dieselbe Regel wie
 * bei {@link ChangePasswordRequest#aktuellesPasswort()} und {@link LoginRequest}. Die Bestätigung
 * steht im Body, nicht in einem Query-Parameter, damit sie nicht in Access-Logs oder der
 * Browser-History landet.
 */
public record DeleteAccountRequest(
        @NotBlank(message = "Passwort ist erforderlich.") String passwort) {
}
