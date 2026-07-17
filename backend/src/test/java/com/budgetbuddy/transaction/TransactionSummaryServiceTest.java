package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.transaction.dto.CategorySummaryItem;
import com.budgetbuddy.transaction.dto.CategorySummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit-Test der Aggregations- und Prozentlogik von {@link TransactionSummaryService} (BE-CAT-05).
 *
 * <p>Das Repository wird gemockt; die Monats-Filterung selbst ist Repository-Zuständigkeit und wird
 * im Integrationstest gegen echtes SQLite geprüft. Hier steht der rechnerische Kern im Fokus:
 * Aggregation, BigDecimal-Genauigkeit und die Largest-Remainder-Prozente (Summe exakt 100.00).
 */
class TransactionSummaryServiceTest {

    private static final long USER_ID = 42L;

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final TransactionSummaryService service = new TransactionSummaryService(repository);

    private static Transaction expense(String category, String betrag) {
        Transaction tx = mock(Transaction.class);
        when(tx.getCategory()).thenReturn(category);
        when(tx.getBetrag()).thenReturn(new BigDecimal(betrag));
        return tx;
    }

    private void stubExpenses(Transaction... expenses) {
        when(repository.findByUserIdAndIncomeFalseAndBuchungsdatumBetween(any(), any(), any()))
                .thenReturn(List.of(expenses));
    }

    private CategorySummaryItem itemFor(CategorySummaryResponse response, String category) {
        return response.categories().stream()
                .filter(i -> i.category().equals(category))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kategorie fehlt im Response: " + category));
    }

    @Test
    void aggregatesSumAndCountPerCategory() {
        stubExpenses(
                expense("Lebensmittel", "20.50"),
                expense("Lebensmittel", "9.50"),
                expense("Transport", "5.00"));

        CategorySummaryResponse response = service.summarize(USER_ID, "2026-07");

        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("35.00");
        assertThat(itemFor(response, "Lebensmittel").amount()).isEqualByComparingTo("30.00");
        assertThat(itemFor(response, "Lebensmittel").count()).isEqualTo(2);
        assertThat(itemFor(response, "Transport").amount()).isEqualByComparingTo("5.00");
        assertThat(itemFor(response, "Transport").count()).isEqualTo(1);
    }

    @Test
    void percentagesSumToExactlyHundredForThreeEqualCategories() {
        // 3 × gleich hohe Ausgaben: exakt 33.333… % → naives Runden ergäbe 99.99.
        stubExpenses(
                expense("Lebensmittel", "100.00"),
                expense("Transport", "100.00"),
                expense("Freizeit", "100.00"));

        CategorySummaryResponse response = service.summarize(USER_ID, "2026-07");

        BigDecimal sumOfPercentages = response.categories().stream()
                .map(CategorySummaryItem::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfPercentages).isEqualByComparingTo("100.00");
        // Zwei Kategorien 33.33, eine erhält den Rest-Rappen → 33.34.
        assertThat(response.categories())
                .extracting(i -> i.percentage().toPlainString())
                .containsExactlyInAnyOrder("33.34", "33.33", "33.33");
    }

    @Test
    void percentagesSumToExactlyHundredForWeightedCategories() {
        stubExpenses(
                expense("Wohnen", "75.00"),
                expense("Shopping", "15.00"),
                expense("Restaurant", "10.00"));

        CategorySummaryResponse response = service.summarize(USER_ID, "2026-07");

        assertThat(itemFor(response, "Wohnen").percentage()).isEqualByComparingTo("75.00");
        assertThat(itemFor(response, "Shopping").percentage()).isEqualByComparingTo("15.00");
        assertThat(itemFor(response, "Restaurant").percentage()).isEqualByComparingTo("10.00");
        BigDecimal sum = response.categories().stream()
                .map(CategorySummaryItem::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    @Test
    void nullCategoryCountsAsSonstiges() {
        stubExpenses(
                expense(null, "40.00"),
                expense("Lebensmittel", "60.00"));

        CategorySummaryResponse response = service.summarize(USER_ID, "2026-07");

        assertThat(itemFor(response, "Sonstiges").amount()).isEqualByComparingTo("40.00");
        assertThat(itemFor(response, "Sonstiges").count()).isEqualTo(1);
        assertThat(itemFor(response, "Sonstiges").percentage()).isEqualByComparingTo("40.00");
    }

    @Test
    void categoriesAreSortedByAmountDescending() {
        stubExpenses(
                expense("Transport", "5.00"),
                expense("Wohnen", "80.00"),
                expense("Lebensmittel", "15.00"));

        CategorySummaryResponse response = service.summarize(USER_ID, "2026-07");

        assertThat(response.categories())
                .extracting(CategorySummaryItem::category)
                .containsExactly("Wohnen", "Lebensmittel", "Transport");
    }

    @Test
    void emptyMonthReturnsEmptyListAndZeroTotal() {
        stubExpenses();

        CategorySummaryResponse response = service.summarize(USER_ID, "2026-07");

        assertThat(response.categories()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(response.month()).isEqualTo("2026-07");
    }

    @Test
    void queriesRepositoryWithFullMonthRange() {
        stubExpenses();

        service.summarize(USER_ID, "2026-02");

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                eq(USER_ID), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 2, 28)); // Nicht-Schaltjahr
    }

    @Test
    void invalidMonthThrows() {
        assertThatThrownBy(() -> service.summarize(USER_ID, null))
                .isInstanceOf(InvalidMonthException.class);
        assertThatThrownBy(() -> service.summarize(USER_ID, "  "))
                .isInstanceOf(InvalidMonthException.class);
        assertThatThrownBy(() -> service.summarize(USER_ID, "2026-13"))
                .isInstanceOf(InvalidMonthException.class);
        assertThatThrownBy(() -> service.summarize(USER_ID, "2026-7"))
                .isInstanceOf(InvalidMonthException.class);
        assertThatThrownBy(() -> service.summarize(USER_ID, "keinDatum"))
                .isInstanceOf(InvalidMonthException.class);
    }
}
