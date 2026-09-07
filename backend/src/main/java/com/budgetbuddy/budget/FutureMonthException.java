package com.budgetbuddy.budget;

import java.time.YearMonth;

/**
 * Der {@code month}-Parameter von {@code GET /api/budget/safe-to-spend} liegt nach dem laufenden
 * Monat (BE-STS-06). Wird vom {@link BudgetExceptionHandler} auf HTTP 400 abgebildet.
 *
 * <p>Eigener Typ statt einer {@code InvalidMonthException} aus dem transaction-Modul: das Format
 * ist in diesem Fall einwandfrei — {@code 2027-03} ist ein gültiger {@link YearMonth}. Abgelehnt
 * wird er aus einem fachlichen Grund, und dessen Meldung («erwartet YYYY-MM») wäre schlicht falsch.
 * Beide landen auf demselben Status, weil beide dasselbe bedeuten: der Client hat einen Monat
 * geschickt, für den es keine Antwort gibt.
 */
public class FutureMonthException extends RuntimeException {

    public FutureMonthException(YearMonth month, YearMonth currentMonth) {
        super("Safe-to-Spend ist für einen künftigen Monat nicht definiert: " + month
                + " liegt nach " + currentMonth);
    }
}
