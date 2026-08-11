package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Test der Einkommens-Heuristik (BE-STS-02, US-06). Repository und {@link Clock} sind gemockt;
 * der Pfad über echtes PostgreSQL inklusive Mandantentrennungs-Gegenprobe liegt im
 * {@link IncomeSuggestionServiceIntegrationTest}.
 *
 * <p>Die Buchungstexte stammen aus den echten PDF-Fixtures ({@code Post_kontoauszug.pdf},
 * {@code UBS_Konto_Bewegungen_2021_Juli.pdf}) — erfundene Texte würden die Normalisierung an einem
 * Problem messen, das es so nicht gibt.
 */
@ExtendWith(MockitoExtension.class)
class IncomeSuggestionServiceTest {

    private static final long USER_ID = 42L;
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Mock private TransactionRepository transactionRepository;
    @Mock private Clock clock;

    @InjectMocks private IncomeSuggestionService service;

    // --- AC1: wiederkehrende Gutschrift (±5%, ≥ 2 Monate) wird erkannt ---

    @Test
    void identicalSalaryEveryMonthIsRecognised() {
        // UBS-Fixture: «Saläreingang» 6800.00, monatlich von Januar bis Juni.
        givenToday("2026-07-01");
        givenCredits(
                credit("2026-01-25", "Saläreingang", "6800.00"),
                credit("2026-02-25", "Saläreingang", "6800.00"),
                credit("2026-03-25", "Saläreingang", "6800.00"),
                credit("2026-04-25", "Saläreingang", "6800.00"),
                credit("2026-05-25", "Saläreingang", "6800.00"),
                credit("2026-06-25", "Saläreingang", "6800.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("6800.00"));
    }

    @Test
    void monthNameInsideTheTextDoesNotSplitTheGroup() {
        // Post-Fixture: der Lohn ist als «GUTSCHRIFT LOHN <Monat>» gebucht. Ohne Normalisierung
        // hätte jeder Monat einen eigenen Schlüssel und keine Gruppe käme auf zwei Vorkommen.
        givenToday("2026-11-15");
        givenCredits(
                credit("2026-09-30", "GUTSCHRIFT LOHN SEPTEMBER", "5500.00"),
                credit("2026-10-30", "GUTSCHRIFT LOHN OKTOBER", "5500.00"),
                credit("2026-11-30", "GUTSCHRIFT LOHN NOVEMBER", "5500.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("5500.00"));
    }

    @Test
    void changingReferenceNumbersDoNotSplitTheGroup() {
        // Post-Fixture: «GIRO AUS KONTO 25-9034-2». Ziffernhaltige Tokens wechseln von Buchung zu
        // Buchung und dürfen den Schlüssel nicht bestimmen.
        givenToday("2026-04-01");
        givenCredits(
                credit("2026-02-25", "GIRO AUS KONTO 25-9034-2", "4589.10"),
                credit("2026-03-25", "GIRO AUS KONTO 25-9034-7", "4589.10"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("4589.10"));
    }

    @Test
    void amountExactlyOnTheFivePercentBoundStillBelongsToTheGroup() {
        // Median 1000.00, Band ±50.00. 950.00 liegt exakt auf der Grenze und zählt noch dazu.
        givenToday("2026-04-01");
        givenCredits(
                credit("2026-01-25", "Saläreingang", "950.00"),
                credit("2026-02-25", "Saläreingang", "1000.00"),
                credit("2026-03-25", "Saläreingang", "1050.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("1000.00"));
    }

    @Test
    void oneAmountJustOutsideTheBoundDisqualifiesTheWholeGroup() {
        // Ein Rappen weiter draussen als im Test darüber: 949.99 statt 950.00. «Gleicher Betrag
        // (±5%)» ist eine Aussage über alle Vorkommen — also kippt die ganze Gruppe, statt den
        // Ausreisser herauszufiltern. Lieber kein Vorschlag als ein falscher.
        givenToday("2026-04-01");
        givenCredits(
                credit("2026-01-25", "Saläreingang", "949.99"),
                credit("2026-02-25", "Saläreingang", "1000.00"),
                credit("2026-03-25", "Saläreingang", "1050.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).isEmpty();
    }

    @Test
    void twoCreditsInTheSameMonthAreNotEnough() {
        // US-06 verlangt «mindestens 2 Monate» — nicht zwei Buchungen.
        givenToday("2026-04-01");
        givenCredits(
                credit("2026-03-05", "Saläreingang", "6800.00"),
                credit("2026-03-25", "Saläreingang", "6800.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).isEmpty();
    }

    @Test
    void twoCreditsInDifferentMonthsAreEnough() {
        // Der Minimalfall an der Untergrenze — Gegenprobe zum Test darüber.
        givenToday("2026-04-01");
        givenCredits(
                credit("2026-02-25", "Saläreingang", "6800.00"),
                credit("2026-03-25", "Saläreingang", "6800.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("6800.00"));
    }

    @Test
    void singleCreditIsNotARecurringPattern() {
        givenToday("2026-04-01");
        givenCredits(credit("2026-03-25", "Saläreingang", "6800.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).isEmpty();
    }

    // --- Auswahl unter mehreren qualifizierten Gruppen ---

    @Test
    void theGroupWithTheHighestMedianWins() {
        // Lohn und eine wiederkehrende Kleinrückerstattung erfüllen beide die Bedingungen. Als
        // Monatseinkommen taugt nur der Lohn.
        givenToday("2026-05-01");
        givenCredits(
                credit("2026-02-25", "Saläreingang", "6800.00"),
                credit("2026-03-25", "Saläreingang", "6800.00"),
                credit("2026-04-25", "Saläreingang", "6800.00"),
                credit("2026-02-10", "Rueckerstattung Krankenkasse", "120.00"),
                credit("2026-03-10", "Rueckerstattung Krankenkasse", "120.00"),
                credit("2026-04-10", "Rueckerstattung Krankenkasse", "120.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("6800.00"));
    }

    // --- Median ---

    @Test
    void evenNumberOfAmountsUsesTheMeanOfTheTwoMiddleValuesRoundedToRappen() {
        // 1000.00 und 1000.01 → Mittel 1000.005 → HALF_UP → 1000.01. Der halbe Rappen entscheidet;
        // mit double käme hier ein anderer Wert heraus (ADR-9).
        givenToday("2026-04-01");
        givenCredits(
                credit("2026-02-25", "Saläreingang", "1000.00"),
                credit("2026-03-25", "Saläreingang", "1000.01"));

        Optional<BigDecimal> suggestion = service.suggestMonthlyIncome(USER_ID);

        assertThat(suggestion).hasValue(new BigDecimal("1000.01"));
        assertThat(suggestion.orElseThrow().scale()).isEqualTo(2);
    }

    // --- Leer- und Randfälle ---

    @Test
    void noCreditsAtAllYieldsNoSuggestion() {
        givenToday("2026-04-01");
        givenCredits();

        assertThat(service.suggestMonthlyIncome(USER_ID)).isEmpty();
    }

    @Test
    void zeroAmountCreditsNeverBecomeASuggestion() {
        // Median 0.00 ergäbe ein Toleranzband der Breite 0 und einen Vorschlag, den niemand als
        // Einkommen übernehmen kann.
        givenToday("2026-04-01");
        givenCredits(
                credit("2026-02-25", "Storno", "0.00"),
                credit("2026-03-25", "Storno", "0.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).isEmpty();
    }

    // --- Fenster und Mandantentrennung ---

    @Test
    void onlyTheLastTwelveMonthsAreConsideredForExactlyThisUser() {
        // Das Fenster steckt in der Query, nicht in einem Filter danach — belegt wird deshalb, mit
        // welchen Grenzen und welcher userId sie abgesetzt wird. Zugleich die Mandantentrennung:
        // ein Aufruf ohne userId-Einschränkung wäre hier sichtbar.
        givenToday("2026-08-11");
        givenCredits();

        service.suggestMonthlyIncome(USER_ID);

        verify(transactionRepository).findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
                USER_ID, LocalDate.parse("2025-08-11"), LocalDate.parse("2026-08-11"));
    }

    @Test
    void todayFollowsSwissLocalTimeNotUtc() {
        // 31.12.2026 23:30 UTC ist in Zürich bereits der 01.01.2027 00:30. Das Fenster muss vom
        // Schweizer Kalendertag ausgehen — mit UTC wäre es um einen Tag verschoben.
        when(clock.instant()).thenReturn(java.time.Instant.parse("2026-12-31T23:30:00Z"));
        givenCredits();

        service.suggestMonthlyIncome(USER_ID);

        verify(transactionRepository).findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
                USER_ID, LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"));
    }

    // --- Helfer ---

    /** Stellt die Clock auf 12:00 Ortszeit des angegebenen Tages — mitten im Tag, zonen-neutral. */
    private void givenToday(String isoDate) {
        when(clock.instant())
                .thenReturn(LocalDate.parse(isoDate).atTime(12, 0).atZone(ZURICH).toInstant());
    }

    private void givenCredits(Transaction... credits) {
        when(transactionRepository.findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
                eq(USER_ID), any(), any()))
                .thenReturn(Arrays.asList(credits));
    }

    private static Transaction credit(String isoDate, String buchungstext, String betrag) {
        return new Transaction(USER_ID, LocalDate.parse(isoDate), buchungstext,
                new BigDecimal(betrag), true, null, null);
    }
}
