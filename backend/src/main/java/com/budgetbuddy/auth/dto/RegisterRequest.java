package com.budgetbuddy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request-Body für {@code POST /auth/register} (BE-AUTH-03).
 *
 * <p>{@code email} muss eine syntaktisch gültige, nicht leere Adresse sein, höchstens
 * {@link AuthFieldLimits#EMAIL_MAX_LENGTH} Zeichen (RFC 5321); {@code password} mindestens 8
 * Zeichen und höchstens 72 UTF-8-Bytes — die bcrypt-Grenze (BE-AUTH-10, #200). Das Passwort wird
 * ausschliesslich als bcrypt-Hash gespeichert (ADR-7).
 *
 * <p>{@code firstName}/{@code lastName} sind bewusst optional (BE-AUTH-05, #114) — ein
 * Pflichtfeld würde die Registrierungshürde erhöhen (Churn-Risiko #1). Der {@code AuthService}
 * normalisiert Blank-Strings vor dem Speichern zu {@code null}. Beide höchstens
 * {@link AuthFieldLimits#NAME_MAX_LENGTH} Zeichen (BE-AUTH-12, #231) — ohne Grenze konnte
 * {@code POST /auth/register} (unauthentifiziert erreichbar) beliebig grosse Strings unverändert
 * in {@code users} schreiben.
 */
public record RegisterRequest(
        @NotBlank
                @Email
                @Size(max = AuthFieldLimits.EMAIL_MAX_LENGTH,
                        message = "E-Mail darf höchstens " + AuthFieldLimits.EMAIL_MAX_LENGTH
                                + " Zeichen lang sein.")
                String email,
        @NotBlank
                @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein.")
                @MaxBcryptBytes
                String password,
        @Size(max = AuthFieldLimits.NAME_MAX_LENGTH,
                message = "Vorname darf höchstens " + AuthFieldLimits.NAME_MAX_LENGTH
                        + " Zeichen lang sein.")
                String firstName,
        @Size(max = AuthFieldLimits.NAME_MAX_LENGTH,
                message = "Nachname darf höchstens " + AuthFieldLimits.NAME_MAX_LENGTH
                        + " Zeichen lang sein.")
                String lastName) {
}
