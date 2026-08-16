package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.transaction.dto.TransactionListResponse;
import com.budgetbuddy.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

/**
 * Unit-Test von {@link TransactionListService} (FE-CAT-03, FE-CAT-05).
 *
 * <p>Seit FE-CAT-05 laufen Filter, Sortierung und Begrenzung in der Datenbank. Dieser Test prüft
 * deshalb, was darüber liegt: <em>welche</em> Repository-Methode mit <em>welchen</em> Argumenten
 * gerufen wird, wie die Seitenparameter geprüft werden und was aus dem {@link Slice} in die
 * Antwort wandert. Dass die Query dabei die richtigen Zeilen liefert — Kategorie-Filter inklusive
 * der {@code null}-Sonderregel, Reihenfolge, Seitengrenzen — kann ein Mock nicht belegen; das steht
 * in {@code TransactionListControllerIntegrationTest} gegen echtes PostgreSQL.
 *
 * <p>{@link Transaction} wird gemockt statt konstruiert, weil die {@code id} sonst nur über JPA
 * gesetzt würde — sie ist hier aber das Feld, das die Sortierung und den späteren
 * {@code PUT /transactions/{id}/category} trägt.
 */
class TransactionListServiceTest {

    private static final long USER_ID = 42L;
    private static final int PAGE = 0;
    private static final int SIZE = 20;

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final TransactionListService service = new TransactionListService(repository);

    private static Transaction expense(long id, String datum, String text, String betrag,
            String category) {
        Transaction tx = mock(Transaction.class);
        when(tx.getId()).thenReturn(id);
        when(tx.getBuchungsdatum()).thenReturn(LocalDate.parse(datum));
        when(tx.getBuchungstext()).thenReturn(text);
        when(tx.getBetrag()).thenReturn(new BigDecimal(betrag));
        when(tx.getCategory()).thenReturn(category);
        return tx;
    }

    /** Stubt den ungefilterten Pfad mit einer letzten Seite (kein {@code hasNext}). */
    private void stubExpenses(Transaction... expenses) {
        stubExpenses(false, expenses);
    }

    private void stubExpenses(boolean hasNext, Transaction... expenses) {
        when(repository.findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                        any(), any(), any(), any(Pageable.class)))
                .thenReturn(slice(hasNext, expenses));
    }

    private void stubFilteredExpenses(boolean hasNext, Transaction... expenses) {
        when(repository.findExpensesByCategoryLabel(
                        any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(slice(hasNext, expenses));
    }

    private static Slice<Transaction> slice(boolean hasNext, Transaction... expenses) {
        return new SliceImpl<>(List.of(expenses), Pageable.ofSize(SIZE), hasNext);
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void queriesTheFullMonthForTheGivenUser() {
        stubExpenses();

        service.list(USER_ID, "2026-02", null, PAGE, SIZE);

        // Februar 2026 hat 28 Tage — die Obergrenze kommt aus YearMonth, nicht aus einer Konstanten.
        verify(repository).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                eq(USER_ID), eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)),
                any(Pageable.class));
    }

    @Test
    void requestsExactlyTheAskedForWindow() {
        stubExpenses();

        service.list(USER_ID, "2026-07", null, 2, 20);

        assertThat(capturedPageable().getPageNumber()).isEqualTo(2);
        assertThat(capturedPageable().getPageSize()).isEqualTo(20);
    }

    @Test
    void sortsByDateDescendingAndIdDescendingWithinTheSameDay() {
        // Die Reihenfolge gehört seit FE-CAT-05 in die Query: ein Sortieren in Java könnte nur die
        // bereits geladene Seite ordnen — und damit stünden die falschen Zeilen darauf.
        stubExpenses();

        service.list(USER_ID, "2026-07", null, PAGE, SIZE);

        assertThat(capturedPageable().getSort())
                .containsExactly(
                        Sort.Order.desc("buchungsdatum"),
                        Sort.Order.desc("id"));
    }

    @Test
    void usesTheFilteringQueryWhenACategoryIsGiven() {
        stubFilteredExpenses(false);

        service.list(USER_ID, "2026-07", "Lebensmittel", PAGE, SIZE);

        // 'Sonstiges' geht als Parameter in die Query, damit das Label nicht ein zweites Mal als
        // Literal im JPQL steht.
        verify(repository).findExpensesByCategoryLabel(
                eq(USER_ID), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31)),
                eq("Lebensmittel"), eq("Sonstiges"), any(Pageable.class));
        verify(repository, never()).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                any(), any(), any(), any(Pageable.class));
    }

    @Test
    void blankCategoryIsTreatedAsNoFilter() {
        stubExpenses();

        service.list(USER_ID, "2026-07", "  ", PAGE, SIZE);

        verify(repository).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                any(), any(), any(), any(Pageable.class));
        verify(repository, never()).findExpensesByCategoryLabel(
                any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void passesTheUnknownCategoryLabelToTheQueryInsteadOfRejectingIt() {
        // Was die Übersicht anzeigt, muss sich auch aufklappen lassen. Das Frontend schickt genau
        // dieses Label zurück — eine Validierung gegen das Enum ergäbe hier eine 400.
        stubFilteredExpenses(false);

        service.list(USER_ID, "2026-07", "Kryptowährung", PAGE, SIZE);

        verify(repository).findExpensesByCategoryLabel(
                any(), any(), any(), eq("Kryptowährung"), any(), any(Pageable.class));
    }

    @Test
    void returnsTheFieldsNeededForTheCategoryDropdown() {
        stubExpenses(expense(7L, "2026-07-03", "MIGROS BERN", "60.00", "Lebensmittel"));

        TransactionListResponse result = service.list(USER_ID, "2026-07", null, PAGE, SIZE);

        assertThat(result.transactions()).singleElement().satisfies(tx -> {
            assertThat(tx.id()).isEqualTo(7L);
            assertThat(tx.buchungsdatum()).isEqualTo(LocalDate.of(2026, 7, 3));
            assertThat(tx.buchungstext()).isEqualTo("MIGROS BERN");
            assertThat(tx.betrag()).isEqualByComparingTo("60.00");
            assertThat(tx.category()).isEqualTo("Lebensmittel");
        });
    }

    @Test
    void reportsUncategorizedTransactionsAsSonstiges() {
        stubExpenses(expense(1L, "2026-07-03", "UNBEKANNT AG", "10.00", null));

        TransactionListResponse result = service.list(USER_ID, "2026-07", null, PAGE, SIZE);

        assertThat(result.transactions()).singleElement()
                .extracting(TransactionResponse::category)
                .isEqualTo("Sonstiges");
    }

    @Test
    void passesThroughUnknownCategoryLabelsFromTheDatabase() {
        // Gegenprobe zum Summary, das denselben Wert unverändert durchreicht: ein Lesepfad darf
        // nicht mit 500 abbrechen, wo der andere die Zahl anzeigt.
        stubExpenses(expense(1L, "2026-07-03", "NEUE KATEGORIE AG", "10.00", "Kryptowährung"));

        assertThat(service.list(USER_ID, "2026-07", null, PAGE, SIZE).transactions())
                .singleElement()
                .extracting(TransactionResponse::category)
                .isEqualTo("Kryptowährung");
    }

    @Test
    void reportsFurtherPagesAsHasMore() {
        stubExpenses(true, expense(1L, "2026-07-03", "MIGROS BERN", "60.00", "Lebensmittel"));

        assertThat(service.list(USER_ID, "2026-07", null, PAGE, SIZE).hasMore()).isTrue();
    }

    @Test
    void reportsTheLastPageAsNoMore() {
        stubExpenses(false, expense(1L, "2026-07-03", "MIGROS BERN", "60.00", "Lebensmittel"));

        assertThat(service.list(USER_ID, "2026-07", null, PAGE, SIZE).hasMore()).isFalse();
    }

    @Test
    void emptyPageYieldsAnEmptyListInsteadOfAnError() {
        stubExpenses();

        TransactionListResponse result = service.list(USER_ID, "2026-07", null, 5, SIZE);

        assertThat(result.transactions()).isEmpty();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void rejectsInvalidMonth() {
        assertThatThrownBy(() -> service.list(USER_ID, "2026-13", null, PAGE, SIZE))
                .isInstanceOf(InvalidMonthException.class);
    }

    @Test
    void rejectsMissingMonth() {
        assertThatThrownBy(() -> service.list(USER_ID, null, null, PAGE, SIZE))
                .isInstanceOf(InvalidMonthException.class);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> service.list(USER_ID, "2026-07", null, -1, SIZE))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void rejectsSizeBelowOne() {
        assertThatThrownBy(() -> service.list(USER_ID, "2026-07", null, PAGE, 0))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void rejectsSizeAboveTheMaximum() {
        // Ohne diese Grenze liesse sich der Vollload, den US-13 ausschliesst, per size wiederholen.
        assertThatThrownBy(() -> service.list(USER_ID, "2026-07", null, PAGE,
                        TransactionListService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void acceptsTheMaximumSize() {
        stubExpenses();

        service.list(USER_ID, "2026-07", null, PAGE, TransactionListService.MAX_PAGE_SIZE);

        assertThat(capturedPageable().getPageSize())
                .isEqualTo(TransactionListService.MAX_PAGE_SIZE);
    }

    @Test
    void formatsAvailableMonthsAsTheParameterFormat() {
        // Ausgabe von availableMonths() und Eingabe des month-Parameters müssen dasselbe Format
        // haben — sonst liefert das Dropdown Werte, die der Endpoint mit 400 abweist.
        when(repository.findDistinctExpenseMonths(USER_ID))
                .thenReturn(List.<Object[]>of(new Object[] {2026, 7}, new Object[] {2019, 8}));

        List<String> months = service.availableMonths(USER_ID);

        assertThat(months).containsExactly("2026-07", "2019-08");
        assertThat(MonthParser.parse(months.getFirst())).isEqualTo(YearMonth.of(2026, 7));
    }

    @Test
    void acceptsWhateverNumericTypeTheDatabaseReturns() {
        // year()/month() liefern je nach Dialekt Integer oder Long — deshalb steht im Service ein
        // Cast auf Number und nicht auf Integer. Ein ClassCastException fiele sonst erst in
        // Produktion auf, weil der Unit-Test die Typen selbst wählt.
        when(repository.findDistinctExpenseMonths(USER_ID))
                .thenReturn(List.<Object[]>of(new Object[] {2021L, 3L}));

        assertThat(service.availableMonths(USER_ID)).containsExactly("2021-03");
    }

    @Test
    void reportsNoAvailableMonthsWhenThereAreNoExpenses() {
        when(repository.findDistinctExpenseMonths(USER_ID)).thenReturn(List.of());

        assertThat(service.availableMonths(USER_ID)).isEmpty();
    }

    @Test
    void doesNotQueryWhenThePageParametersAreInvalid() {
        // Die Prüfung liegt vor dem Repository-Zugriff: ein unbrauchbares Fenster darf gar nicht
        // erst zu einer Query werden.
        assertThatThrownBy(() -> service.list(USER_ID, "2026-07", null, PAGE, 5000))
                .isInstanceOf(InvalidPaginationException.class);

        verify(repository, never()).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                any(), any(), any(), any(Pageable.class));
    }
}
