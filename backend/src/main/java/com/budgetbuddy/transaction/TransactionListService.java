package com.budgetbuddy.transaction;

import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.transaction.dto.TransactionListResponse;
import com.budgetbuddy.transaction.dto.TransactionResponse;
import java.time.YearMonth;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listet die einzelnen Ausgaben eines Monats seitenweise, optional auf eine Kategorie eingegrenzt
 * (FE-CAT-03, FE-CAT-05).
 *
 * <p>Gegenstück zum aggregierten {@link TransactionSummaryService}: dort steht die Summe pro
 * Kategorie, hier stehen die Buchungen dahinter — inklusive {@code id}, ohne die
 * {@code PUT /transactions/{id}/category} (BE-CAT-04) nicht adressierbar ist.
 *
 * <p>Dieselben Auswahlregeln wie beim Summary, damit die aufgeklappte Liste einer Kategorie exakt
 * die Buchungen zeigt, aus denen deren Summe entstanden ist: nur Ausgaben ({@code is_income =
 * false}), nicht kategorisierte Transaktionen zählen als {@link Category#SONSTIGES}.
 *
 * <p>Anders als das Summary läuft die Auswahl hier vollständig in der Datenbank — Filter,
 * Sortierung und Begrenzung. Ein Slice auf einer zuvor komplett geladenen Monatsliste wäre
 * korrekt, aber die Last pro Request wüchse mit der Monatsgrösse statt mit der Seitengrösse, und
 * zwar bei jedem «Weitere laden»-Klick erneut. Genau das schliesst US-13 aus.
 */
@Service
public class TransactionListService {

    /**
     * Seitengrösse, wenn der Aufrufer keine angibt — dieselbe, die das Frontend initial zeigt. Ein
     * Aufruf ohne Parameter verhält sich damit wie der erste Seitenaufruf, und es gibt eine Zahl
     * statt zweier.
     *
     * <p>Als {@code String}, weil der Wert im {@code defaultValue} einer Annotation am Controller
     * steht und dort ein Konstantenausdruck sein muss. Der Service selbst wendet ihn nie an — er
     * bekommt vom Controller immer einen konkreten Wert.
     */
    public static final String DEFAULT_PAGE_SIZE = "20";

    /**
     * Grösste erlaubte Seite. Ohne diese Grenze liesse sich der Vollload, den US-13 ausschliesst,
     * mit einem grossen {@code size} wiederherstellen.
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Neueste Buchung zuoberst; bei gleichem Datum entscheidet die ID, damit die Reihenfolge über
     * mehrere Seiten hinweg stabil bleibt. Ohne eindeutigen zweiten Schlüssel dürfte die Datenbank
     * gleichdatierte Zeilen je Abfrage anders anordnen — dann erschiene eine Buchung auf zwei
     * Seiten und eine andere auf keiner.
     */
    private static final Sort SORT =
            Sort.by(Sort.Order.desc("buchungsdatum"), Sort.Order.desc("id"));

    private final TransactionRepository transactionRepository;

    public TransactionListService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Liefert eine Seite der Ausgaben des Users im angegebenen Monat, absteigend nach
     * Buchungsdatum.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param month Monat im Format {@code YYYY-MM}.
     * @param categoryLabel deutsches Kategorie-Label als Filter, oder {@code null}/leer für alle
     *     Kategorien. Ein Label ohne Treffer liefert eine leere Liste, keinen Fehler.
     * @param page nullbasierte Seitennummer.
     * @param size Anzahl Buchungen pro Seite, 1 bis {@link #MAX_PAGE_SIZE}.
     * @return die Buchungen dieser Seite samt Hinweis, ob weitere folgen.
     * @throws InvalidMonthException wenn {@code month} fehlt oder kein {@code YYYY-MM} ist.
     * @throws InvalidPaginationException wenn {@code page} oder {@code size} ausserhalb des
     *     erlaubten Bereichs liegt.
     */
    @Transactional(readOnly = true)
    public TransactionListResponse list(long userId, String month, String categoryLabel, int page,
            int size) {
        YearMonth yearMonth = MonthParser.parse(month);
        String filter = parseFilter(categoryLabel);
        Pageable pageable = pageRequest(page, size);

        // Beide Repository-Methoden sind auf den User eingeschränkt — der Kategorie-Filter
        // schränkt nur weiter ein und kann die Mandantentrennung nicht aufweichen.
        Slice<Transaction> slice = filter == null
                ? transactionRepository.findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth(), pageable)
                : transactionRepository.findExpensesByCategoryLabel(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth(), filter,
                        Category.SONSTIGES.getLabel(), pageable);

        List<TransactionResponse> transactions = slice.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new TransactionListResponse(transactions, slice.hasNext());
    }

    /**
     * Die Monate, in denen der User Ausgaben hat — absteigend, im Format {@code YYYY-MM}
     * (FE-CAT-04, US-12).
     *
     * <p>Eingabe des Monats-Dropdowns: ohne diese Liste müsste das Frontend eine Jahresspanne
     * raten. Zu kurz, und alte Kontoauszüge wären unerreichbar — die Test-PDFs enthalten Buchungen
     * aus 2019, 2021 und 2025; zu lang, und die Auswahl bestünde aus leeren Jahren.
     *
     * <p>Formatiert über {@link YearMonth#toString()}, die exakte Umkehrung von
     * {@link MonthParser#parse(String)}: was hier herauskommt, ist genau das, was der
     * {@code month}-Parameter wieder annimmt.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return die Monate mit Ausgaben, neuester zuerst. Leer, wenn der User keine Ausgaben hat.
     */
    @Transactional(readOnly = true)
    public List<String> availableMonths(long userId) {
        return transactionRepository.findDistinctExpenseMonths(userId).stream()
                .map(row -> YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()))
                .map(YearMonth::toString)
                .toList();
    }

    /**
     * Baut das Seitenfenster und weist unbrauchbare Werte ab, statt sie stillschweigend
     * zurechtzubiegen: ein zu grosses {@code size} als Vollload zu beantworten wäre genau der
     * Zustand, den dieser Task beseitigt, und ein zurechtgestutzter Wert wäre für den Aufrufer
     * nicht von einem erfüllten Wunsch zu unterscheiden.
     */
    private Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("page darf nicht negativ sein: " + page);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "size muss zwischen 1 und " + MAX_PAGE_SIZE + " liegen: " + size);
        }
        return PageRequest.of(page, size, SORT);
    }

    /**
     * Kategorie-Label einer Transaktion, wobei {@code null} als {@link Category#SONSTIGES} gilt —
     * dieselbe Regel wie im {@link TransactionSummaryService}. Ein Filter auf {@code Sonstiges}
     * trifft damit auch die noch nicht kategorisierten Buchungen, die in der Übersicht unter diesem
     * Namen summiert sind; auf dem Auswahlpfad steht dieselbe Regel als {@code coalesce} in
     * {@link TransactionRepository#findExpensesByCategoryLabel}.
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
    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getBuchungsdatum(), tx.getBuchungstext(),
                tx.getBuchungsdetails(), tx.getBetrag(), tx.isIncome(), labelOf(tx));
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
