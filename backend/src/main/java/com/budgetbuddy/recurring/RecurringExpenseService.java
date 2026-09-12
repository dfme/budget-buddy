package com.budgetbuddy.recurring;

import com.budgetbuddy.money.ChfAmounts;
import com.budgetbuddy.notification.NotificationPort;
import com.budgetbuddy.transaction.ExpenseHistoryPort;
import com.budgetbuddy.transaction.ExpenseHistoryPort.ExpenseEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erkennt wiederkehrende Ausgaben — Abos, Ratenzahlungen — in der Ausgaben-Historie eines Users
 * (BE-REC-01, US-08).
 *
 * <p><strong>Regel.</strong> Ein Empfänger gilt als wiederkehrend, wenn er in zwei
 * <em>aufeinanderfolgenden</em> Kalendermonaten je eine Belastung trägt, deren Beträge um höchstens
 * {@value #TOLERANCE_PERCENT}&nbsp;% auseinanderliegen — Basis ist der frühere Betrag. Januar und
 * März genügen nicht, auch nicht mit identischem Betrag; Januar und Februar genügen, auch wenn
 * dazwischen 20.90 und 21.20 stehen. Der Empfänger kommt bereits normalisiert über den
 * {@link ExpenseHistoryPort}; welche Zeile des Bank-PDFs ihn trägt, weiss dieses Modul nicht.
 *
 * <p><strong>Was geschrieben wird.</strong> Pro neu erkanntem Empfänger genau eine Zeile mit
 * {@link RecurringExpenseStatus#DETECTED}: der Betrag des <em>jüngsten</em> qualifizierenden
 * Monatspaars und der erste Monat der Reihe in den Daten. Dazu eine Benachrichtigung vom Typ
 * {@value #NOTIFICATION_TYPE} mit der ID der Zeile als {@code referenceId} — der «Neu»-Hinweis aus
 * US-08.
 *
 * <p><strong>Was nicht angefasst wird.</strong> Ein Empfänger, für den bereits eine Zeile existiert,
 * wird übersprungen — in beiden Status. Bei {@code DISMISSED} ist das die Regel aus US-08 («künftige
 * Transaktionen desselben Empfängers werden nicht mehr automatisch erkannt»); bei {@code DETECTED}
 * verhindert es, dass jeder weitere Import dieselbe Benachrichtigung noch einmal erzeugt. Ein
 * gestiegener Abo-Preis aktualisiert die Zeile deshalb nicht — die Übersicht zeigt weiterhin den
 * Betrag der ersten Erkennung. Das ist eine bewusste Vereinfachung; sie ist im PR zu BE-REC-01
 * benannt.
 *
 * <p><strong>Eine Zeile pro Empfänger.</strong> {@code UNIQUE (user_id, payee_key)} (V11) erlaubt
 * keine zweite. Zwei Abos beim selben Anbieter — Mobile und Internet bei Swisscom — ergeben damit
 * einen Eintrag mit dem Betrag des jüngsten Paars, nicht zwei. Die Grenze liegt im Schema, nicht in
 * dieser Klasse.
 *
 * <p><strong>Kein Zeitfenster.</strong> Gruppiert wird über die gesamte Historie, weil ein Import
 * einen Jahresauszug von 2025 nachreichen kann und dessen Abos genauso zählen. Die Menge ist die
 * eines einzelnen Users; die Gruppen sind klein, der paarweise Vergleich zweier Folgemonate
 * bleibt es auch.
 *
 * <p>Sämtliche Beträge sind {@link BigDecimal} (ADR-9). Verglichen wird auf Rappen normalisiert,
 * wie im {@code FixedCostDebitMatcher} — {@link BigDecimal#compareTo} statt {@code equals}, damit
 * die Skala des Vergleichs nicht an der Skala einer anderen Klasse hängt.
 *
 * <p><strong>Mandantentrennung:</strong> beide Lesezugriffe — {@link ExpenseHistoryPort} und
 * {@link RecurringExpenseRepository#findByUserId} — sind auf den übergebenen User eingeschränkt;
 * geschrieben wird mit derselben ID.
 *
 * <p><strong>Logging:</strong> nur Zähler. Empfängernamen sind Transaktionsdaten und gehören nicht
 * ins Log (BE-PDF-06, CONVENTIONS «Logging-Kontext»).
 */
@Service
public class RecurringExpenseService implements RecurringExpenseDetectionPort {

    /** Typ der Benachrichtigung — der Wert, den {@code NotificationPort} als freien String führt. */
    public static final String NOTIFICATION_TYPE = "RECURRING_EXPENSE_DETECTED";

    /** ±2 % — die Toleranz aus US-08. */
    static final int TOLERANCE_PERCENT = 2;

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);

    private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    /** Rappen — Zielskala aller Beträge (ADR-9). */
    private static final int RAPPEN_SCALE = ChfAmounts.RAPPEN_SCALE;

    private final ExpenseHistoryPort expenseHistoryPort;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final NotificationPort notificationPort;
    private final Clock clock;

    public RecurringExpenseService(
            ExpenseHistoryPort expenseHistoryPort,
            RecurringExpenseRepository recurringExpenseRepository,
            NotificationPort notificationPort,
            Clock clock) {
        this.expenseHistoryPort = expenseHistoryPort;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Eine Transaktion für Lesen, Schreiben und Benachrichtigen: entweder stehen Zeile und
     * Notification zusammen in der Datenbank oder keines von beiden. Eine Notification, deren
     * {@code referenceId} auf nichts zeigt, wäre der schlechtere Zustand.
     */
    @Override
    @Transactional
    public void detect(long userId) {
        List<ExpenseEntry> history = expenseHistoryPort.expenseHistory(userId);

        // Bekannte Empfänger in beiden Status — beide werden übersprungen (siehe Klassen-Javadoc).
        // Grossgeschrieben verglichen, weil V11 den Schlüssel so speichert.
        Set<String> known = new HashSet<>();
        for (RecurringExpense existing : recurringExpenseRepository.findByUserId(userId)) {
            known.add(existing.getPayeeKey().toUpperCase(Locale.ROOT));
        }

        // TreeMap statt HashMap: die Reihenfolge, in der Zeilen und Notifications entstehen, soll
        // nicht an der Zeilenreihenfolge der Query hängen.
        Map<String, List<ExpenseEntry>> byPayee = new TreeMap<>();
        for (ExpenseEntry entry : history) {
            String key = entry.payeeKey().toUpperCase(Locale.ROOT);
            if (known.contains(key)) {
                continue;
            }
            byPayee.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        int detected = 0;
        for (Map.Entry<String, List<ExpenseEntry>> group : byPayee.entrySet()) {
            Optional<Detection> detection = qualify(group.getValue());
            if (detection.isEmpty()) {
                continue;
            }
            RecurringExpense saved = recurringExpenseRepository.save(new RecurringExpense(
                    userId, group.getKey(), detection.get().amount(),
                    detection.get().firstMonth(), clock.instant()));
            notificationPort.create(userId, NOTIFICATION_TYPE, saved.getId(),
                    message(saved.getPayeeKey(), saved.getAmount()));
            detected++;
        }

        log.info("Abo-Erkennung: {} neue wiederkehrende Ausgabe(n) aus {} Belastung(en), "
                        + "{} Empfänger bereits bekannt oder ausgeschlossen.",
                detected, history.size(), known.size());
    }

    /**
     * Prüft die Belastungen eines Empfängers gegen die Regel aus US-08.
     *
     * <p>Je Monat werden die Beträge gesammelt und dann aufsteigend die Paare (Monat, Folgemonat)
     * durchgegangen. Der erste Monat eines Treffers ist der Beginn der Reihe; der Betrag des
     * <em>letzten</em> Treffers — die aufsteigende Reihenfolge macht ihn zum jüngsten — wird
     * gespeichert.
     *
     * <p><strong>Eine Lücke beginnt die Reihe neu.</strong> Schliesst ein Treffer nicht direkt an
     * den vorherigen an — Januar/Februar erkannt, März bis Juli nichts, August/September wieder —,
     * wird der Erstmonat auf den jüngeren Abschnitt gesetzt. Sonst behauptete die Zeile «seit
     * Januar» eine Laufzeit, die die Daten nicht hergeben (Review PR #298). Ein Preissprung wirkt
     * gleich: das Paar über den Sprung hinweg qualifiziert nicht, die Reihe beginnt danach.
     *
     * <p><strong>Deterministisch auch bei mehreren Buchungen im selben Monat.</strong> Die Beträge
     * je Monat werden aufsteigend sortiert, bevor die Paare verglichen werden. Ohne das hinge bei
     * zwei gleichzeitig qualifizierenden Paaren — etwa zwei Coop-Einkäufe zu 49.90 und 50.00 in
     * beiden Monaten — der gespeicherte Betrag an der Zeilenreihenfolge der Query, die keine
     * Zusage trägt ({@link ExpenseHistoryPort#expenseHistory}); und weil die Zeile danach nie
     * aktualisiert wird, bliebe der Zufall dauerhaft. Mit der Sortierung ist es immer der
     * <em>höchste</em> qualifizierende Betrag des jüngsten Paars (Review PR #298).
     *
     * @return leer, wenn kein Folgemonatspaar innerhalb der Toleranz liegt.
     */
    private static Optional<Detection> qualify(List<ExpenseEntry> group) {
        Map<YearMonth, List<BigDecimal>> byMonth = new TreeMap<>();
        for (ExpenseEntry entry : group) {
            byMonth.computeIfAbsent(entry.month(), m -> new ArrayList<>())
                    .add(rappen(entry.amount()));
        }
        byMonth.values().forEach(amounts -> amounts.sort(Comparator.naturalOrder()));

        YearMonth firstMonth = null;
        YearMonth lastHitMonth = null;
        BigDecimal latestAmount = null;
        for (Map.Entry<YearMonth, List<BigDecimal>> month : byMonth.entrySet()) {
            YearMonth nextMonth = month.getKey().plusMonths(1);
            List<BigDecimal> next = byMonth.get(nextMonth);
            if (next == null) {
                continue;
            }
            boolean pairHit = false;
            for (BigDecimal earlier : month.getValue()) {
                for (BigDecimal later : next) {
                    if (withinTolerance(earlier, later)) {
                        pairHit = true;
                        latestAmount = later;
                    }
                }
            }
            if (!pairHit) {
                continue;
            }
            // Schliesst das Paar nicht an den letzten Treffer an, beginnt die Reihe neu. Pro Paar
            // entschieden, nicht pro Treffer: mehrere Buchungen im Monat sind keine Lücke.
            if (!month.getKey().equals(lastHitMonth)) {
                firstMonth = month.getKey();
            }
            lastHitMonth = nextMonth;
        }
        return firstMonth == null
                ? Optional.empty()
                : Optional.of(new Detection(latestAmount, firstMonth));
    }

    /**
     * {@code |later − earlier| ≤ earlier × 2 %}. Der frühere Betrag ist die Basis, weil er der
     * bekannte Vergleichswert ist — der Betrag, den der Nutzer bisher gezahlt hat.
     *
     * <p>Ein nicht positiver früherer Betrag qualifiziert nie: Bei {@code 0.00} wäre das Band
     * leer und ein Paar aus Nullbuchungen hiesse «Abo über CHF 0.00».
     */
    private static boolean withinTolerance(BigDecimal earlier, BigDecimal later) {
        if (earlier.signum() <= 0) {
            return false;
        }
        BigDecimal maxAbweichung = earlier.multiply(TOLERANCE);
        return later.subtract(earlier).abs().compareTo(maxAbweichung) <= 0;
    }

    /**
     * Normalisiert einen Betrag auf Rappen. {@link RoundingMode#HALF_UP} statt
     * {@code UNNECESSARY}: Die Werte kommen aus einer {@code DECIMAL(10,2)}-Spalte und haben
     * Skala 2 — aber ein Vergleich, der an dieser Zusage hängt, soll bei einer Abweichung leicht
     * runden statt den Import-Flow mit einer {@code ArithmeticException} abzubrechen.
     */
    private static BigDecimal rappen(BigDecimal betrag) {
        return betrag.setScale(RAPPEN_SCALE, RoundingMode.HALF_UP);
    }

    /** Anzeigetext der Benachrichtigung — Deutsch wie die gesamte Oberfläche. */
    private static String message(String payeeKey, BigDecimal amount) {
        return "Wiederkehrende Ausgabe erkannt: " + payeeKey + " — CHF "
                + rappen(amount).toPlainString() + " pro Monat";
    }

    /**
     * Ergebnis einer qualifizierten Gruppe.
     *
     * @param amount Betrag des jüngsten qualifizierenden Paars, Skala 2.
     * @param firstMonth erster Monat der Reihe in den Daten.
     */
    private record Detection(BigDecimal amount, YearMonth firstMonth) {}
}
