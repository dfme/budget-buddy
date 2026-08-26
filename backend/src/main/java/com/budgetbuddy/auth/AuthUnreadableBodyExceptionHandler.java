package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.AuthErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fängt kaputte Request-Bodys von {@link AuthController} ab: fehlender Body, ungültiges JSON oder
 * ein Typfehler in einem Feld — alles, was Jackson beim Lesen abbricht, bevor der Controller läuft
 * (BE-AUTH-09).
 *
 * <p>Eigene, auf {@link AuthController} beschränkte Advice-Klasse statt eines weiteren Handlers in
 * {@link UserExceptionHandler}: {@link UserController} hat mit {@code UserIncomeExceptionHandler}
 * bereits einen {@link HttpMessageNotReadableException}-Handler, der zusätzlich den Feldnamen aus
 * dem Jackson-Pfad liest ({@link com.budgetbuddy.auth.dto.IncomeErrorResponse}). Zwei
 * {@code @ExceptionHandler}-Methoden für denselben Exception-Typ auf demselben Controller lösen
 * Spring nicht deterministisch auf — deshalb bleibt {@link UserController} aussen vor und
 * {@code UserIncomeExceptionHandler} bleibt allein zuständig.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthUnreadableBodyExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuthErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        return new AuthErrorResponse("Der Request-Body fehlt oder ist kein gültiges JSON.");
    }
}
