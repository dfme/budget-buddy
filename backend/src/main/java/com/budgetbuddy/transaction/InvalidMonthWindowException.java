package com.budgetbuddy.transaction;

/**
 * Der {@code months}-Parameter von {@code GET /transactions/monthly-totals} liegt ausserhalb des
 * erlaubten Bereichs (BE-STS-07): er ist kleiner als 1 oder grösser als
 * {@link MonthlyTotalsService#MAX_WINDOW_MONTHS}. Wird vom {@link TransactionExceptionHandler} auf
 * HTTP 400 abgebildet.
 *
 * <p>Eigener Typ und nicht die bestehende {@link InvalidPaginationException}: die gehört
 * semantisch zu {@code page}/{@code size} der Transaktionsliste, und ihre Meldung zeigte auf ein
 * anderes Problem. Dieselbe Überlegung, aus der {@link InvalidMonthException} und
 * {@link InvalidCategoryException} getrennt geführt werden.
 *
 * <p>Die Obergrenze ist nicht Komfort, sondern die Fenstergrenze: {@code months} bestimmt, wie
 * viele Monate der Endpoint lädt, und ein unbegrenzter Wert liesse einen einzelnen Aufruf die
 * gesamte Historie des Users durchziehen.
 */
public class InvalidMonthWindowException extends RuntimeException {

    public InvalidMonthWindowException(String message) {
        super(message);
    }
}
