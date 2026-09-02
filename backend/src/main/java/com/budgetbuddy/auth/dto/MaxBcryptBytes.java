package com.budgetbuddy.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Begrenzt ein Passwort-Feld auf {@link MaxBcryptBytesValidator#MAX_BYTES} UTF-8-Bytes — die
 * harte Grenze von {@code BCryptPasswordEncoder.encode()} (Spring Security 6.3+, verifiziert
 * gegen die im Projekt genutzte 6.5.1). Ohne diese Prüfung wirft {@code encode()} eine
 * ungefangene {@code IllegalArgumentException}, die zu einem 500 statt einem 400 führt
 * (BE-AUTH-10, #200).
 *
 * <p>Prüft die Byte-Länge, nicht {@code String#length()}: ein {@code @Size(max = 72)} allein
 * liesse ein 72 Zeichen langes Passwort aus Umlauten oder Emoji durch, das in UTF-8 weit über
 * 72 Bytes liegt.
 *
 * <p>Eine Stelle für beide Endpoints, die ein neues Passwort entgegennehmen —
 * {@link RegisterRequest#password()} und {@link ChangePasswordRequest#neuesPasswort()}.
 */
@Documented
@Constraint(validatedBy = MaxBcryptBytesValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBcryptBytes {

    String message() default "Passwort ist zu lang (maximal 72 Bytes).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
