package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.categorization.CategoryLearningPort;
import com.budgetbuddy.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit-Test der manuellen Kategorie-Korrektur (BE-CAT-04). Repository und {@link CategoryLearningPort}
 * sind gemockt; der End-to-End-Pfad inkl. Persistierung und Lookup-Lerneffekt ist im
 * {@link TransactionCategoryControllerIntegrationTest} abgedeckt.
 */
class TransactionCategoryServiceTest {

    private static final long USER_ID = 42L;
    private static final long TX_ID = 7L;

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final CategoryLearningPort learningPort = mock(CategoryLearningPort.class);

    private final TransactionCategoryService service =
            new TransactionCategoryService(repository, learningPort);

    private Transaction ownTransaction() {
        return new Transaction(USER_ID, LocalDate.of(2026, 7, 3), "MIGROS BERN",
                new BigDecimal("60.00"), false, "Sonstiges", "sha");
    }

    @Test
    void updatesCategoryAndLearnsPattern() {
        Transaction tx = ownTransaction();
        when(repository.findById(TX_ID)).thenReturn(Optional.of(tx));

        TransactionResponse response = service.updateCategory(USER_ID, TX_ID, "Lebensmittel");

        assertThat(tx.getCategory()).isEqualTo("Lebensmittel");
        assertThat(response.category()).isEqualTo("Lebensmittel");
        verify(repository).save(tx);
        // Lerneffekt: buchungstext verbatim → Kategorie.
        verify(learningPort).learn("MIGROS BERN", Category.LEBENSMITTEL);
    }

    @Test
    void unknownTransactionThrowsNotFound() {
        when(repository.findById(TX_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategory(USER_ID, TX_ID, "Lebensmittel"))
                .isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(learningPort);
    }

    @Test
    void foreignTransactionThrowsNotFound() {
        Transaction foreign = new Transaction(999L, LocalDate.of(2026, 7, 3), "MIGROS BERN",
                new BigDecimal("60.00"), false, "Sonstiges", "sha");
        when(repository.findById(TX_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.updateCategory(USER_ID, TX_ID, "Lebensmittel"))
                .isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(learningPort);
    }

    @Test
    void invalidCategoryLabelThrowsInvalidCategory() {
        assertThatThrownBy(() -> service.updateCategory(USER_ID, TX_ID, "Foobar"))
                .isInstanceOf(InvalidCategoryException.class);
        // Validierung vor dem DB-Zugriff — kein Laden, kein Lernen.
        verifyNoInteractions(repository);
        verifyNoInteractions(learningPort);
    }
}
