package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.MonthlyTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregiert Einnahmen, Ausgaben und deren Differenz je Monat über ein Monatsfenster (BE-STS-07,
 * US-12).
 *
 * <p>Datenquelle der Drei-Monats-Übersicht des Dashboards (FE-STS-04). Geliefert werden reine
 * Transaktions-Aggregate: kein Einkommen aus den User-Einstellungen, keine Fixkosten, kein
 * Safe-to-Spend. Damit bleibt alles im transaction-Modul und es braucht keinen neuen Port über die
 * Modulgrenze (CLAUDE.md).
 *
 * <p><strong>Zwei Queries für das ganze Fenster, nicht zwei pro Monat.</strong> Geladen wird genau
 * zweimal über die gesamte Fensterbreite — einmal die Gutschriften, einmal die Belastungen —, und
 * gruppiert wird anschliessend in Java nach {@link YearMonth}. Bei drei Monaten sind das zwei
 * Zugriffe statt sechs, und die Zahl bleibt konstant, wenn das Fenster wächst.
 *
 * <p><strong>Summiert wird in Java mit {@link BigDecimal}</strong> (ADR-9), nicht per SQL-Aggregat
 * — dieselbe Begründung, die an den beiden benutzten Repository-Methoden steht: die
 * Rappen-Genauigkeit wird an einer Stelle durchgesetzt statt an zweien.
 *
 * <p><strong>Der Ausgabenbegriff ist derselbe wie in {@link TransactionSummaryService}</strong>,
 * weil es dieselbe Query mit derselben Auswahlregel ist: nur Belastungen, ganzer Monat, kein Abzug
 * der per Dauerauftrag bezahlten Fixkosten. Die in {@link MonthlyTotals#expenses()} zugesagte
 * Betragsgleichheit mit {@code CategorySummaryResponse.totalAmount} folgt daraus strukturell; ein
 * Test hält sie fest, aber sie hängt nicht an ihm. Zwei eigene Queries mit «derselben» Regel wären
 * genau die Doppelpflege, die auseinanderläuft.
 *
 * <p><strong>Buchungen mit ungeprüfter Richtung</strong> (BE-PDF-10) zählen hier wie überall sonst
 * als Belastung — das ist der konservative Import-Default. Eine darunter liegende Gutschrift drückt
 * also {@code income} und hebt {@code expenses}. Die Regel wird hier nicht abgeändert; sie ist der
 * Grund, warum die Übersicht vom Kontoauszug abweichen kann, solange die Prüfliste aus US-04 nicht
 * abgearbeitet ist.
 */
@Service
public class MonthlyTotalsService {

    /** Fenstergrösse, wenn der Client keine angibt — die drei Monate der Übersicht (FE-STS-04). */
    public static final int DEFAULT_WINDOW_MONTHS = 3;

    /**
     * Grösstes zulässiges Fenster.
     *
     * <p>Zwölf Monate sind ein Jahr und damit die grösste Spanne, für die eine Monatsübersicht
     * überhaupt eine Aussage trägt. Die Grenze ist nicht Komfort: ohne sie zöge ein einzelner
     * Aufruf mit grossem {@code months} die gesamte Historie des Users durch — dieselbe
     * Überlegung, aus der {@code TransactionListService.MAX_PAGE_SIZE} existiert.
     */
    public static final int MAX_WINDOW_MONTHS = 12;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final TransactionRepository transactionRepository;

    public MonthlyTotalsService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Liefert die Kennzahlen des Fensters, <strong>neuester Monat zuerst</strong> — analog zur
     * Reihenfolge von {@code GET /api/transactions/months}.
     *
     * <p>Für jeden Monat des Fensters kommt eine Zeile, auch für einen ohne Buchungen: der Client
     * soll drei Zeilen rendern können und keine Lücken füllen müssen.
     *
     * <p>Ein Monat in der Zukunft ist <strong>kein Fehler</strong>, anders als bei
     * {@code GET /api/budget/safe-to-spend}: dort hat {@code weeksLeft} für einen künftigen Monat
     * keine Bedeutung, hier gibt es einfach keine Buchungen. Die Zeile kommt mit {@code null}s
     * zurück. Damit verhält sich der Endpoint wie {@code GET /api/transactions/summary}, das einen
     * leeren Monat ebenfalls beantwortet statt abzulehnen.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT). Beide Queries sind user-gebunden.
     * @param newest jüngster Monat des Fensters.
     * @param months Grösse des Fensters in Monaten, {@code 1..}{@link #MAX_WINDOW_MONTHS}.
     * @return eine Zeile je Monat, neuester zuerst; nie {@code null}, nie kürzer als
     *     {@code months}.
     * @throws InvalidMonthWindowException wenn {@code months} ausserhalb des erlaubten Bereichs
     *     liegt.
     */
    @Transactional(readOnly = true)
    public List<MonthlyTotals> totals(long userId, YearMonth newest, int months) {
        if (months < 1 || months > MAX_WINDOW_MONTHS) {
            throw new InvalidMonthWindowException(
                    "months muss zwischen 1 und " + MAX_WINDOW_MONTHS + " liegen: " + months);
        }

        YearMonth oldest = newest.minusMonths(months - 1L);
        LocalDate von = oldest.atDay(1);
        LocalDate bis = newest.atEndOfMonth();

        Map<YearMonth, BigDecimal> incomeByMonth = sumByMonth(
                transactionRepository.findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
                        userId, von, bis));
        Map<YearMonth, BigDecimal> expensesByMonth = sumByMonth(
                transactionRepository.findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
                        userId, von, bis));

        List<MonthlyTotals> rows = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth month = newest.minusMonths(i);
            rows.add(row(month, incomeByMonth.get(month), expensesByMonth.get(month)));
        }
        return List.copyOf(rows);
    }

    /**
     * Baut die Zeile eines Monats aus den beiden Teilsummen.
     *
     * <p>Ein {@code null} in der Map heisst «in dieser Query kam für den Monat keine Zeile
     * zurück» — und weil jede Transaktion entweder Gutschrift oder Belastung ist, decken die
     * beiden Queries alle Buchungen des Monats ab. Sind <em>beide</em> Summen {@code null}, trägt
     * der Monat also keine einzige Buchung, und alle drei Beträge bleiben {@code null}; ein
     * dritter Zugriff nur zum Zählen ist dafür nicht nötig.
     *
     * <p>Ist nur eine der beiden {@code null}, gibt es Buchungen — die fehlende Seite ist dann
     * echte {@code 0.00} und nicht «unbekannt». Genau diese Unterscheidung begründet
     * {@link MonthlyTotals}.
     */
    private MonthlyTotals row(YearMonth month, BigDecimal income, BigDecimal expenses) {
        if (income == null && expenses == null) {
            return new MonthlyTotals(month.toString(), null, null, null);
        }
        BigDecimal in = income != null ? income : ZERO;
        BigDecimal out = expenses != null ? expenses : ZERO;
        return new MonthlyTotals(month.toString(), in, out, in.subtract(out));
    }

    /**
     * Summiert die Beträge je Monat. Ein Monat ohne Zeile in dieser Liste fehlt in der Map — die
     * Abwesenheit ist die Information, die {@link #row} auswertet, und wäre mit einem
     * vorbefüllten {@code 0.00} verloren.
     *
     * <p>{@code setScale(2, }{@link RoundingMode#UNNECESSARY}{@code )} auf jedem Betrag: die
     * Zusage «Skala 2 nach aussen» wird damit zur Eigenschaft dieser Schicht statt zu einer von
     * der Datenbank geliehenen — dieselbe Überlegung wie in
     * {@link TransactionSummaryService#expenseAmounts}. {@code UNNECESSARY}, weil
     * {@code transactions.betrag} als {@code numeric(10,2)} nie mehr Stellen tragen kann; täte es
     * das doch, wäre das ein Datendefekt und soll laut scheitern statt still zu runden (#148).
     */
    private Map<YearMonth, BigDecimal> sumByMonth(List<Transaction> transactions) {
        Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
        for (Transaction tx : transactions) {
            YearMonth month = YearMonth.from(tx.getBuchungsdatum());
            BigDecimal betrag = tx.getBetrag().setScale(2, RoundingMode.UNNECESSARY);
            byMonth.merge(month, betrag, BigDecimal::add);
        }
        return byMonth;
    }
}
