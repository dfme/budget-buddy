package com.budgetbuddy.transaction;

import com.budgetbuddy.money.ChfAmounts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitet aus den Gutschriften eines Users ein wahrscheinliches Monatseinkommen ab (BE-STS-02,
 * US-06) — der Vorschlag, den das Dashboard anbietet, solange kein Einkommen erfasst ist.
 *
 * <p><strong>Verfahren.</strong> Gutschriften der letzten {@value #LOOKBACK_MONTHS} Monate werden
 * über einen normalisierten Absender gruppiert — die erste Detailzeile der Buchung, mit Rückfall
 * auf den Buchungstext (BE-STS-05, siehe {@link #groupingKey}). Eine Gruppe gilt als
 * wiederkehrendes Einkommen, wenn sie in mindestens {@value #MIN_DISTINCT_MONTHS}
 * <em>verschiedenen</em> Kalendermonaten vorkommt und alle ihre Beträge innerhalb von ±5 % des
 * Gruppen-Medians liegen. Vorgeschlagen wird der Median.
 *
 * <p><strong>Warum der Median.</strong> Er ist robust gegen einen Ausreisser innerhalb des Bands und
 * gegen einen 13. Monatslohn, der knapp mit hineinrutscht. Der jüngste Betrag würde einer
 * Lohnerhöhung schneller folgen, kippt aber auf jede einmalige Abweichung; das arithmetische Mittel
 * zieht jeden Ausreisser mit.
 *
 * <p><strong>Warum alle Beträge im Band liegen müssen.</strong> US-06 formuliert «mit gleichem
 * Betrag (±5 %)» als Aussage über die Gutschriftenreihe, nicht über ihre Mehrheit. Ein einzelner
 * Ausreisser kippt deshalb die ganze Gruppe, statt herausgefiltert zu werden — lieber kein Vorschlag
 * als ein falscher, den Lara ungeprüft übernimmt.
 *
 * <p><strong>Bekannte Einschränkung — Eigenübertragungen.</strong> Die Auswahlregel «höchster
 * Median» bevorzugt systematisch den grössten wiederkehrenden Betrag, und das muss nicht der Lohn
 * sein: eine monatliche Umbuchung vom eigenen Sparkonto ist typischerweise rund, gross und
 * regelmässig. Die Absender-Gruppierung <em>entschärft</em> das, löst es aber nicht: Die Umbuchung
 * bekommt seit BE-STS-05 einen eigenen Schlüssel und wird nicht mehr mit dem Lohn in eine Gruppe
 * geworfen — als eigene Gruppe qualifiziert sie sich aber weiterhin, und liegt ihr Median über dem
 * des Lohns, gewinnt sie. Die Folge ist dann ein <em>zu hoher</em> Vorschlag und, falls er
 * übernommen wird, ein zu hoher Safe-to-Spend. Aufzulösen wäre das erst über ein Merkmal, das eine
 * Eigenübertragung als solche ausweist — der Absender allein leistet das nicht, weil er bei einer
 * Umbuchung auf den eigenen Namen lautet und damit genauso konstant ist wie ein Arbeitgeber.
 * Entschärft bleibt es durch die Rückfrage aus US-06 («als Monatseinkommen übernehmen?») — der
 * Vorschlag wird nie still übernommen.
 *
 * <p><strong>Bekannte Einschränkung — Transaktionen vor V06.</strong> Der Absender steht in
 * {@code transactions.buchungsdetails}, gefüllt seit BE-PDF-07 (#159, Migration {@code V06}). Für
 * alles davor Importierte ist die Spalte {@code NULL}, und ein Backfill ist ausgeschlossen: die
 * Detailzeilen stehen nur im Quell-PDF, gespeichert wird davon nur der SHA-256. Diese Buchungen
 * gruppieren deshalb weiterhin über den Buchungstext — bei UBS und Raiffeisen tragfähig, weil der
 * Absender dort in der Buchungszeile steht, bei PostFinance nicht. Aufgelöst wird das nur durch
 * einen Reimport des Auszugs.
 *
 * <p><strong>Zeitzone.</strong> «Heute» wird wie im {@code SafeToSpendService} in
 * {@code Europe/Zurich} bestimmt und nicht in der Zone der {@link Clock}-Bean, die
 * {@code Clock.systemUTC()} liefert — sonst läge der Stichtag zwischen 00:00 und 02:00 Ortszeit noch
 * im Vortag und das Fenster wäre um einen Tag verschoben.
 *
 * <p>Sämtliche Beträge sind {@link BigDecimal} (ADR-9) — nie {@code double}/{@code float}.
 *
 * <p><strong>Mandantentrennung:</strong> gelesen wird ausschliesslich über
 * {@link TransactionRepository#findByUserIdAndIncomeTrueAndBuchungsdatumBetween}; die Einschränkung
 * auf den User steckt im Query-Namen selbst.
 */
@Service
public class IncomeSuggestionService implements IncomeSuggestionPort {

    /** Rappen — Zielskala des vorgeschlagenen Betrags (ADR-9). */
    private static final int RAPPEN_SCALE = ChfAmounts.RAPPEN_SCALE;

    /**
     * Länge des Rückblicks. Ohne Grenze zählte ein Jobwechsel vor Jahren noch mit; zwölf Monate
     * decken einen vollen Jahreszyklus ab, ohne eine veraltete Lohnhöhe hereinzuholen.
     */
    private static final int LOOKBACK_MONTHS = 12;

    /** US-06: «importierte Transaktionen mindestens 2 Monate umfassen». */
    private static final int MIN_DISTINCT_MONTHS = 2;

    /** ±5 % um den Median — die Bandbreite aus US-06. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    private static final BigDecimal TWO = new BigDecimal("2");

    /** Wohnsitz-Zone der Nutzer (CLAUDE.md), analog {@code SafeToSpendService}. */
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    /**
     * Deutsche Monatsnamen als eigenständige Wörter. Ein Lohn kann als «GUTSCHRIFT LOHN SEPTEMBER»
     * gebucht sein — ohne diesen Schritt hätte jeder Monat einen eigenen Schlüssel und die Gruppe
     * käme nie auf zwei Vorkommen. Die Wortgrenzen sind über {@code \p{L}} formuliert und nicht über
     * {@code \b}, damit «Maien» oder «Marzipan» nicht angeschnitten werden.
     *
     * <p>März steht in drei Schreibweisen da: {@code märz}, {@code maerz} und das blosse
     * {@code marz}. Bank-PDFs kommen auch ASCII-transliteriert, und dabei fällt der Umlaut je nach
     * Quelle entweder auf die Ersatzschreibweise oder ersatzlos weg. Bliebe {@code marz} stehen,
     * zerlegte es die Gruppe genau so, wie dieses Muster es verhindern soll.
     */
    private static final Pattern MONTH_NAME = Pattern.compile(
            "(?<!\\p{L})(?:januar|februar|m(?:ä|ae|a)rz|april|mai|juni|juli|august|september"
                    + "|oktober|november|dezember)(?!\\p{L})");

    /**
     * Jedes Token, das eine Ziffer enthält — Kontonummern, Referenzen, Jahreszahlen. Sie wechseln
     * von Buchung zu Buchung und würden die Gruppe sonst zerlegen («GIRO AUS KONTO 25-9034-2»).
     */
    private static final Pattern DIGIT_TOKEN = Pattern.compile("\\S*\\d\\S*");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public IncomeSuggestionService(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Liest die Gutschriften des Fensters und wertet sie nach dem oben beschriebenen Verfahren
     * aus. Der Aufruf ist zustandslos: es wird nichts zwischengespeichert und nichts geschrieben,
     * das Ergebnis hängt allein an den Transaktionen und am heutigen Datum.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> suggestMonthlyIncome(long userId) {
        LocalDate heute = LocalDate.ofInstant(clock.instant(), ZURICH);
        List<Transaction> credits = transactionRepository
                .findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
                        userId, heute.minusMonths(LOOKBACK_MONTHS), heute);

        // TreeMap statt HashMap: bei Gleichstand in Median und Anzahl entscheidet der Schlüssel,
        // und der muss dafür in einer stabilen Reihenfolge vorliegen.
        Map<String, List<Transaction>> byKey = new TreeMap<>();
        for (Transaction tx : credits) {
            byKey.computeIfAbsent(groupingKey(tx), k -> new ArrayList<>()).add(tx);
        }

        // Höchster Median gewinnt: das ist der Lohn, nicht die wiederkehrende Kleinrückerstattung.
        // Danach mehr Vorkommen, zuletzt der Schlüssel — die letzte Stufe entscheidet nie fachlich,
        // sie macht das Ergebnis nur unabhängig von der Zeilenreihenfolge der Query.
        return byKey.entrySet().stream()
                .map(e -> qualify(e.getKey(), e.getValue()))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(Candidate::median).reversed()
                        .thenComparing(Comparator.comparingInt(Candidate::occurrences).reversed())
                        .thenComparing(Candidate::key))
                .map(Candidate::median);
    }

    /**
     * Prüft eine Gruppe gegen die beiden Bedingungen aus US-06 und liefert bei Erfolg ihren Median.
     *
     * @return leer, wenn die Gruppe weniger als {@value #MIN_DISTINCT_MONTHS} verschiedene Monate
     *     abdeckt, ihr Median nicht positiv ist oder einer ihrer Beträge ausserhalb des ±5 %-Bands
     *     liegt.
     */
    private static Optional<Candidate> qualify(String key, List<Transaction> group) {
        Set<YearMonth> months = new HashSet<>();
        for (Transaction tx : group) {
            months.add(YearMonth.from(tx.getBuchungsdatum()));
        }
        if (months.size() < MIN_DISTINCT_MONTHS) {
            return Optional.empty();
        }

        List<BigDecimal> amounts = group.stream().map(Transaction::getBetrag).sorted().toList();
        BigDecimal median = median(amounts);
        // Ein Median von 0.00 (nur Null-Gutschriften) ergäbe ein Toleranzband der Breite 0 und einen
        // Vorschlag, den niemand als Einkommen übernehmen kann.
        if (median.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal maxAbweichung = median.multiply(TOLERANCE);
        for (BigDecimal amount : amounts) {
            if (amount.subtract(median).abs().compareTo(maxAbweichung) > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new Candidate(key, median, group.size()));
    }

    /**
     * Median einer <em>aufsteigend sortierten</em> Liste, auf Rappen gerundet. Bei gerader Anzahl
     * das Mittel der beiden mittleren Werte — {@code HALF_UP} wie in {@code FixedCostService} und
     * {@code SafeToSpendService}.
     */
    private static BigDecimal median(List<BigDecimal> sorted) {
        int mitte = sorted.size() / 2;
        BigDecimal roh = sorted.size() % 2 == 1
                ? sorted.get(mitte)
                : sorted.get(mitte - 1).add(sorted.get(mitte))
                        .divide(TWO, RAPPEN_SCALE, RoundingMode.HALF_UP);
        return roh.setScale(RAPPEN_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Der Gruppenschlüssel einer Buchung: der <strong>Absender</strong>, normalisiert (BE-STS-05).
     *
     * <p>Quelle ist die erste Detailzeile — in den drei PostFinance-Fixtures durchgängig die
     * Gegenpartei jeder Gutschrift ({@code SwissBankStatementParserFixtureTest}). Gibt es keine,
     * dient der Buchungstext als Rückfall; das ist der Zustand für alles vor {@code V06}
     * Importierte und für Auszüge, deren Buchungen gar keine Detailzeilen tragen — in den
     * Fixtures UBS und Raiffeisen, die den Absender stattdessen in die Buchungszeile schreiben.
     *
     * <p><strong>Warum nur die erste Zeile.</strong> Die zweite und dritte tragen den
     * Verwendungszweck, und der wechselt von Monat zu Monat: {@code Post_Kontoauszug_2026_Juli}
     * bricht ihn sogar mitten im Wort auf zwei Zeilen um ({@code LOHN JULI 2026 SOWIE SPE} /
     * {@code SENVERGUETUNG}). Alle Zeilen zu verketten zerlegte damit genau die Gruppe, die
     * zusammengehört — die Normalisierung fängt Monatsnamen und Jahreszahlen ab, einen anderen
     * Zeilenumbruch nicht.
     *
     * <p><strong>Warum Absender- und Buchungstext-Schlüssel denselben Raum teilen.</strong> Eine
     * Kollision setzte einen Absender voraus, der normalisiert exakt wie eine Zahlungsart heisst.
     * Träte sie ein, wäre das Ergebnis das bisherige Verhalten — kein neuer Fehlermodus, der ein
     * Präfix je Herkunft aufwöge. Der Schlüssel verlässt diese Klasse ohnehin nie: über
     * {@link IncomeSuggestionPort} geht nur der Betrag.
     */
    private static String groupingKey(Transaction tx) {
        String absender = senderLine(tx.getBuchungsdetails());
        return normalise(absender != null ? absender : tx.getBuchungstext());
    }

    /**
     * Die erste Detailzeile — der Absender —, oder {@code null}, wenn es keine verwertbare gibt.
     *
     * <p>{@code null} deckt beide Fälle ab, in denen der Absender fehlt: die Spalte ist {@code NULL}
     * (vor {@code V06} importiert oder Buchung ohne Detailzeilen, siehe
     * {@link Transaction#getBuchungsdetails()}), oder die erste Zeile ist leer. Der zweite Fall
     * kann aus dem Parser nicht kommen — {@code ParsedTransaction#detailsAsText()} verbindet nur
     * nichtleere Zeilen —, aber ein leerer Schlüssel würde hier sämtliche betroffenen Buchungen in
     * einen Topf werfen, und das ist der teuerste denkbare Fehler dieser Methode.
     */
    private static String senderLine(String buchungsdetails) {
        if (buchungsdetails == null) {
            return null;
        }
        int umbruch = buchungsdetails.indexOf('\n');
        String erste = (umbruch < 0 ? buchungsdetails : buchungsdetails.substring(0, umbruch)).trim();
        return erste.isEmpty() ? null : erste;
    }

    /**
     * Normalisiert einen Text zum Gruppenschlüssel: kleingeschrieben, ohne Monatsnamen und ohne
     * ziffernhaltige Tokens.
     *
     * <p>Bleibt davon nichts übrig — etwa bei einem Text, der nur aus einer Referenznummer
     * besteht —, dient der kleingeschriebene Originaltext als Schlüssel. Ein leerer Schlüssel würde
     * sonst alle solchen Buchungen in einen Topf werfen, obwohl sie nichts miteinander zu tun haben;
     * mit dem Fallback gruppieren sich nur wirklich identische Texte.
     */
    private static String normalise(String text) {
        String klein = text.toLowerCase(Locale.ROOT);
        String ohneMonate = MONTH_NAME.matcher(klein).replaceAll(" ");
        String ohneZiffern = DIGIT_TOKEN.matcher(ohneMonate).replaceAll(" ");
        String normalisiert = WHITESPACE.matcher(ohneZiffern).replaceAll(" ").trim();
        return normalisiert.isEmpty() ? WHITESPACE.matcher(klein).replaceAll(" ").trim() : normalisiert;
    }

    /**
     * Eine Gruppe, die beide Bedingungen erfüllt.
     *
     * @param key normalisierter Absender, siehe {@link #groupingKey} — letzte Tiebreak-Stufe, damit
     *     die Auswahl nicht an der Zeilenreihenfolge der Query hängt.
     * @param median vorgeschlagener Betrag, Skala 2.
     * @param occurrences Anzahl Gutschriften in der Gruppe — Tiebreak, wenn zwei Gruppen denselben
     *     Median haben.
     */
    private record Candidate(String key, BigDecimal median, int occurrences) {}
}
