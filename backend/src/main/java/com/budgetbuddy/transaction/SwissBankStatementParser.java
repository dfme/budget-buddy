package com.budgetbuddy.transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
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
 *       Gutschrift (Zahlung/Rückerstattung) ist an einem nachgestellten „-" erkennbar.
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

  /** CHF-Betrag mit Apostroph- oder Leerzeichen-Tausendertrennzeichen, z. B. {@code 1'234.56}. */
  private static final String AMOUNT = "-?\\d{1,3}(?:['  ]\\d{3})*\\.\\d{2}";

  private static final String DATE4_RE = "\\d{2}\\.\\d{2}\\.\\d{4}";
  private static final String DATE2_RE = "\\d{2}\\.\\d{2}\\.\\d{2}";

  // --- Generisch (Raiffeisen-Kontoauszug) -------------------------------------------------------
  private static final Pattern GENERIC_ROW =
      Pattern.compile(
          "^(" + DATE4_RE + ")\\s+(?:" + DATE4_RE + "\\s+)?(.+?)\\s+(" + AMOUNT + ")\\s+("
              + AMOUNT + ")$");
  private static final Pattern SALDOVORTRAG =
      Pattern.compile("(?i)saldovortrag.*?(" + AMOUNT + ")\\s*$");
  private static final Pattern STARTS_WITH_DATE4 = Pattern.compile("^" + DATE4_RE + "\\b");

  // --- Viseca / Kreditkarte ---------------------------------------------------------------------
  private static final Pattern VISECA_ROW =
      Pattern.compile("^(" + DATE2_RE + ")\\s+(" + DATE2_RE + ")\\s+(.*)$");
  private static final Pattern AMOUNT_TOKEN = Pattern.compile(AMOUNT);
  private static final Pattern TRAILING_CURRENCY = Pattern.compile("\\s+[A-Z]{3}$");

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

  /**
   * Parst alle Transaktionen aus den PDF-Bytes.
   *
   * @param pdfBytes vollständiger Inhalt der PDF-Datei.
   * @return die extrahierten Transaktionen (evtl. leer).
   * @throws PasswordProtectedPdfException wenn das PDF verschlüsselt ist.
   * @throws PdfParseException wenn das PDF nicht gelesen werden kann.
   */
  public List<ParsedTransaction> parse(byte[] pdfBytes) {
    String text = extractText(pdfBytes);
    List<String> lines = new ArrayList<>();
    for (String raw : text.split("\\R")) {
      String line = raw.strip();
      if (!line.isEmpty()) {
        lines.add(line);
      }
    }
    return switch (detectFormat(text)) {
      case VISECA -> parseViseca(lines);
      case POSTFINANCE -> parsePostFinance(lines);
      case UBS -> parseUbs(lines);
      case GENERIC -> parseGeneric(lines);
    };
  }

  private enum Format {
    VISECA,
    POSTFINANCE,
    UBS,
    GENERIC
  }

  private static Format detectFormat(String text) {
    if (text.contains("Viseca") || text.contains("Mastercard") || text.contains("Kartenkontonummer")) {
      return Format.VISECA;
    }
    if (text.contains("PostFinance")) {
      return Format.POSTFINANCE;
    }
    if (text.contains("Kontobewegungen") && text.contains("UBS")) {
      return Format.UBS;
    }
    return Format.GENERIC;
  }

  private String extractText(byte[] pdfBytes) {
    // PDFBox 3.x: Loader.loadPDF(byte[]) statt der veralteten 2.x-API PDDocument.load() (ADR-8).
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(document);
    } catch (InvalidPasswordException e) {
      throw new PasswordProtectedPdfException(e);
    } catch (IOException e) {
      throw new PdfParseException("PDF konnte nicht gelesen werden", e);
    }
  }

  // === Generisch (Raiffeisen) ===================================================================

  private List<ParsedTransaction> parseGeneric(List<String> lines) {
    List<MutableRow> rows = new ArrayList<>();
    BigDecimal previousSaldo = null;
    for (String line : lines) {
      Matcher saldovortrag = SALDOVORTRAG.matcher(line);
      if (saldovortrag.find()) {
        previousSaldo = parseAmount(saldovortrag.group(1));
        continue;
      }
      Matcher row = GENERIC_ROW.matcher(line);
      if (row.matches()) {
        BigDecimal saldo = parseAmount(row.group(4));
        boolean isIncome = previousSaldo != null && saldo.compareTo(previousSaldo) > 0;
        previousSaldo = saldo;
        rows.add(
            new MutableRow(
                LocalDate.parse(row.group(1), DATE_4),
                row.group(2).strip(),
                parseAmount(row.group(3)).abs(),
                isIncome));
        continue;
      }
      if (!STARTS_WITH_DATE4.matcher(line).find() && !rows.isEmpty()) {
        rows.getLast().appendText(line);
      }
    }
    return toResult(rows);
  }

  // === Viseca / Kreditkarte =====================================================================

  private List<ParsedTransaction> parseViseca(List<String> lines) {
    List<MutableRow> rows = new ArrayList<>();
    for (String line : lines) {
      Matcher m = VISECA_ROW.matcher(line);
      if (!m.matches()) {
        continue; // Kopf-, Total- und Kategoriezeilen haben keine zwei Datumsangaben.
      }
      String rest = m.group(3);
      Matcher amounts = AMOUNT_TOKEN.matcher(rest);
      int lastStart = -1;
      int lastEnd = -1;
      String lastAmount = null;
      while (amounts.find()) {
        lastStart = amounts.start();
        lastEnd = amounts.end();
        lastAmount = amounts.group();
      }
      if (lastAmount == null) {
        continue; // z. B. "5500 20XX XXXX 5446 Mastercard Silber, ..." ohne Betrag.
      }
      boolean isIncome = rest.substring(lastEnd).strip().equals("-");
      String textPart = rest.substring(0, lastStart).strip();
      textPart = TRAILING_CURRENCY.matcher(textPart).replaceAll("");
      rows.add(
          new MutableRow(
              LocalDate.parse(m.group(1), DATE_2),
              textPart,
              parseAmount(lastAmount).abs(),
              isIncome));
    }
    return toResult(rows);
  }

  // === PostFinance ==============================================================================

  private List<ParsedTransaction> parsePostFinance(List<String> lines) {
    List<MutableRow> result = new ArrayList<>();
    List<MutableRow> pending = new ArrayList<>();
    BigDecimal previousSaldo = null;

    for (String line : lines) {
      Matcher kontostand = POST_KONTOSTAND.matcher(line);
      if (kontostand.matches()) {
        previousSaldo = parseAmount(kontostand.group(1));
        continue;
      }
      Matcher m = POST_ROW.matcher(line);
      if (!m.matches()) {
        continue;
      }
      LocalDate date = m.group(1) != null ? LocalDate.parse(m.group(1), DATE_2) : lastDate(pending, result);
      if (date == null) {
        continue; // Betrag vor der ersten datierten Buchung — ignorieren.
      }
      pending.add(new MutableRow(date, m.group(2).strip(), parseAmount(m.group(3)).abs(), false));

      if (m.group(5) != null) { // Saldo vorhanden -> Block auflösen.
        BigDecimal saldo = parseAmount(m.group(5));
        assignDirections(pending, previousSaldo, saldo);
        result.addAll(pending);
        pending.clear();
        previousSaldo = saldo;
      }
    }
    // Buchungen ohne abschliessenden Saldo: als Belastung übernehmen (Default isIncome=false).
    result.addAll(pending);
    return toResult(result);
  }

  /**
   * Bestimmt die Richtung eines PostFinance-Buchungsblocks aus dem Saldo-Delta. Für gemischte Blöcke
   * (Gutschrift + Belastung am selben Tag) wird die Vorzeichenkombination gesucht, deren Summe dem
   * Delta entspricht. Ist keine Lösung möglich, bleiben alle Buchungen Belastungen.
   */
  private static void assignDirections(List<MutableRow> block, BigDecimal before, BigDecimal after) {
    if (before == null || block.isEmpty()) {
      return;
    }
    BigDecimal delta = after.subtract(before);
    int k = block.size();
    if (k > 16) {
      return;
    }
    for (int mask = 0; mask < (1 << k); mask++) {
      BigDecimal sum = BigDecimal.ZERO.setScale(2);
      for (int i = 0; i < k; i++) {
        BigDecimal b = block.get(i).betrag;
        sum = sum.add((mask >> i & 1) == 1 ? b.negate() : b);
      }
      if (sum.compareTo(delta) == 0) {
        for (int i = 0; i < k; i++) {
          block.get(i).isIncome = (mask >> i & 1) == 0;
        }
        return;
      }
    }
    log.debug("PostFinance: Saldo-Delta {} nicht auflösbar für {} Buchung(en)", delta, k);
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

  private List<ParsedTransaction> parseUbs(List<String> lines) {
    List<MutableRow> rows = new ArrayList<>();
    BigDecimal anfangssaldo = null;
    for (String line : lines) {
      Matcher anfang = UBS_ANFANGSSALDO.matcher(line);
      if (anfang.matches()) {
        anfangssaldo = parseAmount(anfang.group(1));
        continue;
      }
      Matcher m = UBS_ROW.matcher(line);
      if (m.matches()) {
        rows.add(
            new MutableRow(
                LocalDate.parse(m.group(1), DATE_4),
                m.group(2).strip(),
                parseAmount(m.group(3)).abs(),
                false));
        // Saldo (group 5) getrennt merken für die Richtungsbestimmung.
        rows.getLast().saldo = parseAmount(m.group(5));
      }
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

  // === Helpers ==================================================================================

  /**
   * Wandelt einen Betrags-String in einen {@link BigDecimal} mit Skala 2 um. Apostroph-, Leer- und
   * geschützte Leerzeichen als Tausendertrennzeichen werden entfernt (CLAUDE.md / ADR-9). Das
   * Vorzeichen bleibt erhalten.
   */
  private static BigDecimal parseAmount(String raw) {
    return new BigDecimal(raw.replace("'", "").replace(" ", "").replace(" ", "")).setScale(2);
  }

  private static List<ParsedTransaction> toResult(List<MutableRow> rows) {
    List<ParsedTransaction> result = new ArrayList<>(rows.size());
    for (MutableRow r : rows) {
      result.add(r.toParsedTransaction());
    }
    log.debug("PDF geparst: {} Transaktion(en) extrahiert", result.size());
    return result;
  }

  /** Veränderbarer Zwischenzustand einer Zeile — erlaubt Textanhang und späte Richtungsbestimmung. */
  private static final class MutableRow {
    private final LocalDate buchungsdatum;
    private final StringBuilder buchungstext;
    private final BigDecimal betrag;
    private boolean isIncome;
    private BigDecimal saldo;

    MutableRow(LocalDate buchungsdatum, String buchungstext, BigDecimal betrag, boolean isIncome) {
      this.buchungsdatum = buchungsdatum;
      this.buchungstext = new StringBuilder(buchungstext);
      this.betrag = betrag;
      this.isIncome = isIncome;
    }

    void appendText(String continuation) {
      buchungstext.append(' ').append(continuation);
    }

    ParsedTransaction toParsedTransaction() {
      return new ParsedTransaction(buchungsdatum, buchungstext.toString(), betrag, isIncome);
    }
  }
}
