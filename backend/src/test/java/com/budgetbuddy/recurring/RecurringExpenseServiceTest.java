package com.budgetbuddy.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.notification.NotificationPort;
import com.budgetbuddy.transaction.ExpenseHistoryPort;
import com.budgetbuddy.transaction.ExpenseHistoryPort.ExpenseEntry;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
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
        // IDENTITY-Spalte simuliert: save() vergibt eine ID, die als referenceId der
        // Notification wieder auftauchen muss.
        when(repository.save(any(RecurringExpense.class))).thenAnswer(invocation -> {
            RecurringExpense entity = invocation.getArgument(0);
            setId(entity, nextId.getAndIncrement());
            return entity;
        });
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

        verify(notificationPort).create(USER_ID, RecurringExpenseService.NOTIFICATION_TYPE,
                saved.getId(), "Wiederkehrende Ausgabe erkannt: NETFLIX INTERNATIONAL BV — CHF 20.90 pro Monat");
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

    /** Verschiedene Empfänger gruppieren getrennt, auch bei gleichem Betrag. */
    @Test
    void differentPayeesWithTheSameAmount_areNotOneGroup() {
        history(entry("SPOTIFY AB", "12.95", 2026, 6), entry("DEEZER SA", "12.95", 2026, 7));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
    }

    /** Pro Empfänger genau eine Zeile und eine Notification, in stabiler Reihenfolge. */
    @Test
    void severalRecurringPayees_eachGetOneRowAndOneNotification() {
        history(entry("SPOTIFY AB", "12.95", 2026, 6), entry("SPOTIFY AB", "12.95", 2026, 7),
                entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7));

        service.detect(USER_ID);

        ArgumentCaptor<RecurringExpense> captor = ArgumentCaptor.forClass(RecurringExpense.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecurringExpense::getPayeeKey)
                .containsExactly(NETFLIX, "SPOTIFY AB");
        verify(notificationPort).create(eq(USER_ID), anyString(), eq(100L), anyString());
        verify(notificationPort).create(eq(USER_ID), anyString(), eq(101L), anyString());
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
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(
                new RecurringExpense(USER_ID, NETFLIX, new BigDecimal("20.90"),
                        YearMonth.of(2026, 6), NOW)));
        history(entry(NETFLIX, "20.90", 2026, 6), entry(NETFLIX, "20.90", 2026, 7),
                entry(NETFLIX, "20.90", 2026, 8));

        service.detect(USER_ID);

        verify(repository, never()).save(any());
        verify(notificationPort, never()).create(anyLong(), anyString(), any(), anyString());
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

    // --- Helfer ---

    /** Eine DISMISSED-Zeile — die Entity hat bewusst keinen Setter dafür (BE-REC-02 stellt um). */
    private static RecurringExpense dismissed(String payeeKey, String amount) {
        RecurringExpense entity = new RecurringExpense(USER_ID, payeeKey, new BigDecimal(amount),
                YearMonth.of(2026, 1), NOW);
        setField(entity, "status", RecurringExpenseStatus.DISMISSED);
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
