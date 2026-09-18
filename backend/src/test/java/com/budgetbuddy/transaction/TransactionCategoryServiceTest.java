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
        return new Transaction(USER_ID, LocalDate.of(2026, 7, 3), "MIGROS BERN", null,
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
        // Ohne Detailzeilen ist fullText() der Buchungstext — unverändert zu BE-CAT-04. Gelernt
        // wird für den korrigierenden User (BE-CAT-12), nicht global.
        verify(learningPort).learn(USER_ID, "MIGROS BERN", Category.LEBENSMITTEL);
    }

    /**
     * Der Regressionstest zum blockierenden Befund aus PR #320: Gelernt wird Buchungstext
     * <em>plus</em> Detailzeilen — derselbe Schlüssel, den die Claude-Stufe beim Import schreibt.
     * Mit dem blossen {@code buchungstext} wäre das Pattern bei PostFinance {@code GIRO POST}:
     * eine zweite Zeile neben dem Claude-Eintrag, die ausserdem jede Überweisung des Kontos
     * gematcht hätte.
     */
    @Test
    void learnsBuchungstextAndDetailsAsOnePattern() {
        Transaction tx = new Transaction(USER_ID, LocalDate.of(2026, 7, 3), "GIRO POST",
                "MUSTER IMMOBILIEN AG\nMIETE JULI", new BigDecimal("1450.00"), false,
                "Sonstiges", "sha");
        when(repository.findById(TX_ID)).thenReturn(Optional.of(tx));

        service.updateCategory(USER_ID, TX_ID, "Wohnen");

        verify(learningPort).learn(USER_ID, "GIRO POST MUSTER IMMOBILIEN AG MIETE JULI", Category.WOHNEN);
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
        Transaction foreign = new Transaction(999L, LocalDate.of(2026, 7, 3), "MIGROS BERN", null,
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
