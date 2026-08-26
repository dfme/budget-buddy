package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.AuthErrorResponse;
import org.springframework.http.HttpStatus;
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
 * <p><strong>Alle 400er dieser beiden Controller tragen deshalb denselben Body</strong> — mit einer
 * Ausnahme. Das Scoping allein löst nur die Hälfte des Problems: Springdoc dokumentiert
 * {@link AuthErrorResponse} sonst nur an den Endpoints, die tatsächlich {@code @Valid}-Bodys lesen
 * ({@code POST /auth/register}, {@code POST /auth/login}, {@code PUT /users/me/password}). Deshalb
 * fängt dieses Advice auch {@link MethodArgumentNotValidException} ab, die sonst Spring Boots
 * Default-Fehlerbody lieferte.
 *
 * <p><strong>{@code HttpMessageNotReadableException} (kaputtes JSON) gehört bewusst nicht hierher.</strong>
 * Für {@link UserController} deckt das bereits {@code UserIncomeExceptionHandler} ab — inklusive
 * Feldname aus dem Jackson-Pfad, was {@link AuthErrorResponse} nicht kann. Zwei
 * {@code @ExceptionHandler}-Methoden für denselben Exception-Typ auf demselben Controller sind ein
 * nicht deterministischer Konflikt zwischen zwei {@code @RestControllerAdvice}-Beans (aufgefallen
 * beim Merge von BE-AUTH-08 und BE-AUTH-09: {@code UserControllerTest
 * .updateIncomeWithAStringBetragReturns400WithTheSameBodyShape} verlor sein {@code $.field}, weil
 * dieses Advice zufällig gewonnen hätte). Für {@link AuthController} (kein konkurrierendes Advice)
 * übernimmt das eigene {@code AuthUnreadableBodyExceptionHandler}.
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
    public AuthErrorResponse handleInvalidCurrentPassword(InvalidCurrentPasswordException ex) {
        return new AuthErrorResponse(ex.getMessage());
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
    public AuthErrorResponse handleValidationError(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Ungültige Eingabe.";
        return new AuthErrorResponse(message);
    }
}
