package com.budgetbuddy.recurring;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet die Exceptions des {@link RecurringExpenseController} auf HTTP-Status ab (BE-REC-02).
 *
 * <p>Auf den Controller beschränkt statt global — gleiche Aufteilung wie beim
 * {@code NotificationExceptionHandler}: {@link RecurringExpenseNotFoundException} ist ein
 * recurring-eigener Typ, und ein globales Advice würde die Zuständigkeit über Modulgrenzen ziehen.
 */
@RestControllerAdvice(assignableTypes = RecurringExpenseController.class)
public class RecurringExpenseExceptionHandler {

    @ExceptionHandler(RecurringExpenseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleRecurringExpenseNotFound(RecurringExpenseNotFoundException ex) {
        // Kein Body: 404 ohne Auskunft, ob die ID existiert oder einem anderen User gehört.
    }
}
