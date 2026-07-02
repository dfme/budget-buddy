package com.budgetbuddy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request-Body für {@code POST /auth/register} (BE-AUTH-03).
 *
 * <p>{@code email} muss eine syntaktisch gültige, nicht leere Adresse sein; {@code password}
 * mindestens 8 Zeichen. Das Passwort wird ausschliesslich als bcrypt-Hash gespeichert (ADR-7).
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein.")
                String password) {
}
