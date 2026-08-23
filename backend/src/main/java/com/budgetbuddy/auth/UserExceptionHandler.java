package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.PasswordErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet auth-spezifische Exceptions auf HTTP-Status ab.
 *
 * <p>Validierungsfehler ({@code MethodArgumentNotValidException}) liefert Spring Boot bereits ohne
 * Zutun als 400. Hier werden die domänenspezifischen Fälle abgebildet: fehlender User → 404,
 * doppelte E-Mail bei Registrierung → 409, ungültige Anmeldedaten → 401, falsches aktuelles
 * Passwort bei der Passwort-Änderung → 400 (BE-AUTH-09).
 *
 * <p>Auf {@link AuthController} und {@link UserController} beschränkt statt global (analog
 * {@code FixedCostExceptionHandler}): seit {@link InvalidCurrentPasswordException} einen 400-Body
 * liefert, würde ein unscoped {@code @RestControllerAdvice} Springdoc dazu bringen, dieses Schema
 * auch als 400-Antwort <em>anderer</em> Controller (z. B. {@code FixedCostController}) zu
 * dokumentieren — beobachtet in {@code FixedCostOpenApiTest}.
 */
@RestControllerAdvice(assignableTypes = {AuthController.class, UserController.class})
public class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleUserNotFound(UserNotFoundException ex) {
        // Kein Body: 404 genügt; keine Detail-Auskunft über existierende User-IDs.
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleEmailExists(EmailAlreadyExistsException ex) {
        // Kein Body: 409 genügt für die Duplikat-E-Mail bei der Registrierung.
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidCredentials(InvalidCredentialsException ex) {
        // Kein Body: 401 ohne Auskunft, ob die E-Mail existiert (User-Enumeration-Schutz).
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PasswordErrorResponse handleInvalidCurrentPassword(InvalidCurrentPasswordException ex) {
        return new PasswordErrorResponse(ex.getMessage());
    }
}
