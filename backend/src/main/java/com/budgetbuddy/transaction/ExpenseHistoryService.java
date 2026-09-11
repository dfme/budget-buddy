package com.budgetbuddy.transaction;

import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementiert {@link ExpenseHistoryPort} für die Abo-Erkennung (BE-REC-01, US-08): liefert die
 * Ausgaben eines Users mit normalisiertem Empfänger.
 *
 * <p><strong>Der Empfängerschlüssel.</strong> Quelle ist die erste Detailzeile — bei PostFinance
 * durchgängig die Gegenpartei (BE-PDF-07, {@code SwissBankStatementParserFixtureTest}); fehlt sie,
 * der Buchungstext, der bei UBS und Raiffeisen den Händler trägt. Dieselbe Quelle wie
 * {@code IncomeSuggestionService.groupingKey} für die Gutschriften-Seite. Aus dem Text fallen
 * Ziffernfolgen heraus: {@code COOP-1234 BERN} und {@code COOP-5678 BERN} sind derselbe Händler,
 * die Filialnummer wechselt. Was bleibt, wird in Grossschreibung gespeichert — der Vertrag
 * aus {@code V11__create_recurring_expenses_table.sql}, den das Schema ohne Extension nicht
 * erzwingen kann und den deshalb der Code einhält, so wie {@code CategoryLearningService} es für
 * {@code category_lookup} tut.
 *
 * <p><strong>Warum nicht {@code IncomeSuggestionService.normalise} wiederverwendet wird.</strong>
 * Dessen Schlüssel ist kleingeschrieben und verlässt die Klasse nie; er streicht zusätzlich
 * Monatsnamen, weil ein Lohn als «LOHN SEPTEMBER» gebucht wird. Dieser Schlüssel hier wird
 * persistiert und muss dem V11-Vertrag genügen. Die beiden Regeln teilen die Quelle, nicht das
 * Ergebnis — sie zusammenzulegen hiesse, eine der beiden über die andere zu verbiegen.
 *
 * <p><strong>Transaktionen vor V06.</strong> Für alles vor BE-PDF-07 Importierte ist
 * {@code buchungsdetails} {@code NULL}, und der Schlüssel fällt auf den Buchungstext — bei
 * PostFinance also auf {@code LASTSCHRIFT} oder {@code GIRO POST}. Solche Buchungen gruppieren
 * damit über die Zahlungsart statt über den Empfänger; die Erkennung kann daraus nichts Brauchbares
 * machen, erzeugt aber auch keinen falschen Eintrag, solange nicht zufällig zwei Folgemonate eine
 * betragsgleiche Lastschrift tragen. Aufgelöst wird das nur durch einen Reimport des Auszugs,
 * dieselbe Einschränkung wie bei {@code IncomeSuggestionService}.
 *
 * <p><strong>Mandantentrennung:</strong> gelesen wird ausschliesslich über
 * {@link TransactionRepository#findByUserIdAndIncomeFalse}; die Einschränkung auf den User steckt
 * im Query-Namen selbst.
 */
@Service
public class ExpenseHistoryService implements ExpenseHistoryPort {

    /**
     * Ziffernfolgen samt anhängendem Binde-, Schräg- oder Punktzeichen — Filialnummern,
     * Referenzen, Kartennummern-Reste. Sie wechseln von Buchung zu Buchung und würden die Gruppe
     * sonst zerlegen.
     *
     * <p>Anders als {@code IncomeSuggestionService.DIGIT_TOKEN} fällt nicht das ganze Token, nur
     * die Ziffern: {@code COOP-1234 BERN} soll zu {@code COOP BERN} werden, nicht zu {@code BERN}
     * — der Rest wäre ein Schlüssel, den jeder andere Händler mit Filialnummer in Bern teilt.
     */
    private static final Pattern DIGITS = Pattern.compile("[-/.]*\\d+[-/.]*");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final TransactionRepository transactionRepository;

    public ExpenseHistoryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseEntry> expenseHistory(long userId) {
        return transactionRepository.findByUserIdAndIncomeFalse(userId).stream()
                .map(tx -> new ExpenseEntry(
                        payeeKey(tx), tx.getBetrag(), YearMonth.from(tx.getBuchungsdatum())))
                .toList();
    }

    /**
     * Der Empfängerschlüssel einer Buchung — erste Detailzeile, sonst Buchungstext, normalisiert
     * (siehe Klassen-Javadoc). Package-privat, damit der Unit-Test die Regel ohne Repository prüft.
     */
    static String payeeKey(Transaction tx) {
        String payee = firstDetailLine(tx.getBuchungsdetails());
        return normalise(payee != null ? payee : tx.getBuchungstext());
    }

    /**
     * Die erste Detailzeile, oder {@code null}, wenn es keine verwertbare gibt — die Spalte ist
     * {@code NULL} oder die erste Zeile leer. Der zweite Fall kommt aus dem Parser nicht
     * ({@code ParsedTransaction#detailsAsText()} verbindet nur nichtleere Zeilen), aber ein leerer
     * Schlüssel würfe alle betroffenen Buchungen in einen Topf.
     */
    private static String firstDetailLine(String buchungsdetails) {
        if (buchungsdetails == null) {
            return null;
        }
        int umbruch = buchungsdetails.indexOf('\n');
        String erste = (umbruch < 0 ? buchungsdetails : buchungsdetails.substring(0, umbruch)).trim();
        return erste.isEmpty() ? null : erste;
    }

    /**
     * Grossschreibung, ohne Ziffernfolgen, Whitespace kollabiert.
     *
     * <p>Bleibt davon nichts übrig — ein Text nur aus einer Referenznummer —, dient der
     * grossgeschriebene Originaltext als Schlüssel: dann gruppieren sich nur wirklich identische
     * Texte, statt dass alle solchen Buchungen unter dem leeren Schlüssel zusammenfallen.
     *
     * <p>{@link Locale#ROOT} statt Default-Locale: unter {@code tr-TR} würde «i» sonst zu «İ»
     * (dieselbe Begründung wie in {@code CategoryLearningService}).
     */
    private static String normalise(String text) {
        String gross = text.toUpperCase(Locale.ROOT);
        String ohneZiffern = DIGITS.matcher(gross).replaceAll(" ");
        String normalisiert = WHITESPACE.matcher(ohneZiffern).replaceAll(" ").trim();
        return normalisiert.isEmpty() ? WHITESPACE.matcher(gross).replaceAll(" ").trim() : normalisiert;
    }
}
