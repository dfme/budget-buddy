package com.budgetbuddy.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Reiner Unit-Test für {@link MaxBcryptBytesValidator} (kein Spring-Kontext, kein Bean-Validation-
 * Bootstrap) — deckt die Grenze in beide Richtungen und die Byte- statt Zeichen-Zählung direkt ab,
 * ohne über einen Controller-Roundtrip zu gehen (BE-AUTH-10, #200, Review-Befund an PR #262).
 */
class MaxBcryptBytesValidatorTest {

    private final MaxBcryptBytesValidator validator = new MaxBcryptBytesValidator();

    @Test
    void nullIsValid() {
        // Ablehnung eines fehlenden Werts ist Aufgabe von @NotBlank auf demselben Feld.
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void exactly72AsciiBytesIsValid() {
        assertThat(validator.isValid("a".repeat(72), null)).isTrue();
    }

    @Test
    void moreThan72AsciiBytesIsInvalid() {
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
    }

    @Test
    void countsUtf8BytesNotJavaCharLength() {
        // 40 Umlaute: 40 char, aber 80 UTF-8-Bytes. Eine zeichenbasierte Prüfung (z. B.
        // @Size(max = 72)) würde das durchlassen — dieser Test unterscheidet die beiden Ansätze.
        assertThat(validator.isValid("ä".repeat(40), null)).isFalse();
    }
}
