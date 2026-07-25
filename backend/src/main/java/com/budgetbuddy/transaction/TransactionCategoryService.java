package com.budgetbuddy.transaction;

import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.categorization.CategoryLearningPort;
import com.budgetbuddy.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manuelle Kategorie-Korrektur einer Transaktion (BE-CAT-04, US-05).
 *
 * <p>Zwei Schreibvorgänge in einer Transaktion: die Kategorie der Transaktion wird aktualisiert,
 * und über den {@link CategoryLearningPort} wird der Händlertext ({@code buchungstext}) als
 * Lookup-Pattern gelernt (ADR-6, Schritt 3). Dadurch kategorisiert der PDF-Import die nächste
 * Transaktion desselben Händlers deterministisch über die Lookup-Tabelle — ohne Claude-Call.
 *
 * <p>Der Schreibzugriff auf {@code category_lookup} läuft bewusst nicht direkt über dessen
 * Repository, sondern über den Port des {@code categorization}-Moduls (Modulgrenze, CLAUDE.md).
 */
@Service
public class TransactionCategoryService {

    private final TransactionRepository transactionRepository;
    private final CategoryLearningPort categoryLearningPort;

    public TransactionCategoryService(
            TransactionRepository transactionRepository,
            CategoryLearningPort categoryLearningPort) {
        this.transactionRepository = transactionRepository;
        this.categoryLearningPort = categoryLearningPort;
    }

    /**
     * Setzt die Kategorie einer Transaktion des Users und lernt das Händler-Pattern.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param transactionId ID der zu ändernden Transaktion.
     * @param categoryLabel deutsches Kategorie-Label (z. B. {@code "Lebensmittel"}).
     * @return die aktualisierte Transaktion.
     * @throws InvalidCategoryException wenn {@code categoryLabel} keinem gültigen Label entspricht.
     * @throws TransactionNotFoundException wenn keine Transaktion mit dieser ID dem User gehört.
     */
    @Transactional
    public TransactionResponse updateCategory(long userId, long transactionId, String categoryLabel) {
        Category category = parseCategory(categoryLabel);

        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(tx -> tx.getUserId() == userId)
                .orElseThrow(() -> new TransactionNotFoundException(userId, transactionId));

        transaction.setCategory(category.getLabel());
        transactionRepository.save(transaction);

        // Lerneffekt: buchungstext verbatim als Lookup-Pattern (BE-CAT-04-Entscheid).
        categoryLearningPort.learn(transaction.getBuchungstext(), category);

        return TransactionResponse.from(transaction);
    }

    private Category parseCategory(String categoryLabel) {
        try {
            return Category.fromLabel(categoryLabel);
        } catch (IllegalArgumentException e) {
            throw new InvalidCategoryException(categoryLabel);
        }
    }
}
