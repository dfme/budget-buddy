package com.budgetbuddy.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.notification.NotificationPort;
import com.budgetbuddy.recurring.dto.RecurringExpenseResponse;
import com.budgetbuddy.transaction.ExpenseHistoryPort;
import com.budgetbuddy.transaction.ExpenseHistoryPort.ExpenseEntry;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit-Test der Abo-Erkennung (BE-REC-01, US-08) mit gemockten Ports: die Regel «gleicher
 * Empfänger, ähnlicher Betrag, zwei aufeinanderfolgende Monate», was daraus geschrieben wird, und
 * was bewusst nicht.
 *
 * <p>Die Ausgaben kommen bereits normalisiert über den {@link ExpenseHistoryPort} — wie der
 * Schlüssel entsteht, prüft {@code ExpenseHistoryServiceTest} im transaction-Modul.
 */
class RecurringExpenseServiceTest {

    private static final long USER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
    private static final String NETFLIX = "NETFLIX INTERNATIONAL BV";
    /** Die ID, die der gemockte {@link NotificationPort} für jede Bündel-Benachrichtigung liefert. */
    private static final long BUNDLE_ID = 7L;

    private final ExpenseHistoryPort expenseHistoryPort = mock(ExpenseHistoryPort.class);
    private final RecurringExpenseRepository repository = mock(RecurringExpenseRepository.class);
    private final NotificationPort notificationPort = mock(NotificationPort.class);
    private final Clock clock = Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    private final RecurringExpenseService service = new RecurringExpenseService(
            expenseHistoryPort, repository, notificationPort, clock);

    private final AtomicLong nextId = new AtomicLong(100);

    @BeforeEach
    void repositoryAssignsIdsOnSave() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of());
        // IDENTITY-Spalte simuliert: save() vergibt eine ID.
        when(repository.save(any(RecurringExpense.class))).thenAnswer(invocation -> {
            RecurringExpense entity = invocation.getArgument(0);
            setId(entity, nextId.getAndIncrement());
            return entity;
        });
        // Die Bündel-Benachrichtigung bekommt ihre ID vom Notification-Modul; die Zeilen müssen
        // sie tragen (FE-NOTIF-04).
        when(notificationPort.create(anyLong(), anyString(), any(), anyString())).thenReturn(BUNDLE_ID);
    }

    private static ExpenseEntry entry(String payee, String amount, int year, int month) {
        return new ExpenseEntry(payee, new BigDecimal(amount), YearMonth.of(year, month));
    }

    private void history(ExpenseEntry... entries) {
        when(expenseHistoryPort.expenseHistory(USER_ID)).thenReturn(List.of(entries));
    }

    private RecurringExpense captureSaved() {
        ArgumentCaptor<RecurringExpense> captor = ArgumentCaptor.forClass(RecurringExpense.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    // --- Die Regel ---

    /** AC 1 + 3 + 4: Zwei Folgemonate, gleicher Betrag → DETECTED-Zeile plus Notification. */
    @Test
    void sameAmountInTwoConsecutiveMonths_isDetectedAndNotified() {
        history(entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7));

        service.detect(USER_ID);

        RecurringExpense saved = captureSaved();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getPayeeKey()).isEqualTo(NETFLIX);
        assertThat(saved.getAmount()).isEqualByComparingTo("20.90");
        assertThat(saved.getStatus()).isEqualTo(RecurringExpenseStatus.DETECTED);
        assertThat(saved.getFirstDetectedMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getNotificationId()).isEqualTo(BUNDLE_ID);

        // Kein referenceId mehr: der Verweis läuft seit FE-NOTIF-04 von der Zeile zur Notification.
        verify(notificationPort).create(USER_ID, RecurringExpenseService.NOTIFICATION_TYPE,
                null, "1 neues Abo erkannt: NETFLIX INTERNATIONAL BV");
    }

    /** «aufeinanderfolgend» heisst Kalender-Folgemonat: Januar und März reichen nicht. */
    @Test
    void sameAmountWithAGapMonth_isNotDetected() {
        history(entry(NETFLIX, "20.90", 2026, 1), entry(NETFLIX, "20.90", 2026, 3));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
        verify(notificationPort, never()).create(anyLong(), anyString(), any(), anyString());
    }

    /** Ein einzelner Monat ist keine Reihe — auch nicht mit zwei Buchungen darin. */
    @Test
    void twoBookingsInTheSameMonth_areNotDetected() {
        history(entry(NETFLIX, "20.90", 2026, 7), entry(NETFLIX, "20.90", 2026, 7));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
    }

    /** ±2 % um den früheren Betrag: 20.00 → 20.40 liegt genau auf der Grenze und zählt. */
    @Test
    void amountsWithinTwoPercent_areDetected() {
        history(entry(NETFLIX, "20.00", 2026, 6), entry(NETFLIX, "20.40", 2026, 7));

        service.detect(USER_ID);

        assertThat(captureSaved().getAmount()).isEqualByComparingTo("20.40");
    }

    /** 20.00 → 20.41 liegt einen Rappen darüber und zählt nicht. */
    @Test
    void amountsOutsideTwoPercent_areNotDetected() {
        history(entry(NETFLIX, "20.00", 2026, 6), entry(NETFLIX, "20.41", 2026, 7));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
    }

    /** Die Toleranz gilt in beide Richtungen — ein leicht gesunkener Betrag qualifiziert ebenso. */
    @Test
    void aSlightlyLowerAmount_isDetected() {
        history(entry(NETFLIX, "20.00", 2026, 6), entry(NETFLIX, "19.60", 2026, 7));

        service.detect(USER_ID);

        assertThat(captureSaved().getAmount()).isEqualByComparingTo("19.60");
    }

    /** Ein Paar aus Nullbuchungen ist kein Abo über CHF 0.00. */
    @Test
    void zeroAmounts_neverQualify() {
        history(entry(NETFLIX, "0.00", 2026, 6), entry(NETFLIX, "0.00", 2026, 7));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
    }

    /** Gespeichert wird der Betrag des jüngsten Paars, als Beginn der erste Monat der Reihe. */
    @Test
    void longerSeries_storesTheLatestAmountAndTheFirstMonth() {
        history(entry(NETFLIX, "17.90", 2026, 3), entry(NETFLIX, "17.90", 2026, 4),
                entry(NETFLIX, "18.20", 2026, 5), entry(NETFLIX, "18.20", 2026, 6));

        service.detect(USER_ID);

        RecurringExpense saved = captureSaved();
        assertThat(saved.getAmount()).isEqualByComparingTo("18.20");
        assertThat(saved.getFirstDetectedMonth()).isEqualTo(YearMonth.of(2026, 3));
    }

    /** Ein Preissprung über die Toleranz hinaus unterbricht die Reihe; sie beginnt danach neu. */
    @Test
    void aPriceJump_startsTheSeriesAfterTheJump() {
        history(entry(NETFLIX, "17.90", 2026, 3), entry(NETFLIX, "20.90", 2026, 4),
                entry(NETFLIX, "20.90", 2026, 5));

        service.detect(USER_ID);

        RecurringExpense saved = captureSaved();
        assertThat(saved.getAmount()).isEqualByComparingTo("20.90");
        assertThat(saved.getFirstDetectedMonth()).isEqualTo(YearMonth.of(2026, 4));
    }

    /**
     * Review PR #298: Reisst die Reihe ab und setzt später wieder ein, ist der Erstmonat der
     * Beginn des jüngeren Abschnitts — nicht der des ersten. Sonst behauptete die Zeile «seit
     * Januar» eine Laufzeit, die die Daten nicht hergeben.
     */
    @Test
    void aGapInTheSeries_restartsItAtTheLaterSegment() {
        history(entry(NETFLIX, "17.90", 2026, 1), entry(NETFLIX, "17.90", 2026, 2),
                entry(NETFLIX, "17.90", 2026, 8), entry(NETFLIX, "17.90", 2026, 9));

        service.detect(USER_ID);

        RecurringExpense saved = captureSaved();
        assertThat(saved.getFirstDetectedMonth()).isEqualTo(YearMonth.of(2026, 8));
    }

    /**
     * Gegenprobe zur Lückenregel: mehrere Buchungen im selben Monat sind keine Lücke — die Reihe
     * läuft durch und behält ihren Erstmonat.
     */
    @Test
    void severalBookingsPerMonth_doNotRestartTheSeries() {
        history(entry("COOP BERN", "50.00", 2026, 5),
                entry("COOP BERN", "49.90", 2026, 6), entry("COOP BERN", "50.00", 2026, 6),
                entry("COOP BERN", "50.00", 2026, 7));

        service.detect(USER_ID);

        RecurringExpense saved = captureSaved();
        assertThat(saved.getFirstDetectedMonth()).isEqualTo(YearMonth.of(2026, 5));
    }

    /**
     * Review PR #298: Mehrere Buchungen im selben Monat, mehrere gleichzeitig qualifizierende
     * Paare — der gespeicherte Betrag darf nicht von der Zeilenreihenfolge der Query abhängen.
     * Gespeichert wird der höchste qualifizierende Betrag des jüngsten Paars, in beiden
     * Reihenfolgen derselbe.
     */
    @Test
    void severalBookingsPerMonth_storeTheSameAmountRegardlessOfInputOrder() {
        ExpenseEntry juniKlein = entry("COOP BERN", "49.90", 2026, 6);
        ExpenseEntry juniGross = entry("COOP BERN", "50.00", 2026, 6);
        ExpenseEntry juliKlein = entry("COOP BERN", "49.90", 2026, 7);
        ExpenseEntry juliGross = entry("COOP BERN", "50.00", 2026, 7);

        history(juniKlein, juniGross, juliKlein, juliGross);
        service.detect(USER_ID);
        BigDecimal ersteReihenfolge = captureSaved().getAmount();

        org.mockito.Mockito.reset(repository);
        repositoryAssignsIdsOnSave();
        history(juliGross, juniGross, juliKlein, juniKlein);
        service.detect(USER_ID);
        BigDecimal zweiteReihenfolge = captureSaved().getAmount();

        assertThat(ersteReihenfolge).isEqualByComparingTo("50.00");
        assertThat(zweiteReihenfolge).isEqualByComparingTo(ersteReihenfolge);
    }

    /** Verschiedene Empfänger gruppieren getrennt, auch bei gleichem Betrag. */
    @Test
    void differentPayeesWithTheSameAmount_areNotOneGroup() {
        history(entry("SPOTIFY AB", "12.95", 2026, 6), entry("DEEZER SA", "12.95", 2026, 7));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
    }

    /**
     * FE-NOTIF-04 (#336): Pro Empfänger genau eine Zeile, in stabiler Reihenfolge — aber pro Lauf
     * nur <em>eine</em> Notification, an der alle Zeilen hängen. Vorher war es eine pro Zeile;
     * 27 erkannte Abos ergaben 27 Benachrichtigungen, die nur einzeln zu lesen waren.
     */
    @Test
    void severalRecurringPayees_eachGetOneRowButShareOneNotification() {
        history(entry("SPOTIFY AB", "12.95", 2026, 6), entry("SPOTIFY AB", "12.95", 2026, 7),
                entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7));

        service.detect(USER_ID);

        ArgumentCaptor<RecurringExpense> captor = ArgumentCaptor.forClass(RecurringExpense.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecurringExpense::getPayeeKey)
                .containsExactly(NETFLIX, "SPOTIFY AB");
        assertThat(captor.getAllValues()).extracting(RecurringExpense::getNotificationId)
                .containsOnly(BUNDLE_ID);
        verify(notificationPort, org.mockito.Mockito.times(1))
                .create(anyLong(), anyString(), any(), anyString());
        verify(notificationPort).create(USER_ID, RecurringExpenseService.NOTIFICATION_TYPE, null,
                "2 neue Abos erkannt: NETFLIX INTERNATIONAL BV, SPOTIFY AB");
    }

    /** Die Notification entsteht vor den Zeilen — sie tragen ihre ID; ohne Treffer gar nicht. */
    @Test
    void theNotificationIsCreatedBeforeTheRowsThatReferenceIt() {
        history(entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7));

        service.detect(USER_ID);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(notificationPort, repository);
        inOrder.verify(notificationPort).create(anyLong(), anyString(), any(), anyString());
        inOrder.verify(repository).save(any(RecurringExpense.class));
    }

    // --- Anzeigetext der Bündel-Benachrichtigung ---

    @Test
    void messageNamesUpToThreePayees() {
        assertThat(RecurringExpenseService.message(List.of("A")))
                .isEqualTo("1 neues Abo erkannt: A");
        assertThat(RecurringExpenseService.message(List.of("A", "B", "C")))
                .isEqualTo("3 neue Abos erkannt: A, B, C");
    }

    @Test
    void messageSummarisesTheRestBeyondThreePayees() {
        assertThat(RecurringExpenseService.message(List.of("A", "B", "C", "D")))
                .isEqualTo("4 neue Abos erkannt: A, B, C und 1 weiteres");
        assertThat(RecurringExpenseService.message(List.of("A", "B", "C", "D", "E", "F", "G")))
                .isEqualTo("7 neue Abos erkannt: A, B, C und 4 weitere");
    }

    // --- Bekannte Empfänger ---

    /** AC 5: «Kein Abo» wirkt dauerhaft — der Empfänger wird nicht erneut erkannt. */
    @Test
    void dismissedPayee_isExcludedFromDetection() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(
                dismissed(NETFLIX, "20.90")));
        history(entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
        verify(notificationPort, never()).create(anyLong(), anyString(), any(), anyString());
    }

    /** Ein bereits erkannter Empfänger erzeugt beim nächsten Import keine zweite Zeile und keine zweite Notification. */
    @Test
    void alreadyDetectedPayee_isNotDetectedAgain() {
        RecurringExpense known = detectedRow(NETFLIX, "20.90", YearMonth.of(2026, 6));
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(known));
        history(entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7),
                entry(NETFLIX, "20.90", 2026, 8));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
        verify(notificationPort, never()).create(anyLong(), anyString(), any(), anyString());
        // Unverändert, weil sich nichts geändert hat — nicht, weil nicht hingesehen wurde.
        assertThat(known.getAmount()).isEqualByComparingTo("20.90");
        assertThat(known.getStatus()).isEqualTo(RecurringExpenseStatus.DETECTED);
    }

    // --- BE-REC-04: bestehende Zeilen werden neu bewertet ---

    /**
     * AC 1: Preissprung über die Toleranz hinaus. 17.90 in Jan–Mär, 19.90 ab Apr — das Paar über
     * den Sprung qualifiziert nicht, die Reihe beginnt im April neu, und die Zeile folgt ihr.
     *
     * <p>Betrag <em>und</em> Erstmonat: {@code qualify} setzt beide gemeinsam, und eine Zeile mit
     * dem April-Betrag und «seit Januar» behauptete eine Laufzeit, die die Daten nicht hergeben.
     */
    @Test
    void priceJumpBeyondTolerance_updatesAmountAndFirstMonth() {
        RecurringExpense known = detectedRow(NETFLIX, "17.90", YearMonth.of(2026, 1));
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(known));
        history(entry(NETFLIX, "17.90", 2026, 1), entry(NETFLIX, "17.90", 2026, 2),
                entry(NETFLIX, "17.90", 2026, 3), entry(NETFLIX, "19.90", 2026, 4),
                entry(NETFLIX, "19.90", 2026, 5), entry(NETFLIX, "19.90", 2026, 6));

        service.detect(USER_ID);

        assertThat(known.getAmount()).isEqualByComparingTo("19.90");
        assertThat(known.getFirstDetectedMonth()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(known.getStatus()).isEqualTo(RecurringExpenseStatus.DETECTED);
    }

    /** AC 4: Die Aktualisierung ist kein Fund — sie erzeugt kein Bündel und keine zweite Zeile. */
    @Test
    void updatingAKnownRow_writesNoNotificationAndNoSecondRow() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(
                detectedRow(NETFLIX, "17.90", YearMonth.of(2026, 1))));
        history(entry(NETFLIX, "19.90", 2026, 5), entry(NETFLIX, "19.90", 2026, 6));

        service.detect(USER_ID);

        verify(notificationPort, never()).create(anyLong(), anyString(), any(), anyString());
        verify(repository, never()).save(any());
    }

    /**
     * AC 2: Ohne Abbuchung in den drei jüngsten Monaten der Historie gilt die Reihe als
     * ausgelaufen. Gemessen wird gegen den jüngsten Monat der <em>Historie</em> (hier September),
     * nicht gegen die Uhr.
     */
    @Test
    void payeeWithoutADebitInTheActivityWindow_becomesEnded() {
        RecurringExpense known = detectedRow(NETFLIX, "20.90", YearMonth.of(2026, 1));
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(known));
        history(entry(NETFLIX, "20.90", 2026, 1), entry(NETFLIX, "20.90", 2026, 2),
                entry("COOP BERN", "45.60", 2026, 9));

        service.detect(USER_ID);

        assertThat(known.getStatus()).isEqualTo(RecurringExpenseStatus.ENDED);
    }

    /** Die Fenstergrenze selbst zählt noch als aktiv: jüngster Monat September, Abbuchung im Juli. */
    @Test
    void aDebitAtTheEdgeOfTheWindow_keepsThePayeeDetected() {
        RecurringExpense known = detectedRow(NETFLIX, "20.90", YearMonth.of(2026, 6));
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(known));
        history(entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7),
                entry("COOP BERN", "45.60", 2026, 9));

        service.detect(USER_ID);

        assertThat(known.getStatus()).isEqualTo(RecurringExpenseStatus.DETECTED);
    }

    /** ENDED ist kein Endzustand: bucht der Empfänger wieder ab, läuft die Zeile wieder — lautlos. */
    @Test
    void endedPayeeThatDebitsAgain_returnsToDetectedWithoutANotification() {
        RecurringExpense known = detectedRow(NETFLIX, "20.90", YearMonth.of(2026, 1));
        known.markEnded();
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(known));
        history(entry(NETFLIX, "22.90", 2026, 8), entry(NETFLIX, "22.90", 2026, 9));

        service.detect(USER_ID);

        assertThat(known.getStatus()).isEqualTo(RecurringExpenseStatus.DETECTED);
        assertThat(known.getAmount()).isEqualByComparingTo("22.90");
        verify(notificationPort, never()).create(anyLong(), anyString(), any(), anyString());
    }

    /**
     * AC 3: {@code DISMISSED} ist terminal. Weder der Betrag noch der Status werden angefasst,
     * auch wenn der Empfänger munter weiter abbucht — «war nie ein Abo» ist die Aussage des
     * Nutzers und keine Beobachtung, die sich widerlegen liesse.
     */
    @Test
    void dismissedPayee_isNeitherReevaluatedNorReactivated() {
        RecurringExpense verneint = dismissed(NETFLIX, "20.90");
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(verneint));
        history(entry(NETFLIX, "29.90", 2026, 8), entry(NETFLIX, "29.90", 2026, 9));

        service.detect(USER_ID);

        assertThat(verneint.getStatus()).isEqualTo(RecurringExpenseStatus.DISMISSED);
        assertThat(verneint.getAmount()).isEqualByComparingTo("20.90");
        assertThat(verneint.getFirstDetectedMonth()).isEqualTo(YearMonth.of(2026, 1));
    }

    /**
     * Ein nachgereichter Altauszug: der Empfänger qualifiziert, hat aber lange vor dem jüngsten
     * Monat der Historie zuletzt abgebucht. Die Zeile entsteht direkt als {@code ENDED} — und geht
     * trotzdem ins Bündel, denn gefunden wurde sie.
     */
    @Test
    void aNewlyDetectedPayeeThatAlreadyStopped_isCreatedAsEnded() {
        history(entry(NETFLIX, "20.90", 2026, 1), entry(NETFLIX, "20.90", 2026, 2),
                entry("COOP BERN", "45.60", 2026, 9));

        service.detect(USER_ID);

        assertThat(captureSaved().getStatus()).isEqualTo(RecurringExpenseStatus.ENDED);
        verify(notificationPort).create(USER_ID, RecurringExpenseService.NOTIFICATION_TYPE,
                null, "1 neues Abo erkannt: " + NETFLIX);
    }

    /**
     * Eine Zeile, deren Empfänger in der Historie überhaupt nicht mehr vorkommt, läuft aus. Der
     * Fall existiert nur, weil die Neubewertung über die <em>Zeilen</em> iteriert und nicht über
     * die Gruppen der Historie — ein Durchgang über die Gruppen liesse genau sie stehen.
     */
    @Test
    void aRowWhosePayeeIsAbsentFromTheHistory_becomesEnded() {
        RecurringExpense known = detectedRow("SPOTIFY", "12.95", YearMonth.of(2026, 1));
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(known));
        history(entry("COOP BERN", "45.60", 2026, 9));

        service.detect(USER_ID);

        assertThat(known.getStatus()).isEqualTo(RecurringExpenseStatus.ENDED);
        assertThat(known.getAmount()).isEqualByComparingTo("12.95");
    }

    /** Ohne Belastungen wird nichts bewertet: keine Daten sind kein Beleg für ein Ende. */
    @Test
    void anEmptyHistory_leavesExistingRowsUntouched() {
        RecurringExpense known = detectedRow(NETFLIX, "20.90", YearMonth.of(2026, 1));
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(known));
        history();

        service.detect(USER_ID);

        assertThat(known.getStatus()).isEqualTo(RecurringExpenseStatus.DETECTED);
    }

    /** Der Ausschluss greift unabhängig von der Schreibweise, mit der der Port den Schlüssel liefert. */
    @Test
    void knownPayeeMatchesCaseInsensitively() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(
                dismissed(NETFLIX, "20.90")));
        history(entry("Netflix International BV", "20.90", 2026, 6),
                entry("netflix international bv", "20.90", 2026, 7));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
    }

    /** V11-Vertrag: Der Schlüssel wird in Grossschreibung gespeichert, was auch immer hereinkommt. */
    @Test
    void payeeKeyIsStoredInUpperCase() {
        history(entry("Netflix International BV", "20.90", 2026, 6),
                entry("netflix international bv", "20.90", 2026, 7));

        service.detect(USER_ID);

        assertThat(captureSaved().getPayeeKey()).isEqualTo(NETFLIX);
    }

    @Test
    void noExpenses_writesNothing() {
        history();

        service.detect(USER_ID);

        verify(repository, never()).save(any());
        verify(notificationPort, never()).create(anyLong(), anyString(), any(), anyString());
    }

    // --- BE-REC-02: list() ---

    /**
     * Beide Status gehören in die Antwort (FE-NOTIF-03): die Übersicht trennt selbst nach
     * {@code status}. Vor #333 filterte die Abfrage auf DETECTED.
     */
    @Test
    void listReturnsDetectedAndDismissedEntriesWithTheirStatus() {
        RecurringExpense detected = withId(NETFLIX, "20.90", 200L);
        RecurringExpense dismissed = withId("SPOTIFY AB", "12.95", 201L);
        dismissed.dismiss();
        when(repository.findByUserIdOrderByPayeeKeyAsc(USER_ID))
                .thenReturn(List.of(detected, dismissed));
        when(notificationPort.unreadIds(USER_ID, RecurringExpenseService.NOTIFICATION_TYPE))
                .thenReturn(Set.of());

        List<RecurringExpenseResponse> result = service.list(USER_ID);

        assertThat(result).extracting(RecurringExpenseResponse::payeeKey, RecurringExpenseResponse::status)
                .containsExactly(
                        tuple(NETFLIX, RecurringExpenseStatus.DETECTED),
                        tuple("SPOTIFY AB", RecurringExpenseStatus.DISMISSED));
        verify(repository).findByUserIdOrderByPayeeKeyAsc(USER_ID);
    }

    /**
     * Das «Neu»-Flag kommt aus der ungelesenen Bündel-Notification, nicht aus einem eigenen Feld
     * — alle Zeilen eines Bündels sind gemeinsam neu oder gemeinsam nicht (FE-NOTIF-04).
     */
    @Test
    void listMarksEntriesOfAnUnreadBundleAsNew() {
        RecurringExpense inUnreadBundle = withId(NETFLIX, "20.90", 200L, BUNDLE_ID);
        RecurringExpense alsoInUnreadBundle = withId("SPOTIFY AB", "12.95", 201L, BUNDLE_ID);
        RecurringExpense inReadBundle = withId("SWISSCOM", "59.00", 202L, 8L);
        RecurringExpense withoutBundle = withId("ZALANDO", "30.00", 203L, null);
        when(repository.findByUserIdOrderByPayeeKeyAsc(USER_ID))
                .thenReturn(List.of(inUnreadBundle, alsoInUnreadBundle, inReadBundle, withoutBundle));
        when(notificationPort.unreadIds(USER_ID, RecurringExpenseService.NOTIFICATION_TYPE))
                .thenReturn(Set.of(BUNDLE_ID));

        List<RecurringExpenseResponse> result = service.list(USER_ID);

        assertThat(result).extracting(RecurringExpenseResponse::id, RecurringExpenseResponse::isNew)
                .containsExactly(
                        tuple(200L, true), tuple(201L, true), tuple(202L, false), tuple(203L, false));
    }

    @Test
    void listReturnsEmptyForAUserWithoutEntries() {
        when(repository.findByUserIdOrderByPayeeKeyAsc(USER_ID)).thenReturn(List.of());
        when(notificationPort.unreadIds(USER_ID, RecurringExpenseService.NOTIFICATION_TYPE))
                .thenReturn(Set.of());

        assertThat(service.list(USER_ID)).isEmpty();
    }

    // --- FE-FC-05 / BE-REC-04: detectedAmounts() für den Safe-to-Spend ---

    /**
     * Nur die Beträge, nur {@code DETECTED}: der Port fragt das Repository mit dem Status ab und
     * reicht keine Entities weiter. Ob eine Zeile noch läuft, hat {@code detect} beim letzten
     * Import entschieden — seit BE-REC-04 prüft der Lesepfad das nicht mehr selbst und lädt dafür
     * auch keine Historie mehr nach.
     */
    @Test
    void detectedAmountsReturnsTheAmountsOfDetectedEntriesOnly() {
        when(repository.findByUserIdAndStatus(USER_ID, RecurringExpenseStatus.DETECTED))
                .thenReturn(List.of(withId(NETFLIX, "20.90", 200L), withId("SWISSCOM", "59.00", 201L)));

        List<BigDecimal> result = service.detectedAmounts(USER_ID);

        assertThat(result).containsExactly(new BigDecimal("20.90"), new BigDecimal("59.00"));
        verify(repository).findByUserIdAndStatus(USER_ID, RecurringExpenseStatus.DETECTED);
        verify(repository, never()).findByUserIdAndStatus(USER_ID, RecurringExpenseStatus.DISMISSED);
        verify(repository, never()).findByUserIdAndStatus(USER_ID, RecurringExpenseStatus.ENDED);
        verify(expenseHistoryPort, never()).expenseHistory(anyLong());
    }

    @Test
    void detectedAmountsIsEmptyForAUserWithoutDetectedEntries() {
        when(repository.findByUserIdAndStatus(USER_ID, RecurringExpenseStatus.DETECTED))
                .thenReturn(List.of());

        assertThat(service.detectedAmounts(USER_ID)).isEmpty();
        verify(expenseHistoryPort, never()).expenseHistory(anyLong());
    }

    // --- BE-REC-02: dismiss() ---

    @Test
    void dismissSetsStatusToDismissedAndReturnsTheUpdatedState() {
        RecurringExpense entity = withId(NETFLIX, "20.90", 200L);
        when(repository.findByIdAndUserId(200L, USER_ID)).thenReturn(Optional.of(entity));

        RecurringExpenseResponse response = service.dismiss(USER_ID, 200L);

        assertThat(entity.getStatus()).isEqualTo(RecurringExpenseStatus.DISMISSED);
        assertThat(response.status()).isEqualTo(RecurringExpenseStatus.DISMISSED);
    }

    // --- BE-REC-03: dismiss() markiert die Bündel-Benachrichtigung als gelesen (FE-NOTIF-04) ---

    /** Der letzte offene Eintrag des Bündels wird verneint → die Benachrichtigung ist erledigt. */
    @Test
    void dismissingTheLastOpenEntryOfABundleMarksItsNotificationAsRead() {
        RecurringExpense entity = withId(NETFLIX, "20.90", 200L, BUNDLE_ID);
        RecurringExpense alreadyDismissed = withId("SPOTIFY AB", "12.95", 201L, BUNDLE_ID);
        alreadyDismissed.dismiss();
        when(repository.findByIdAndUserId(200L, USER_ID)).thenReturn(Optional.of(entity));
        when(repository.findByUserIdAndNotificationId(USER_ID, BUNDLE_ID))
                .thenReturn(List.of(entity, alreadyDismissed));

        RecurringExpenseResponse response = service.dismiss(USER_ID, 200L);

        verify(notificationPort).markRead(USER_ID, BUNDLE_ID);
        // Ein verneinter Eintrag ist nie neu — ein Nachfragen beim Port wäre eine Abfrage, deren
        // Ergebnis feststeht.
        assertThat(response.isNew()).isFalse();
        verify(notificationPort, never()).unreadIds(anyLong(), anyString());
    }

    /** Solange ein anderer Eintrag des Bündels offen ist, bleibt die Benachrichtigung ungelesen. */
    @Test
    void dismissingOneOfSeveralOpenEntriesLeavesTheNotificationUnread() {
        RecurringExpense entity = withId(NETFLIX, "20.90", 200L, BUNDLE_ID);
        RecurringExpense stillOpen = withId("SPOTIFY AB", "12.95", 201L, BUNDLE_ID);
        when(repository.findByIdAndUserId(200L, USER_ID)).thenReturn(Optional.of(entity));
        when(repository.findByUserIdAndNotificationId(USER_ID, BUNDLE_ID))
                .thenReturn(List.of(entity, stillOpen));

        RecurringExpenseResponse response = service.dismiss(USER_ID, 200L);

        assertThat(entity.getStatus()).isEqualTo(RecurringExpenseStatus.DISMISSED);
        assertThat(response.isNew()).isFalse();
        verify(notificationPort, never()).markRead(anyLong(), anyLong());
    }

    /** Bestandsdaten ohne Bündel (V14-Backfill fand keine Notification): kein Port-Aufruf. */
    @Test
    void dismissingAnEntryWithoutABundleDoesNotTouchTheNotificationPort() {
        RecurringExpense entity = withId(NETFLIX, "20.90", 200L, null);
        when(repository.findByIdAndUserId(200L, USER_ID)).thenReturn(Optional.of(entity));

        service.dismiss(USER_ID, 200L);

        assertThat(entity.getStatus()).isEqualTo(RecurringExpenseStatus.DISMISSED);
        verify(notificationPort, never()).markRead(anyLong(), anyLong());
        verify(repository, never()).findByUserIdAndNotificationId(anyLong(), anyLong());
    }

    @Test
    void dismissIsIdempotent() {
        RecurringExpense entity = withId(NETFLIX, "20.90", 200L, BUNDLE_ID);
        entity.dismiss();
        when(repository.findByIdAndUserId(200L, USER_ID)).thenReturn(Optional.of(entity));
        when(repository.findByUserIdAndNotificationId(USER_ID, BUNDLE_ID)).thenReturn(List.of(entity));

        RecurringExpenseResponse response = service.dismiss(USER_ID, 200L);

        assertThat(response.status()).isEqualTo(RecurringExpenseStatus.DISMISSED);
        // Auch beim zweiten Mal wird markiert — der Port ist idempotent, ein Abbruch hier würde
        // eine beim ersten Mal fehlgeschlagene Markierung nie nachholen.
        verify(notificationPort).markRead(USER_ID, BUNDLE_ID);
    }

    @Test
    void dismissThrowsNotFoundWhenTheEntryIsMissingOrForeign() {
        when(repository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(USER_ID, 999L))
                .isInstanceOf(RecurringExpenseNotFoundException.class);
    }

    // --- Helfer ---

    /** Eine DETECTED-Zeile mit gesetzter ID im Bündel {@link #BUNDLE_ID}, wie sie aus der Datenbank käme. */
    private static RecurringExpense withId(String payeeKey, String amount, long id) {
        return withId(payeeKey, amount, id, BUNDLE_ID);
    }

    /** Eine DETECTED-Zeile mit gesetzter ID und explizitem Bündel ({@code null} = ohne). */
    private static RecurringExpense withId(String payeeKey, String amount, long id, Long notificationId) {
        RecurringExpense entity = new RecurringExpense(USER_ID, payeeKey, new BigDecimal(amount),
                YearMonth.of(2026, 1), NOW, notificationId);
        setField(entity, "id", id);
        return entity;
    }

    /** Eine DETECTED-Zeile ohne ID, wie sie {@code findByUserId} für die Neubewertung liefert. */
    private static RecurringExpense detectedRow(String payeeKey, String amount, YearMonth firstMonth) {
        return new RecurringExpense(USER_ID, payeeKey, new BigDecimal(amount), firstMonth, NOW,
                BUNDLE_ID);
    }

    /** Eine DISMISSED-Zeile, über {@link RecurringExpense#dismiss()} (BE-REC-02). */
    private static RecurringExpense dismissed(String payeeKey, String amount) {
        RecurringExpense entity = new RecurringExpense(USER_ID, payeeKey, new BigDecimal(amount),
                YearMonth.of(2026, 1), NOW, BUNDLE_ID);
        entity.dismiss();
        return entity;
    }

    private static void setId(RecurringExpense entity, long id) {
        setField(entity, "id", id);
    }

    private static void setField(RecurringExpense entity, String name, Object value) {
        try {
            Field field = RecurringExpense.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
