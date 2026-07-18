package com.budgetbuddy.transaction;

/**
 * Der {@code month}-Parameter von {@code GET /transactions/summary} fehlt oder entspricht nicht dem
 * Format {@code YYYY-MM}. Wird vom {@link TransactionExceptionHandler} auf HTTP 400 abgebildet.
 */
public class InvalidMonthException extends RuntimeException {

    public InvalidMonthException(String month) {
        super("Ungültiger Monat (erwartet YYYY-MM): " + month);
    }
}
