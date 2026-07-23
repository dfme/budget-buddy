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

  /** Betrags-Token an beliebiger Stelle einer Zeile. */
  private static final Pattern AMOUNT_TOKEN = Pattern.compile(AMOUNT);

  /** Führendes Buchungsdatum, zwei- oder vierstelliges Jahr. */
  private static final Pattern STARTS_WITH_DATE =
      Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{2}(?:\\d{2})?\\b");

  // --- Generisch (Raiffeisen-Kontoauszug) -------------------------------------------------------
  private static final Pattern GENERIC_ROW =
      Pattern.compile(
          "^(" + DATE4_RE + ")\\s+(?:" + DATE4_RE + "\\s+)?(.+?)\\s+(" + AMOUNT + ")\\s+("
              + AMOUNT + ")$");
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

  /**
   * Zeilen ohne Kategorisierungswert: reine Label-Zeilen, Gegenpartei-IBAN und die maskierte
   * Kartennummer samt Limite auf Viseca-Abrechnungen.
   */
  private static final Pattern DETAIL_NOISE =
      Pattern.compile(
          "(?i)^(?:absender|empfänger|empfaenger):?$"
              + "|^[A-Z]{2}\\d{2}[\\d ]{10,}$"
              + "|\\bXXXX\\b"
              + "|^Kartenlimite\\b");

  /** Maximale Anzahl Detailzeilen pro Buchung. */
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
   * @return die extrahierten Transaktionen — nie leer.
   * @throws PasswordProtectedPdfException wenn das PDF verschlüsselt ist.
   * @throws MissingTextLayerException wenn das PDF keinen Textlayer enthält (Scan).
   * @throws UnsupportedStatementFormatException wenn das PDF Text enthält, daraus aber keine
   *     Buchungszeile erkannt wurde.
   * @throws PdfParseException wenn das PDF nicht gelesen werden kann.
   */
  public List<ParsedTransaction> parse(byte[] pdfBytes) {
    List<List<String>> pages = extractPages(pdfBytes);
    // Kein Text auf keiner Seite: gescanntes PDF ohne Textlayer. Eigene Exception, weil die
    // hilfreiche Nutzermeldung hier eine andere ist als bei unbekanntem Layout (BE-PDF-04).
    if (pages.stream().allMatch(List::isEmpty)) {
      throw new MissingTextLayerException();
    }
    List<ParsedTransaction> transactions;
    try {
      transactions = switch (detectFormat(pages)) {
        case VISECA -> parseViseca(pages);
        case POSTFINANCE -> parsePostFinance(pages);
        case UBS -> parseUbs(pages);
        case GENERIC -> parseGeneric(pages);
      };
    } catch (DateTimeParseException e) {
      // Kalendarisch ungültiges Datum (z. B. 32.01.) hat die Datums-Regex passiert.
      throw new PdfParseException("PDF enthält ein ungültiges Datum: " + e.getParsedString(), e);
    }
    // Text vorhanden, aber keine einzige Buchung erkannt: Layout wird nicht unterstützt. Ohne
    // Exception sähe das für den User wie "Upload erfolgreich, 0 Transaktionen" aus (#83).
    if (transactions.isEmpty()) {
      throw new UnsupportedStatementFormatException();
    }
    return transactions;
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
                  LocalDate.parse(row.group(1), DATE_4),
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
   */
  private static void assignDirections(List<MutableRow> block, BigDecimal before, BigDecimal after) {
    if (before == null || block.isEmpty()) {
      return;
    }
    BigDecimal delta = after.subtract(before);
    int k = block.size();
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
        BigDecimal b = block.get(i).betrag;
        sum = sum.add((mask >> i & 1) == 1 ? b.negate() : b);
      }
      if (sum.compareTo(delta) == 0) {
        if (solution >= 0) {
          log.warn(
              "PostFinance: Saldo-Delta {} mehrdeutig für {} Buchung(en) — alle als Belastung"
                  + " übernommen",
              delta,
              k);
          return;
        }
        solution = mask;
      }
    }
    if (solution < 0) {
      log.warn(
          "PostFinance: Saldo-Delta {} nicht auflösbar für {} Buchung(en) — alle als Belastung"
              + " übernommen",
          delta,
          k);
      return;
    }
    for (int i = 0; i < k; i++) {
      block.get(i).isIncome = (solution >> i & 1) == 0;
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
