package com.budgetbuddy.budget;

import com.budgetbuddy.transaction.InvalidMonthException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet die Exceptions des {@link BudgetController} auf HTTP-Status ab (BE-STS-06).
 *
 * <p><strong>Warum es diese Klasse braucht.</strong> Der {@code month}-Parameter wird über den
 * gemeinsamen {@code MonthParser} gelesen, der bei ungültigem Format eine
 * {@link InvalidMonthException} wirft. Deren Abbildung auf 400 hängt aber nicht am
 * Exception-Typ, sondern am Advice des aufrufenden Controllers: der
 * {@code TransactionExceptionHandler} ist über {@code assignableTypes} auf die vier
 * Transaktions-Controller begrenzt, {@link BudgetController} steht nicht darunter. Ohne diese
 * Klasse liefe ein ungültiger Monat hier in einen 500 — die Wiederverwendung des Parsers allein
 * erfüllt AC4 von #248 also nicht.
 *
 * <p><strong>Warum nicht {@link BudgetController} in den {@code TransactionExceptionHandler}
 * eintragen.</strong> Das wäre die kleinere Änderung, zöge aber die Zuständigkeit für einen
 * budget-Endpoint in das transaction-Modul. Genau diese Aufteilung benennt der
 * {@link FixedCostExceptionHandler} nebenan als den Grund für seine eigene Existenz.
 *
 * <p><strong>Warum nicht global.</strong> Ein {@code @RestControllerAdvice} ohne
 * {@code assignableTypes} gibt es in diesem Projekt nirgends. {@code UserExceptionHandler} hält
 * fest, warum: Springdoc hängt das Fehlerschema eines unscoped Advice an sämtliche Endpoints, und
 * zwei unscoped Advices konkurrieren nicht deterministisch.
 *
 * <p>Beide Fälle antworten body-los — wie im {@code TransactionExceptionHandler} und anders als
 * beim {@link FixedCostController}, dessen 400er einen Feldnamen tragen müssen. Hier gibt es nur
 * ein Eingabefeld; welches gemeint ist, steht bereits im Status.
 */
@RestControllerAdvice(assignableTypes = BudgetController.class)
public class BudgetExceptionHandler {

    @ExceptionHandler(InvalidMonthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidMonth(InvalidMonthException ex) {
        // Kein Body: 400 genügt für einen fehlerhaften month-Parameter, das Format steht in Swagger.
    }

    @ExceptionHandler(FutureMonthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleFutureMonth(FutureMonthException ex) {
        // Kein Body: dass ein künftiger Monat nicht beantwortet wird, steht in der Operation-Doku.
    }
}
