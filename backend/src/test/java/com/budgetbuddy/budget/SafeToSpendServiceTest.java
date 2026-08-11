package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.auth.UserIncomePort;
import com.budgetbuddy.budget.dto.FixedCostSummaryResponse;
import com.budgetbuddy.budget.dto.SafeToSpendResponse;
import com.budgetbuddy.transaction.MonthlyExpensePort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Test des {@link SafeToSpendService} (BE-STS-01, US-06). Beide Ports, der
 * {@link FixedCostService} und die {@link Clock} sind gemockt; der Pfad über echtes PostgreSQL
 * inklusive Mandantentrennungs-Gegenprobe liegt im {@link SafeToSpendServiceIntegrationTest}.
 *
 * <p>{@link MockitoExtension} mit Strict Stubs wie im {@link FixedCostServiceTest}: ein Stub, den
 * der getestete Pfad nicht mehr aufruft, wird rot statt still ins Leere zu laufen. Für den
 * {@code noIncome}-Fall ist das direkt Teil des Nachweises — dort dürfen Fixkosten und Ausgaben gar
 * nicht erst gelesen werden.
 */
@ExtendWith(MockitoExtension.class)
class SafeToSpendServiceTest {

    private static final long USER_ID = 42L;
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Mock private UserIncomePort userIncomePort;
    @Mock private FixedCostService fixedCostService;
    @Mock private MonthlyExpensePort monthlyExpensePort;
    @Mock private Clock clock;

    @InjectMocks private SafeToSpendService service;

    // --- AC1: Formel berechnet korrekt mit BigDecimal ---

    @Test
    void computesTheExampleFromUs06() {
        // US-06: Einkommen 2000, Fixkosten 800, bisherige Ausgaben 400 in Woche 1 → 200 CHF/Woche.
        // 1. Februar 2026: 28 Resttage → 4 Wochen; (2000 − 800 − 400) ÷ 4 = 200.00
        givenToday("2026-02-01");
        givenIncome("2000.00");
        givenFixedCosts("800.00");
        givenExpenses("400.00");

        SafeToSpendResponse result = service.calculate(USER_ID);

        assertThat(result.amount()).isEqualByComparingTo("200.00");
        assertThat(result.weeksLeft()).isEqualTo(4);
        assertThat(result.negative()).isFalse();
        assertThat(result.noIncome()).isFalse();
    }

    @Test
    void nonTerminatingDivisionIsRoundedHalfUpToRappen() {
        // 11. August 2026: 21 Resttage → 3 Wochen. 800.00 ÷ 3 = 266.666… → 266.67
        givenToday("2026-08-11");
        givenIncome("1000.00");
        givenFixedCosts("0.00");
        givenExpenses("200.00");

        SafeToSpendResponse result = service.calculate(USER_ID);

        assertThat(result.weeksLeft()).isEqualTo(3);
        assertThat(result.amount()).isEqualByComparingTo("266.67");
        assertThat(result.amount().scale()).isEqualTo(2);
    }

    @Test
    void rappenAreExactAcrossTheWholeFormula() {
        // Der eigentliche ADR-9-Nachweis: diese Fixture ist so gewählt, dass BigDecimal und double
        // auseinanderlaufen — ein Wechsel auf double macht den Test rot.
        //
        //   BigDecimal: 2000.00 − 800.07 − 400.03 = 799.90     ÷ 4 = 199.975         → HALF_UP → 199.98
        //   double:     2000.00 − 800.07 − 400.03 = 799.8999…  ÷ 4 = 199.97499999997 → HALF_UP → 199.97
        //
        // Der double-Zwischenwert liegt knapp *unter* der Rundungsgrenze, deshalb kippt HALF_UP dort
        // nach unten. Nicht jede krumme Fixture zeigt das: bei 2000.55/800.15/400.10 etwa landet der
        // double-Wert bei 200.07500000000002 und damit über der Grenze — beide Welten runden auf
        // 200.08 und der Test wäre mit double genauso grün.
        givenToday("2026-02-01");
        givenIncome("2000.00");
        givenFixedCosts("800.07");
        givenExpenses("400.03");

        assertThat(service.calculate(USER_ID).amount()).isEqualByComparingTo("199.98");
    }

    // --- AC2: Divisor ist mindestens 1 (kein Division-by-Zero) ---

    @Test
    void thirtyOneDayMonthStartsWithFiveWeeks() {
        // 31 Resttage ÷ 7 = 4.43 → aufgerundet 5. Konservativ: auch die angebrochene fünfte Woche
        // bekommt Budget.
        assertThat(weeksLeftOn("2026-08-01")).isEqualTo(5);
    }

    @Test
    void twentyEightDayMonthStartsWithFourWeeks() {
        assertThat(weeksLeftOn("2026-02-01")).isEqualTo(4);
    }

    @Test
    void eightRemainingDaysCountAsTwoWeeks() {
        // 24.08. → 8 Resttage → aufgerundet 2. Gegenprobe zur Grenze bei genau 7.
        assertThat(weeksLeftOn("2026-08-24")).isEqualTo(2);
    }

    @Test
    void exactlySevenRemainingDaysCountAsOneWeek() {
        assertThat(weeksLeftOn("2026-08-25")).isEqualTo(1);
    }

    @Test
    void fewerThanSevenRemainingDaysStillCountAsOneWeek() {
        // US-06: «weniger als 7 Tage verbleiben → Divisor mindestens 1».
        assertThat(weeksLeftOn("2026-08-28")).isEqualTo(1);
    }

    @Test
    void lastDayOfMonthDividesByOneInsteadOfZero() {
        // Der schärfste Fall: ein einziger Resttag. Ohne die Klemmung wäre das eine Division durch
        // 0 — hier muss der volle Restbetrag als Wochenbudget herauskommen.
        givenToday("2026-08-31");
        givenIncome("1000.00");
        givenFixedCosts("300.00");
        givenExpenses("200.00");

        SafeToSpendResponse result = service.calculate(USER_ID);

        assertThat(result.weeksLeft()).isEqualTo(1);
        assertThat(result.amount()).isEqualByComparingTo("500.00");
    }

    // --- AC3: Negativ-Flag gesetzt wenn Safe-to-Spend < 0 ---

    @Test
    void negativeAmountSetsTheNegativeFlag() {
        givenToday("2026-02-01");
        givenIncome("1000.00");
        givenFixedCosts("900.00");
        givenExpenses("300.00");

        SafeToSpendResponse result = service.calculate(USER_ID);

        assertThat(result.amount()).isEqualByComparingTo("-50.00");
        assertThat(result.negative()).isTrue();
        assertThat(result.noIncome()).isFalse();
    }

    @Test
    void exactlyZeroIsNotNegative() {
        // Grenzfall: aufgebraucht ist nicht überzogen. US-06 verlangt das Warn-Banner erst bei < 0.
        givenToday("2026-02-01");
        givenIncome("1000.00");
        givenFixedCosts("600.00");
        givenExpenses("400.00");

        SafeToSpendResponse result = service.calculate(USER_ID);

        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.negative()).isFalse();
    }

    // --- AC4: noIncome-Flag gesetzt wenn monthly_income nicht erfasst ---

    @Test
    void missingIncomeSetsTheFlagAndSkipsTheDivision() {
        givenToday("2026-08-01");
        when(userIncomePort.findMonthlyIncome(USER_ID)).thenReturn(Optional.empty());

        SafeToSpendResponse result = service.calculate(USER_ID);

        assertThat(result.noIncome()).isTrue();
        assertThat(result.amount()).isNull();
        assertThat(result.negative()).isFalse();
        // Der Divisor hängt allein am Datum und wird auch ohne Einkommen geliefert.
        assertThat(result.weeksLeft()).isEqualTo(5);

        // «Keine Division wird ausgeführt» (US-06): belegt dadurch, dass die beiden anderen
        // Eingabewerte gar nicht erst gelesen werden. Ein null-Betrag allein zeigte das nicht.
        verify(fixedCostService, never()).list(anyLong());
        verify(monthlyExpensePort, never()).sumExpenses(anyLong(), any());
    }

    // --- Zeitzone: «heute» ist Europe/Zurich, nicht UTC ---

    @Test
    void theCurrentMonthFollowsSwissLocalTimeNotUtc() {
        // 31.07.2026 23:30 UTC ist in Zürich bereits der 01.08.2026 01:30. Gerechnet werden muss
        // der August — mit UTC wäre es der Juli und damit der falsche Monat.
        when(clock.instant()).thenReturn(Instant.parse("2026-07-31T23:30:00Z"));
        givenIncome("1000.00");
        givenFixedCosts("0.00");
        when(monthlyExpensePort.sumExpenses(eq(USER_ID), any())).thenReturn(new BigDecimal("0.00"));

        SafeToSpendResponse result = service.calculate(USER_ID);

        verify(monthlyExpensePort).sumExpenses(USER_ID, YearMonth.of(2026, 8));
        // August hat 31 Tage: ein voller Monat ergibt 5 Wochen, der Juli-Rest hätte 1 ergeben.
        assertThat(result.weeksLeft()).isEqualTo(5);
    }

    // --- Mandantentrennung: alle Eingabewerte werden für genau diesen User gelesen ---

    @Test
    void everyInputIsReadForTheGivenUserOnly() {
        givenToday("2026-08-11");
        givenIncome("1000.00");
        givenFixedCosts("0.00");
        givenExpenses("0.00");

        service.calculate(USER_ID);

        verify(userIncomePort).findMonthlyIncome(USER_ID);
        verify(fixedCostService).list(USER_ID);
        verify(monthlyExpensePort).sumExpenses(USER_ID, YearMonth.of(2026, 8));
    }

    // --- Helfer ---

    /** Stellt die Clock auf 12:00 Ortszeit des angegebenen Tages — mitten im Tag, zonen-neutral. */
    private void givenToday(String isoDate) {
        when(clock.instant())
                .thenReturn(LocalDate.parse(isoDate).atTime(12, 0).atZone(ZURICH).toInstant());
    }

    private void givenIncome(String betrag) {
        when(userIncomePort.findMonthlyIncome(USER_ID))
                .thenReturn(Optional.of(new BigDecimal(betrag)));
    }

    private void givenFixedCosts(String summeMonatlich) {
        when(fixedCostService.list(USER_ID))
                .thenReturn(new FixedCostSummaryResponse(
                        List.of(), new BigDecimal(summeMonatlich), null, false));
    }

    private void givenExpenses(String summe) {
        when(monthlyExpensePort.sumExpenses(eq(USER_ID), any()))
                .thenReturn(new BigDecimal(summe));
    }

    /**
     * Liest den Divisor für einen Stichtag ab. Einkommen, Fixkosten und Ausgaben sind dabei so
     * gewählt, dass sie das Ergebnis nicht beeinflussen.
     */
    private int weeksLeftOn(String isoDate) {
        givenToday(isoDate);
        givenIncome("0.00");
        givenFixedCosts("0.00");
        givenExpenses("0.00");
        return service.calculate(USER_ID).weeksLeft();
    }
}
