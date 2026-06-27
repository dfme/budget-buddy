package com.budgetbuddy.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet auth-spezifische Exceptions auf HTTP-Status ab.
 *
 * <p>Validierungsfehler ({@code MethodArgumentNotValidException}) liefert Spring Boot bereits ohne
 * Zutun als 400; hier wird nur der domänenspezifische {@link UserNotFoundException}-Fall auf 404
 * abgebildet.
 */
@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleUserNotFound(UserNotFoundException ex) {
        // Kein Body: 404 genügt; keine Detail-Auskunft über existierende User-IDs.
    }
}
