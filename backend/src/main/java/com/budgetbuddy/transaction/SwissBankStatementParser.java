package com.budgetbuddy.transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extrahiert Transaktionen aus Text-Layer-PDFs Schweizer Banken — BE-PDF-01, US-04.
 *
 * <p>Unterstützt vier Layouts, die sich im extrahierten Text deutlich unterscheiden. Das Format
 * wird anhand von Kopfzeilen-Schlüsselwörtern erkannt und an einen spezialisierten Parser
 * delegiert:
 *
 * <ul>
 *   <li><b>Viseca / Raiffeisen Kreditkarte</b> — Zeilen {@code Buchungsdatum Valuta Text [Währung
 *       Fremdbetrag] BetragCHF}, zweistelliges Jahr {@code dd.MM.yy}, kein laufender Saldo. Eine
 *       Gutschrift (Zahlung/Rückerstattung) ist an einem nachgestellten „-" erkennbar. Der
 *       Buchungstext ist der Teil vor dem ersten Betrag; ein nachgestellter Währungscode
 *       (z. B. {@code EUR}) wird entfernt.
 *   <li><b>PostFinance</b> — Zeilen {@code [Datum] Text Betrag Valuta [Saldo]}, {@code dd.MM.yy},
 *       Leerzeichen als Tausendertrennzeichen ({@code 1 000.00}). Der Saldo steht nur am Tagesende;
 *       die Richtung wird über das Saldo-Delta eines Buchungsblocks rekonstruiert.
 *   <li><b>UBS</b> — Zeilen {@code Buchungsdatum Text Betrag Valuta Saldo}, {@code dd.MM.yyyy},
 *       Apostroph-Trennzeichen, <em>absteigend</em> sortiert (neueste zuerst). Richtung über
 *       Saldo-Delta gegenüber der älteren (nachfolgenden) Zeile bzw. dem Anfangssaldo.
 *   <li><b>Generisch (Raiffeisen-Kontoauszug)</b> — {@code Buchungsdatum [Valuta] Text Betrag
 *       Saldo}, {@code dd.MM.yyyy}, {@code Saldovortrag}-Zeile als Startsaldo. Fallback, wenn keine
 *       der obigen Signaturen greift.
 * </ul>
 *
 * <p>Eingerückte Fortsetzungszeilen unter einer Buchung landen in {@link
 * ParsedTransaction#details()} — bei Überweisungen steht dort der Empfänger, den die
 * Kategorisierung braucht (siehe {@link #appendDetail}).
 *
 * <p>Alle Beträge werden als {@link BigDecimal} verarbeitet (ADR-9). Diese Klasse ist zustandslos
 * und threadsicher.
 */
@Component
public class SwissBankStatementParser {

  private static final Logger log = LoggerFactory.getLogger(SwissBankStatementParser.class);

  private static final DateTimeFormatter DATE_4 = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  /** Zweistelliges Jahr {@code yy} → {@code 20yy} (Basisjahr 2000). */
  private static final DateTimeFormatter DATE_2 =
      new DateTimeFormatterBuilder()
          .appendPattern("dd.MM.")
          .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
          .toFormatter();

  /**
   * Tausendertrennzeichen in CHF-Beträgen: Apostroph, Leerzeichen, geschütztes Leerzeichen
   * (U+00A0) und schmales geschütztes Leerzeichen (U+202F, übliche Schweizer Zahlformatierung).
   * Einzige Quelle für {@link #AMOUNT} und {@code parseAmount} — sichtbar für Tests.
   */
  static final String THOUSANDS_SEPARATORS = "'\u0020\u00A0\u202F";

  /** CHF-Betrag mit Tausendertrennzeichen, z. B. {@code 1'234.56} — sichtbar für Tests. */
  static final String AMOUNT = "-?\\d{1,3}(?:[" + THOUSANDS_SEPARATORS + "]\\d{3})*\\.\\d{2}";

  private static final Pattern SEPARATOR_CHARS = Pattern.compile("[" + THOUSANDS_SEPARATORS + "]");

  private static final String DATE4_RE = "\\d{2}\\.\\d{2}\\.\\d{4}";
  private static final String DATE2_RE = "\\d{2}\\.\\d{2}\\.\\d{2}";

  /**
   * Datum mit vier- <em>oder</em> zweistelligem Jahr — nur fürs generische Layout.
   *
   * <p>Die anderen drei Layouts drucken je genau eine Form, und dort ist die Einschränkung ein
   * Teil der Formaterkennung. Das generische ist der Fallback für alles Übrige und darf sich
   * nicht auf eine Form festlegen: Der echte Raiffeisen-Kontoauszug druckt {@code 01.06.26}, die
   * bisherige Fixture {@code 01.06.2026}.
   *
   * <p>Die vierstellige Alternative steht zuerst, weil sie sich so liest, wie die Engine
   * ohnehin auflöst — <em>nicht</em>, weil die andere Reihenfolge falsch parsen würde. Beide
   * Reihenfolgen liefern gegen alle vier vorkommenden Zeilenformen identische Gruppen: Bei
   * {@code 01.06.2026} scheitert der Teilmatch {@code 01.06.20} sofort am {@code \s+} gegen die
   * {@code 2}, die Engine backtrackt und nimmt die vierstellige Form. Wer diese Regex ändert,
   * sollte die Reihenfolge also als Lesbarkeit behandeln und nicht als Schutzmechanismus.
   */
  private static final String DATE_ANY_RE = "\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2})";

  /** Vierstellige Jahresform, exakt — wählt in {@link #parseGenericDate} den Formatter. */
  private static final Pattern DATE4_ONLY = Pattern.compile(DATE4_RE);

  /** Betrags-Token an beliebiger Stelle einer Zeile. */
  private static final Pattern AMOUNT_TOKEN = Pattern.compile(AMOUNT);

  /** Führendes Buchungsdatum, zwei- oder vierstelliges Jahr. */
  private static final Pattern STARTS_WITH_DATE =
      Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{2}(?:\\d{2})?\\b");

  // --- Generisch (Raiffeisen-Kontoauszug) -------------------------------------------------------
  private static final Pattern GENERIC_ROW =
      Pattern.compile(
          "^(" + DATE_ANY_RE + ")\\s+(?:" + DATE_ANY_RE + "\\s+)?(.+?)\\s+(" + AMOUNT
              + ")\\s+(" + AMOUNT + ")$");
  private static final Pattern SALDOVORTRAG =
      Pattern.compile("(?i)saldovortrag.*?(" + AMOUNT + ")\\s*$");

  // --- Viseca / Kreditkarte ---------------------------------------------------------------------
  private static final Pattern VISECA_ROW =
      Pattern.compile("^(" + DATE2_RE + ")\\s+(" + DATE2_RE + ")\\s+(.*)$");

  /**
   * Nachgestellter Fremdwährungscode im Buchungstext (Whitelist gängiger Codes statt beliebiger
   * dreistelliger Grossbuchstaben-Tokens, damit z. B. „WALMART USA" nicht verstümmelt wird).
   */
  private static final Pattern TRAILING_CURRENCY =
      Pattern.compile("\\s+(?:CHF|EUR|USD|GBP|SEK|NOK|DKK|PLN|CZK|HUF|JPY|CAD|AUD)$");

  // --- PostFinance ------------------------------------------------------------------------------
  private static final Pattern POST_KONTOSTAND =
      Pattern.compile("^(?:" + DATE2_RE + "\\s+)?Kontostand\\s+(" + AMOUNT + ")$");
  private static final Pattern POST_ROW =
      Pattern.compile(
          "^(?:(" + DATE2_RE + ")\\s+)?(.+?)\\s+(" + AMOUNT + ")\\s+(" + DATE2_RE + ")(?:\\s+("
              + AMOUNT + "))?$");

  // --- UBS --------------------------------------------------------------------------------------
  private static final Pattern UBS_ROW =
      Pattern.compile(
          "^(" + DATE4_RE + ")\\s+(.+?)\\s+(" + AMOUNT + ")\\s+(" + DATE4_RE + ")\\s+(" + AMOUNT
              + ")$");
  private static final Pattern UBS_ANFANGSSALDO =
      Pattern.compile("^Anfangssaldo\\s+(" + AMOUNT + ")$");

  // --- Fortsetzungszeilen -----------------------------------------------------------------------

  /**
   * Abschlusszeile des Buchungsteils einer Seite. Danach folgen nur noch Summen, Rechtshinweise und
   * Grussformeln — nichts davon gehört an eine Buchung.
   */
  private static final Pattern TOTALS_LINE =
      Pattern.compile(
          "(?i)^(?:total|umsatztotal|kontostand|anfangssaldo|schlusssaldo|saldovortrag)\\b");

  // Bausteine von DETAIL_NOISE. Einzeln benannt statt als ein langer Alternativ-Ausdruck: Jeder
  // trägt eine eigene Begründung, und die Fälle stammen aus verschiedenen Layouts.

  /**
   * Reine Label-Zeile. Ihr Wert steht in der Zeile <em>darunter</em> und soll durch — genau
   * deshalb muss das Label weg: Es belegt sonst einen der {@link #MAX_DETAIL_LINES} Plätze, den
   * der Wert danach nicht mehr bekommt.
   */
  private static final String NOISE_LABEL =
      "^(?:absender|empfänger|empfaenger|mitteilungen|sender referenz|referenz"
          + "|payment id|bestellnummer|zahlungszweck):?$";

  /** Gegenpartei-IBAN — mit Leerzeichen gedruckt (UBS) oder ohne (PostFinance). */
  private static final String NOISE_IBAN = "^[A-Z]{2}\\d{2}[\\d ]{10,}$";

  /**
   * Maskierte Karten- oder Kontonummer. Ohne Wortgrenzen, weil beide Layouts sie anders setzen:
   * PostFinance druckt {@code KARTEN NR. XXXX4417} (kein {@code \b} zwischen X und Ziffer),
   * Viseca {@code 5500 20XX XXXX 5446}. Die frühere Fassung {@code \bXXXX\b} traf nur die
   * zweite und liess die Kartennummer jeder PostFinance-Kartenzahlung in den Prompt.
   */
  private static final String NOISE_MASKED_NUMBER = "X{4}";

  /**
   * Postanschrift der Gegenpartei: {@code Bahnhofstrasse 1} bzw. {@code 8000 Zürich}.
   *
   * <p>Trägt nichts zur Kategorie bei — der Name der Gegenpartei steht eine Zeile darüber und
   * bleibt erhalten — und ist zugleich das Personendatum, das am wenigsten im Claude-Prompt zu
   * suchen hat (BE-PDF-06). Auf echten PostFinance-Auszügen belegen Strasse und Ort zusammen
   * zwei der drei Plätze, sodass der Verwendungszweck darunter wegfällt.
   *
   * <p>Heuristik mit bekanntem Rand: Eine Zweckzeile, die mit einer vierstelligen Zahl
   * <em>beginnt</em> ({@code 2026 PRAEMIE}), fiele mit heraus. Beobachtet wurde die Form
   * bisher nicht — die Jahreszahl steht in allen vorliegenden Auszügen am Ende.
   */
  private static final String NOISE_ADDRESS =
      "^[1-9]\\d{3}\\s+\\p{L}[\\p{L}.\\-' ]*$"
          + "|^\\p{L}[\\p{L}.\\-' ]*(?:strasse|str\\.|weg|gasse|platz|allee|ring)\\s*\\d+[a-z]?$";

  /** Auftragsnummer hinter ihrem Label ({@code DAUERAUFTRAG: 90-11223344}). */
  private static final String NOISE_ORDER_NUMBER = "^dauerauftrag:\\s*\\d";

  /**
   * Undurchsichtige Referenz als eigene Zeile ({@code 250704111222333444AB},
   * {@code C040725R010A}) — der Wert unter Labels wie {@code PAYMENT ID} oder
   * {@code BESTELLNUMMER}.
   *
   * <p>Erst durch {@link #NOISE_LABEL} überhaupt sichtbar geworden: Vorher belegte das Label
   * den Platz, jetzt würde ihn sein Wert belegen. Beides ist für die Kategorisierung wertlos.
   *
   * <p>Zehn Zeichen ohne Leerzeichen, nur Grossbuchstaben und Ziffern, mindestens eine Ziffer.
   * Die Ziffernbedingung hält Händlernamen heraus: {@code SWISSCOM} hat keine, und Namen mit
   * Ziffer ({@code COOP-1234 BERN}) tragen Leerzeichen oder Bindestrich.
   *
   * <p>Als einzige Alternative in {@link #DETAIL_NOISE} ausdrücklich case-<em>sensitiv</em>
   * ({@code (?-i:…)}): Das globale {@code (?i)} des zusammengesetzten Ausdrucks liesse
   * {@code [0-9A-Z]} auch Kleinbuchstaben treffen, und dann verschwände jede einwortige
   * Zweckzeile mit Ziffer ab zehn Zeichen — {@code Rechnung2026} etwa — still aus dem
   * Kategorisierungs-Input. Die Grossschreibung ist hier das eigentliche Signal: Referenzen
   * druckt PostFinance durchgängig in Versalien, Zweckzeilen nicht.
   */
  private static final String NOISE_OPAQUE_REFERENCE =
      "^(?-i:(?=[0-9A-Z]*\\d)[0-9A-Z]{10,})$";

  /** Platzhalter, den PostFinance in leer gebliebene Felder druckt. */
  private static final String NOISE_PLACEHOLDER = "^n/a$";

  /** Kartenlimite unter der Zahlungszeile auf Viseca-Abrechnungen. */
  private static final String NOISE_CARD_LIMIT = "^Kartenlimite\\b";

  /**
   * Seitennummerierung im Fuss.
   *
   * <p>Sie steht physisch am Seitenende, im extrahierten Text aber direkt hinter der letzten
   * Buchung der Seite — kurz genug für {@link #MAX_DETAIL_LENGTH}, ohne Datum und ohne Betrag.
   * Ohne diesen Eintrag hängt an jeder Seite genau eine Buchung, deren Kategorisierungs-Input
   * auf „… Seite 2/4" endet. Jeder mehrseitige Auszug ist betroffen.
   */
  private static final String NOISE_PAGE_NUMBER = "^Seite\\s*:?\\s*\\d+\\s*(?:/|von)\\s*\\d+$";

  /** Zeilen ohne Kategorisierungswert — siehe die Bausteine oben. */
  private static final Pattern DETAIL_NOISE =
      Pattern.compile(
          "(?i)"
              + String.join(
                  "|",
                  NOISE_LABEL,
                  NOISE_IBAN,
                  NOISE_MASKED_NUMBER,
                  NOISE_ADDRESS,
                  NOISE_ORDER_NUMBER,
                  NOISE_OPAQUE_REFERENCE,
                  NOISE_PLACEHOLDER,
                  NOISE_CARD_LIMIT,
                  NOISE_PAGE_NUMBER));

  /**
   * Maximale Anzahl Detailzeilen pro Buchung.
   *
   * <p>Bewusst bei 3 belassen, obwohl echte PostFinance-Blöcke bis zu acht Zeilen haben. Die
   * Grenze begrenzt, was pro Transaktion in den Claude-Prompt geht — an Kosten wie an
   * Personendaten (BE-PDF-06). Das beobachtete Problem war nie die Grenze selbst, sondern dass
   * Rauschen das Rennen um die drei Plätze gewann und die sprechende Zeile verdrängte; behoben
   * wird das über {@link #DETAIL_NOISE}, nicht über ein höheres Limit.
   */
  private static final int MAX_DETAIL_LINES = 3;

  /**
   * Längengrenze einer Detailzeile. Empfänger, Referenzen und Händlerkategorien liegen in allen
   * beobachteten Layouts deutlich darunter (Maximum ~30 Zeichen); Seitenfüsse, Grussformeln und
   * Rechtshinweise deutlich darüber. Heuristik — die strukturell saubere Variante wäre der
   * Einrückungsvergleich über die x-Koordinaten der {@code TextPosition} (eigenes Issue).
   */
  private static final int MAX_DETAIL_LENGTH = 40;

  /**
   * Parst alle Transaktionen aus den PDF-Bytes.
   *
   * @param pdfBytes vollständiger Inhalt der PDF-Datei.
   * @return die extrahierten Transaktionen — leer nur, wenn das Format erkannt wurde, der
   *     Auszug aber keine Buchung enthält (Konto ohne Bewegung, BE-PDF-05).
   * @throws PasswordProtectedPdfException wenn das PDF verschlüsselt ist.
   * @throws MissingTextLayerException wenn das PDF keinen Textlayer enthält (Scan).
   * @throws UnsupportedStatementFormatException wenn das PDF Text enthält, daraus aber weder
   *     eine Buchungszeile noch eine Format-Signatur erkannt wurde.
   * @throws PdfParseException wenn das PDF nicht gelesen werden kann.
   */
  public List<ParsedTransaction> parse(byte[] pdfBytes) {
    List<List<String>> pages = extractPages(pdfBytes);
    // Kein Text auf keiner Seite: gescanntes PDF ohne Textlayer. Eigene Exception, weil die
    // hilfreiche Nutzermeldung hier eine andere ist als bei unbekanntem Layout (BE-PDF-04).
    if (pages.stream().allMatch(List::isEmpty)) {
      throw new MissingTextLayerException();
    }
    Format format = detectFormat(pages);
    List<ParsedTransaction> transactions;
    try {
      transactions = switch (format) {
        case VISECA -> parseViseca(pages);
        case POSTFINANCE -> parsePostFinance(pages);
        case UBS -> parseUbs(pages);
        case GENERIC -> parseGeneric(pages);
      };
    } catch (DateTimeParseException e) {
      // Kalendarisch ungültiges Datum (z. B. 32.01.) hat die Datums-Regex passiert.
      throw new PdfParseException("PDF enthält ein ungültiges Datum: " + e.getParsedString(), e);
    }
    // Text vorhanden, aber keine einzige Buchung erkannt: Ohne positives Format-Signal ist das
    // ein nicht unterstütztes Layout — ohne Exception sähe es für den User wie "Upload
    // erfolgreich, 0 Transaktionen" aus (#83). MIT erkannter Signatur ist es ein gültiger
    // Auszug ohne Kontobewegung: Erfolg mit leerer Liste (BE-PDF-05, #95).
    if (transactions.isEmpty() && !hasFormatSignature(format, pages)) {
      throw new UnsupportedStatementFormatException();
    }
    return transactions;
  }

  /**
   * Positives Format-Signal eines Auszugs ohne erkannte Buchung (BE-PDF-05): Ein per
   * Kopfzeilen-Schlüsselwort erkanntes Layout (Viseca/PostFinance/UBS) zählt immer; das
   * generische Layout ist nur ein Fallback und zählt erst mit einer Saldovortrag- oder
   * Anfangssaldo-Zeile als erkannt.
   */
  private static boolean hasFormatSignature(Format format, List<List<String>> pages) {
    if (format != Format.GENERIC) {
      return true;
    }
    return pages.stream()
        .flatMap(List::stream)
        .anyMatch(
            line ->
                SALDOVORTRAG.matcher(line).find() || UBS_ANFANGSSALDO.matcher(line).matches());
  }

  private enum Format {
    VISECA,
    POSTFINANCE,
    UBS,
    GENERIC
  }

  /** Anzahl Zeichen am Dokumentanfang, in denen Format-Schlüsselwörter gesucht werden. */
  private static final int DETECTION_HEAD_LENGTH = 2000;

  private static Format detectFormat(List<List<String>> pages) {
    // Nur der Kopfbereich der ersten Seite wird geprüft: Schlüsselwörter in Buchungszeilen (z. B.
    // ein "MASTERCARD"-Händlertext in einem Kontoauszug) dürfen das Format nicht umleiten.
    // "Mastercard" allein ist bewusst KEIN Viseca-Signal — PostFinance-/UBS-Karten sind
    // ebenfalls Mastercard-gebrandet.
    String firstPage = pages.isEmpty() ? "" : String.join("\n", pages.getFirst());
    String head = firstPage.substring(0, Math.min(firstPage.length(), DETECTION_HEAD_LENGTH));
    if (head.contains("Viseca") || head.contains("Kartenkontonummer")) {
      return Format.VISECA;
    }
    if (head.contains("PostFinance")) {
      return Format.POSTFINANCE;
    }
    if (head.contains("Kontobewegungen") && head.contains("UBS")) {
      return Format.UBS;
    }
    // Fallback für jedes nicht erkannte Layout — mit einer Folge, die DATE_ANY_RE hinzugefügt
    // hat: Ein unbekannter Auszug mit zweistelligen Jahren lief vorher garantiert leer aus und
    // damit in UnsupportedStatementFormatException; jetzt kann er teilweise parsen. Bewusst in
    // Kauf genommen, weil GENERIC_ROW zwei Beträge am Zeilenende verlangt — Viseca hat nur
    // einen, PostFinance und UBS fängt die Kopfzeilenerkennung oben ab.
    return Format.GENERIC;
  }

  /**
   * Extrahiert den Text seitenweise. Die Seitengrenze ist die Reset-Marke für die
   * Fortsetzungszeilen-Zuordnung: ohne sie würde der Seitenkopf von Seite 2 (Adresse, IBAN,
   * Spaltenüberschriften) an der letzten Buchung von Seite 1 landen.
   */
  private List<List<String>> extractPages(byte[] pdfBytes) {
    // PDFBox 3.x: Loader.loadPDF(byte[]) statt der veralteten 2.x-API PDDocument.load() (ADR-8).
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      List<List<String>> pages = new ArrayList<>();
      for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
        stripper.setStartPage(pageNo);
        stripper.setEndPage(pageNo);
        pages.add(nonEmptyLines(stripper.getText(document)));
      }
      return pages;
    } catch (InvalidPasswordException e) {
      throw new PasswordProtectedPdfException(e);
    } catch (IOException e) {
      throw new PdfParseException("PDF konnte nicht gelesen werden", e);
    }
  }

  private static List<String> nonEmptyLines(String text) {
    List<String> lines = new ArrayList<>();
    for (String raw : text.split("\\R")) {
      String line = raw.strip();
      if (!line.isEmpty()) {
        lines.add(line);
      }
    }
    return lines;
  }

  // === Generisch (Raiffeisen) ===================================================================

  private List<ParsedTransaction> parseGeneric(List<List<String>> pages) {
    List<MutableRow> rows = new ArrayList<>();
    BigDecimal previousSaldo = null;
    boolean warnedMissingSaldovortrag = false;

    for (List<String> page : pages) {
      MutableRow current = null;
      boolean bookingsEnded = false;

      for (String line : page) {
        Matcher saldovortrag = SALDOVORTRAG.matcher(line);
        if (saldovortrag.find()) {
          previousSaldo = parseAmount(saldovortrag.group(1));
          continue;
        }
        Matcher row = GENERIC_ROW.matcher(line);
        if (row.matches()) {
          BigDecimal saldo = parseAmount(row.group(4));
          if (previousSaldo == null && rows.isEmpty() && !warnedMissingSaldovortrag) {
            warnedMissingSaldovortrag = true;
            log.warn(
                "Kontoauszug ohne Saldovortrag-Zeile: Richtung der ersten Buchung kann nicht"
                    + " verifiziert werden — als Belastung übernommen");
          }
          boolean isIncome = previousSaldo != null && saldo.compareTo(previousSaldo) > 0;
          previousSaldo = saldo;
          current =
              new MutableRow(
                  parseGenericDate(row.group(1)),
                  row.group(2).strip(),
                  parseAmount(row.group(3)).abs(),
                  isIncome);
          rows.add(current);
          continue;
        }
        bookingsEnded |= endsBookings(current, line);
        if (!bookingsEnded) {
          appendDetail(current, line);
        }
      }
    }
    return toResult(rows);
  }

  /**
   * Parst ein Datum des generischen Layouts, das in beiden Jahresformen vorkommt. Zweistellig
   * heisst {@code 20yy} (Basisjahr 2000, siehe {@link #DATE_2}) — Kontoauszüge aus dem
   * 20. Jahrhundert sind kein Anwendungsfall.
   */
  private static LocalDate parseGenericDate(String value) {
    // Unterscheidung über die Jahresform selbst, nicht über die Stringlänge: Die Länge stimmt
    // nur, solange der Wert aus DATE_ANY_RE stammt — eine Kopplung, die beim Lesen unsichtbar
    // ist und beim nächsten Aufrufer bricht.
    return LocalDate.parse(value, DATE4_ONLY.matcher(value).matches() ? DATE_4 : DATE_2);
  }

  // === Viseca / Kreditkarte =====================================================================

  private List<ParsedTransaction> parseViseca(List<List<String>> pages) {
    List<MutableRow> rows = new ArrayList<>();

    for (List<String> page : pages) {
      MutableRow current = null;
      boolean bookingsEnded = false;

      for (String line : page) {
        Matcher m = VISECA_ROW.matcher(line);
        if (!m.matches()) {
          // Kopf-, Total- und Kategoriezeilen haben keine zwei Datumsangaben.
          bookingsEnded |= endsBookings(current, line);
          if (!bookingsEnded) {
            appendDetail(current, line);
          }
          continue;
        }
        String rest = m.group(3);
        Matcher amounts = AMOUNT_TOKEN.matcher(rest);
        int firstStart = -1;
        int lastEnd = -1;
        String lastAmount = null;
        while (amounts.find()) {
          if (firstStart < 0) {
            firstStart = amounts.start();
          }
          lastEnd = amounts.end();
          lastAmount = amounts.group();
        }
        if (lastAmount == null) {
          continue; // z. B. "5500 20XX XXXX 5446 Mastercard Silber, ..." ohne Betrag.
        }
        boolean isIncome = rest.substring(lastEnd).strip().equals("-");
        // Buchungstext = Teil vor dem ERSTEN Betrag: bei Fremdwährungszeilen ("... EUR 89.99
        // 85.90") bleibt so weder Fremdbetrag noch Währungscode im Text hängen.
        String textPart = rest.substring(0, firstStart).strip();
        textPart = TRAILING_CURRENCY.matcher(textPart).replaceAll("");
        current =
            new MutableRow(
                LocalDate.parse(m.group(1), DATE_2),
                textPart,
                parseAmount(lastAmount).abs(),
                isIncome);
        rows.add(current);
      }
    }
    return toResult(rows);
  }

  // === PostFinance ==============================================================================

  private List<ParsedTransaction> parsePostFinance(List<List<String>> pages) {
    List<MutableRow> result = new ArrayList<>();
    List<MutableRow> pending = new ArrayList<>();
    BigDecimal previousSaldo = null;

    for (List<String> page : pages) {
      MutableRow current = null;
      boolean bookingsEnded = false;

      for (String line : page) {
        Matcher kontostand = POST_KONTOSTAND.matcher(line);
        if (kontostand.matches()) {
          previousSaldo = parseAmount(kontostand.group(1));
          continue;
        }
        Matcher m = POST_ROW.matcher(line);
        if (!m.matches()) {
          bookingsEnded |= endsBookings(current, line);
          if (!bookingsEnded) {
            appendDetail(current, line);
          }
          continue;
        }
        LocalDate date =
            m.group(1) != null ? LocalDate.parse(m.group(1), DATE_2) : lastDate(pending, result);
        if (date == null) {
          continue; // Betrag vor der ersten datierten Buchung — ignorieren.
        }
        current = new MutableRow(date, m.group(2).strip(), parseAmount(m.group(3)).abs(), false);
        pending.add(current);

        if (m.group(5) != null) { // Saldo vorhanden -> Block auflösen.
          BigDecimal saldo = parseAmount(m.group(5));
          assignDirections(pending, previousSaldo, saldo);
          result.addAll(pending);
          pending.clear();
          previousSaldo = saldo;
        }
      }
    }
    // Buchungen ohne abschliessenden Saldo: als Belastung übernehmen (Default isIncome=false).
    result.addAll(pending);
    return toResult(result);
  }

  /**
   * Bestimmt die Richtung eines PostFinance-Buchungsblocks aus dem Saldo-Delta. Für gemischte Blöcke
   * (Gutschrift + Belastung am selben Tag) wird die Vorzeichenkombination gesucht, deren Summe dem
   * Delta entspricht. Zugewiesen wird nur eine <em>eindeutige</em> Lösung: ist keine oder mehr als
   * eine Kombination möglich, bleiben alle Buchungen Belastungen und es wird gewarnt — eine
   * willkürlich gewählte Kombination könnte einzelne Richtungen falsch setzen, obwohl die Summe
   * stimmt.
   *
   * <p>Buchungen über {@code 0.00} nehmen an der Suche nicht teil. Sie verschieben den Saldo
   * nicht und haben damit keine bestimmbare Richtung — {@code +0.00} und {@code -0.00} lösen
   * beide auf, was jeden Block mit einer solchen Zeile als „mehrdeutig" abstempeln würde. Auf
   * echten PostFinance-Auszügen ist das kein Randfall: Eine kostenlose Gebührenzeile
   * ({@code PREIS FÜR … 0.00}) steht dort am Monatsende. Sie bleiben Belastung (Default) und
   * die Warnung ist wieder den echten Fällen vorbehalten.
   */
  private static void assignDirections(List<MutableRow> block, BigDecimal before, BigDecimal after) {
    if (before == null || block.isEmpty()) {
      return;
    }
    BigDecimal delta = after.subtract(before);
    List<MutableRow> resolvable = new ArrayList<>(block.size());
    for (MutableRow row : block) {
      if (row.betrag.signum() != 0) {
        resolvable.add(row);
      }
    }
    if (resolvable.isEmpty()) {
      return;
    }
    int k = resolvable.size();
    if (k > 16) {
      log.warn(
          "PostFinance: {} Buchungen im Saldo-Block — Richtungen nicht auflösbar, alle als"
              + " Belastung übernommen",
          k);
      return;
    }
    int solution = -1;
    for (int mask = 0; mask < (1 << k); mask++) {
      BigDecimal sum = BigDecimal.ZERO.setScale(2);
      for (int i = 0; i < k; i++) {
        BigDecimal b = resolvable.get(i).betrag;
        sum = sum.add((mask >> i & 1) == 1 ? b.negate() : b);
      }
      if (sum.compareTo(delta) == 0) {
        if (solution >= 0) {
          // Kein Delta-Betrag im Log (BE-PDF-06, Datenminimierung): Beträge sind Zahlungsdaten.
          log.warn(
              "PostFinance: Saldo-Delta mehrdeutig für {} Buchung(en) — alle als Belastung"
                  + " übernommen",
              k);
          return;
        }
        solution = mask;
      }
    }
    if (solution < 0) {
      // Kein Delta-Betrag im Log (BE-PDF-06, Datenminimierung): Beträge sind Zahlungsdaten.
      log.warn(
          "PostFinance: Saldo-Delta nicht auflösbar für {} Buchung(en) — alle als Belastung"
              + " übernommen",
          k);
      return;
    }
    for (int i = 0; i < k; i++) {
      resolvable.get(i).isIncome = (solution >> i & 1) == 0;
    }
  }

  private static LocalDate lastDate(List<MutableRow> pending, List<MutableRow> result) {
    if (!pending.isEmpty()) {
      return pending.getLast().buchungsdatum;
    }
    if (!result.isEmpty()) {
      return result.getLast().buchungsdatum;
    }
    return null;
  }

  // === UBS ======================================================================================

  private List<ParsedTransaction> parseUbs(List<List<String>> pages) {
    List<MutableRow> rows = new ArrayList<>();
    BigDecimal anfangssaldo = null;

    for (List<String> page : pages) {
      MutableRow current = null;
      boolean bookingsEnded = false;

      for (String line : page) {
        Matcher anfang = UBS_ANFANGSSALDO.matcher(line);
        if (anfang.matches()) {
          anfangssaldo = parseAmount(anfang.group(1));
          continue;
        }
        Matcher m = UBS_ROW.matcher(line);
        if (m.matches()) {
          current =
              new MutableRow(
                  LocalDate.parse(m.group(1), DATE_4),
                  m.group(2).strip(),
                  parseAmount(m.group(3)).abs(),
                  false);
          // Saldo (group 5) getrennt merken für die Richtungsbestimmung.
          current.saldo = parseAmount(m.group(5));
          rows.add(current);
          continue;
        }
        bookingsEnded |= endsBookings(current, line);
        if (!bookingsEnded) {
          appendDetail(current, line);
        }
      }
    }

    if (anfangssaldo == null && !rows.isEmpty()) {
      log.warn(
          "UBS-Auszug ohne Anfangssaldo-Zeile: Richtung der ältesten Buchung kann nicht"
              + " verifiziert werden — als Belastung übernommen");
    }
    // UBS ist absteigend sortiert: von der ältesten Buchung (unten) aufwärts rechnen.
    BigDecimal previousSaldo = anfangssaldo;
    for (int i = rows.size() - 1; i >= 0; i--) {
      MutableRow r = rows.get(i);
      if (previousSaldo != null) {
        r.isIncome = r.saldo.compareTo(previousSaldo) > 0;
      }
      previousSaldo = r.saldo;
    }
    return toResult(rows);
  }

  // === Fortsetzungszeilen =======================================================================

  /**
   * Markiert das Ende des Buchungsteils einer Seite. Erst ab der ersten Buchung wirksam: die
   * {@code Kontostand}-/{@code Schlusssaldo}-Zeile mancher Layouts steht <em>vor</em> den
   * Buchungen und darf den Buchungsteil nicht vorzeitig schliessen.
   */
  private static boolean endsBookings(MutableRow current, String line) {
    return current != null && TOTALS_LINE.matcher(line).find();
  }

  /**
   * Ordnet eine Zeile der laufenden Buchung als Detailzeile zu, sofern sie wie eine aussieht.
   *
   * <p>Notwendig, weil bei Überweisungen der Empfänger nicht in der Buchungszeile steht, sondern
   * darunter: {@code ESR} → {@code Stadtwerke Bern}, {@code GIRO POST} → {@code Muster Immobilien
   * AG}. Ohne diese Zeilen liefern beide Stufen der Hybrid-Kategorisierung {@code Sonstiges}
   * (ADR-6, US-05).
   *
   * <p>Bewusst konservativ: alles mit Datum oder Betrag ist eine eigene Buchung oder eine
   * Summenzeile, und Fliesstext (Rechtshinweise, Grussformeln, Seitenfüsse) fällt über die
   * Längengrenze heraus. Lieber eine Detailzeile zu wenig als Seitenmöblierung im
   * Kategorisierungs-Input.
   */
  private static void appendDetail(MutableRow current, String line) {
    if (current == null
        || current.details.size() >= MAX_DETAIL_LINES
        || line.length() > MAX_DETAIL_LENGTH
        || STARTS_WITH_DATE.matcher(line).find()
        || AMOUNT_TOKEN.matcher(line).find()
        || DETAIL_NOISE.matcher(line).find()) {
      return;
    }
    current.details.add(line);
  }

  // === Helpers ==================================================================================

  /**
   * Wandelt einen Betrags-String in einen {@link BigDecimal} mit Skala 2 um. Alle
   * Tausendertrennzeichen aus {@link #THOUSANDS_SEPARATORS} werden entfernt (CLAUDE.md /
   * ADR-9). Das Vorzeichen bleibt erhalten. Sichtbar für Tests.
   */
  static BigDecimal parseAmount(String raw) {
    return new BigDecimal(SEPARATOR_CHARS.matcher(raw).replaceAll("")).setScale(2);
  }

  private static List<ParsedTransaction> toResult(List<MutableRow> rows) {
    List<ParsedTransaction> result = new ArrayList<>(rows.size());
    for (MutableRow r : rows) {
      result.add(r.toParsedTransaction());
    }
    log.debug("PDF geparst: {} Transaktion(en) extrahiert", result.size());
    return result;
  }

  /** Veränderbarer Zwischenzustand einer Zeile — erlaubt Detailzeilen und späte Richtungsbestimmung. */
  private static final class MutableRow {
    private final LocalDate buchungsdatum;
    private final String buchungstext;
    private final List<String> details = new ArrayList<>();
    private final BigDecimal betrag;
    private boolean isIncome;
    private BigDecimal saldo;

    MutableRow(LocalDate buchungsdatum, String buchungstext, BigDecimal betrag, boolean isIncome) {
      this.buchungsdatum = buchungsdatum;
      this.buchungstext = buchungstext;
      this.betrag = betrag;
      this.isIncome = isIncome;
    }

    ParsedTransaction toParsedTransaction() {
      return new ParsedTransaction(buchungsdatum, buchungstext, details, betrag, isIncome);
    }
  }
}
