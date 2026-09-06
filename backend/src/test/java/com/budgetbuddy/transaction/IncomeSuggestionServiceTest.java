package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
 * <p>Buchungstexte und Detailzeilen stammen aus den echten PDF-Fixtures
 * ({@code Post_kontoauszug.pdf}, {@code Post_Kontoauszug_2025_240_Buchungen.pdf},
 * {@code Post_Kontoauszug_2026_Juli_20_Buchungen.pdf}, {@code UBS_Konto_Bewegungen_2021_Juli.pdf})
 * — erfundene Texte würden die Normalisierung an einem Problem messen, das es so nicht gibt.
 * {@link #theRealPostFinanceYearStatementYieldsTheSalary()} geht einen Schritt weiter und parst
 * die Fixture im Test selbst, statt ihre Zeilen abzuschreiben.
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
        // «Heute» liegt hinter allen drei Buchungen: die echte Query kappt die obere Grenze bei
        // heute, eine Gutschrift mit Zukunftsdatum könnte gar nicht zurückkommen. Das Mock würde
        // das verdecken.
        givenToday("2026-12-15");
        givenCredits(
                credit("2026-09-30", "GUTSCHRIFT LOHN SEPTEMBER", "5500.00"),
                credit("2026-10-30", "GUTSCHRIFT LOHN OKTOBER", "5500.00"),
                credit("2026-11-30", "GUTSCHRIFT LOHN NOVEMBER", "5500.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("5500.00"));
    }

    @Test
    void marchWithoutUmlautOrSubstituteIsAlsoStripped() {
        // «märz» kommt in Bank-PDFs ASCII-transliteriert vor — als «maerz», aber auch ersatzlos als
        // «marz». Bliebe «marz» stehen, bekäme diese Buchung einen eigenen Schlüssel und beide
        // Gruppen hätten nur ein Vorkommen: kein Vorschlag.
        givenToday("2026-06-01");
        givenCredits(
                credit("2026-03-25", "GUTSCHRIFT LOHN MARZ", "5000.00"),
                credit("2026-04-25", "GUTSCHRIFT LOHN APRIL", "5000.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("5000.00"));
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

    // --- BE-STS-05: Gruppierung über den Absender aus der ersten Detailzeile ---

    @Test
    void differentSendersUnderTheSameBookingTextFormSeparateGroups() {
        // Der Kern von BE-STS-05, mit den Werten aus Post_Kontoauszug_2025_240_Buchungen.pdf: Bei
        // PostFinance heisst jede Gutschrift «GUTSCHRIFT», der Absender steht ausschliesslich in
        // der ersten Detailzeile. Über den Buchungstext gruppiert wäre das EINE Gruppe aus 4250.00
        // und 340.00; ihr Median 4250.00 hat ein Band von ±212.50, die 340er liegen ausserhalb und
        // kippen sie ganz — der Auszug ergäbe gar keinen Vorschlag. Über den Absender sind es zwei
        // Gruppen, und die höhere gewinnt.
        givenToday("2026-01-05");
        givenCredits(
                creditWithDetails("2025-10-31", "GUTSCHRIFT",
                        "MUSTER CONSULTING GMBH\nLOHN OKTOBER 2025", "4250.00"),
                creditWithDetails("2025-11-30", "GUTSCHRIFT",
                        "MUSTER CONSULTING GMBH\nLOHN NOVEMBER 2025", "4250.00"),
                creditWithDetails("2025-12-31", "GUTSCHRIFT",
                        "MUSTER CONSULTING GMBH\nLOHN DEZEMBER 2025", "4250.00"),
                creditWithDetails("2025-08-21", "GUTSCHRIFT",
                        "STEUERVERWALTUNG KT. BERN\nRUECKERSTATTUNG AUGUST 2025", "340.00"),
                creditWithDetails("2025-12-21", "GUTSCHRIFT",
                        "STEUERVERWALTUNG KT. BERN\nRUECKERSTATTUNG DEZEMBER 2025", "340.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("4250.00"));
    }

    @Test
    void theRealPostFinanceYearStatementYieldsTheSalary() {
        // Gegenprobe am echten Auszug statt an nachgebauten Zeilen: Der Jahresauszug wird geparst
        // und seine Gutschriften gehen unverändert in die Heuristik. Die erste Zusicherung belegt
        // die Ausgangslage — alle 15 Gutschriften teilen sich einen einzigen Buchungstext, über ihn
        // gruppiert wären sie unweigerlich eine Gruppe. Dass trotzdem 4250.00 herauskommt, kann
        // deshalb nur am Absender liegen.
        List<Transaction> credits = creditsFromFixture("/pdf/Post_Kontoauszug_2025_240_Buchungen.pdf");

        assertThat(credits).hasSize(15)
                .extracting(Transaction::getBuchungstext).containsOnly("GUTSCHRIFT");

        givenToday("2026-01-05");
        givenCredits(credits.toArray(new Transaction[0]));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("4250.00"));
    }

    @Test
    void onlyTheFirstDetailLineDecidesTheGroup() {
        // Derselbe Absender, unterschiedliche Folgezeilen: PostFinance bricht das Mitteilungsfeld
        // mitten im Wort um (Post_Kontoauszug_2026_Juli: «LOHN JULI 2026 SOWIE SPE» /
        // «SENVERGUETUNG»), und der Zweck wechselt ohnehin monatlich. Über alle Zeilen gruppiert
        // zerfiele die Gruppe in Einzelvorkommen und es gäbe keinen Vorschlag.
        givenToday("2026-09-01");
        givenCredits(
                creditWithDetails("2026-06-30", "GUTSCHRIFT",
                        "MUSTER CONSULTING GMBH\nLOHN JUNI 2026", "4250.00"),
                creditWithDetails("2026-07-28", "GUTSCHRIFT",
                        "MUSTER CONSULTING GMBH\nLOHN JULI 2026 SOWIE SPE\nSENVERGUETUNG",
                        "4250.00"),
                creditWithDetails("2026-08-31", "GUTSCHRIFT",
                        "MUSTER CONSULTING GMBH\nGRATIFIKATION UND LOHN", "4250.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("4250.00"));
    }

    @Test
    void theDetailKeyIsNormalisedLikeTheBookingText() {
        // AC4: Kleinschreibung, Monatsnamen und ziffernhaltige Tokens fallen auch aus dem neuen
        // Schlüssel heraus. Hier steht ausnahmsweise der Zweck in der ersten Zeile — ohne
        // Normalisierung hätte jeder Monat einen eigenen Schlüssel.
        givenToday("2026-06-01");
        givenCredits(
                creditWithDetails("2026-03-25", "GUTSCHRIFT", "Lohn Maerz 2026", "5000.00"),
                creditWithDetails("2026-04-25", "GUTSCHRIFT", "LOHN APRIL 2026", "5000.00"),
                creditWithDetails("2026-05-25", "GUTSCHRIFT", "lohn mai 2026", "5000.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("5000.00"));
    }

    @Test
    void transactionsWithoutDetailsFallBackToTheBookingTextIndividually() {
        // AC2: Der Rückfall gilt je Transaktion, nicht für den ganzen Datenbestand. Ein Nutzer, der
        // vor V06 importiert und danach erneut, hat beides nebeneinander. Die drei UBS-Zeilen ohne
        // Detailzeilen gruppieren über «Saläreingang» und gewinnen mit dem höheren Median; die
        // PostFinance-Zeilen daneben gruppieren über ihren Absender. Ohne den Rückfall hätten die
        // UBS-Zeilen einen leeren Schlüssel und lägen mit allem anderen ohne Details in einem Topf.
        givenToday("2026-09-01");
        givenCredits(
                credit("2026-06-25", "Saläreingang", "6800.00"),
                credit("2026-07-25", "Saläreingang", "6800.00"),
                credit("2026-08-25", "Saläreingang", "6800.00"),
                creditWithDetails("2026-07-18", "GUTSCHRIFT",
                        "MUSTER, ANNA\nRUECKZAHLUNG FERIENKASSE", "180.00"),
                creditWithDetails("2026-08-18", "GUTSCHRIFT",
                        "MUSTER, ANNA\nRUECKZAHLUNG FERIENKASSE", "180.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("6800.00"));
    }

    @Test
    void aBlankFirstDetailLineFallsBackToTheBookingText() {
        // Der Parser erzeugt das nicht — detailsAsText() verbindet nur nichtleere Zeilen. Ein
        // leerer Schlüssel würde aber sämtliche betroffenen Buchungen in einen Topf werfen, und
        // genau das ist der teuerste denkbare Fehler dieser Methode. Hier trägt nur eine der beiden
        // Gutschriften eine leere erste Zeile: Ohne den Rückfall bekäme sie einen anderen Schlüssel
        // als ihr Gegenstück und keine der beiden Gruppen käme auf zwei Monate.
        givenToday("2026-04-01");
        givenCredits(
                creditWithDetails("2026-02-25", "Saläreingang", "\nVERWENDUNGSZWECK", "6800.00"),
                credit("2026-03-25", "Saläreingang", "6800.00"));

        assertThat(service.suggestMonthlyIncome(USER_ID)).hasValue(new BigDecimal("6800.00"));
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

    /**
     * Eine Gutschrift ohne Detailzeilen — der Zustand vor {@code V06}. Alle Tests oberhalb der
     * Absender-Sektion verwenden ihn und belegen damit den Rückfall auf den Buchungstext.
     */
    private static Transaction credit(String isoDate, String buchungstext, String betrag) {
        return creditWithDetails(isoDate, buchungstext, null, betrag);
    }

    /** Wie {@link #credit}, zusätzlich mit den Detailzeilen aus {@code buchungsdetails}. */
    private static Transaction creditWithDetails(
            String isoDate, String buchungstext, String buchungsdetails, String betrag) {
        return new Transaction(USER_ID, LocalDate.parse(isoDate), buchungstext, buchungsdetails,
                new BigDecimal(betrag), true, null, null);
    }

    /**
     * Die Gutschriften einer PDF-Fixture, so abgebildet, wie {@code PdfImportService} sie
     * persistiert — {@code detailsAsText()} landet in {@code buchungsdetails}. Damit misst der Test
     * die Heuristik an echten Parser-Ausgaben statt an nachgebauten Zeilen.
     */
    private static List<Transaction> creditsFromFixture(String classpathResource) {
        byte[] pdf;
        try (InputStream in = IncomeSuggestionServiceTest.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath: " + classpathResource);
            }
            pdf = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SwissBankStatementParser().parse(pdf).stream()
                .filter(ParsedTransaction::isIncome)
                .map(t -> new Transaction(USER_ID, t.buchungsdatum(), t.buchungstext(),
                        t.detailsAsText(), t.betrag(), true, null, null))
                .toList();
    }
}
