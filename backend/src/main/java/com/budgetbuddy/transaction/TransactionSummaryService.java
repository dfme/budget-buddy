package com.budgetbuddy.transaction;

import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.transaction.dto.CategorySummaryItem;
import com.budgetbuddy.transaction.dto.CategorySummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregiert die Ausgaben eines Monats pro Kategorie (BE-CAT-05).
 *
 * <p>Einbezogen werden ausschliesslich Ausgaben ({@code is_income = false}); Einkommen fliesst nicht
 * ein. Nicht kategorisierte Transaktionen ({@code category = null}) zählen als
 * {@link Category#SONSTIGES}. Es erscheinen nur Kategorien, die im Monat tatsächlich Ausgaben haben.
 *
 * <p>Sämtliche Beträge sind {@link BigDecimal} (ADR-9). Die Prozentanteile werden mit dem
 * Largest-Remainder-Verfahren berechnet, sodass ihre Summe exakt {@code 100.00} ergibt — naives
 * Runden jedes Anteils könnte z. B. 99.99 liefern.
 */
@Service
public class TransactionSummaryService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final int PERCENT_SCALE = 2;

    private final TransactionRepository transactionRepository;

    public TransactionSummaryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Liefert das Ausgaben-Summary des Users für den angegebenen Monat.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param month Monat im Format {@code YYYY-MM}.
     * @return Summary mit Kategorien absteigend nach Betrag.
     * @throws InvalidMonthException wenn {@code month} fehlt oder nicht dem Format {@code YYYY-MM}
     *     entspricht.
     */
    @Transactional(readOnly = true)
    public CategorySummaryResponse summarize(long userId, String month) {
        YearMonth yearMonth = parseMonth(month);

        List<Transaction> expenses = transactionRepository
                .findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        // Aggregation pro Kategorie-Label. LinkedHashMap für deterministische Erst-Reihenfolge.
        Map<String, Aggregate> byCategory = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Transaction tx : expenses) {
            String label = tx.getCategory() != null ? tx.getCategory() : Category.SONSTIGES.getLabel();
            byCategory.computeIfAbsent(label, Aggregate::new).add(tx.getBetrag());
            total = total.add(tx.getBetrag());
        }

        List<Aggregate> aggregates = new ArrayList<>(byCategory.values());
        assignPercentages(aggregates, total);

        // Ausgabe absteigend nach Betrag, bei Gleichstand alphabetisch nach Label (deterministisch).
        aggregates.sort(Comparator.comparing((Aggregate a) -> a.sum).reversed()
                .thenComparing(a -> a.label));

        List<CategorySummaryItem> items = new ArrayList<>(aggregates.size());
        for (Aggregate a : aggregates) {
            items.add(new CategorySummaryItem(a.label, a.sum, a.count, a.percentage));
        }

        return new CategorySummaryResponse(month, total, expenses.size(), items);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new InvalidMonthException(month);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new InvalidMonthException(month);
        }
    }

    /**
     * Setzt {@link Aggregate#percentage} für jeden Eintrag per Largest-Remainder, sodass die Summe
     * über alle Einträge exakt {@code 100.00} ergibt. Bei Gesamtsumme 0 (nur 0-CHF-Ausgaben oder
     * keine Ausgaben) bleiben alle Anteile {@code 0.00}.
     */
    private void assignPercentages(List<Aggregate> aggregates, BigDecimal total) {
        BigDecimal zero = BigDecimal.ZERO.setScale(PERCENT_SCALE);
        if (aggregates.isEmpty() || total.signum() == 0) {
            for (Aggregate a : aggregates) {
                a.percentage = zero;
            }
            return;
        }

        // 1. Abgerundeter Basis-Anteil (scale 2) je Kategorie + fraktionaler Rest.
        BigDecimal baseSum = zero;
        for (Aggregate a : aggregates) {
            BigDecimal exact = a.sum.multiply(HUNDRED).divide(total, 10, RoundingMode.HALF_UP);
            a.percentage = exact.setScale(PERCENT_SCALE, RoundingMode.DOWN);
            a.remainder = exact.subtract(a.percentage);
            baseSum = baseSum.add(a.percentage);
        }

        // 2. Verbleibende Rappen (100.00 - Summe der Basis-Anteile) an die grössten Reste verteilen.
        int leftoverCents = HUNDRED.setScale(PERCENT_SCALE).subtract(baseSum)
                .divide(CENT).setScale(0, RoundingMode.HALF_UP).intValue();

        List<Aggregate> byRemainder = new ArrayList<>(aggregates);
        byRemainder.sort(Comparator.comparing((Aggregate a) -> a.remainder).reversed()
                .thenComparing((Aggregate a) -> a.sum, Comparator.reverseOrder())
                .thenComparing(a -> a.label));
        for (int i = 0; i < leftoverCents && i < byRemainder.size(); i++) {
            Aggregate a = byRemainder.get(i);
            a.percentage = a.percentage.add(CENT);
        }
    }

    /** Veränderliche Akkumulation je Kategorie während der Aggregation. */
    private static final class Aggregate {
        private final String label;
        private BigDecimal sum = BigDecimal.ZERO.setScale(2);
        private int count;
        private BigDecimal percentage;
        private BigDecimal remainder;

        private Aggregate(String label) {
            this.label = label;
        }

        private void add(BigDecimal betrag) {
            this.sum = this.sum.add(betrag);
            this.count++;
        }
    }
}
