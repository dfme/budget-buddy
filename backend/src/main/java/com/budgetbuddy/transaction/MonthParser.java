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
 *
 * <p><strong>{@code public} seit BE-STS-06.</strong> Der Safe-to-Spend-Endpoint im
 * {@code budget}-Modul nimmt denselben {@code month}-Parameter entgegen und muss ihn identisch
 * auslegen — genau das Argument, aus dem die Klasse innerhalb dieses Pakets schon geteilt wird,
 * nur eine Paketgrenze weiter. Die Sichtbarkeit ist damit kein Bruch der Modulregel aus
 * CLAUDE.md: die verbietet den direkten Zugriff auf <em>Repositories und Services</em> eines
 * anderen Moduls, und diese Klasse ist weder das eine noch das andere — keine Spring-Bean, kein
 * Zustand, kein Datenzugriff. Die von ihr geworfene {@link InvalidMonthException} ist bereits
 * {@code public}; mit BE-STS-06 wird sie erstmals ausserhalb dieses Pakets behandelt.
 */
public final class MonthParser {

    private MonthParser() {
    }

    /**
     * Wandelt {@code YYYY-MM} in einen {@link YearMonth}.
     *
     * @param month Monat im Format {@code YYYY-MM}, z. B. {@code "2026-07"}.
     * @return der geparste Monat.
     * @throws InvalidMonthException wenn {@code month} fehlt, leer ist oder nicht dem Format
     *     entspricht. Auf HTTP 400 abgebildet wird sie vom Advice des jeweiligen Controllers —
     *     {@link TransactionExceptionHandler} für die Transaktions-Endpoints,
     *     {@code BudgetExceptionHandler} für {@code GET /api/budget/safe-to-spend}. Ein
     *     {@code @RestControllerAdvice} ohne {@code assignableTypes} gibt es in diesem Projekt
     *     bewusst nicht (Begründung in {@code UserExceptionHandler}), ein neuer Aufrufer braucht
     *     daher immer auch einen Handler.
     */
    public static YearMonth parse(String month) {
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
