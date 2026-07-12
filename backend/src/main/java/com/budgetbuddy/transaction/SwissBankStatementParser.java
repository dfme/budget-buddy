package com.budgetbuddy.transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * Extrahiert Transaktionen aus Text-Layer-PDFs Schweizer Banken (Raiffeisen, PostFinance, UBS) —
 * BE-PDF-01, US-04.
 *
 * <p>Layout laut CLAUDE.md: {@code Buchungsdatum | Valuta | Text | Belastungen CHF | Gutschriften
 * CHF | Saldo CHF}. Datum {@code dd.MM.yyyy}, Beträge im Format {@code 1'234.56} (Apostroph als
 * Tausendertrennzeichen). Alle Beträge werden als {@link BigDecimal} verarbeitet (ADR-9).
 *
 * <p><b>Richtungserkennung:</b> Belastung vs. Gutschrift lässt sich aus dem reinen Textlayer nicht
 * über die Spaltenposition ableiten. Stattdessen wird der Saldo als Anker verwendet: steigt der
 * Saldo gegenüber der Vorzeile, ist die Buchung eine Gutschrift ({@code isIncome=true}), sinkt er,
 * eine Belastung. Die {@code Saldovortrag}-Zeile setzt den Startsaldo. Fehlt sie, wird die erste
 * Buchung als Belastung behandelt ({@code isIncome=false}).
 *
 * <p>Diese Klasse ist zustandslos und threadsicher.
 */
@Component
public class SwissBankStatementParser {

  private static final Logger log = LoggerFactory.getLogger(SwissBankStatementParser.class);

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  /** Ein CHF-Betrag mit optionalen Apostroph-Tausendertrennzeichen, z. B. {@code 1'234.56}. */
  private static final String AMOUNT = "-?\\d{1,3}(?:'\\d{3})*\\.\\d{2}";

  /**
   * Eine Transaktionszeile: Buchungsdatum, optionale Valuta, Text, Betrag und Saldo. Der Text ist
   * „reluctant" ({@code .+?}), damit die beiden letzten Zahlen als Betrag und Saldo greifen.
   */
  private static final Pattern ROW =
      Pattern.compile(
          "^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+"
              + "(?:\\d{2}\\.\\d{2}\\.\\d{4}\\s+)?"
              + "(.+?)\\s+"
              + "(" + AMOUNT + ")\\s+"
              + "(" + AMOUNT + ")$");

  /** Die Saldovortrag-Zeile (Anfangssaldo) — der letzte Betrag der Zeile ist der Startsaldo. */
  private static final Pattern SALDOVORTRAG =
      Pattern.compile("(?i)saldovortrag.*?(" + AMOUNT + ")\\s*$");

  private static final Pattern STARTS_WITH_DATE = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}\\b");

  /**
   * Parst alle Transaktionen aus den PDF-Bytes.
   *
   * @param pdfBytes vollständiger Inhalt der PDF-Datei.
   * @return die extrahierten Transaktionen in Reihenfolge des Dokuments (evtl. leer).
   * @throws PasswordProtectedPdfException wenn das PDF verschlüsselt/passwortgeschützt ist.
   * @throws PdfParseException wenn das PDF nicht gelesen werden kann (beschädigt, kein PDF).
   */
  public List<ParsedTransaction> parse(byte[] pdfBytes) {
    String text = extractText(pdfBytes);
    return parseText(text);
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

  private List<ParsedTransaction> parseText(String text) {
    List<MutableRow> rows = new ArrayList<>();
    BigDecimal previousSaldo = null;

    for (String rawLine : text.split("\\R")) {
      String line = rawLine.strip();
      if (line.isEmpty()) {
        continue;
      }

      Matcher saldovortrag = SALDOVORTRAG.matcher(line);
      if (saldovortrag.find()) {
        previousSaldo = parseSigned(saldovortrag.group(1));
        continue;
      }

      Matcher row = ROW.matcher(line);
      if (row.matches()) {
        LocalDate buchungsdatum = LocalDate.parse(row.group(1), DATE_FORMAT);
        String buchungstext = row.group(2).strip();
        BigDecimal betrag = parseSigned(row.group(3)).abs();
        BigDecimal saldo = parseSigned(row.group(4));

        boolean isIncome = previousSaldo != null && saldo.compareTo(previousSaldo) > 0;
        previousSaldo = saldo;

        rows.add(new MutableRow(buchungsdatum, buchungstext, betrag, isIncome));
        continue;
      }

      // Zeile ohne führendes Datum und ohne Betragspaar: Fortsetzung eines umgebrochenen
      // Buchungstextes. An die zuletzt erkannte Buchung anhängen.
      if (!STARTS_WITH_DATE.matcher(line).find() && !rows.isEmpty()) {
        rows.getLast().appendText(line);
      }
    }

    List<ParsedTransaction> result = new ArrayList<>(rows.size());
    for (MutableRow r : rows) {
      result.add(r.toParsedTransaction());
    }
    log.debug("PDF geparst: {} Transaktion(en) extrahiert", result.size());
    return result;
  }

  /**
   * Wandelt einen Betrags-String (z. B. {@code 1'234.56} oder {@code -12.00}) in einen {@link
   * BigDecimal} mit Skala 2 um. Apostroph-Tausendertrennzeichen werden vorher entfernt (CLAUDE.md /
   * ADR-9). Das Vorzeichen bleibt erhalten — wichtig für den (ggf. negativen) Saldo, aus dessen
   * Delta die Buchungsrichtung abgeleitet wird. Der Buchungsbetrag wird vom Aufrufer per {@code
   * abs()} zur Magnitude gemacht.
   */
  private static BigDecimal parseSigned(String raw) {
    return new BigDecimal(raw.replace("'", "")).setScale(2);
  }

  /** Veränderbarer Zwischenzustand einer Zeile — erlaubt das Anhängen umgebrochenen Textes. */
  private static final class MutableRow {
    private final LocalDate buchungsdatum;
    private final StringBuilder buchungstext;
    private final BigDecimal betrag;
    private final boolean isIncome;

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
