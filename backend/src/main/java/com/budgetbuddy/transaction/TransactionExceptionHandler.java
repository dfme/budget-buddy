package com.budgetbuddy.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet transaction-spezifische Exceptions auf HTTP-Status ab (analog {@code UserExceptionHandler}).
 *
 * <p>Ein fehlender {@code month}-Parameter bzw. ein leeres {@code category}-Feld liefert Spring Boot
 * bereits als 400 ({@code MissingServletRequestParameterException} bzw.
 * {@code MethodArgumentNotValidException}); hier werden zusätzlich die domänenspezifischen Fälle
 * abgebildet: ungültiger {@code month}/{@code category}-Wert → 400, unbekannte Transaktion → 404.
 */
@RestControllerAdvice(assignableTypes = {
    TransactionSummaryController.class,
    TransactionCategoryController.class
})
public class TransactionExceptionHandler {

    @ExceptionHandler(InvalidMonthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidMonth(InvalidMonthException ex) {
        // Kein Body: 400 genügt für einen fehlerhaften month-Parameter.
    }

    @ExceptionHandler(InvalidCategoryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidCategory(InvalidCategoryException ex) {
        // Kein Body: 400 genügt für ein ungültiges Kategorie-Label.
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleTransactionNotFound(TransactionNotFoundException ex) {
        // Kein Body: 404 ohne Auskunft, ob die Transaktions-ID existiert (Enumeration-Schutz).
    }
}
