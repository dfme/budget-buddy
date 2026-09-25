package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.budgetbuddy.transaction.ExpenseHistoryPort.ExpenseEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit-Test des Empfängerschlüssels für die Abo-Erkennung (BE-REC-01, US-08): Aus welcher Zeile er
 * kommt und was die Normalisierung daraus macht. Der V11-Vertrag — ausschliesslich
 * Grossschreibung — wird hier durchgesetzt und deshalb hier belegt.
 */
class ExpenseHistoryServiceTest {

    private static final long USER_ID = 42L;

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final ExpenseHistoryService service = new ExpenseHistoryService(repository);

    private static Transaction expense(String buchungstext, String details, String betrag) {
        return new Transaction(USER_ID, LocalDate.of(2026, 7, 15), buchungstext, details,
                new BigDecimal(betrag), false, "Sonstiges", "sha");
    }

    /** PostFinance: Die Buchungszeile trägt nur die Zahlungsart, der Empfänger steht darunter. */
    @Test
    void payeeKey_isTheFirstDetailLineInUpperCase() {
        Transaction tx = expense("LASTSCHRIFT", "Netflix International BV\nABO JULI", "20.90");

        assertThat(ExpenseHistoryService.payeeKey(tx)).isEqualTo("NETFLIX INTERNATIONAL BV");
    }

    /** UBS/Raiffeisen und alles vor V06: keine Detailzeilen, der Händler steht in der Buchungszeile. */
    @Test
    void payeeKey_fallsBackToBuchungstextWithoutDetails() {
        Transaction tx = expense("Swisscom (Schweiz) AG", null, "65.00");

        assertThat(ExpenseHistoryService.payeeKey(tx)).isEqualTo("SWISSCOM (SCHWEIZ) AG");
    }

    /** Filialnummern und Referenzen wechseln von Buchung zu Buchung — ohne den Schritt zerfiele die Gruppe. */
    @Test
    void payeeKey_dropsDigitRunsButKeepsTheMerchantName() {
        assertThat(ExpenseHistoryService.payeeKey(expense("TWINT", "COOP-1234 BERN", "12.00")))
                .isEqualTo("COOP BERN");
        assertThat(ExpenseHistoryService.payeeKey(expense("TWINT", "COOP-5678 BERN", "12.00")))
                .isEqualTo("COOP BERN");
        assertThat(ExpenseHistoryService.payeeKey(expense("TWINT", "Migros 042 Zürich", "12.00")))
                .isEqualTo("MIGROS ZÜRICH");
    }

    @Test
    void payeeKey_collapsesWhitespace() {
        Transaction tx = expense("KAUF/DIENSTLEISTUNG", "  MIGROS   M  BERN\tWANKDORF ", "45.60");

        assertThat(ExpenseHistoryService.payeeKey(tx)).isEqualTo("MIGROS M BERN WANKDORF");
    }

    /** Ein Text nur aus Ziffern darf nicht auf den leeren Schlüssel fallen — der würfe alles zusammen. */
    @Test
    void payeeKey_keepsTheOriginalWhenNothingSurvivesTheNormalisation() {
        Transaction tx = expense("ESR", "12-345678-9", "99.00");

        assertThat(ExpenseHistoryService.payeeKey(tx)).isEqualTo("12-345678-9");
    }

    /** Eine leere erste Zeile zählt nicht als Empfänger — Rückfall wie bei fehlenden Details. */
    @Test
    void payeeKey_ignoresABlankFirstDetailLine() {
        Transaction tx = expense("GIRO POST", "   \nSTADTWERKE BERN", "78.50");

        assertThat(ExpenseHistoryService.payeeKey(tx)).isEqualTo("GIRO POST");
    }

    @Test
    void expenseHistory_mapsEveryExpenseToKeyAmountAndMonth() {
        when(repository.findByUserIdAndIncomeFalse(USER_ID)).thenReturn(List.of(
                expense("LASTSCHRIFT", "NETFLIX INTERNATIONAL BV", "20.90"),
                expense("ESR", "Stadtwerke Bern", "78.50")));

        assertThat(service.expenseHistory(USER_ID)).containsExactly(
                new ExpenseEntry("NETFLIX INTERNATIONAL BV", new BigDecimal("20.90"),
                        YearMonth.of(2026, 7)),
                new ExpenseEntry("STADTWERKE BERN", new BigDecimal("78.50"),
                        YearMonth.of(2026, 7)));
    }
}
