package com.budgetbuddy.categorization;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
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
 * <p><strong>Bekannte Grenzen.</strong> Die zwei Restexpositionen, die BE-CAT-06 offen liess, sind
 * mit BE-CAT-08 (#233) geschlossen: der nachgestellte Vorname über die Echo-Maskierung in
 * {@link #maskPersonNames} und die Händler-Telefonnummer über {@link #PHONE}. Was bleibt, ist
 * <em>ein</em> Rand, und er ist geerbt, nicht neu:
 *
 * <ul>
 *   <li>Die Echo-Maskierung hängt vollständig an {@link #PERSON_NAME}. Dessen dokumentierte Kante
 *       — ein reiner Versalien-Händler mit Komma ({@code COOP, BERN}) — wird dadurch breiter:
 *       nach einem solchen Fehltreffer fielen auch die nachfolgenden {@code COOP} und
 *       {@code BERN} weg. Eine <em>neue</em> Kante entsteht nicht; ohne Fehltreffer der
 *       Grundregel gibt es keinen Echo-Fehltreffer. Im Fixture-Korpus kommt die Form nicht vor.
 *   <li>Ein Vorname, der in einer Zweckzeile steht, <em>ohne</em> dass derselbe Text die Form
 *       {@code NACHNAME, VORNAME} trägt, bleibt unerkannt. Das ist Absicht: ohne diesen Anker
 *       bliebe nur eine Vornamensliste, und die kostete Trefferquote (siehe
 *       {@code PromptSanitizerTest.RealerKorpus}).
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
     * Ziffernfresser wird. {@code COOP-1234} und {@code 044 913 2323} bleiben <em>von dieser
     * Regel</em> unberührt — der eine ist zu kurz, der andere durch Leerzeichen getrennt. Die
     * Telefonnummer nimmt seit BE-CAT-08 {@link #PHONE}; die Filialnummer bleibt stehen, sie ist
     * Teil des Händlertokens.
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
     * Schweizer Telefonnummer im Buchungstext: {@code 044 913 2323}, {@code 079 123 45 67},
     * {@code +41 44 913 23 23} (BE-CAT-08).
     *
     * <p>Sie ist die Nummer des <em>Händlers</em>, nicht des Nutzers, und damit harmloser als
     * alles andere hier. Für die Kategorisierung trägt sie nichts bei — was ohne Verlust
     * wegfallen kann, fällt weg.
     *
     * <p><strong>Der Anker ist die Nummernlänge, nicht die Gruppierung.</strong> National sind
     * es {@code 0} plus neun Ziffern, international {@code +41}/{@code 0041} plus neun. Wie sie
     * gruppiert sind ({@code 044 913 2323} oder {@code 044 913 23 23}), ist gleichgültig. Die
     * führende {@code 0} ist die eigentliche Bedingung: {@code RECHNUNG 2024 2025} trägt zehn
     * Ziffern in zwei Gruppen und kommt trotzdem nicht in die Nähe.
     *
     * <p><strong>Kein Punkt als Trennzeichen</strong>, nur Leerzeichen und {@code /}. Mit dem
     * Punkt ginge {@code 01.02.2026 45} als zehnstellige Nummer durch. Ohne ihn sind
     * Datumsangaben strukturell ausgeschlossen, statt wie bei {@link #AMOUNT} nachträglich per
     * Lookbehind abgefangen werden zu müssen.
     *
     * <p><strong>Der Lookahead schliesst Buchstaben ein</strong> — dieselbe Lehre wie bei
     * {@link #LONG_DIGIT_RUN}. Mit {@code (?!\d)} zerschnitte die Regel
     * {@code 0441234567AB} in {@code <TEL>AB}; mit {@code (?![0-9A-Z])} greift sie dort gar
     * nicht und {@link #OPAQUE_REFERENCE} nimmt den ganzen Token.
     */
    private static final Pattern PHONE =
            Pattern.compile("(?<![0-9A-Z+])(?:\\+41|0041|0)(?:[ /]?\\d){9}(?![0-9A-Z])");

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
     *
     * <p><strong>Was diese Regel findet, gilt für den ganzen Text.</strong> Ein Treffer hier ist
     * kein Verdacht, sondern ein Beweis: die Form {@code NACHNAME, VORNAME} in Versalien kommt
     * nicht zufällig zustande. {@link #maskPersonNames} nutzt das und maskiert dieselben Tokens
     * auch in ihren weiteren Vorkommen — so verschwindet das nachgestellte {@code LEA} aus
     * {@code LASTSCHRIFT MUSTER, LEA SACKGELD LEA}, ohne dass eine Vornamensliste nötig wäre.
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
     *   <li>{@link #OPAQUE_REFERENCE} vor {@link #PHONE} — hier ausnahmsweise <em>umgekehrt</em>
     *       zum vorigen Punkt. Eine kompakt gedruckte Nummer ({@code 0449132323}) erfüllt beide
     *       Regeln und wird von der Referenzregel schon heute genommen. Liefe {@code PHONE}
     *       davor, würde jede zehnstellige Kontonummer zu {@code <TEL>} umbenannt. Nachgestellt
     *       deckt {@code PHONE} genau das ab, was sonst keine Regel fängt: die durch
     *       Leerzeichen getrennte Nummer.
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
        masked = PHONE.matcher(masked).replaceAll("<TEL>");
        masked = maskPersonNames(masked);
        masked = EMAIL.matcher(masked).replaceAll("<EMAIL>");

        // Mehrfachersetzungen hinterlassen doppelte Leerzeichen; der Prompt bleibt dadurch
        // lesbar und die Tests müssen keine Whitespace-Varianten abdecken.
        return masked.replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * Ersetzt jeden {@link #PERSON_NAME}-Treffer durch {@code <NAME>} — und danach dieselben
     * Namenstokens überall dort, wo sie im selben Text noch einmal alleine stehen (BE-CAT-08).
     *
     * <p><strong>Warum das keine Vornamensliste braucht.</strong> Der zweite Durchgang rät
     * nicht, welches Wort ein Vorname sein <em>könnte</em>. Er verwendet nur, was der erste
     * Durchgang strukturell bewiesen hat: in {@code LASTSCHRIFT MUSTER, LEA SACKGELD LEA} ist
     * {@code LEA} nachweislich ein Vorname, weil derselbe Text ihn in der Form
     * {@code NACHNAME, VORNAME} führt. Ohne diesen Anker passiert nichts — die Methode ist
     * selbst-bedingt und kann einen Text ohne Personentreffer gar nicht verändern.
     *
     * <p>Das ist der Grund, warum die Trefferquoten-AC unberührt bleibt, und zwar strukturell
     * und nicht bloss empirisch: keine der vierzehn Zeilen in
     * {@code PromptSanitizerTest.RealerKorpus}, die unverändert durchgehen müssen, trägt einen
     * {@link #PERSON_NAME}-Treffer.
     *
     * <p><strong>Die Wortgrenzen schliessen {@code <} und {@code >} ein</strong>, nicht nur
     * Buchstaben. Sonst träfe ein Nachname {@code NAME} das {@code NAME} in einem bereits
     * gesetzten {@code <NAME>} und die Ersetzung liefe auf sich selbst.
     *
     * <p><strong>Das {@code Pattern.compile} in der Schleife ist kein Versehen</strong>, auch
     * wenn jede andere Regel dieser Klasse ein statisches Feld ist: das Token steht erst zur
     * Laufzeit fest. Die Schleife läuft über zwei bis drei Tokens und nur für Texte, die
     * überhaupt einen Personentreffer haben — gemessen an dem Netzwerk-Call, für den dieser
     * Text vorbereitet wird, ist das nicht messbar.
     */
    private static String maskPersonNames(String text) {
        Matcher matcher = PERSON_NAME.matcher(text);
        Set<String> nameTokens = new LinkedHashSet<>();
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Collections.addAll(nameTokens, matcher.group().split("[,\\s-]+"));
            matcher.appendReplacement(result, "<NAME>");
        }
        matcher.appendTail(result);

        String masked = result.toString();
        for (String token : nameTokens) {
            masked = Pattern.compile("(?<![\\p{L}<])" + Pattern.quote(token) + "(?![\\p{L}>])")
                    .matcher(masked)
                    .replaceAll("<NAME>");
        }
        return masked;
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
