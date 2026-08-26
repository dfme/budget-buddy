package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.IncomeErrorResponse;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet die 400er des {@link UserController} auf {@link IncomeErrorResponse} ab (BE-AUTH-08).
 *
 * <p><strong>Auf {@link UserController} beschränkt, nicht global</strong> — und das ist hier keine
 * Stilfrage. {@link HttpMessageNotReadableException} entsteht in <em>jedem</em> Controller, der
 * einen JSON-Body liest; ein globales Advice dafür überschriebe das Verhalten app-weit und nähme
 * dem PDF-Import und den Transaktions-Endpoints ihre eigene Fehlerabbildung. Dieselbe Aufteilung
 * wie beim {@code FixedCostExceptionHandler}.
 *
 * <p>Der {@link UserExceptionHandler} bleibt daneben auf {@link AuthController} und
 * {@link UserController} beschränkt: {@code UserNotFoundException}, {@code EmailAlreadyExistsException},
 * {@code InvalidCredentialsException}, {@code InvalidCurrentPasswordException} und
 * {@code MethodArgumentNotValidException} sind eigene Fälle, die mit diesem Advice nicht
 * kollidieren — {@link HttpMessageNotReadableException} bewusst ausgenommen (BE-AUTH-09): dafür ist
 * ausschliesslich dieses Advice zuständig, ein zweiter Handler auf demselben Controller wäre ein
 * nicht deterministischer Konflikt zwischen zwei {@code @RestControllerAdvice}-Beans.
 *
 * <p><strong>Warum der Jackson-Fall mit hierher gehört.</strong> Seit die
 * Bean-Validation-Annotationen aus {@code UpdateIncomeRequest} heraus sind, kommen alle fachlichen
 * 400er aus dem {@code UserService} und tragen denselben Body. Ohne den zweiten Handler unten
 * blieben genau die Fälle aussen vor, die Jackson abbricht, <em>bevor</em> der Controller läuft —
 * {@code {"betrag": "abc"}} oder ein Komma-Betrag {@code "12,50"}, wie ihn ein Schweizer Formular
 * liefert. Das OpenAPI-Dokument sagt an diesem Endpoint unbedingt {@link IncomeErrorResponse} zu;
 * ein 400 ohne diesen Body wäre eine Zusage, die der Endpoint nicht hält.
 */
@RestControllerAdvice(assignableTypes = UserController.class)
public class UserIncomeExceptionHandler {

    @ExceptionHandler(InvalidIncomeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public IncomeErrorResponse handleInvalidIncome(InvalidIncomeException ex) {
        // getMessage() stammt aus dem Service und nennt nur die verletzte Regel — die Eingabe des
        // Users wird dort bewusst nicht wiederholt (siehe InvalidIncomeException).
        return new IncomeErrorResponse(ex.getField(), ex.getMessage());
    }

    /**
     * Fehlender Body, ungültiges JSON oder ein Typfehler im Feld — alles, was Jackson beim Lesen
     * des Request-Bodys abbricht, bevor der Controller läuft.
     *
     * <p>Der Feldname kommt aus dem Jackson-Pfad, sofern es einen gibt: bei {@code betrag: "abc"}
     * antwortet der Endpoint mit {@code field: "betrag"} und damit genauso feldspezifisch wie die
     * fachliche Prüfung im Service.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public IncomeErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        String field = fieldOf(ex);
        return "request".equals(field)
                ? new IncomeErrorResponse(field, "Der Request-Body fehlt oder ist kein gültiges JSON.")
                : new IncomeErrorResponse(field, "Wert hat den falschen Typ.");
    }

    /**
     * Liest den betroffenen Feldnamen aus dem Jackson-Pfad, sonst {@code "request"}.
     *
     * <p>Bewusst nur der <em>Name</em>: {@code InvalidFormatException.getValue()} enthält die
     * Eingabe des Users, und die gehört nicht zurück in die Antwort (Reflected-XSS-Pfad, gleiche
     * Regel wie bei {@link InvalidIncomeException}).
     */
    private static String fieldOf(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            String name = mismatch.getPath().getFirst().getFieldName();
            if (name != null) {
                return name;
            }
        }
        return "request";
    }
}
