package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.auth.UserIncomePort;
import com.budgetbuddy.budget.dto.FixedCostRequest;
import com.budgetbuddy.budget.dto.FixedCostResponse;
import com.budgetbuddy.budget.dto.FixedCostSummaryResponse;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Test des {@link FixedCostService} (BE-FC-02). Repository und {@link UserIncomePort} sind
 * gemockt; der Pfad über echte SQLite inklusive Mandantentrennungs-Gegenprobe liegt im
 * {@link FixedCostServiceIntegrationTest}.
 *
 * <p>{@link MockitoExtension} statt manuellem {@code mock(...)}: die Strict-Stub-Prüfung lässt
 * einen Stub, den der getestete Pfad gar nicht mehr aufruft, rot werden statt still ins Leere
 * laufen. Bei einem Test mit zentralen {@code given…}-Helfern ist genau das die Fehlerklasse, die
 * sonst unbemerkt bleibt.
 */
@ExtendWith(MockitoExtension.class)
class FixedCostServiceTest {

    private static final long USER_ID = 42L;
    private static final long FIXED_COST_ID = 7L;

    @Mock private FixedCostRepository repository;
    @Mock private UserIncomePort userIncomePort;

    @InjectMocks private FixedCostService service;

    // --- AC1/AC2: Normalisierung quartalsweise ÷ 3 und jährlich ÷ 12 ---

    @Test
    void monatlichIsTakenAsIs() {
        assertThat(monatsbetragOf("1200.00", Intervall.MONATLICH)).isEqualByComparingTo("1200.00");
    }

    @Test
    void quartalsweiseIsDividedByThree() {
        assertThat(monatsbetragOf("300.00", Intervall.QUARTALSWEISE)).isEqualByComparingTo("100.00");
    }

    @Test
    void jaehrlichIsDividedByTwelve() {
        assertThat(monatsbetragOf("1200.00", Intervall.JAEHRLICH)).isEqualByComparingTo("100.00");
    }

    @Test
    void nonDivisibleQuartalsweiseIsRoundedHalfUpToRappen() {
        // 100.00 / 3 = 33.333… → 33.33 (abgerundet, weil die dritte Stelle < 5 ist)
        assertThat(monatsbetragOf("100.00", Intervall.QUARTALSWEISE)).isEqualByComparingTo("33.33");
    }

    @Test
    void nonDivisibleJaehrlichIsRoundedHalfUpToRappen() {
        // 335.00 / 12 = 27.91666… → 27.92 (aufgerundet, weil die dritte Stelle ≥ 5 ist)
        assertThat(monatsbetragOf("335.00", Intervall.JAEHRLICH)).isEqualByComparingTo("27.92");
    }

    @Test
    void monatsbetragAlwaysHasScaleTwo() {
        // Das Repository liefert aus SQLite Skala 0 zurück (#141) — die Antwort trotzdem Skala 2.
        givenEntries(entry(1L, "Serafe", new BigDecimal("335"), Intervall.JAEHRLICH));

        FixedCostResponse item = service.list(USER_ID).fixedCosts().getFirst();

        assertThat(item.betrag().scale()).isEqualTo(2);
        assertThat(item.betrag()).isEqualByComparingTo("335.00");
        assertThat(item.monatsbetrag().scale()).isEqualTo(2);
    }

    @Test
    void summeIsTheSumOfTheAlreadyRoundedItems() {
        givenEntries(
                entry(1L, "Miete", new BigDecimal("1200.00"), Intervall.MONATLICH),
                entry(2L, "Handy", new BigDecimal("100.00"), Intervall.QUARTALSWEISE),
                entry(3L, "Serafe", new BigDecimal("335.00"), Intervall.JAEHRLICH));

        FixedCostSummaryResponse summary = service.list(USER_ID);

        // 1200.00 + 33.33 + 27.92 — exakt die Summe der drei angezeigten Zeilen.
        assertThat(summary.fixedCosts()).extracting(FixedCostResponse::monatsbetrag)
                .containsExactly(
                        new BigDecimal("1200.00"), new BigDecimal("33.33"), new BigDecimal("27.92"));
        assertThat(summary.summeMonatlich()).isEqualByComparingTo("1261.25");
    }

    @Test
    void emptyListYieldsZeroSum() {
        givenEntries();

        FixedCostSummaryResponse summary = service.list(USER_ID);

        assertThat(summary.fixedCosts()).isEmpty();
        assertThat(summary.summeMonatlich()).isEqualByComparingTo("0.00");
        assertThat(summary.summeMonatlich().scale()).isEqualTo(2);
    }

    // --- AC3: Warning-Flag bei Fixkosten ≥ Einkommen ---

    @Test
    void warnsWhenSumEqualsIncome() {
        givenEntries(entry(1L, "Miete", new BigDecimal("4200.00"), Intervall.MONATLICH));
        givenIncome("4200.00");

        assertThat(service.list(USER_ID).exceedsIncome()).isTrue();
    }

    @Test
    void warnsWhenSumExceedsIncome() {
        givenEntries(entry(1L, "Miete", new BigDecimal("4200.01"), Intervall.MONATLICH));
        givenIncome("4200.00");

        assertThat(service.list(USER_ID).exceedsIncome()).isTrue();
    }

    @Test
    void doesNotWarnWhenSumIsBelowIncome() {
        givenEntries(entry(1L, "Miete", new BigDecimal("4199.99"), Intervall.MONATLICH));
        givenIncome("4200.00");

        FixedCostSummaryResponse summary = service.list(USER_ID);

        assertThat(summary.exceedsIncome()).isFalse();
        assertThat(summary.monthlyIncome()).isEqualByComparingTo("4200.00");
    }

    @Test
    void doesNotWarnWhenNoIncomeIsRecorded() {
        givenEntries(entry(1L, "Miete", new BigDecimal("4200.00"), Intervall.MONATLICH));
        when(userIncomePort.findMonthlyIncome(USER_ID)).thenReturn(Optional.empty());

        FixedCostSummaryResponse summary = service.list(USER_ID);

        // Ohne Vergleichswert gibt es keine belegbare Aussage; null macht den Zustand für den
        // Client von «Einkommen reicht» unterscheidbar.
        assertThat(summary.exceedsIncome()).isFalse();
        assertThat(summary.monthlyIncome()).isNull();
    }

    @Test
    void comparesAgainstTheUnroundedIncomeButReportsItWithScaleTwo() {
        givenEntries(entry(1L, "Miete", new BigDecimal("4200.00"), Intervall.MONATLICH));
        // PUT /users/me/income prüft nur > 0, nicht auf Rappen — mehr als zwei Stellen sind möglich.
        givenIncome("4200.004");

        FixedCostSummaryResponse summary = service.list(USER_ID);

        assertThat(summary.exceedsIncome()).isFalse(); // 4200.00 < 4200.004
        assertThat(summary.monthlyIncome()).isEqualTo(new BigDecimal("4200.00"));
    }

    // --- CRUD ---

    @Test
    void createPersistsForTheGivenUserAndReturnsTheNormalizedItem() {
        whenSavingReturnTheArgument();

        FixedCostResponse response = service.create(
                USER_ID, new FixedCostRequest("  Krankenkasse  ", new BigDecimal("312.45"),
                        "quartalsweise"));

        ArgumentCaptor<FixedCost> saved = ArgumentCaptor.forClass(FixedCost.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getBezeichnung()).isEqualTo("Krankenkasse"); // getrimmt
        assertThat(saved.getValue().getIntervall()).isEqualTo(Intervall.QUARTALSWEISE);

        assertThat(response.intervall()).isEqualTo("quartalsweise");
        assertThat(response.monatsbetrag()).isEqualByComparingTo("104.15");
    }

    @Test
    void getReturnsTheOwnEntry() {
        when(repository.findByIdAndUserId(FIXED_COST_ID, USER_ID))
                .thenReturn(Optional.of(
                        entry(FIXED_COST_ID, "Miete", new BigDecimal("1200.00"),
                                Intervall.MONATLICH)));

        assertThat(service.get(USER_ID, FIXED_COST_ID).bezeichnung()).isEqualTo("Miete");
    }

    @Test
    void getThrowsNotFoundWhenTheEntryIsMissingOrForeign() {
        when(repository.findByIdAndUserId(FIXED_COST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, FIXED_COST_ID))
                .isInstanceOf(FixedCostNotFoundException.class);
    }

    @Test
    void updateChangesAllThreeFields() {
        FixedCost existing =
                entry(FIXED_COST_ID, "Miete", new BigDecimal("1200.00"), Intervall.MONATLICH);
        when(repository.findByIdAndUserId(FIXED_COST_ID, USER_ID)).thenReturn(Optional.of(existing));
        whenSavingReturnTheArgument();

        FixedCostResponse response = service.update(USER_ID, FIXED_COST_ID,
                new FixedCostRequest("Serafe", new BigDecimal("335.00"), "jaehrlich"));

        assertThat(existing.getBezeichnung()).isEqualTo("Serafe");
        assertThat(existing.getBetrag()).isEqualByComparingTo("335.00");
        assertThat(existing.getIntervall()).isEqualTo(Intervall.JAEHRLICH);
        assertThat(response.monatsbetrag()).isEqualByComparingTo("27.92");
    }

    @Test
    void updateThrowsNotFoundWhenTheEntryIsMissingOrForeign() {
        when(repository.findByIdAndUserId(FIXED_COST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, FIXED_COST_ID,
                        new FixedCostRequest("Serafe", new BigDecimal("335.00"), "jaehrlich")))
                .isInstanceOf(FixedCostNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteUsesTheUserBoundRepositoryMethodWithTheCorrectArgumentOrder() {
        when(repository.deleteByIdAndUserId(FIXED_COST_ID, USER_ID)).thenReturn(1L);

        service.delete(USER_ID, FIXED_COST_ID);

        // Vertauschte Argumente wären hier still: beide sind long. Der Test hält die Reihenfolge
        // (id, userId) fest, die das Repository erwartet.
        verify(repository).deleteByIdAndUserId(FIXED_COST_ID, USER_ID);
    }

    @Test
    void deleteThrowsNotFoundWhenNoRowWasDeleted() {
        when(repository.deleteByIdAndUserId(FIXED_COST_ID, USER_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(USER_ID, FIXED_COST_ID))
                .isInstanceOf(FixedCostNotFoundException.class);
    }

    // --- Validierung (US-03: Pflichtfelder mit feldspezifischer Meldung) ---

    @Test
    void rejectsMissingBody() {
        assertThatInvalid(null, "request");
    }

    @Test
    void rejectsNullBezeichnung() {
        assertThatInvalid(request(null, "100.00", "monatlich"), "bezeichnung");
    }

    @Test
    void rejectsBlankBezeichnung() {
        assertThatInvalid(request("   ", "100.00", "monatlich"), "bezeichnung");
    }

    @Test
    void rejectsOverlongBezeichnung() {
        assertThatInvalid(request("x".repeat(101), "100.00", "monatlich"), "bezeichnung");
    }

    @Test
    void acceptsBezeichnungAtTheLengthLimit() {
        whenSavingReturnTheArgument();

        assertThat(service.create(USER_ID, request("x".repeat(100), "100.00", "monatlich"))
                        .bezeichnung())
                .hasSize(100);
    }

    @Test
    void rejectsNullBetrag() {
        assertThatInvalid(new FixedCostRequest("Miete", null, "monatlich"), "betrag");
    }

    @Test
    void rejectsZeroBetrag() {
        assertThatInvalid(request("Miete", "0.00", "monatlich"), "betrag");
    }

    @Test
    void rejectsNegativeBetrag() {
        assertThatInvalid(request("Miete", "-1.00", "monatlich"), "betrag");
    }

    @Test
    void rejectsBetragWithMoreThanTwoDecimals() {
        // Würde in DECIMAL(10,2) still gerundet — deshalb ablehnen statt annehmen.
        assertThatInvalid(request("Miete", "12.345", "monatlich"), "betrag");
    }

    @Test
    void acceptsBetragWithTrailingZerosBeyondTwoDecimals() {
        whenSavingReturnTheArgument();

        // 100.000 ist wertgleich zu 100.00 — entscheidend ist der Wert, nicht die Nullen.
        assertThat(service.create(USER_ID, request("Miete", "100.000", "monatlich")).betrag())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsBetragBeyondTheColumnCapacity() {
        assertThatInvalid(request("Miete", "100000000.00", "monatlich"), "betrag");
    }

    @Test
    void rejectsNullIntervall() {
        assertThatInvalid(request("Miete", "100.00", null), "intervall");
    }

    @Test
    void rejectsUnknownIntervall() {
        assertThatInvalid(request("Miete", "100.00", "woechentlich"), "intervall");
    }

    @Test
    void rejectsGermanUmlautSpellingOfJaehrlich() {
        // Der API-Contract ist bewusst ASCII (Intervall-Javadoc); «jährlich» ist Anzeigetext.
        assertThatInvalid(request("Serafe", "335.00", "jährlich"), "intervall");
    }

    @Test
    void invalidInputIsRejectedBeforeTheEntryIsLoaded() {
        assertThatThrownBy(() -> service.update(USER_ID, FIXED_COST_ID,
                        request("Miete", "-1.00", "monatlich")))
                .isInstanceOf(InvalidFixedCostException.class);

        verify(repository, never()).findByIdAndUserId(any(), any());
        verify(repository, never()).save(any());
    }

    // --- Helfer ---

    private void assertThatInvalid(FixedCostRequest request, String expectedField) {
        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(InvalidFixedCostException.class)
                .extracting(e -> ((InvalidFixedCostException) e).getField())
                .isEqualTo(expectedField);
    }

    private static FixedCostRequest request(String bezeichnung, String betrag, String intervall) {
        return new FixedCostRequest(bezeichnung, new BigDecimal(betrag), intervall);
    }

    private BigDecimal monatsbetragOf(String betrag, Intervall intervall) {
        givenEntries(entry(1L, "Position", new BigDecimal(betrag), intervall));
        return service.list(USER_ID).fixedCosts().getFirst().monatsbetrag();
    }

    private void givenEntries(FixedCost... entries) {
        when(repository.findByUserIdOrderByIdAsc(USER_ID)).thenReturn(List.of(entries));
    }

    private void givenIncome(String income) {
        when(userIncomePort.findMonthlyIncome(USER_ID))
                .thenReturn(Optional.of(new BigDecimal(income)));
    }

    private void whenSavingReturnTheArgument() {
        when(repository.save(any(FixedCost.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Baut eine {@link FixedCost} mit gesetzter ID. Die ID vergibt sonst die Datenbank; das Entity
     * hat dafür bewusst keinen Setter, deshalb Reflection — analog zu {@code UserServiceTest}.
     */
    private static FixedCost entry(long id, String bezeichnung, BigDecimal betrag,
            Intervall intervall) {
        FixedCost fixedCost = new FixedCost(USER_ID, bezeichnung, betrag, intervall);
        try {
            Field field = FixedCost.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(fixedCost, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return fixedCost;
    }
}
