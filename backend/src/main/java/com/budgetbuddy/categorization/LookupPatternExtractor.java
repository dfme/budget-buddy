package com.budgetbuddy.categorization;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitet aus einem Transaktionstext das Pattern ab, unter dem er in {@code user_category_lookup}
 * gelernt wird (BE-CAT-13, ADR-6 Schritt 4).
 *
 * <p><strong>Warum nicht der volle Text.</strong> Seit BE-CAT-11 lernt die Tabelle jeden von
 * Claude eingestuften Text. Für Kartenzahlungen ist der stabil ({@code KAUF/DIENSTLEISTUNG MIGROS
 * M BERN WANKDORF BERN (CH)} sieht jeden Monat gleich aus). Für Überweisungen mit Mitteilung
 * nicht: {@code GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025} trifft {@code … MIETE FEBRUAR
 * 2025} nie — pro Monat ein Claude-Call <em>und</em> eine Zeile, die nie wieder trifft. Im
 * 240er-Fixture erzeugen die 96 Claude-Fälle 43 verschiedene Zeilen, davon 27 für nur drei
 * Gegenparteien (Miete 12, Lohn 12, Steuerrückerstattung 3); mit dem Schnitt sind es 19.
 *
 * <p><strong>Präfix-Schnitt statt Maskierung.</strong>
 * {@link UserCategoryLookupRepository#findMatching} matcht per {@code locate(...)} —
 * Substring-Suche ohne Wildcards (BE-CAT-14): Das Pattern muss ein zusammenhängender Substring
 * jedes künftigen Textes sein. Tokens aus der Mitte herauszuschneiden würde das brechen. Der Text
 * wird deshalb <em>vor dem ersten variablen Token abgeschnitten</em> — das Ergebnis ist ein
 * Präfix des Inputs und damit garantiert ein Substring. Das trägt, weil die Mitteilung in allen
 * drei geparsten Layouts hinter der Gegenpartei steht (PostFinance: {@code TYP | Gegenpartei |
 * Mitteilung}; die {@code Bezahlt für}-Zeilen des 2026er-Layouts ebenso). Alle Regeln sind gegen
 * den Korpus der acht PDF-Fixtures gegengeprüft.
 *
 * <p><strong>Was als variabel gilt</strong> — jede Regel bewusst eng, weil ein zu früher Schnitt
 * teurer ist als keiner (siehe Guard):
 *
 * <ul>
 *   <li>Ein Monatsname <em>direkt gefolgt von einem Jahr</em> ({@code MAI 2025}, {@code MAI 25},
 *       {@code JAN-2025}). Ein alleinstehender Monat bleibt stehen: {@code MAI THAI} ist ein
 *       Restaurant, {@code MARS} ein Händler. Eine Tagesnummer davor ({@code 15. MAI 2025}) zieht
 *       den Schnitt nach vorn.
 *   <li>Ein alleinstehendes Jahr {@code 1900–2099}.
 *   <li>Ein numerisches Datum: {@code 15.05.2025}, {@code 15.05.}, {@code 08-2019},
 *       {@code 12/2025}, {@code 2025-08}, {@code 2025-08-15}.
 *   <li>Ein Token mit mindestens fünf Ziffern am Stück: Referenz ({@code 10000001},
 *       {@code P123456789}), kompakte IBAN. Vier Ziffern bleiben: Filialnummer
 *       ({@code COOP-1234}), PLZ, Telefon in Dreier-/Vierergruppen ({@code 044 913 2323}) sind
 *       stabil je Händler.
 *   <li>Der Beginn einer gespreizten IBAN ({@code CH66 0076 …}).
 * </ul>
 *
 * <p><strong>Guard gegen generische Patterns.</strong> Der Schnitt gilt nur, wenn das Präfix
 * mindestens {@value #MIN_TOKENS} Tokens <em>und</em> mindestens die Hälfte aller Tokens behält.
 * Sonst wird der volle Text gelernt — das ist der Zustand vor BE-CAT-13, also nie schlechter.
 * Der Grund ist die Asymmetrie der Fehler: Ein zu langes Pattern trifft nie wieder und kostet
 * einen Call pro Import. Ein zu kurzes wie {@code TWINT KAUF/DIENSTLEISTUNG VOM} (aus
 * {@code … VOM 12345 SHOP}) zwänge dagegen jede TWINT-Zahlung des Kontos still und dauerhaft in
 * eine Kategorie, und keine spätere Korrektur räumte die Zeile wieder ab. Die Hälfte-Regel ist
 * es, die diesen Fall fängt — drei Tokens allein wären für das TWINT-Layout genau ein Token zu
 * wenig. Bekannter Preis: Eine sehr lange Mitteilung ({@code MIETE JANUAR 2025 NEBENKOSTEN AKONTO
 * WOHNUNG 3 OG LINKS}) fällt unter den Guard und wird wie bisher voll gelernt.
 *
 * <p>Angewendet ausschliesslich in {@link CategoryLearningService#learn} — für beide Lernquellen
 * (BE-CAT-04 und BE-CAT-11), damit der Upsert weiterhin denselben Schlüssel trifft. Die
 * Lookup-Stufe selbst sieht den vollen Text; sie braucht keine Extraktion, weil das Pattern ein
 * Präfix davon ist.
 */
final class LookupPatternExtractor {

    /** Untergrenze für ein geschnittenes Pattern — siehe Guard im Klassen-Javadoc. */
    static final int MIN_TOKENS = 3;

    /**
     * Monatsnamen in den Landessprachen und Englisch, wie sie nach {@code toUpperCase(Locale.ROOT)}
     * im Text stehen. {@code MAERZ} und {@code MÄRZ} beide, weil die Bank-PDFs Umlaute mal
     * ausschreiben, mal transliterieren ({@code ZUERICH}, aber {@code PREIS FÜR}).
     */
    private static final Set<String> MONTHS = Set.of(
            // Deutsch
            "JANUAR", "FEBRUAR", "MÄRZ", "MAERZ", "APRIL", "MAI", "JUNI", "JULI", "AUGUST",
            "SEPTEMBER", "OKTOBER", "NOVEMBER", "DEZEMBER",
            "JAN", "FEB", "MRZ", "MÄR", "MAER", "APR", "JUN", "JUL", "AUG", "SEP", "SEPT", "OKT",
            "NOV", "DEZ",
            // Französisch
            "JANVIER", "FÉVRIER", "FEVRIER", "MARS", "AVRIL", "JUIN", "JUILLET", "AOÛT", "AOUT",
            "SEPTEMBRE", "OCTOBRE", "NOVEMBRE", "DÉCEMBRE", "DECEMBRE",
            // Italienisch
            "GENNAIO", "FEBBRAIO", "MARZO", "APRILE", "MAGGIO", "GIUGNO", "LUGLIO", "AGOSTO",
            "SETTEMBRE", "OTTOBRE", "DICEMBRE",
            // Englisch
            "JANUARY", "FEBRUARY", "MARCH", "MAY", "JUNE", "JULY", "OCTOBER", "DECEMBER",
            "MAR", "OCT", "DEC");

    /** Tokens: alles zwischen Whitespace. Positionen bleiben erhalten, der Schnitt ist ein Index. */
    private static final Pattern TOKEN = Pattern.compile("\\S+");

    /**
     * Satzzeichen am Token-Rand, die für die Erkennung nicht zählen ({@code JANUAR,}). Der Punkt
     * bleibt: {@code 15.05.} und {@code 15.} sind Datumsformen, in denen er Bedeutung trägt.
     */
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[,;:()]+|[,;:()]+$");

    /** Jahr, zwei- oder vierstellig, wie es hinter einem Monatsnamen steht. */
    private static final Pattern YEAR_AFTER_MONTH = Pattern.compile("(?:19|20)\\d{2}|\\d{2}");

    /** Monat und Jahr in einem Token: {@code JAN-2025}, {@code JANUAR/2025}, {@code JAN.2025}. */
    private static final Pattern MONTH_YEAR_JOINED =
            Pattern.compile("(\\p{L}+)[./-]((?:19|20)\\d{2}|\\d{2})");

    /** Tagesnummer, die einem Monat vorangeht: {@code 15.} oder {@code 15}. */
    private static final Pattern DAY_BEFORE_MONTH = Pattern.compile("(?:[1-9]|[12]\\d|3[01])\\.?");

    private static final Pattern STANDALONE_YEAR = Pattern.compile("(?:19|20)\\d{2}");

    /**
     * Numerische Datumsformen, die im Korpus und in Schweizer Zahlungsmitteilungen vorkommen.
     * Bewusst keine Form «zwei Zahlen, ein Trenner» ohne Jahr oder Punkt: {@code 1/2} und
     * {@code 3-4} sind keine Daten.
     */
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "\\d{1,2}\\.\\d{1,2}\\.(?:\\d{4}|\\d{2})?"     // 15.05.2025, 15.05.25, 15.05.
                    + "|\\d{1,2}/\\d{1,2}/(?:\\d{4}|\\d{2})"   // 15/05/2025
                    + "|\\d{1,2}[./-](?:19|20)\\d{2}"          // 08-2019, 12/2025, 08.2025
                    + "|(?:19|20)\\d{2}-\\d{1,2}(?:-\\d{1,2})?"); // 2025-08, 2025-08-15

    /** Referenz, Rechnungsnummer, kompakte IBAN — alles mit fünf Ziffern am Stück. */
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{5}");

    /** Erster Block einer gespreizten IBAN: Länderkennung plus Prüfziffer. */
    private static final Pattern IBAN_START = Pattern.compile("[A-Z]{2}\\d{2}");

    private LookupPatternExtractor() {}

    /**
     * Schneidet den Text vor dem ersten variablen Token ab.
     *
     * @param text normalisierter Transaktionstext (getrimmt, Grossschreibung) — die Normalisierung
     *     ist Sache des Aufrufers, damit gespeichertes Pattern und Matching-Query dieselbe Form
     *     sehen.
     * @return das Präfix bis zum ersten variablen Token, ohne Whitespace am Ende; oder der
     *     unveränderte Text, wenn kein Token variabel ist oder der Guard greift.
     */
    static String extract(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        List<Token> tokens = tokenize(text);
        int cut = firstVariableToken(tokens);
        if (cut < 0) {
            return text;
        }

        // Guard: Präfix muss mindestens MIN_TOKENS und mindestens die Hälfte behalten.
        if (cut < MIN_TOKENS || cut * 2 < tokens.size()) {
            return text;
        }

        return text.substring(0, tokens.get(cut).start()).stripTrailing();
    }

    private static int firstVariableToken(List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i).word();
            String next = i + 1 < tokens.size() ? tokens.get(i + 1).word() : null;

            if (isMonthFollowedByYear(word, next) || isMonthYearJoined(word)) {
                // «15. MAI 2025»: der Tag gehört zum Datum, der Schnitt liegt vor ihm.
                if (i > 0 && DAY_BEFORE_MONTH.matcher(tokens.get(i - 1).word()).matches()) {
                    return i - 1;
                }
                return i;
            }
            if (STANDALONE_YEAR.matcher(word).matches()
                    || NUMERIC_DATE.matcher(word).matches()
                    || LONG_DIGIT_RUN.matcher(word).find()
                    || IBAN_START.matcher(word).matches()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isMonthFollowedByYear(String word, String next) {
        return MONTHS.contains(word) && next != null && YEAR_AFTER_MONTH.matcher(next).matches();
    }

    private static boolean isMonthYearJoined(String word) {
        Matcher m = MONTH_YEAR_JOINED.matcher(word);
        return m.matches() && MONTHS.contains(m.group(1));
    }

    private static List<Token> tokenize(String text) {
        Matcher matcher = TOKEN.matcher(text);
        List<Token> tokens = new ArrayList<>();
        while (matcher.find()) {
            String word = EDGE_PUNCTUATION.matcher(matcher.group()).replaceAll("");
            tokens.add(new Token(matcher.start(), word));
        }
        return tokens;
    }

    /**
     * @param start Position des Tokens im Text — dort wird geschnitten.
     * @param word das Token ohne Satzzeichen am Rand — dagegen laufen die Regeln.
     */
    private record Token(int start, String word) {}
}
