package com.budgetbuddy.transaction;

/**
 * Der {@code month}-Parameter von {@code GET /transactions/summary} oder {@code GET /transactions}
 * fehlt oder entspricht nicht dem Format {@code YYYY-MM}. Beide Endpoints parsen ihn über den
 * gemeinsamen {@link MonthParser}. Wird vom {@link TransactionExceptionHandler} auf HTTP 400
 * abgebildet.
 */
public class InvalidMonthException extends RuntimeException {

    public InvalidMonthException(String month) {
        super("Ungültiger Monat (erwartet YYYY-MM): " + month);
    }
}
