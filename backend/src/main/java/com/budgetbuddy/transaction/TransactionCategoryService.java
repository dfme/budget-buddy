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
 * Händlers <em>dieses Users</em> deterministisch über die Lookup-Tabelle — ohne Claude-Call.
 * Gelernt wird pro User (BE-CAT-12, ADR-15): Das Pattern gehört zu dem, der es korrigiert hat.
 *
 * <p>Gelernt wird {@link Transaction#fullText()}, nicht der blosse {@code buchungstext}: Die
 * Claude-Stufe lernt beim Import unter demselben Schlüssel (BE-CAT-11), und nur wenn beide
 * Quellen zeichengleich schreiben, wirkt der Upsert und überschreibt die Korrektur des Users den
 * Claude-Eintrag. Die Begründung im Detail steht bei {@link Transaction#fullText()}.
 *
 * <p>Der Schreibzugriff auf {@code user_category_lookup} läuft bewusst nicht direkt über dessen
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

        // Lerneffekt: derselbe Schlüssel wie bei BE-CAT-11 (Buchungstext + Detailzeilen) — seit
        // DB-05/ADR-12 beim Speichern auf Grossschreibung normalisiert, weil PostgreSQL kein
        // COLLATE NOCASE kennt, und seit BE-CAT-13 um die variable Mitteilung gekürzt. In
        // user_category_lookup steht deshalb COOP PRONTO BERN, nicht "Coop Pronto Bern", und
        // GIRO POST MUSTER IMMOBILIEN AG MIETE ohne den Monat.
        categoryLearningPort.learn(userId, transaction.fullText(), category);

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
