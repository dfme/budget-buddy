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
 * <p><strong>Was geschrieben wird.</strong> Pro neu erkanntem Empfänger genau eine Zeile mit
 * {@link RecurringExpenseStatus#DETECTED}: der Betrag des <em>jüngsten</em> qualifizierenden
 * Monatspaars und der erste Monat der Reihe in den Daten. Dazu <em>pro Lauf</em> eine
 * Benachrichtigung vom Typ {@value #NOTIFICATION_TYPE}, die alle Treffer des Laufs bündelt
 * («3 neue Abos erkannt: …») — der «Neu»-Hinweis aus US-08. Jede Zeile trägt die ID dieser
 * Benachrichtigung ({@code notification_id}, V14); der Verweis läuft seit FE-NOTIF-04 (#336) von
 * der Zeile zur Benachrichtigung und nicht mehr umgekehrt, weil eine Benachrichtigung jetzt
 * mehrere Zeilen meldet. Ein Import, der 27 Abos auf einmal erkennt, erzeugte vorher 27
 * Benachrichtigungen, die nur einzeln als gelesen zu markieren waren.
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
 * <p><strong>Safe-to-Spend (FE-FC-05).</strong> Erkannte Abos mindern seit FE-FC-05 den
 * Safe-to-Spend wie Fixkosten-Positionen. Das budget-Modul liest dafür über
 * {@link RecurringExpenseAmountPort#detectedAmounts} nur die Beträge der {@code DETECTED}-Zeilen,
 * die im Aktivitätsfenster noch abgebucht wurden — eine Zeile verfällt nie von selbst, und ein
 * gekündigtes Abo darf nicht dauerhaft abgezogen werden. Die Zuordnung zur Abbuchung des Monats
 * und die Regel gegen Doppelzählung liegen drüben ({@code FixedCostDebitMatcher},
 * ADR-13-Nachtrag); die Toleranz dafür ist dieselbe wie hier bei der Erkennung.
 *
 * <p><strong>Mandantentrennung:</strong> alle Lesezugriffe — beide Fassungen von
 * {@link ExpenseHistoryPort#expenseHistory}, {@link RecurringExpenseRepository#findByUserId} und
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
     * wenn es mindestens einen Treffer gibt: ein Lauf ohne Ergebnis meldet nichts.
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

        // Erst sammeln, dann schreiben: die Benachrichtigung nennt die Zahl und die Namen aller
        // Treffer, und die Zeilen tragen ihre ID — beides steht erst nach dem Durchgang fest.
        // TreeMap auch hier, damit die Reihenfolge in Text und Tabelle die der Empfänger ist.
        Map<String, Detection> detections = new TreeMap<>();
        for (Map.Entry<String, List<ExpenseEntry>> group : byPayee.entrySet()) {
            qualify(group.getValue()).ifPresent(d -> detections.put(group.getKey(), d));
        }

        if (!detections.isEmpty()) {
            long notificationId = notificationPort.create(userId, NOTIFICATION_TYPE, null,
                    message(List.copyOf(detections.keySet())));
            Instant now = clock.instant();
            for (Map.Entry<String, Detection> detection : detections.entrySet()) {
                recurringExpenseRepository.save(new RecurringExpense(
                        userId, detection.getKey(), detection.getValue().amount(),
                        detection.getValue().firstMonth(), now, notificationId));
            }
        }

        log.info("Abo-Erkennung: {} neue wiederkehrende Ausgabe(n) aus {} Belastung(en), "
                        + "{} Empfänger bereits bekannt oder ausgeschlossen.",
                detections.size(), history.size(), known.size());
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
     * Der frühere Betrag ist die Basis, weil er der bekannte Vergleichswert ist — der Betrag, den
     * der Nutzer bisher gezahlt hat. Die Regel selbst steht am Port, weil der Safe-to-Spend
     * dieselbe braucht (siehe Klassen-Javadoc).
     */
    private static boolean withinTolerance(BigDecimal earlier, BigDecimal later) {
        return RecurringExpenseAmountPort.withinTolerance(earlier, later);
    }

    /**
     * Liefert die Abo-Übersicht des Users (BE-REC-02): alle Einträge, {@code DETECTED} wie
     * {@code DISMISSED}, unterscheidbar am {@code status}-Feld. Das «Neu»-Flag kommt aus dem
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
     * <p>Aktiv heisst: der Empfänger der Zeile hat im Fenster {@code [month −
     * (ACTIVE_WINDOW_MONTHS − 1), month]} mindestens eine Belastung — unabhängig vom Betrag. Der
     * Betrag wird hier nicht verglichen, weil das die Aufgabe des Aufrufers ist: er entscheidet
     * mit {@link RecurringExpenseAmountPort#withinTolerance}, welche Belastung die Abbuchung des
     * Abos ist. Verglichen wird der Schlüssel, so wie {@link #detect(long)} ihn speichert
     * (Grossschreibung, V11).
     *
     * <p>Erst die Zeilen, dann die Historie — ohne {@code DETECTED}-Zeile wird die Historie gar
     * nicht geladen: der häufigste Fall auf dem Dashboard ist ein User ohne erkannte Abos.
     *
     * <p><strong>Mandantentrennung:</strong>
     * {@link RecurringExpenseRepository#findByUserIdAndStatus} und die gefensterte
     * {@link ExpenseHistoryPort#expenseHistory(long, YearMonth, YearMonth)} sind auf den
     * übergebenen User eingeschränkt. Es gehen nur Beträge über die Kante — der Safe-to-Spend
     * braucht weder Empfänger noch «Neu»-Flag, und beides hätte im budget-Modul nichts zu suchen.
     */
    @Override
    @Transactional(readOnly = true)
    public List<BigDecimal> detectedAmounts(long userId, YearMonth month) {
        List<RecurringExpense> detected = recurringExpenseRepository
                .findByUserIdAndStatus(userId, RecurringExpenseStatus.DETECTED);
        if (detected.isEmpty()) {
            return List.of();
        }

        YearMonth from = month.minusMonths(RecurringExpenseAmountPort.ACTIVE_WINDOW_MONTHS - 1);
        Set<String> activePayees = new HashSet<>();
        for (ExpenseEntry entry : expenseHistoryPort.expenseHistory(userId, from, month)) {
            activePayees.add(entry.payeeKey().toUpperCase(Locale.ROOT));
        }

        return detected.stream()
                .filter(expense -> activePayees.contains(expense.getPayeeKey().toUpperCase(Locale.ROOT)))
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

    /** {@code true}, wenn kein Eintrag des Bündels mehr {@code DETECTED} ist. */
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
