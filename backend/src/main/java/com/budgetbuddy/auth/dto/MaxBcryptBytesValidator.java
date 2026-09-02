package com.budgetbuddy.auth.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/**
 * Prüft {@link MaxBcryptBytes} gegen die UTF-8-Byte-Länge des Werts.
 *
 * <p>{@code null} gilt als gültig — das abzulehnen ist Aufgabe von {@code @NotBlank} auf
 * demselben Feld, dieselbe Komposition wie bei den Standard-Bean-Validation-Constraints.
 */
public class MaxBcryptBytesValidator implements ConstraintValidator<MaxBcryptBytes, String> {

    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
