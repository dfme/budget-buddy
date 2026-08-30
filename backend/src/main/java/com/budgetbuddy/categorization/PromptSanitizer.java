package com.budgetbuddy.categorization;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maskiert Personen- und Zahlungsdaten im Transaktionstext, bevor er als Prompt zur Claude-API
 * hinausgeht (BE-CAT-06, Risiko #2, nDSG).
 *
 * <p>Angewendet wird ausschliesslich in {@link ClaudeCategorizationService#buildUserPrompt} — der
 * einzigen Stelle, an der Text in einen API-Request serialisiert wird. Die Lookup-Stufe
 * ({@link HybridCategorizationService}) arbeitet weiter auf dem unmaskierten Text: sie ist lokal,
 * ihr Input verlässt das System nicht, und eine Maskierung senkte dort nur die Trefferquote.
 *
 * <p><strong>Zweite Verteidigungslinie, nicht die erste.</strong> IBAN, Kartennummer, Anschrift
 * und Referenzzeilen erreichen den Prompt schon heute nicht mehr — {@code DETAIL_NOISE} verwirft
 * sie beim Parsen ({@code SwissBankStatementParser}, #196). Diese Klasse verlässt sich nicht
 * darauf: der Parser kennt drei Bank-Layouts, und ein viertes bringt seine eigenen Rauschzeilen
 * mit. Für den <em>Gegenpartei-Namen</em> und das <em>Referenz-Token am Händlernamen</em> ist sie
 * dagegen die erste und einzige Linie — beides überlebt den Parser.
 *
 * <p><strong>Was die Kategorisierung braucht, bleibt stehen.</strong> Für die Zuordnung genügt der
 * Händler- oder Zwecktoken; Betrag, Konto und Gegenpartei tragen nichts bei. Jede Regel unten ist
 * deshalb gegen den Korpus aller sechs PDF-Fixtures gegengeprüft — eine Regel, die einen
 * Händlernamen verstümmelt, kostet Trefferquote und ist damit teurer als der Schutz wert wäre.
 *
 * <p><strong>Bekannte Grenzen.</strong> Zwei Dinge löst ein regelbasierter Sanitizer nicht:
 *
 * <ul>
 *   <li>Ein Vorname in einer frei getippten Zweckzeile. Aus {@code LASTSCHRIFT MUSTER, LEA
 *       SACKGELD LEA} wird {@code LASTSCHRIFT <NAME> SACKGELD LEA} — das {@code LEA} dahinter ist
 *       von einem Händlernamen nicht zu unterscheiden. Offen als BE-CAT-08 (#233).
 *   <li>Die Telefonnummer eines Händlers ({@code DIGITEC GALAXUS AG 044 913 2323}). Sie ist kein
 *       Datum des Nutzers; ebenfalls in BE-CAT-08 (#233) vermerkt.
 * </ul>
 */
final class PromptSanitizer {

    /**
     * IBAN der Gegenpartei — kompakt gedruckt (PostFinance) oder in Vierergruppen (UBS).
     *
     * <p>Die führenden {@code [A-Z]{2}\d{2}} sind das eigentliche Sieb: sie verlangen zwei
     * Buchstaben <em>und direkt danach</em> zwei Ziffern. Ein Händlername wie
     * {@code MUSTER IMMOBILIEN AG} kommt so nie in die Nähe der Regel.
     */
    private static final Pattern IBAN = Pattern.compile(
            "(?<![A-Z0-9])[A-Z]{2}\\d{2}(?:[A-Z0-9]{11,30}|(?: [A-Z0-9]{1,4}){2,8})(?![A-Z0-9])");

    /**
     * Maskierte Karten- oder Kontonummer als ein Token: {@code XXXX4417}, {@code 5500XXXX5446}.
     *
     * <p>Die gespreizte Schreibweise mit Leerzeichen deckt {@link #CARD_GROUPS} ab.
     */
    private static final Pattern CARD_INLINE =
            Pattern.compile("(?<![A-Z0-9])[0-9]*X{4,}[0-9]*(?![A-Z0-9])");

    /**
     * Karten- oder Kontonummer in Vierergruppen: {@code 5500 20XX XXXX 5446} (Viseca).
     *
     * <p>Ersetzt wird <strong>nur</strong>, wenn die Fundstelle ein {@code XX} trägt — siehe
     * {@link #maskCardGroups}. Ohne diese Bedingung fiele auch eine harmlose Gruppe aus vier
     * Zahlen heraus; mit ihr bleibt die Regel auf das beschränkt, was erkennbar eine maskierte
     * Nummer ist. Als Regex allein wäre die Bedingung nur mit einem Lookahead über die ganze
     * Fundstelle auszudrücken und damit unlesbar.
     */
    private static final Pattern CARD_GROUPS =
            Pattern.compile("(?<![0-9X])(?:[0-9X]{4} ){1,4}[0-9X]{4}(?![0-9X])");

    /**
     * Unmaskierter Ziffernlauf in Karten- oder Kontonummernlänge.
     *
     * <p>Zwölf ist die Untergrenze, nicht sechzehn: Kontonummern sind kürzer als Kartennummern.
     * Nach oben begrenzt auf 19 (Maestro), damit die Regel nicht zu einem allgemeinen
     * Ziffernfresser wird. {@code COOP-1234} und {@code 044 913 2323} bleiben unberührt — der
     * eine ist zu kurz, der andere durch Leerzeichen getrennt.
     *
     * <p><strong>Die Wortgrenzen schliessen Buchstaben ein, nicht nur Ziffern.</strong> Mit dem
     * naheliegenden {@code (?<!\d)…(?!\d)} zerschnitt die Regel eine Referenz wie
     * {@code 250704111222333444AB} in {@code <KARTE>AB} — sie sah die achtzehn führenden Ziffern
     * und ignorierte, dass der Token weiterläuft. Mit {@code (?![0-9A-Z])} greift sie dort gar
     * nicht mehr, und {@link #OPAQUE_REFERENCE} nimmt den ganzen Token.
     */
    private static final Pattern LONG_DIGIT_RUN =
            Pattern.compile("(?<![0-9A-Z])\\d{12,19}(?![0-9A-Z])");

    /**
     * Geldbetrag im Schweizer Format: {@code 42.50}, {@code 1'234.56}.
     *
     * <p>Beträge stehen heute nicht in {@code ParsedTransaction.fullText()} — der Betrag ist ein
     * eigenes Record-Feld. Die Regel ist deshalb eine Regressionsbremse für den Tag, an dem ein
     * neues Layout den Betrag in die Detailzeilen druckt, und zugleich der Nachweis für die AC.
     *
     * <p><strong>Der Lookbehind {@code (?<!\d\.)} hält Datumsangaben heraus.</strong> In
     * {@code 03.07.26} sähe {@code \d+\.\d{2}} sonst am Ende ein {@code 07.26} und machte aus
     * einem Datum einen Betrag. Der Lookbehind prüft die zwei Zeichen davor: steht dort
     * {@code Ziffer + Punkt}, ist die Fundstelle das Mittelstück eines Datums und wird verworfen.
     */
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.'])(?<!\\d\\.)(?:\\d{1,3}(?:'\\d{3})+|\\d+)\\.\\d{2}(?![\\d.])");

    /**
     * Undurchsichtige Referenz: mindestens zehn Zeichen aus {@code [0-9A-Z]} mit mindestens einer
     * Ziffer — {@code 250704111222333444AB}, {@code P123456789}.
     *
     * <p>Die Ziffernbedingung ist das, was Händlernamen heraushält: {@code CONSULTING},
     * {@code IMMOBILIEN} und {@code RUECKZAHLUNG} sind lang genug, tragen aber keine Ziffer.
     *
     * <p>Bewusst case-<strong>sensitiv</strong>, aus demselben Grund wie
     * {@code NOISE_OPAQUE_REFERENCE} im Parser: unter {@code (?i)} träfe {@code [0-9A-Z]} auch
     * Kleinbuchstaben, und dann verschwände jede einwortige Zweckzeile mit Ziffer ab zehn Zeichen
     * ({@code Rechnung2026}) aus dem Prompt. Die Versalien sind hier das eigentliche Signal.
     */
    private static final Pattern OPAQUE_REFERENCE =
            Pattern.compile("(?<![0-9A-Z])(?=[0-9A-Z]*\\d)[0-9A-Z]{10,}(?![0-9A-Z])");

    /**
     * Gegenpartei als natürliche Person: {@code MUSTER, LEA}, {@code MUSTER, ANNA}.
     *
     * <p><strong>Warum beide Teile Versalien tragen müssen.</strong> Die naheliegende Fassung
     * «Wort, Wort» wäre fatal: die Viseca-Abrechnung besteht aus Zeilen der Form
     * {@code Händler, Ort LAND Kategorie} — {@code Coop-1122, Bern CH Lebensmittel},
     * {@code Zalando SE, Berlin DE Bekleidung}, {@code SBB CFF FFS, Bern CH Öffentlicher
     * Verkehr}. Eine zu breite Regel zerstörte dort genau den Händlertoken, den die
     * Trefferquoten-AC schützt. Der Trennschnitt ist die Schreibweise: nach dem Komma steht bei
     * Viseca durchgängig ein gemischt geschriebener Ortsname, der an {@code \p{Lu}{2,}}
     * scheitert. Am Korpus aller sechs Fixtures gegengeprüft — von vierzehn Zeilen mit Komma
     * treffen genau die zwei Personennamen zu.
     *
     * <p><strong>Heuristik mit bekanntem Rand</strong> (wie {@code NOISE_ADDRESS} im Parser): ein
     * reiner Versalien-Händler mit Komma ({@code COOP, BERN}) fiele mit heraus. Im gesamten
     * Fixture-Korpus kommt die Form nicht vor. Der Preis wäre eine Transaktion in
     * {@code Sonstiges} — nicht der Preis, den die andere Richtung kostet.
     *
     * <p><strong>Der Doppelname wird nur mit Bindestrich verbunden, nicht mit Leerzeichen.</strong>
     * Die erste Fassung liess beides zu und frass damit das Wort <em>vor</em> dem Namen mit: aus
     * {@code LASTSCHRIFT MUSTER, LEA} wurde {@code <NAME>}, weil {@code LASTSCHRIFT MUSTER} als
     * zweiteiliger Nachname durchging. Ein durch Leerzeichen getrennter Nachname ist von einem
     * vorangehenden Buchungstyp nicht zu unterscheiden — {@code MUSTER-MEIER, LEA} dagegen schon.
     */
    private static final Pattern PERSON_NAME = Pattern.compile(
            "(?<!\\p{L})\\p{Lu}{2,}(?:-\\p{Lu}{2,})?, ?\\p{Lu}{2,}(?!\\p{L})");

    /** E-Mail-Adresse. Im Fixture-Korpus ohne Treffer — Defense-in-depth. */
    private static final Pattern EMAIL =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]*[\\w]");

    private PromptSanitizer() {}

    /**
     * Maskiert alles, was für die Kategorisierung entbehrlich und als Personen- oder Zahlungsdatum
     * schützenswert ist.
     *
     * <p><strong>Die Reihenfolge ist Teil der Regeln</strong>, an zwei Stellen zwingend:
     *
     * <ul>
     *   <li>{@link #CARD_GROUPS} vor {@link #CARD_INLINE}. Umgekehrt riss {@code CARD_INLINE} das
     *       {@code XXXX} aus {@code 5500 20XX XXXX 5446} heraus, und die Gruppenregel fand
     *       danach nichts Zusammenhängendes mehr — übrig blieb
     *       {@code <KARTE> <KARTE> 5446} statt eines Platzhalters.
     *   <li>{@link #IBAN} vor {@link #OPAQUE_REFERENCE}. Eine kompakt gedruckte IBAN erfüllt auch
     *       die Referenzregel; das Ergebnis wäre richtig maskiert, aber falsch benannt.
     * </ul>
     *
     * <p>Die Platzhalter sind so gewählt, dass keine spätere Regel auf einer früheren Ersetzung
     * greift: kurz, ohne Ziffer und ohne Komma.
     *
     * @param text Transaktionstext; {@code null} und Leerstring werden unverändert
     *     zurückgegeben — die Behandlung leerer Eingaben liegt beim Aufrufer
     *     ({@code ClaudeCategorizationService.categorizeAll} nimmt sie gar nicht erst ins Bündel).
     * @return maskierter Text. Nie leer: jede Regel hinterlässt ihren Platzhalter, aus
     *     {@code CH7709000000850055555} wird {@code <IBAN>} und nicht der Leerstring.
     */
    static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String masked = IBAN.matcher(text).replaceAll("<IBAN>");
        masked = maskCardGroups(masked);
        masked = CARD_INLINE.matcher(masked).replaceAll("<KARTE>");
        masked = LONG_DIGIT_RUN.matcher(masked).replaceAll("<KARTE>");
        masked = AMOUNT.matcher(masked).replaceAll("<BETRAG>");
        masked = OPAQUE_REFERENCE.matcher(masked).replaceAll("<REF>");
        masked = PERSON_NAME.matcher(masked).replaceAll("<NAME>");
        masked = EMAIL.matcher(masked).replaceAll("<EMAIL>");

        // Mehrfachersetzungen hinterlassen doppelte Leerzeichen; der Prompt bleibt dadurch
        // lesbar und die Tests müssen keine Whitespace-Varianten abdecken.
        return masked.replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * Ersetzt Vierergruppen nur dann, wenn die Fundstelle maskierte Stellen trägt.
     *
     * <p>Die Bedingung liesse sich in {@link #CARD_GROUPS} als Lookahead formulieren, wäre dort
     * aber die dritte verschachtelte Gruppe in einem Ausdruck, den danach niemand mehr liest.
     * Hier steht sie als eine {@code if}-Zeile.
     */
    private static String maskCardGroups(String text) {
        Matcher matcher = CARD_GROUPS.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group().contains("XX") ? "<KARTE>" : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
