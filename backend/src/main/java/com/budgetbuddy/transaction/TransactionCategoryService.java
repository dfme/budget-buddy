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
 * und über den {@link CategoryLearningPort} wird der Händlertext als Lookup-Pattern gelernt
 * (ADR-6, Schritt 4). Dadurch kategorisiert der PDF-Import die nächste Transaktion desselben
 * Händlers deterministisch über die Lookup-Tabelle — ohne Claude-Call.
 *
 * <p>Gelernt wird {@link Transaction#fullText()}, nicht der blosse {@code buchungstext}: Die
 * Claude-Stufe lernt beim Import unter demselben Schlüssel (BE-CAT-11), und nur wenn beide
 * Quellen zeichengleich schreiben, wirkt der Upsert und überschreibt die Korrektur des Users den
 * Claude-Eintrag. Die Begründung im Detail steht bei {@link Transaction#fullText()}.
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

        // Lerneffekt: derselbe Text wie bei BE-CAT-11 (Buchungstext + Detailzeilen). Gespeichert
        // wird davon das grossgeschriebene, um die variable Mitteilung gekürzte Präfix (DB-05,
        // BE-CAT-13): In category_lookup steht COOP PRONTO BERN, nicht "Coop Pronto Bern", und
        // GIRO POST MUSTER IMMOBILIEN AG MIETE ohne den Monat.
        categoryLearningPort.learn(transaction.fullText(), category);

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
