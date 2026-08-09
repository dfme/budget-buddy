package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit-Test der Auswahl-, Filter- und Sortierlogik von {@link TransactionListService} (FE-CAT-03).
 *
 * <p>Das Repository ist gemockt — die Einschränkung auf User und Monat ist dessen Zuständigkeit und
 * wird im Integrationstest gegen echtes PostgreSQL geprüft. Hier steht die Logik darüber im Fokus:
 * die an das Repository übergebenen Monatsgrenzen, der Kategorie-Filter inklusive der
 * {@code null}-Sonderregel und die Reihenfolge der Antwort.
 *
 * <p>{@link Transaction} wird gemockt statt konstruiert, weil die {@code id} sonst nur über JPA
 * gesetzt würde — sie ist hier aber das Feld, das die Sortierung und den späteren
 * {@code PUT /transactions/{id}/category} trägt.
 */
class TransactionListServiceTest {

    private static final long USER_ID = 42L;

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

    private void stubExpenses(Transaction... expenses) {
        when(repository.findByUserIdAndIncomeFalseAndBuchungsdatumBetween(any(), any(), any()))
                .thenReturn(List.of(expenses));
    }

    @Test
    void queriesTheFullMonthForTheGivenUser() {
        stubExpenses();

        service.list(USER_ID, "2026-02", null);

        // Februar 2026 hat 28 Tage — die Obergrenze kommt aus YearMonth, nicht aus einer Konstanten.
        verify(repository).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                eq(USER_ID), eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)));
    }

    @Test
    void returnsTheFieldsNeededForTheCategoryDropdown() {
        stubExpenses(expense(7L, "2026-07-03", "MIGROS BERN", "60.00", "Lebensmittel"));

        List<TransactionResponse> result = service.list(USER_ID, "2026-07", null);

        assertThat(result).singleElement().satisfies(tx -> {
            assertThat(tx.id()).isEqualTo(7L);
            assertThat(tx.buchungsdatum()).isEqualTo(LocalDate.of(2026, 7, 3));
            assertThat(tx.buchungstext()).isEqualTo("MIGROS BERN");
            assertThat(tx.betrag()).isEqualByComparingTo("60.00");
            assertThat(tx.category()).isEqualTo("Lebensmittel");
        });
    }

    @Test
    void sortsByDateDescendingAndIdDescendingWithinTheSameDay() {
        stubExpenses(
                expense(1L, "2026-07-03", "ÄLTER", "10.00", "Transport"),
                expense(2L, "2026-07-20", "GLEICHER TAG, KLEINERE ID", "10.00", "Transport"),
                expense(3L, "2026-07-20", "GLEICHER TAG, GRÖSSERE ID", "10.00", "Transport"));

        List<TransactionResponse> result = service.list(USER_ID, "2026-07", null);

        assertThat(result).extracting(TransactionResponse::id).containsExactly(3L, 2L, 1L);
    }

    @Test
    void reportsUncategorizedTransactionsAsSonstiges() {
        stubExpenses(expense(1L, "2026-07-03", "UNBEKANNT AG", "10.00", null));

        List<TransactionResponse> result = service.list(USER_ID, "2026-07", null);

        assertThat(result).singleElement()
                .extracting(TransactionResponse::category)
                .isEqualTo("Sonstiges");
    }

    @Test
    void filtersByCategory() {
        stubExpenses(
                expense(1L, "2026-07-03", "MIGROS BERN", "60.00", "Lebensmittel"),
                expense(2L, "2026-07-04", "SBB CFF FFS", "40.00", "Transport"));

        List<TransactionResponse> result = service.list(USER_ID, "2026-07", "Lebensmittel");

        assertThat(result).extracting(TransactionResponse::buchungstext)
                .containsExactly("MIGROS BERN");
    }

    @Test
    void filterOnSonstigesAlsoMatchesUncategorizedTransactions() {
        // Die Kategorie-Übersicht summiert diese beiden Zeilen unter 'Sonstiges' — die aufgeklappte
        // Liste muss deshalb beide zeigen, nicht nur die explizit kategorisierte.
        stubExpenses(
                expense(1L, "2026-07-03", "EXPLIZIT SONSTIGES", "10.00", "Sonstiges"),
                expense(2L, "2026-07-04", "NOCH NICHT KATEGORISIERT", "20.00", null),
                expense(3L, "2026-07-05", "SBB CFF FFS", "40.00", "Transport"));

        List<TransactionResponse> result = service.list(USER_ID, "2026-07", "Sonstiges");

        assertThat(result).extracting(TransactionResponse::id).containsExactly(2L, 1L);
    }

    @Test
    void blankCategoryIsTreatedAsNoFilter() {
        stubExpenses(
                expense(1L, "2026-07-03", "MIGROS BERN", "60.00", "Lebensmittel"),
                expense(2L, "2026-07-04", "SBB CFF FFS", "40.00", "Transport"));

        assertThat(service.list(USER_ID, "2026-07", "  ")).hasSize(2);
    }

    @Test
    void passesThroughUnknownCategoryLabelsFromTheDatabase() {
        // Gegenprobe zum Summary, das denselben Wert unverändert durchreicht: ein Lesepfad darf
        // nicht mit 500 abbrechen, wo der andere die Zahl anzeigt.
        stubExpenses(expense(1L, "2026-07-03", "NEUE KATEGORIE AG", "10.00", "Kryptowährung"));

        assertThat(service.list(USER_ID, "2026-07", null))
                .singleElement()
                .extracting(TransactionResponse::category)
                .isEqualTo("Kryptowährung");
    }

    @Test
    void rejectsInvalidMonth() {
        assertThatThrownBy(() -> service.list(USER_ID, "2026-13", null))
                .isInstanceOf(InvalidMonthException.class);
    }

    @Test
    void rejectsMissingMonth() {
        assertThatThrownBy(() -> service.list(USER_ID, null, null))
                .isInstanceOf(InvalidMonthException.class);
    }

    @Test
    void rejectsInvalidCategoryFilter() {
        assertThatThrownBy(() -> service.list(USER_ID, "2026-07", "Lebensmitel"))
                .isInstanceOf(InvalidCategoryException.class);
    }
}
