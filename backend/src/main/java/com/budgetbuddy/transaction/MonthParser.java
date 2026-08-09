package com.budgetbuddy.transaction;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Parst den {@code month}-Query-Parameter der Transaktions-Endpoints.
 *
 * <p>Gemeinsam genutzt von {@link TransactionSummaryService} (BE-CAT-05) und
 * {@link TransactionListService} (FE-CAT-03): beide Endpoints nehmen denselben Parameter entgegen
 * und müssen ihn identisch auslegen. Als private Methode je Service wäre die Regel zweimal
 * vorhanden und könnte auseinanderlaufen — etwa wenn nur eine Seite später Quartale zulässt.
 */
final class MonthParser {

    private MonthParser() {
    }

    /**
     * Wandelt {@code YYYY-MM} in einen {@link YearMonth}.
     *
     * @param month Monat im Format {@code YYYY-MM}, z. B. {@code "2026-07"}.
     * @return der geparste Monat.
     * @throws InvalidMonthException wenn {@code month} fehlt, leer ist oder nicht dem Format
     *     entspricht. Wird vom {@link TransactionExceptionHandler} auf HTTP 400 abgebildet.
     */
    static YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            throw new InvalidMonthException(month);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new InvalidMonthException(month);
        }
    }
}
