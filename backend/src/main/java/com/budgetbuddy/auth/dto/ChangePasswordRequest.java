package com.budgetbuddy.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request-Body für {@code PUT /users/me/password} (BE-AUTH-09).
 *
 * <p>{@code aktuellesPasswort} nur {@link NotBlank} (keine Längenprüfung): ob es stimmt, prüft der
 * {@link com.budgetbuddy.auth.UserService} über den {@code PasswordEncoder} — dieselbe Regel wie
 * bei {@link LoginRequest}. {@code neuesPasswort} verlangt dieselbe Mindestlänge und dieselbe
 * bcrypt-Bytegrenze wie bei der Registrierung ({@link RegisterRequest}, BE-AUTH-10, #200).
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Aktuelles Passwort ist erforderlich.") String aktuellesPasswort,
        @NotBlank
                @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein.")
                @MaxBcryptBytes
                String neuesPasswort) {
}
