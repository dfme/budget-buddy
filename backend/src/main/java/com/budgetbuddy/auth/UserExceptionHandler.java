package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.PasswordErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet auth-spezifische Exceptions auf HTTP-Status ab.
 *
 * <p>Hier werden die domänenspezifischen Fälle abgebildet: fehlender User → 404, doppelte E-Mail
 * bei Registrierung → 409, ungültige Anmeldedaten → 401, falsches aktuelles Passwort bei der
 * Passwort-Änderung → 400 (BE-AUTH-09).
 *
 * <p>Auf {@link AuthController} und {@link UserController} beschränkt statt global (analog
 * {@code FixedCostExceptionHandler}): seit {@link InvalidCurrentPasswordException} einen 400-Body
 * liefert, würde ein unscoped {@code @RestControllerAdvice} Springdoc dazu bringen, dieses Schema
 * auch als 400-Antwort <em>anderer</em> Controller (z. B. {@code FixedCostController}) zu
 * dokumentieren — beobachtet in {@code FixedCostOpenApiTest}.
 *
 * <p><strong>Alle 400er dieser beiden Controller tragen deshalb denselben Body.</strong> Das
 * Scoping allein löst nur die Hälfte des Problems: Springdoc dokumentiert
 * {@link PasswordErrorResponse} jetzt als 400-Schema <em>jedes</em> Endpoints von
 * {@link AuthController} und {@link UserController} — auch derer, die bislang leer antworteten
 * ({@code POST /auth/register}, {@code PUT /users/me/income}, ein zu kurzes
 * {@code neuesPasswort}). Ohne die beiden Handler unten stimmten Dokument und Verhalten dort nicht
 * überein (gemessen an {@code /v3/api-docs} vs. tatsächlichem Response-Body). Deshalb fängt dieses
 * Advice — analog zum {@code HttpMessageNotReadableException}-Handler in
 * {@code FixedCostExceptionHandler} — jetzt auch Bean-Validation-Fehler und kaputte
 * Request-Bodys ab, die sonst Spring Boots Default-Fehlerbody lieferten.
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

    /**
     * Bean-Validation-Fehler (z. B. {@code @Email}, {@code @Size}, {@code @NotBlank},
     * {@code @Positive}) auf {@code @Valid}-Request-Bodys dieser beiden Controller.
     *
     * <p>Meldet die erste verletzte Regel — dieselbe Vereinfachung wie bei
     * {@code FixedCostErrorResponse}, wo ein Feld genügt, weil der Client ohnehin nur die erste
     * Meldung anzeigt. Die Meldung stammt aus der {@code message}-Angabe der Annotation (z. B.
     * „Passwort muss mindestens 8 Zeichen lang sein.") oder Bean Validations Default-Text; sie
     * wiederholt nie den eingegebenen Wert.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PasswordErrorResponse handleValidationError(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Ungültige Eingabe.";
        return new PasswordErrorResponse(message);
    }

    /**
     * Fehlender Body, ungültiges JSON oder ein Typfehler in einem Feld — alles, was Jackson beim
     * Lesen des Request-Bodys abbricht, bevor der Controller läuft (analog
     * {@code FixedCostExceptionHandler.handleUnreadableBody}).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PasswordErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        return new PasswordErrorResponse("Der Request-Body fehlt oder ist kein gültiges JSON.");
    }
}
