package com.budgetbuddy.recurring;

import com.budgetbuddy.money.ChfAmounts;
import com.budgetbuddy.notification.NotificationPort;
import com.budgetbuddy.recurring.dto.RecurringExpenseResponse;
import com.budgetbuddy.transaction.ExpenseHistoryPort;
import com.budgetbuddy.transaction.ExpenseHistoryPort.ExpenseEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * und stellt die Abo-Übersicht bereit (BE-REC-01/BE-REC-02, US-08).
 *
 * <p><strong>Regel.</strong> Ein Empfänger gilt als wiederkehrend, wenn er in zwei
 * <em>aufeinanderfolgenden</em> Kalendermonaten je eine Belastung trägt, deren Beträge um höchstens
 * {@value RecurringExpenseAmountPort#TOLERANCE_PERCENT}&nbsp;% auseinanderliegen — Basis ist der
 * frühere Betrag ({@link RecurringExpenseAmountPort#withinTolerance}). Januar und
 * März genügen nicht, auch nicht mit identischem Betrag; Januar und Februar genügen, auch wenn
 * dazwischen 20.90 und 21.20 stehen. Der Empfänger kommt bereits normalisiert über den
 * {@link ExpenseHistoryPort}; welche Zeile des Bank-PDFs ihn trägt, weiss dieses Modul nicht.
 *
 * <p><strong>Was geschrieben wird.</strong> Pro neu erkanntem Empfänger genau eine Zeile: der
 * Betrag des <em>jüngsten</em> qualifizierenden Monatspaars und der erste Monat der Reihe in den
 * Daten. Dazu <em>pro Lauf</em> eine
 * Benachrichtigung vom Typ {@value #NOTIFICATION_TYPE}, die alle Treffer des Laufs bündelt
 * («3 neue Abos erkannt: …») — der «Neu»-Hinweis aus US-08. Jede Zeile trägt die ID dieser
 * Benachrichtigung ({@code notification_id}, V14); der Verweis läuft seit FE-NOTIF-04 (#336) von
 * der Zeile zur Benachrichtigung und nicht mehr umgekehrt, weil eine Benachrichtigung jetzt
 * mehrere Zeilen meldet. Ein Import, der 27 Abos auf einmal erkennt, erzeugte vorher 27
 * Benachrichtigungen, die nur einzeln als gelesen zu markieren waren.
 *
 * <p><strong>Bestehende Zeilen werden neu bewertet, nicht übersprungen (BE-REC-04, #350).</strong>
 * Bis dahin übersprang jeder Lauf einen bekannten Empfänger vollständig: {@code amount} blieb die
 * Momentaufnahme der ersten Erkennung, und eine ausgelaufene Reihe blieb {@code DETECTED}. Solange
 * die Zeile nur in einer Liste stand, war das folgenlos; seit FE-FC-05 (#338) ist sie ein
 * finanzieller Eingabewert des Safe-to-Spend, und beides kostet Geld — ein Preissprung über ±2 %
 * zählte doppelt, ein gekündigtes Abo dauerhaft weiter.
 *
 * <p>Jeder Lauf prüft deshalb jede nicht verneinte Zeile gegen die volle Historie:
 *
 * <ul>
 *   <li>{@code amount} und {@code firstDetectedMonth} folgen dem jüngsten qualifizierenden Paar
 *       ({@link RecurringExpense#updateFrom}) — ein Preissprung über die Toleranz hinaus
 *       eingeschlossen, denn er beginnt nach der Regel von {@link #qualify} eine neue Reihe.</li>
 *   <li>Der Status folgt der Aktivität: ohne Abbuchung im Fenster
 *       {@link #ACTIVE_WINDOW_MONTHS} wird die Zeile {@link RecurringExpenseStatus#ENDED}, mit
 *       Abbuchung wieder {@link RecurringExpenseStatus#DETECTED}.</li>
 * </ul>
 *
 * <p>Eine Aktualisierung erzeugt <strong>keine</strong> Benachrichtigung. Der «Neu»-Hinweis gilt
 * dem Fund eines Abos, nicht seiner Preisänderung; ein Bündel für einen Empfänger, den der Nutzer
 * längst kennt, wäre Rauschen im Badge.
 *
 * <p><strong>{@code DISMISSED} ist terminal.</strong> Ein verneinter Empfänger wird weder im Betrag
 * noch im Status angefasst und nie wieder erkannt — die Regel aus US-08 AC3 («künftige
 * Transaktionen desselben Empfängers werden nicht mehr automatisch erkannt»). Er ist der einzige
 * Fall, der weiterhin vollständig übersprungen wird.
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
 * <p><strong>Safe-to-Spend (FE-FC-05).</strong> Erkannte Abos mindern seit FE-FC-05 den
 * Safe-to-Spend wie Fixkosten-Positionen. Das budget-Modul liest dafür über
 * {@link RecurringExpenseAmountPort#detectedAmounts} die Beträge der {@code DETECTED}-Zeilen — und
 * nur diese: dass eine Zeile noch läuft, hat dieser Service beim letzten Import entschieden und in
 * den Status geschrieben. Bis BE-REC-04 prüfte der Lesepfad das selbst über ein Aktivitätsfenster
 * (Review PR #345), weil die Zeile nie neu bewertet wurde; dieses Fenster ist mit der Neubewertung
 * entfallen. Die Zuordnung zur Abbuchung des Monats und die Regel gegen Doppelzählung liegen
 * drüben ({@code FixedCostDebitMatcher}, ADR-13-Nachtrag); die Toleranz dafür ist dieselbe wie
 * hier bei der Erkennung.
 *
 * <p><strong>Mandantentrennung:</strong> alle Lesezugriffe — {@link ExpenseHistoryPort#expenseHistory},
 * {@link RecurringExpenseRepository#findByUserId} und
 * {@link RecurringExpenseRepository#findByUserIdAndStatus} — sind auf den übergebenen User
 * eingeschränkt; geschrieben wird mit derselben ID.
 *
 * <p><strong>Logging:</strong> nur Zähler. Empfängernamen sind Transaktionsdaten und gehören nicht
 * ins Log (BE-PDF-06, CONVENTIONS «Logging-Kontext»).
 */
@Service
public class RecurringExpenseService
        implements RecurringExpenseDetectionPort, RecurringExpenseAmountPort {

    /** Typ der Benachrichtigung — der Wert, den {@code NotificationPort} als freien String führt. */
    public static final String NOTIFICATION_TYPE = "RECURRING_EXPENSE_DETECTED";

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);

    /** Rappen — Zielskala aller Beträge (ADR-9). */
    private static final int RAPPEN_SCALE = ChfAmounts.RAPPEN_SCALE;

    /**
     * Wie viele Monate ein Empfänger ohne Belastung bleiben darf, bevor seine Zeile als
     * ausgelaufen gilt ({@link RecurringExpenseStatus#ENDED}) — den jüngsten Monat der Historie
     * eingeschlossen.
     *
     * <p>Drei und nicht zwei: Abbuchungstage verschieben sich über Wochenenden und Feiertage, eine
     * Jahresrechnung kann einmal einen Monat später kommen, und ein einzelner ausgelassener Monat
     * ist noch kein Kündigungsindiz. Bei zwei Monaten kippte eine am 1. September statt am 31.
     * August gebuchte Belastung die Zeile.
     *
     * <p>Bis BE-REC-04 stand dieselbe Zahl als {@code ACTIVE_WINDOW_MONTHS} am
     * {@link RecurringExpenseAmountPort} und wurde bei jedem Lesen des Safe-to-Spend ausgewertet.
     * Sie ist eine Erkennungsregel und gehört deshalb hierher.
     */
    static final int ACTIVE_WINDOW_MONTHS = 3;

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
     * <p>Eine Transaktion für Lesen, Schreiben und Benachrichtigen: entweder stehen Zeilen und
     * Notification zusammen in der Datenbank oder keines von beiden. Eine Zeile, deren
     * {@code notificationId} auf nichts zeigt, hiesse ein Abo, das nie «Neu» war.
     *
     * <p>Die Notification entsteht <em>vor</em> den Zeilen, weil diese ihre ID tragen — und nur,
     * wenn es mindestens einen <em>neuen</em> Treffer gibt. Die Neubewertung bestehender Zeilen
     * (BE-REC-04) meldet nichts: ein Lauf, der nur einen Preis nachzieht oder ein Abo auslaufen
     * lässt, erzeugt kein Bündel.
     *
     * <p><strong>Eine leere Historie ändert nichts.</strong> Keine Daten sind kein Beleg für ein
     * Ende — sonst setzte ein Lauf ohne Ausgaben sämtliche Zeilen des Users auf
     * {@link RecurringExpenseStatus#ENDED}.
     */
    @Override
    @Transactional
    public void detect(long userId) {
        List<ExpenseEntry> history = expenseHistoryPort.expenseHistory(userId);
        if (history.isEmpty()) {
            log.info("Abo-Erkennung: keine Belastungen, nichts zu bewerten.");
            return;
        }

        // Bestehende Zeilen nach Empfänger, grossgeschrieben verglichen, weil V11 den Schlüssel so
        // speichert. Anders als bis BE-REC-04 ist das keine Ausschlussmenge mehr, sondern der
        // Zugriff auf die Zeile, die neu bewertet wird.
        Map<String, RecurringExpense> existing = new HashMap<>();
        for (RecurringExpense row : recurringExpenseRepository.findByUserId(userId)) {
            existing.put(row.getPayeeKey().toUpperCase(Locale.ROOT), row);
        }

        // TreeMap statt HashMap: die Reihenfolge, in der Zeilen und Notifications entstehen, soll
        // nicht an der Zeilenreihenfolge der Query hängen.
        Map<String, List<ExpenseEntry>> byPayee = new TreeMap<>();
        for (ExpenseEntry entry : history) {
            byPayee.computeIfAbsent(entry.payeeKey().toUpperCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(entry);
        }

        YearMonth activeFrom = latestMonth(history).minusMonths(ACTIVE_WINDOW_MONTHS - 1L);

        int updated = reevaluate(existing, byPayee, activeFrom);
        int created = detectNew(userId, existing, byPayee, activeFrom);

        log.info("Abo-Erkennung: {} neue wiederkehrende Ausgabe(n) aus {} Belastung(en), "
                        + "{} bestehende Zeile(n) neu bewertet, {} davon verändert, "
                        + "{} Empfänger als «Kein Abo» ausgeschlossen.",
                created, history.size(), existing.size() - dismissed(existing), updated,
                dismissed(existing));
    }

    /**
     * Bewertet die bestehenden, nicht verneinten Zeilen gegen die Historie neu (BE-REC-04): Betrag
     * und Erstmonat folgen dem jüngsten qualifizierenden Paar, der Status der Aktivität.
     *
     * <p>Iteriert über die <em>Zeilen</em> und nicht über die Gruppen der Historie. Der Unterschied
     * zählt für den Empfänger ohne jede Belastung in den Daten: er hat keine Gruppe, und ein
     * Durchgang über die Gruppen liesse genau die Zeile stehen, die am eindeutigsten ausgelaufen
     * ist.
     *
     * <p>Kein Aufruf von {@code save}: die Entities stammen aus dem Persistence Context dieser
     * Transaktion und werden beim Commit geschrieben. Ein {@code save} wäre derselbe Merge noch
     * einmal.
     *
     * @return wie viele Zeilen sich inhaltlich geändert haben — nur für das Log.
     */
    private static int reevaluate(Map<String, RecurringExpense> existing,
            Map<String, List<ExpenseEntry>> byPayee, YearMonth activeFrom) {
        int updated = 0;
        for (Map.Entry<String, RecurringExpense> row : existing.entrySet()) {
            RecurringExpense expense = row.getValue();
            if (expense.getStatus() == RecurringExpenseStatus.DISMISSED) {
                continue;
            }
            List<ExpenseEntry> group = byPayee.getOrDefault(row.getKey(), List.of());

            RecurringExpenseStatus before = expense.getStatus();
            BigDecimal amountBefore = expense.getAmount();
            YearMonth firstMonthBefore = expense.getFirstDetectedMonth();

            // Ohne Treffer bleibt die Zeile inhaltlich stehen. Das trifft nur eine geschrumpfte
            // Historie; über DETECTED/ENDED entscheidet trotzdem die Aktivität unten.
            qualify(group).ifPresent(d -> expense.updateFrom(d.amount(), d.firstMonth()));

            if (isActive(group, activeFrom)) {
                expense.markActive();
            } else {
                expense.markEnded();
            }

            if (expense.getStatus() != before
                    || expense.getAmount().compareTo(amountBefore) != 0
                    || !expense.getFirstDetectedMonth().equals(firstMonthBefore)) {
                updated++;
            }
        }
        return updated;
    }

    /**
     * Legt für jeden bislang unbekannten Empfänger, dessen Gruppe qualifiziert, eine Zeile an und
     * meldet alle zusammen in <em>einer</em> Bündel-Benachrichtigung (FE-NOTIF-04).
     *
     * <p>Erst sammeln, dann schreiben: die Benachrichtigung nennt die Zahl und die Namen aller
     * Treffer, und die Zeilen tragen ihre ID — beides steht erst nach dem Durchgang fest. TreeMap
     * auch hier, damit die Reihenfolge in Text und Tabelle die der Empfänger ist.
     *
     * <p>Eine Zeile, die schon bei ihrer Erkennung ausgelaufen ist, entsteht direkt als
     * {@link RecurringExpenseStatus#ENDED} — der Fall des nachgereichten Jahresauszugs. Sie geht
     * trotzdem ins Bündel: gefunden wurde sie, und «du hattest ein Abo, das ausgelaufen ist» ist
     * genau die versteckte Kostenstelle, um die es in US-08 geht.
     *
     * @return wie viele Zeilen neu entstanden sind.
     */
    private int detectNew(long userId, Map<String, RecurringExpense> existing,
            Map<String, List<ExpenseEntry>> byPayee, YearMonth activeFrom) {
        Map<String, Detection> detections = new TreeMap<>();
        for (Map.Entry<String, List<ExpenseEntry>> group : byPayee.entrySet()) {
            if (existing.containsKey(group.getKey())) {
                continue;
            }
            qualify(group.getValue()).ifPresent(d -> detections.put(group.getKey(), d));
        }
        if (detections.isEmpty()) {
            return 0;
        }

        long notificationId = notificationPort.create(userId, NOTIFICATION_TYPE, null,
                message(List.copyOf(detections.keySet())));
        Instant now = clock.instant();
        for (Map.Entry<String, Detection> detection : detections.entrySet()) {
            RecurringExpense expense = new RecurringExpense(
                    userId, detection.getKey(), detection.getValue().amount(),
                    detection.getValue().firstMonth(), now, notificationId);
            if (!isActive(byPayee.get(detection.getKey()), activeFrom)) {
                expense.markEnded();
            }
            recurringExpenseRepository.save(expense);
        }
        return detections.size();
    }

    /**
     * Der jüngste Monat der gesamten Ausgaben-Historie — der Bezugspunkt, gegen den
     * {@link #isActive} misst.
     *
     * <p><strong>Die Daten des Users, nicht die Uhr.</strong> «Beendet» heisst «das Konto lief
     * weiter, dieser Empfänger nicht». Endet schlicht die Datenlage, gibt es für kein Abo einen
     * Beleg in eine der beiden Richtungen — und die Vorannahme bei Abos ist Weiterlaufen, sie
     * verlängern sich von selbst. Gegen die Uhr gemessen, liesse ein nachgereichter Jahresauszug
     * von 2025 jedes darin erkannte Abo sofort als ausgelaufen gelten.
     *
     * <p>Der Preis steht im ADR-13-Nachtrag: wer monatelang nichts importiert, behält seine
     * Abo-Abzüge, statt sie wie bis BE-REC-04 nach drei Monaten zu verlieren. Das ist die
     * gewollte Richtung — ADR-13 nennt «Safe-to-Spend zu hoch» die unangenehmere Fehlerrichtung,
     * und genau die erzeugte das alte Lesefenster.
     *
     * @param history nicht leer — der Aufrufer hat den leeren Fall vorher abgefangen.
     */
    private static YearMonth latestMonth(List<ExpenseEntry> history) {
        YearMonth latest = history.get(0).month();
        for (ExpenseEntry entry : history) {
            if (entry.month().isAfter(latest)) {
                latest = entry.month();
            }
        }
        return latest;
    }

    /**
     * {@code true}, wenn der Empfänger ab {@code activeFrom} mindestens einmal belastet hat —
     * unabhängig vom Betrag, wie schon beim Aktivitätsfenster des Lesepfads (FE-FC-05): ob eine
     * Belastung die Abbuchung <em>dieses</em> Abos ist, entscheidet der Betragsvergleich im
     * budget-Modul, nicht die Frage, ob der Empfänger überhaupt noch aktiv ist.
     */
    private static boolean isActive(List<ExpenseEntry> group, YearMonth activeFrom) {
        return group != null && group.stream().anyMatch(e -> !e.month().isBefore(activeFrom));
    }

    /** Wie viele der bestehenden Zeilen verneint sind — nur für das Log. */
    private static long dismissed(Map<String, RecurringExpense> existing) {
        return existing.values().stream()
                .filter(e -> e.getStatus() == RecurringExpenseStatus.DISMISSED)
                .count();
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
     * Zusage trägt ({@link ExpenseHistoryPort#expenseHistory}). Seit BE-REC-04 schriebe jeder
     * weitere Lauf den Zufall neu und die Zeile flackerte zwischen zwei Beträgen; vorher blieb er
     * dauerhaft stehen. Mit der Sortierung ist es immer der <em>höchste</em> qualifizierende
     * Betrag des jüngsten Paars (Review PR #298).
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
     * Der frühere Betrag ist die Basis, weil er der bekannte Vergleichswert ist — der Betrag, den
     * der Nutzer bisher gezahlt hat. Die Regel selbst steht am Port, weil der Safe-to-Spend
     * dieselbe braucht (siehe Klassen-Javadoc).
     */
    private static boolean withinTolerance(BigDecimal earlier, BigDecimal later) {
        return RecurringExpenseAmountPort.withinTolerance(earlier, later);
    }

    /**
     * Liefert die Abo-Übersicht des Users (BE-REC-02): alle Einträge in allen drei Status,
     * unterscheidbar am {@code status}-Feld — laufende ({@code DETECTED}), verneinte
     * ({@code DISMISSED}) und ausgelaufene ({@code ENDED}, BE-REC-04). Das «Neu»-Flag kommt aus dem
     * Gelesen-Zustand der zugehörigen Notification, nicht aus einem eigenen Feld — siehe
     * Klassen-Javadoc zur Notification-Erzeugung in {@link #detect(long)}.
     *
     * <p>Ursprünglich nur {@code DETECTED} (US-08 AC3: «wird aus der Abo-Übersicht entfernt»).
     * Seit FE-NOTIF-03 (#333) kommen die {@code DISMISSED}-Einträge mit: die Übersicht zeigt sie in
     * einem eigenen Abschnitt «Kein Abo», damit der Klick auf eine
     * {@code RECURRING_EXPENSE_DETECTED}-Benachrichtigung auch dann ein Ziel hat, wenn der Eintrag
     * inzwischen verneint wurde — die Benachrichtigung bleibt in der Glocke stehen (BE-REC-03
     * markiert sie nur als gelesen). «Entfernt» heisst seither «aus der Liste der Abos», nicht
     * «von der Seite».
     *
     * <p>Die Trennung in Abschnitte macht der Client: «Erkannte Abos», «Beendet» und «Kein Abo»
     * stehen untereinander auf {@code /ausgaben}. Ein ausgelaufener Eintrag verschwindet nicht von
     * der Seite — aus demselben Grund wie ein verneinter (FE-NOTIF-03): die Bündel-Benachrichtigung
     * bleibt in der Glocke stehen, und ihr Klick braucht ein Ziel.
     *
     * <p><strong>Mandantentrennung:</strong>
     * {@link RecurringExpenseRepository#findByUserIdOrderByPayeeKeyAsc} ist auf den übergebenen
     * User eingeschränkt. Die Reihenfolge ist alphabetisch nach Empfänger und damit stabil
     * zwischen zwei Aufrufen.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     */
    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> list(long userId) {
        Set<Long> unread = notificationPort.unreadIds(userId, NOTIFICATION_TYPE);
        return recurringExpenseRepository
                .findByUserIdOrderByPayeeKeyAsc(userId)
                .stream()
                .map(expense -> toResponse(expense, isNew(expense, unread)))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ein einziger Zugriff auf den Status. Dass eine Zeile noch läuft, ist beim letzten Import
     * entschieden und in {@link RecurringExpenseStatus#DETECTED} festgehalten worden
     * (BE-REC-04) — der Lesepfad wiederholt diese Prüfung nicht.
     *
     * <p>Bis dahin lud er dafür die Historie des Aktivitätsfensters nach (Review PR #345), weil
     * eine Zeile nie neu bewertet wurde und ein gekündigtes Abo sonst dauerhaft abgezogen worden
     * wäre. Mit der Neubewertung ist dieses Fenster gegenstandslos: es prüfte bei jedem Aufruf des
     * Dashboards, was einmal pro Import feststeht, und der Monat des Aufrufers spielt für die
     * Frage keine Rolle mehr.
     *
     * <p><strong>Mandantentrennung:</strong>
     * {@link RecurringExpenseRepository#findByUserIdAndStatus} ist auf den übergebenen User
     * eingeschränkt. Es gehen nur Beträge über die Kante — der Safe-to-Spend braucht weder
     * Empfänger noch «Neu»-Flag, und beides hätte im budget-Modul nichts zu suchen.
     */
    @Override
    @Transactional(readOnly = true)
    public List<BigDecimal> detectedAmounts(long userId) {
        return recurringExpenseRepository
                .findByUserIdAndStatus(userId, RecurringExpenseStatus.DETECTED)
                .stream()
                .map(RecurringExpense::getAmount)
                .toList();
    }

    /**
     * «Neu», solange die Bündel-Benachrichtigung des Eintrags ungelesen ist. Ein Eintrag ohne
     * Bündel ({@code notificationId == null}) ist nie neu — der Fall existiert nur für
     * Bestandsdaten, deren Einzel-Notification vor V14 bereits gelöscht war.
     */
    private static boolean isNew(RecurringExpense expense, Set<Long> unreadNotificationIds) {
        return expense.getNotificationId() != null
                && unreadNotificationIds.contains(expense.getNotificationId());
    }

    /**
     * Markiert einen Eintrag des Users als «Kein Abo» und liefert seinen aktuellen Zustand
     * (BE-REC-02). Der zugehörige {@code payee_key} bleibt damit dauerhaft von künftiger Erkennung
     * ausgeschlossen — das leistet bereits {@link #detect(long)} (siehe Klassen-Javadoc), hier
     * wird der Status umgestellt. In {@link #list(long)} bleibt der Eintrag mit
     * {@code status=DISMISSED} enthalten (FE-NOTIF-03).
     *
     * <p><strong>Bündel-Benachrichtigung (BE-REC-03, seit FE-NOTIF-04 auf das Bündel bezogen).</strong>
     * Die Benachrichtigung meldet alle Treffer eines Laufs zusammen. Sie wird als gelesen markiert,
     * sobald <em>keiner</em> dieser Treffer mehr {@code DETECTED} ist — eine Glocke, die weiter für
     * «erkannte Abos» wirbt, die alle verneint wurden, zählt ins Badge, obwohl es nichts Neues
     * gibt. Solange ein anderer Eintrag des Bündels offen ist, bleibt sie ungelesen: der ist noch
     * «Neu», und die Benachrichtigung ist sein Hinweis.
     *
     * <p>Status und Gelesen-Marke stehen in <em>einer</em> Transaktion — zusammen in der
     * Datenbank oder keines von beiden, dieselbe Klammer wie bei Zeilen und Notification in
     * {@link #detect(long)}.
     *
     * <p>{@code isNew} ist in der Antwort immer {@code false}: ein verneinter Eintrag ist nicht
     * neu, unabhängig davon, ob sein Bündel noch offen ist. Die Übersicht zeigt für
     * {@code DISMISSED} ohnehin kein «Neu».
     *
     * <p>Idempotent: ein zweiter Aufruf auf einen bereits {@code DISMISSED}-Eintrag ändert nichts
     * (siehe {@link RecurringExpense#dismiss()}); die Benachrichtigung behält ihren ersten
     * Lesezeitpunkt ({@link NotificationPort#markRead}).
     *
     * @throws RecurringExpenseNotFoundException wenn die ID nicht existiert oder einem anderen
     *     User gehört.
     */
    @Transactional
    public RecurringExpenseResponse dismiss(long userId, long recurringExpenseId) {
        RecurringExpense expense = recurringExpenseRepository
                .findByIdAndUserId(recurringExpenseId, userId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException(userId, recurringExpenseId));
        expense.dismiss();
        if (expense.getNotificationId() != null && bundleIsClosed(userId, expense.getNotificationId())) {
            notificationPort.markRead(userId, expense.getNotificationId());
        }
        return toResponse(expense, false);
    }

    /**
     * {@code true}, wenn kein Eintrag des Bündels mehr {@code DETECTED} ist.
     *
     * <p>Unverändert seit FE-NOTIF-04, und {@link RecurringExpenseStatus#ENDED} ändert daran
     * nichts: ein ausgelaufener Eintrag ist kein offener Hinweis mehr, und die Prüfung läuft
     * ohnehin nur aus {@link #dismiss} heraus — die Neubewertung markiert nie etwas als gelesen.
     */
    private boolean bundleIsClosed(long userId, long notificationId) {
        return recurringExpenseRepository.findByUserIdAndNotificationId(userId, notificationId)
                .stream()
                .noneMatch(e -> e.getStatus() == RecurringExpenseStatus.DETECTED);
    }

    private static RecurringExpenseResponse toResponse(RecurringExpense expense, boolean isNew) {
        return new RecurringExpenseResponse(
                expense.getId(),
                expense.getPayeeKey(),
                expense.getAmount(),
                expense.getStatus(),
                expense.getFirstDetectedMonth().toString(),
                expense.getCreatedAt(),
                isNew);
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

    /** Mehr Empfänger als das nennt der Anzeigetext nicht beim Namen — das Dropdown der Glocke ist schmal. */
    static final int NAMED_PAYEES_IN_MESSAGE = 3;

    /**
     * Anzeigetext der Bündel-Benachrichtigung — Deutsch wie die gesamte Oberfläche. Nennt bis zu
     * {@value #NAMED_PAYEES_IN_MESSAGE} Empfänger beim Namen und fasst den Rest als «und N weitere»
     * zusammen; die Beträge stehen in der Übersicht, nicht hier.
     *
     * @param payeeKeys die Empfänger des Laufs, in der Reihenfolge, in der die Zeilen entstehen.
     */
    static String message(List<String> payeeKeys) {
        int count = payeeKeys.size();
        StringBuilder text = new StringBuilder()
                .append(count)
                .append(count == 1 ? " neues Abo erkannt: " : " neue Abos erkannt: ")
                .append(String.join(", ", payeeKeys.subList(0, Math.min(count, NAMED_PAYEES_IN_MESSAGE))));
        int rest = count - NAMED_PAYEES_IN_MESSAGE;
        if (rest > 0) {
            text.append(" und ").append(rest).append(rest == 1 ? " weiteres" : " weitere");
        }
        return text.toString();
    }

    /**
     * Ergebnis einer qualifizierten Gruppe.
     *
     * @param amount Betrag des jüngsten qualifizierenden Paars, Skala 2.
     * @param firstMonth erster Monat der Reihe in den Daten.
     */
    private record Detection(BigDecimal amount, YearMonth firstMonth) {}
}
