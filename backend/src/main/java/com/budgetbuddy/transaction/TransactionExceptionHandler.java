package com.budgetbuddy.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet transaction-spezifische Exceptions auf HTTP-Status ab (analog {@code UserExceptionHandler}).
 *
 * <p>Ein fehlender {@code month}-Parameter liefert Spring Boot bereits als 400
 * ({@code MissingServletRequestParameterException}); hier wird zusätzlich ein syntaktisch
 * ungültiger Wert abgebildet.
 */
@RestControllerAdvice(assignableTypes = TransactionSummaryController.class)
public class TransactionExceptionHandler {

    @ExceptionHandler(InvalidMonthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidMonth(InvalidMonthException ex) {
        // Kein Body: 400 genügt für einen fehlerhaften month-Parameter.
    }
}
