package com.budgetbuddy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request-Body für {@code POST /auth/register} (BE-AUTH-03).
 *
 * <p>{@code email} muss eine syntaktisch gültige, nicht leere Adresse sein; {@code password}
 * mindestens 8 Zeichen und höchstens 72 UTF-8-Bytes — die bcrypt-Grenze (BE-AUTH-10, #200). Das
 * Passwort wird ausschliesslich als bcrypt-Hash gespeichert (ADR-7).
 *
 * <p>{@code firstName}/{@code lastName} sind bewusst optional (BE-AUTH-05, #114) — ein
 * Pflichtfeld würde die Registrierungshürde erhöhen (Churn-Risiko #1). Der {@code AuthService}
 * normalisiert Blank-Strings vor dem Speichern zu {@code null}.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank
                @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein.")
                @MaxBcryptBytes
                String password,
        String firstName,
        String lastName) {
}
