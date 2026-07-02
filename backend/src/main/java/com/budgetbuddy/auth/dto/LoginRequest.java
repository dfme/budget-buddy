package com.budgetbuddy.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request-Body für {@code POST /auth/login} (BE-AUTH-03).
 *
 * <p>Bewusst nur {@link NotBlank} (keine Format-/Längenprüfung): fehlerhafte Credentials führen zu
 * 401, nicht zu 400 — sonst liesse sich aus dem Status die Existenz einer E-Mail ableiten.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
