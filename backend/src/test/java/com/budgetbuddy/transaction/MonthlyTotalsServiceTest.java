package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.transaction.dto.MonthlyTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit-Test des Gruppierungs- und Rechenkerns von {@link MonthlyTotalsService} (BE-STS-07, US-12).
 *
 * <p>Das Repository wird gemockt; die Monats-Filterung selbst ist Repository-Zuständigkeit und wird
 * im {@link MonthlyTotalsControllerIntegrationTest} gegen echtes PostgreSQL geprüft. Hier stehen
 * die Entscheide im Fokus, die der Service trägt: Gruppierung nach Monat, Reihenfolge, die
 * {@code null}-Semantik eines leeren Monats, die serverseitige Differenz und die Fenstergrenzen.
 */
class MonthlyTotalsServiceTest {

    private static final long USER_ID = 42L;

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final MonthlyTotalsService service = new MonthlyTotalsService(repository);

    private static Transaction tx(String datum, String betrag) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getBuchungsdatum()).thenReturn(LocalDate.parse(datum));
        when(transaction.getBetrag()).thenReturn(new BigDecimal(betrag));
        return transaction;
    }

    private void stub(List<Transaction> credits, List<Transaction> debits) {
        when(repository.findByUserIdAndIncomeTrueAndBuchungsdatumBetween(any(), any(), any()))
                .thenReturn(credits);
        when(repository.findByUserIdAndIncomeFalseAndBuchungsdatumBetween(any(), any(), any()))
                .thenReturn(debits);
    }

    private static MonthlyTotals rowFor(List<MonthlyTotals> rows, String month) {
        return rows.stream()
                .filter(r -> r.month().equals(month))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Monat fehlt in der Antwort: " + month));
    }

    @Test
    void groupsIncomeAndExpensesPerMonthAcrossTheWindow() {
        stub(
                List.of(tx("2026-07-25", "5000.00"), tx("2026-06-25", "4800.00")),
                List.of(
                        tx("2026-07-03", "60.00"),
                        tx("2026-07-20", "40.00"),
                        tx("2026-06-15", "1200.00")));

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 7), 3);

        assertThat(rows).hasSize(3);
        assertThat(rowFor(rows, "2026-07").income()).isEqualByComparingTo("5000.00");
        assertThat(rowFor(rows, "2026-07").expenses()).isEqualByComparingTo("100.00");
        assertThat(rowFor(rows, "2026-06").income()).isEqualByComparingTo("4800.00");
        assertThat(rowFor(rows, "2026-06").expenses()).isEqualByComparingTo("1200.00");
    }

    @Test
    void ordersRowsNewestMonthFirst() {
        stub(List.of(), List.of());

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 7), 3);

        // Analog zur Reihenfolge von GET /api/transactions/months.
        assertThat(rows).extracting(MonthlyTotals::month)
                .containsExactly("2026-07", "2026-06", "2026-05");
    }

    @Test
    void loadsTheWholeWindowInTwoQueriesInsteadOfTwoPerMonth() {
        stub(List.of(), List.of());

        service.totals(USER_ID, YearMonth.of(2026, 7), 3);

        // Genau ein Zugriff je Richtung, über die ganze Fensterbreite — nicht einer je Monat.
        // Die Grenzen sind der erste Tag des ältesten und der letzte Tag des jüngsten Monats.
        verify(repository).findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
                eq(USER_ID), eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 7, 31)));
        verify(repository).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                eq(USER_ID), eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 7, 31)));
    }

    @Test
    void aMonthWithoutAnyTransactionCarriesNullsRatherThanZero() {
        // Juli hat Buchungen, Juni und Mai nicht.
        stub(List.of(tx("2026-07-25", "5000.00")), List.of(tx("2026-07-03", "60.00")));

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 7), 3);

        // 0.00 behauptete erfasste Nullbeträge; null hält «wir haben für diesen Monat nichts».
        assertThat(rowFor(rows, "2026-06").income()).isNull();
        assertThat(rowFor(rows, "2026-06").expenses()).isNull();
        assertThat(rowFor(rows, "2026-06").difference()).isNull();
        assertThat(rowFor(rows, "2026-05").income()).isNull();
    }

    @Test
    void aMonthWithOnlyCreditsCarriesZeroExpensesRatherThanNull() {
        stub(List.of(tx("2026-07-25", "5000.00")), List.of());

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 7), 1);

        // Hier GIBT es Buchungen — die Summe der Belastungen ist dann wirklich null, nicht
        // unbekannt. Genau diese Unterscheidung begründet die null-Semantik.
        assertThat(rowFor(rows, "2026-07").income()).isEqualByComparingTo("5000.00");
        assertThat(rowFor(rows, "2026-07").expenses()).isEqualByComparingTo("0.00");
        assertThat(rowFor(rows, "2026-07").difference()).isEqualByComparingTo("5000.00");
    }

    @Test
    void aMonthWithOnlyDebitsCarriesZeroIncomeRatherThanNull() {
        stub(List.of(), List.of(tx("2026-07-03", "60.00")));

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 7), 1);

        assertThat(rowFor(rows, "2026-07").income()).isEqualByComparingTo("0.00");
        assertThat(rowFor(rows, "2026-07").expenses()).isEqualByComparingTo("60.00");
    }

    @Test
    void computesDifferenceServerSideAsIncomeMinusExpenses() {
        stub(List.of(tx("2026-07-25", "5000.00")), List.of(tx("2026-07-03", "1234.55")));

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 7), 1);

        assertThat(rowFor(rows, "2026-07").difference()).isEqualByComparingTo("3765.45");
    }

    @Test
    void differenceIsNegativeWhenMoreWasSpentThanEarned() {
        stub(List.of(tx("2026-07-25", "800.00")), List.of(tx("2026-07-03", "1250.50")));

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 7), 1);

        // Die Aussage, auf die es in der Übersicht ankommt: im Monat mehr ausgegeben als eingenommen.
        assertThat(rowFor(rows, "2026-07").difference()).isEqualByComparingTo("-450.50");
        assertThat(rowFor(rows, "2026-07").difference().signum()).isNegative();
    }

    @Test
    void allAmountsCarryScaleTwo() {
        // Beträge ohne Nachkommastellen bzw. mit nur einer: die Skala kommt aus dieser Schicht,
        // nicht aus der Datenbank (ADR-9).
        stub(List.of(tx("2026-07-25", "5000")), List.of(tx("2026-07-03", "60.5")));

        MonthlyTotals row = rowFor(service.totals(USER_ID, YearMonth.of(2026, 7), 1), "2026-07");

        assertThat(row.income().scale()).isEqualTo(2);
        assertThat(row.expenses().scale()).isEqualTo(2);
        assertThat(row.difference().scale()).isEqualTo(2);
    }

    @Test
    void deliversARowForEveryMonthOfTheWindowEvenWhenNothingExists() {
        stub(List.of(), List.of());

        // Der Client soll drei Zeilen rendern können und keine Lücken füllen müssen.
        assertThat(service.totals(USER_ID, YearMonth.of(2026, 7), 3)).hasSize(3);
        assertThat(service.totals(USER_ID, YearMonth.of(2026, 7), 12)).hasSize(12);
    }

    @Test
    void spansTheYearBoundaryWhenTheWindowReachesBack() {
        stub(List.of(), List.of());

        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2026, 1), 3);

        assertThat(rows).extracting(MonthlyTotals::month)
                .containsExactly("2026-01", "2025-12", "2025-11");
    }

    @Test
    void aFutureMonthIsAnEmptyRowRatherThanAnError() {
        stub(List.of(), List.of());

        // Anders als GET /api/budget/safe-to-spend, das einen Zukunftsmonat mit 400 ablehnt: dort
        // hat weeksLeft keine Bedeutung, hier gibt es einfach keine Buchungen.
        List<MonthlyTotals> rows = service.totals(USER_ID, YearMonth.of(2099, 12), 1);

        assertThat(rows).hasSize(1);
        assertThat(rowFor(rows, "2099-12").income()).isNull();
    }

    @Test
    void rejectsAWindowSizeOutsideTheAllowedRange() {
        stub(List.of(), List.of());

        assertThatThrownBy(() -> service.totals(USER_ID, YearMonth.of(2026, 7), 0))
                .isInstanceOf(InvalidMonthWindowException.class);
        assertThatThrownBy(() -> service.totals(USER_ID, YearMonth.of(2026, 7), -1))
                .isInstanceOf(InvalidMonthWindowException.class);
        assertThatThrownBy(() -> service.totals(
                        USER_ID, YearMonth.of(2026, 7), MonthlyTotalsService.MAX_WINDOW_MONTHS + 1))
                .isInstanceOf(InvalidMonthWindowException.class);
    }

    @Test
    void acceptsTheWindowBounds() {
        stub(List.of(), List.of());

        assertThat(service.totals(USER_ID, YearMonth.of(2026, 7), 1)).hasSize(1);
        assertThat(service.totals(
                        USER_ID, YearMonth.of(2026, 7), MonthlyTotalsService.MAX_WINDOW_MONTHS))
                .hasSize(MonthlyTotalsService.MAX_WINDOW_MONTHS);
    }
}
