package com.budgetbuddy.budget;

import com.budgetbuddy.auth.UserIncomePort;
import com.budgetbuddy.budget.dto.FixedCostSummaryResponse;
import com.budgetbuddy.budget.dto.SafeToSpendResponse;
import com.budgetbuddy.transaction.IncomeSuggestionPort;
import com.budgetbuddy.transaction.MonthlyExpensePort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wöchentlicher Safe-to-Spend-Betrag (BE-STS-01, US-06).
 *
 * <p><strong>Formel:</strong>
 *
 * <pre>{@code
 * amount = (monthly_income − Fixkosten (Monatssumme) − variable Ausgaben) ÷ weeksLeft
 *
 * variable Ausgaben = Belastungen des laufenden Monats
 *                     − je Fixkosten-Position eine betragsgleiche Belastung
 * }</pre>
 *
 * <p><strong>Divisor.</strong> {@code weeksLeft} ist die aufgerundete Zahl der verbleibenden Wochen
 * im laufenden Monat, den heutigen Tag eingeschlossen: {@code ceil(Resttage ÷ 7)}. Aufrunden ist
 * die konservative Wahl — jeder verbleibende Tag ist budgetiert und der Wochenbetrag fällt nie zu
 * hoch aus. Abrunden liesse die letzten ein bis sechs Tage des Monats ohne Budget dastehen. Der
 * Wert ist zusätzlich hart auf mindestens 1 geklemmt (US-06: «Divisor mindestens 1»).
 *
 * <p><strong>Zeitzone.</strong> «Heute» wird in {@code Europe/Zurich} bestimmt und nicht in der
 * Zone der {@link Clock}-Bean, die {@code Clock.systemUTC()} liefert. In der Schweiz (UTC+1/+2)
 * läge der Stichtag sonst zwischen 00:00 und 02:00 Ortszeit noch im Vortag — am Monatsersten also
 * im Vormonat, mit einem Safe-to-Spend für den falschen Monat. Die Zone steht fest statt in einer
 * Property: CLAUDE.md beschränkt die App auf Nutzer mit Wohnsitz in der Schweiz. Gelesen wird nur
 * {@link Clock#instant()}, damit die Berechnung mit einer festen Clock testbar bleibt.
 *
 * <p><strong>Ausgabenfenster.</strong> Abgezogen wird die Summe des <em>ganzen</em> Kalendermonats,
 * nicht nur der Tage bis heute — US-06 spricht von «bisherigen Ausgaben». In der Praxis fällt das
 * zusammen: importierte Kontoauszüge sind rückdatiert, Buchungen nach dem heutigen Tag gibt es also
 * nicht. Für den Monat als Ganzes ist die Monatssumme zudem die richtigere Definition: sie ist
 * unabhängig vom Abrufzeitpunkt und damit über {@link MonthlyExpensePort} wiederverwendbar. Sollte
 * je vordatiert importiert werden, wäre das Fenster auf {@code [Monatserster, heute]} zu verengen.
 *
 * <p><strong>Einkommens-Heuristik (BE-STS-02).</strong> Ist kein Einkommen erfasst, wird über
 * {@link IncomeSuggestionPort} ein Vorschlag aus den wiederkehrenden Gutschriften abgeleitet. Der
 * Aufruf sitzt <em>im</em> {@code noIncome}-Zweig — und das ist eine bewusste <strong>Abweichung von
 * AC3</strong> («Heuristik läuft bei jedem Safe-to-Spend-Aufruf»), keine logische Notwendigkeit.
 *
 * <p>Beide ACs liessen sich auch wörtlich zugleich erfüllen: die Heuristik unbedingt laufen lassen
 * und {@code incomeSuggestion} nur im {@code noIncome}-Fall füllen. AC2 spricht davon, wann ein
 * Vorschlag <em>gemacht</em> wird, nicht davon, wann die Heuristik <em>läuft</em> — ein Widerspruch
 * zwischen den beiden ACs besteht also nicht.
 *
 * <p>Der Grund für die Abweichung ist ein anderer: bei einem User <em>mit</em> erfasstem Einkommen
 * ersparte die wörtliche Variante nichts und kostete bei jedem Dashboard-Aufruf eine Query über
 * zwölf Monate Gutschriften, deren Ergebnis anschliessend verworfen würde. Wird AC3 später anders
 * entschieden, ist die Verlagerung ein Zweizeiler — sie ist nicht dadurch festgeschrieben, dass die
 * ACs es erzwängen. Die Lesart ist an Issue #22 festgehalten.
 *
 * <p><strong>Kein Doppelabzug (BE-STS-04, ADR-13).</strong> Fixkosten stehen in der Formel und
 * erscheinen zusätzlich als Belastung unter den importierten Transaktionen; eine per Dauerauftrag
 * bezahlte Miete minderte den Betrag deshalb zweimal. Der {@link FixedCostDebitMatcher} streicht
 * je Fixkosten-Position höchstens <em>eine</em> betragsgleiche Belastung aus dem Ausgaben-Summanden
 * — die Position wirkt damit genau einmal, über die Fixkosten-Seite. Die Regel, ihre Grenzen und
 * die verworfenen Alternativen stehen dort und in ADR-13.
 *
 * <p>Die gestrichene Belastung bleibt in der Kategorie-Übersicht sichtbar: das Konto wurde ja
 * belastet. Ausgenommen ist sie nur aus <em>diesem</em> Summanden — dieselbe Trennung, die
 * {@code docs/prompts/02_01_mvp-requirements.md} als Auflösung von Risiko 2 vorgeschlagen hat.
 *
 * <p>Sämtliche Beträge sind {@link BigDecimal} (ADR-9) — nie {@code double}/{@code float}.
 *
 * <p><strong>Mandantentrennung:</strong> alle Eingabewerte werden ausschliesslich über die
 * user-gebundenen Methoden von {@link UserIncomePort}, {@link FixedCostService},
 * {@link MonthlyExpensePort} und {@link IncomeSuggestionPort} gelesen. Dieser Service hält kein
 * eigenes Repository und kann damit keine Query absetzen, die den User nicht einschränkt. Das gilt
 * auch für die Zuordnung: der {@link FixedCostDebitMatcher} bekommt ausschliesslich Werte, die hier
 * bereits user-gebunden geladen wurden, und liest selbst nichts nach.
 *
 * <p><strong>Modulgrenzen:</strong> Einkommen, Ausgaben und Einkommens-Vorschlag kommen über Ports
 * aus {@code auth} bzw. {@code transaction} — kein direkter Zugriff auf deren Repositories
 * (CLAUDE.md). Die Fixkosten
 * liegen im eigenen Modul; genutzt wird bewusst {@link FixedCostService#list(long)} und nicht das
 * Repository, damit hier exakt die Monatssumme eingeht, die der Wizard anzeigt (Rundungsregel und
 * Begründung in {@link FixedCostService}).
 */
@Service
public class SafeToSpendService {

    /** Rappen — Zielskala aller Beträge nach aussen. */
    private static final int RAPPEN_SCALE = 2;

    private static final int TAGE_PRO_WOCHE = 7;

    /**
     * Wohnsitz-Zone der Nutzer (CLAUDE.md: nur Kunden mit Wohnsitz in der Schweiz). Bestimmt, welcher
     * Kalendertag «heute» ist und damit den laufenden Monat.
     */
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private final UserIncomePort userIncomePort;
    private final FixedCostService fixedCostService;
    private final MonthlyExpensePort monthlyExpensePort;
    private final IncomeSuggestionPort incomeSuggestionPort;
    private final Clock clock;

    public SafeToSpendService(
            UserIncomePort userIncomePort,
            FixedCostService fixedCostService,
            MonthlyExpensePort monthlyExpensePort,
            IncomeSuggestionPort incomeSuggestionPort,
            Clock clock) {
        this.userIncomePort = userIncomePort;
        this.fixedCostService = fixedCostService;
        this.monthlyExpensePort = monthlyExpensePort;
        this.incomeSuggestionPort = incomeSuggestionPort;
        this.clock = clock;
    }

    /**
     * Berechnet den wöchentlichen Safe-to-Spend-Betrag des Users für den laufenden Monat.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return Betrag samt Divisor und den beiden Zustands-Flags aus US-06. Ohne erfasstes Einkommen
     *     ein Ergebnis mit {@code noIncome = true} und {@code amount = null} — es findet dann keine
     *     Division statt und es werden auch keine Ausgaben geladen; stattdessen läuft die
     *     Einkommens-Heuristik und füllt {@code incomeSuggestion}.
     */
    @Transactional(readOnly = true)
    public SafeToSpendResponse calculate(long userId) {
        LocalDate heute = LocalDate.ofInstant(clock.instant(), ZURICH);
        int weeksLeft = weeksLeft(heute);

        Optional<BigDecimal> monthlyIncome = userIncomePort.findMonthlyIncome(userId);
        if (monthlyIncome.isEmpty()) {
            // US-06: «keine Division wird ausgeführt». Ohne Einkommen gibt es keinen Betrag, den
            // ein Client anzeigen dürfte — 0.00 wäre die Falschaussage «du hast nichts mehr».
            // Genau hier greift die Einkommens-Heuristik (BE-STS-02) — siehe Klassen-Javadoc.
            return new SafeToSpendResponse(null, weeksLeft, false, true,
                    incomeSuggestionPort.suggestMonthlyIncome(userId).orElse(null));
        }

        FixedCostSummaryResponse fixedCostSummary = fixedCostService.list(userId);
        BigDecimal fixedCosts = fixedCostSummary.summeMonatlich();

        // Die per Dauerauftrag bezahlten Fixkosten fallen aus dem Ausgaben-Summanden — sonst
        // stünden sie in beiden und minderten den Betrag zweimal (BE-STS-04, ADR-13).
        BigDecimal expenses = FixedCostDebitMatcher.variableExpenses(
                monthlyExpensePort.expenseAmounts(userId, YearMonth.from(heute)),
                fixedCostSummary.fixedCosts());

        BigDecimal verfuegbar = monthlyIncome.get().subtract(fixedCosts).subtract(expenses);
        // HALF_UP wie in FixedCostService: die Skala-2-Rundung ist damit im ganzen budget-Modul
        // dieselbe. Der Divisor ist ein int und nie 0 — siehe weeksLeft(...).
        BigDecimal amount = verfuegbar.divide(
                BigDecimal.valueOf(weeksLeft), RAPPEN_SCALE, RoundingMode.HALF_UP);

        // incomeSuggestion bleibt null: US-06 lässt die manuelle Eingabe die Schätzung
        // überschreiben, ein Vorschlag neben einem erfassten Einkommen wäre nur verwirrend.
        return new SafeToSpendResponse(amount, weeksLeft, amount.signum() < 0, false, null);
    }

    /**
     * Verbleibende Wochen im Monat von {@code heute}, den heutigen Tag eingeschlossen und
     * aufgerundet: 31 Resttage ergeben 5 Wochen, 28 ergeben 4, 4 ergeben 1.
     *
     * <p>Aus der Herleitung folgt bereits {@code restTage ≥ 1} und damit ein Ergebnis ≥ 1 — der
     * letzte Tag des Monats zählt als volle Woche. Die Klemmung auf 1 ist trotzdem explizit: US-06
     * formuliert «Divisor mindestens 1» als Invariante der Berechnung, und eine spätere Änderung
     * (etwa ein vom Aufrufer übergebener Stichtag) darf nicht still bei einer Division durch 0
     * landen.
     */
    private static int weeksLeft(LocalDate heute) {
        long restTage = ChronoUnit.DAYS.between(heute, YearMonth.from(heute).atEndOfMonth()) + 1;
        long aufgerundet = (restTage + TAGE_PRO_WOCHE - 1) / TAGE_PRO_WOCHE;
        return (int) Math.max(1, aufgerundet);
    }
}
