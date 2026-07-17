package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.junit.jupiter.api.Test;

/**
 * Tests für {@link SwissBankStatementParser}. Die Bank-PDFs werden zur Laufzeit synthetisch mit
 * PDFBox erzeugt (kein echtes Kontoauszug-PDF im Repo) und dem Parser als Bytes übergeben.
 */
class SwissBankStatementParserTest {

  private final SwissBankStatementParser parser = new SwissBankStatementParser();

  // Raiffeisen-Layout: Saldovortrag + 4 Buchungen. Saldo-Deltas sind konsistent
  // (Belastung senkt, Gutschrift erhöht den Saldo) und dienen der Richtungserkennung.
  private static final List<String> RAIFFEISEN_STATEMENT =
      List.of(
          "Saldovortrag 1'000.00",
          "01.03.2024 01.03.2024 MIGROS MMM BERN 45.60 954.40",
          "05.03.2024 05.03.2024 SWISSCOM AG RECHNUNG 89.00 865.40",
          "25.03.2024 25.03.2024 LOHN ARBEITGEBER AG 3'500.00 4'365.40",
          "28.03.2024 28.03.2024 DIGITEC GALAXUS AG 1'234.56 3'130.84");

  @Test
  void parse_raiffeisenStatement_extractsAllTransactions() {
    byte[] pdf = pdfWithLines(RAIFFEISEN_STATEMENT);

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).hasSize(4);
    ParsedTransaction first = transactions.getFirst();
    assertThat(first.buchungsdatum()).isEqualTo(LocalDate.of(2024, 3, 1));
    assertThat(first.buchungstext()).isEqualTo("MIGROS MMM BERN");
    assertThat(first.betrag()).isEqualByComparingTo("45.60");
    assertThat(first.isIncome()).isFalse();
  }

  @Test
  void parse_amountWithThousandsSeparator_convertsToBigDecimal() {
    byte[] pdf = pdfWithLines(RAIFFEISEN_STATEMENT);

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .filteredOn(t -> t.buchungstext().equals("DIGITEC GALAXUS AG"))
        .singleElement()
        .satisfies(t -> assertThat(t.betrag()).isEqualByComparingTo("1234.56"));
  }

  @Test
  void parse_dateFormat_parsedAsDdMmYyyy() {
    byte[] pdf = pdfWithLines(RAIFFEISEN_STATEMENT);

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).extracting(ParsedTransaction::buchungsdatum)
        .containsExactly(
            LocalDate.of(2024, 3, 1),
            LocalDate.of(2024, 3, 5),
            LocalDate.of(2024, 3, 25),
            LocalDate.of(2024, 3, 28));
  }

  @Test
  void parse_creditIncreasingSaldo_isMarkedAsIncome() {
    byte[] pdf = pdfWithLines(RAIFFEISEN_STATEMENT);

    List<ParsedTransaction> transactions = parser.parse(pdf);

    // Nur die Lohn-Gutschrift erhöht den Saldo -> genau eine Income-Buchung.
    assertThat(transactions)
        .filteredOn(ParsedTransaction::isIncome)
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.buchungstext()).isEqualTo("LOHN ARBEITGEBER AG");
              assertThat(t.betrag()).isEqualByComparingTo("3500.00");
            });
  }

  @Test
  void parse_negativeSaldo_keepsDirectionCorrect() {
    // Saldo rutscht ins Minus: die Richtungserkennung muss das Vorzeichen des Saldos beachten.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 50.00",
                "02.05.2024 02.05.2024 MIETE ZAHLUNG 100.00 -50.00",
                "10.05.2024 10.05.2024 RUECKZAHLUNG FREUND 30.00 -20.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).extracting(ParsedTransaction::buchungstext, ParsedTransaction::isIncome)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("MIETE ZAHLUNG", false),
            org.assertj.core.groups.Tuple.tuple("RUECKZAHLUNG FREUND", true));
  }

  @Test
  void parse_wrappedText_isAppendedToBuchungstext() {
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 ONLINE SHOP BESTELLUNG 20.00 480.00",
                "REF NR 123456 ABCDEF"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t -> assertThat(t.buchungstext()).isEqualTo("ONLINE SHOP BESTELLUNG REF NR 123456 ABCDEF"));
  }

  @Test
  void parse_calendarInvalidDate_throwsPdfParseException() {
    // "32.01." passiert die Datums-Regex (nur Ziffernform), muss aber als PdfParseException
    // statt als rohe DateTimeParseException beim Aufrufer ankommen.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 1'000.00",
                "32.01.2024 32.01.2024 KAPUTTE ZEILE 10.00 990.00"));

    assertThatThrownBy(() -> parser.parse(pdf)).isInstanceOf(PdfParseException.class);
  }

  @Test
  void parse_mastercardInBookingText_doesNotMisrouteToVisecaParser() {
    // "MASTERCARD" im Buchungstext (z. B. Kartenzahlung auf einem Kontoauszug) darf die
    // Formaterkennung nicht auf den Viseca-Kreditkarten-Parser umleiten.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 1'000.00",
                "03.03.2024 03.03.2024 MASTERCARD PMT ONLINE SHOP 89.00 911.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t -> {
              assertThat(t.buchungstext()).isEqualTo("MASTERCARD PMT ONLINE SHOP");
              assertThat(t.isIncome()).isFalse();
            });
  }

  @Test
  void parse_ambiguousPostFinanceBlock_defaultsAllToDebit() {
    // Delta 0 mit zwei gleichen Beträgen: +50-50 und -50+50 sind beide gültig. Eine willkürliche
    // Zuweisung wäre potenziell falsch — der Parser muss auf Belastung zurückfallen (und warnen).
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 100.00",
                "05.09.19 BUCHUNG A 50.00 05.09.19",
                "BUCHUNG B 50.00 05.09.19 100.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).hasSize(2).allSatisfy(t -> assertThat(t.isIncome()).isFalse());
  }

  @Test
  void parse_visecaForeignCurrencyRow_stripsCurrencyAndForeignAmountFromText() {
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Viseca Payment Services SA",
                "Kartenkontonummer 1107 0000 0000 0000",
                "01.06.25 02.06.25 TESTSHOP AB, Dublin IE EUR 89.99 85.90"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t -> {
              assertThat(t.buchungstext()).isEqualTo("TESTSHOP AB, Dublin IE");
              assertThat(t.betrag()).isEqualByComparingTo("85.90");
              assertThat(t.isIncome()).isFalse();
            });
  }

  @Test
  void parseAmount_narrowNoBreakSpaceSeparator_isParsed() {
    // U+202F (schmales geschütztes Leerzeichen) ist in Schweizer Zahlformatierung üblich, kann
    // aber nicht mit den Standard-14-Fonts in ein Test-PDF gerendert werden — daher direkter
    // Test der (für Regex und Parsing gemeinsamen) Normalisierung.
    assertThat(SwissBankStatementParser.parseAmount("1\u202F000.00"))
        .isEqualByComparingTo("1000.00");
    assertThat("1\u202F000.00").matches(SwissBankStatementParser.AMOUNT);
  }

  @Test
  void parse_passwordProtectedPdf_throwsPasswordProtectedPdfException() {
    byte[] pdf = passwordProtectedPdf(RAIFFEISEN_STATEMENT);

    assertThatThrownBy(() -> parser.parse(pdf))
        .isInstanceOf(PasswordProtectedPdfException.class);
  }

  @Test
  void parse_corruptBytes_throwsPdfParseException() {
    byte[] notAPdf = "Dies ist kein PDF".getBytes();

    assertThatThrownBy(() -> parser.parse(notAPdf)).isInstanceOf(PdfParseException.class);
  }

  // --- PDF-Fixture-Helper (PDFBox 3.x) -----------------------------------------------------

  private static byte[] pdfWithLines(List<String> lines) {
    try (PDDocument document = new PDDocument()) {
      writeLines(document, lines);
      return toBytes(document);
    } catch (IOException e) {
      throw new IllegalStateException("Test-PDF konnte nicht erzeugt werden", e);
    }
  }

  private static byte[] passwordProtectedPdf(List<String> lines) {
    try (PDDocument document = new PDDocument()) {
      writeLines(document, lines);
      StandardProtectionPolicy policy =
          new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
      policy.setEncryptionKeyLength(128);
      document.protect(policy);
      return toBytes(document);
    } catch (IOException e) {
      throw new IllegalStateException("Passwortgeschütztes Test-PDF konnte nicht erzeugt werden", e);
    }
  }

  private static void writeLines(PDDocument document, List<String> lines) throws IOException {
    PDPage page = new PDPage(PDRectangle.A4);
    document.addPage(page);
    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
      content.beginText();
      content.setFont(new PDType1Font(FontName.HELVETICA), 10);
      content.setLeading(14);
      content.newLineAtOffset(50, 780);
      for (String line : lines) {
        content.showText(line);
        content.newLine();
      }
      content.endText();
    }
  }

  private static byte[] toBytes(PDDocument document) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    document.save(out);
    return out.toByteArray();
  }
}
