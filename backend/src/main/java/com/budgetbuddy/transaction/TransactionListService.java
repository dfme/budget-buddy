package com.budgetbuddy.transaction;

import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.transaction.dto.TransactionResponse;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listet die einzelnen Ausgaben eines Monats, optional auf eine Kategorie eingegrenzt (FE-CAT-03).
 *
 * <p>Gegenstück zum aggregierten {@link TransactionSummaryService}: dort steht die Summe pro
 * Kategorie, hier stehen die Buchungen dahinter — inklusive {@code id}, ohne die
 * {@code PUT /transactions/{id}/category} (BE-CAT-04) nicht adressierbar ist.
 *
 * <p>Dieselben Auswahlregeln wie beim Summary, damit die aufgeklappte Liste einer Kategorie exakt
 * die Buchungen zeigt, aus denen deren Summe entstanden ist: nur Ausgaben ({@code is_income =
 * false}), nicht kategorisierte Transaktionen zählen als {@link Category#SONSTIGES}.
 */
@Service
public class TransactionListService {

    private final TransactionRepository transactionRepository;

    public TransactionListService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Liefert die Ausgaben des Users im angegebenen Monat, absteigend nach Buchungsdatum.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param month Monat im Format {@code YYYY-MM}.
     * @param categoryLabel deutsches Kategorie-Label als Filter, oder {@code null}/leer für alle
     *     Kategorien. Ein Label ohne Treffer liefert eine leere Liste, keinen Fehler.
     * @return die Transaktionen, absteigend nach Buchungsdatum und bei Gleichstand nach ID.
     * @throws InvalidMonthException wenn {@code month} fehlt oder kein {@code YYYY-MM} ist.
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> list(long userId, String month, String categoryLabel) {
        YearMonth yearMonth = MonthParser.parse(month);
        String filter = parseFilter(categoryLabel);

        // Die Repository-Methode ist auf den User eingeschränkt — der Filter unten schränkt nur
        // weiter ein und kann die Mandantentrennung nicht aufweichen.
        List<Transaction> expenses = transactionRepository
                .findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        List<TransactionResponse> result = new ArrayList<>();
        for (Transaction tx : expenses) {
            String label = labelOf(tx);
            if (filter == null || filter.equals(label)) {
                result.add(toResponse(tx, label));
            }
        }

        // Neueste Buchung zuoberst; bei gleichem Datum entscheidet die ID, damit die Reihenfolge
        // über mehrere Requests hinweg stabil bleibt.
        result.sort(Comparator.comparing(TransactionResponse::buchungsdatum)
                .thenComparing(TransactionResponse::id)
                .reversed());
        return result;
    }

    /**
     * Kategorie-Label einer Transaktion, wobei {@code null} als {@link Category#SONSTIGES} gilt —
     * dieselbe Regel wie im {@link TransactionSummaryService}. Ein Filter auf {@code Sonstiges}
     * trifft damit auch die noch nicht kategorisierten Buchungen, die in der Übersicht unter diesem
     * Namen summiert sind.
     *
     * <p>Bewusst als String und nicht über {@link Category#fromLabel(String)}: ein unerwarteter
     * Wert in der Spalte würde dort eine {@link IllegalArgumentException} und damit eine 500
     * auslösen, während das Summary denselben Wert unverändert durchreicht. Ein Lesepfad soll nicht
     * strenger sein als der, der die Zahl daneben berechnet.
     */
    private String labelOf(Transaction tx) {
        return tx.getCategory() != null ? tx.getCategory() : Category.SONSTIGES.getLabel();
    }

    /**
     * Baut die Antwort mit dem <em>aufgelösten</em> Label statt mit dem Rohwert der Entity: so
     * bekommt das Frontend nie {@code null}, und das Dropdown der Zeile hat immer eine gültige
     * Vorauswahl.
     */
    private TransactionResponse toResponse(Transaction tx, String label) {
        return new TransactionResponse(tx.getId(), tx.getBuchungsdatum(), tx.getBuchungstext(),
                tx.getBetrag(), tx.isIncome(), label);
    }

    /**
     * Normalisiert den Filter auf {@code null} (kein Filter) oder das zu vergleichende Label.
     *
     * <p>Bewusst ohne Validierung gegen {@link Category}: der Filter muss jedes Label treffen
     * können, das {@link #labelOf(Transaction)} ausgibt — und das reicht einen unerwarteten Wert
     * aus der Datenbank absichtlich durch. Eine strenge Prüfung hier hätte genau die Zeilen
     * unaufklappbar gemacht, die in der Übersicht sichtbar sind: das Frontend schickt den Wert
     * zurück, den es von dort bekommen hat, und bekäme eine 400.
     *
     * <p>Der Preis ist, dass ein Tippfehler eine leere Liste liefert statt eines Fehlers. Das ist
     * für einen Filter die richtige Antwort — die Vokabular-Prüfung gehört auf den Schreibpfad,
     * wo sie in {@code TransactionCategoryService} auch stattfindet.
     */
    private String parseFilter(String categoryLabel) {
        return categoryLabel == null || categoryLabel.isBlank() ? null : categoryLabel;
    }
}
