package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integrationstests von {@link SwissBankStatementParser} gegen vollständig synthetische
 * Bank-PDF-Fixtures (Kontoinhaber «Peter Muster») aus {@code src/test/resources/pdf/}. Deckt die
 * drei realen Layouts ab: Viseca-Kreditkarte, PostFinance und UBS (plus die generische
 * Raiffeisen-Logik in {@link SwissBankStatementParserTest}).
 *
 * <p>Die stärkste Invariante je Auszug ist die Kreuzprobe: Die Summe der extrahierten Gutschriften
 * bzw. Belastungen muss exakt der im PDF gedruckten Total-/Umsatzzeile entsprechen. Stimmt sie,
 * sind Beträge, Vorzeichen-/Richtungserkennung und Vollständigkeit zugleich verifiziert.
 */
class SwissBankStatementParserFixtureTest {

  private final SwissBankStatementParser parser = new SwissBankStatementParser();

  private static final String KREDITKARTE_APRIL =
      "/pdf/Kreditkarten Rechnung April 2025 - CH9300762011623852957 - 2025-04-25.pdf";
  private static final String KREDITKARTE_JUNI =
      "/pdf/Kreditkarten Rechnung Juni 2025 - CH9300762011623852957 - 2025-06-25.pdf";
  private static final String POST = "/pdf/Post_kontoauszug.pdf";
  private static final String UBS = "/pdf/UBS_Konto_Bewegungen_2021_Juli.pdf";

  @Nested
  class Viseca {

    @Test
    void april_extractsAllRows_andExpensesMatchTotalKarte() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_APRIL));

      assertThat(txns).hasSize(10);
      // Belastungssumme = im PDF gedruckte Zeile "Total Karte Mastercard Silber ... 881.95".
      assertThat(sum(txns, false)).isEqualByComparingTo("881.95");
      // Genau eine Gutschrift: die Zahlung der Vormonatsrechnung (nachgestelltes "-").
      assertThat(txns)
          .filteredOn(ParsedTransaction::isIncome)
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.buchungstext()).isEqualTo("Ihre Zahlung - Danke");
                assertThat(t.betrag()).isEqualByComparingTo("950.20");
                assertThat(t.buchungsdatum()).isEqualTo(LocalDate.of(2025, 3, 25));
              });
    }

    @Test
    void april_twoDigitYear_isParsedAs2025() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_APRIL));

      assertThat(txns).extracting(ParsedTransaction::buchungsdatum).allMatch(d -> d.getYear() == 2025);
    }

    @Test
    void april_foreignCurrencyRow_usesLastAmountAsChf() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_APRIL));

      // "BKG*HOTEL BELLEVUE, Amsterdam NL EUR 250.00 238.55" -> der letzte Betrag ist der CHF-Betrag.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().startsWith("BKG*HOTEL"))
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.betrag()).isEqualByComparingTo("238.55");
                assertThat(t.isIncome()).isFalse();
                assertThat(t.buchungsdatum()).isEqualTo(LocalDate.of(2025, 4, 6));
              });
    }

    @Test
    void juni_extractsAllRows_andSumsMatchPrintedTotals() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_JUNI));

      assertThat(txns).hasSize(11);
      assertThat(sum(txns, false)).isEqualByComparingTo("599.60"); // "Total Karte ... 599.60"
      assertThat(sum(txns, true)).isEqualByComparingTo("881.95"); // "Ihre Zahlung - Danke"
    }
  }

  @Nested
  class PostFinance {

    @Test
    void extractsAllRows_andSumsMatchPrintedTotalLine() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      assertThat(txns).hasSize(7);
      // Gedruckte "Total"-Zeile: Gutschrift 12 489.10 / Lastschrift 1 163.95.
      assertThat(sum(txns, true)).isEqualByComparingTo("12489.10");
      assertThat(sum(txns, false)).isEqualByComparingTo("1163.95");
    }

    @Test
    void spaceThousandsSeparator_isParsed() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // "GIRO AUS KONTO 25-9034-2 4 589.10" -> Leerzeichen als Tausendertrennzeichen.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().startsWith("GIRO AUS KONTO 25-9034-2"))
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.betrag()).isEqualByComparingTo("4589.10");
                assertThat(t.isIncome()).isTrue();
              });
    }

    @Test
    void mixedDayBlock_resolvesCreditAndDebitFromSaldoDelta() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // Am 30.09.: eine Gutschrift (Lohn) und eine Belastung (Kontoführungsgebühr) im selben
      // Saldo-Block. Die Richtungen müssen unterschiedlich aufgelöst werden.
      assertThat(txns)
          .filteredOn(t -> t.buchungsdatum().equals(LocalDate.of(2019, 9, 30)))
          .extracting(ParsedTransaction::isIncome, ParsedTransaction::betrag)
          .containsExactlyInAnyOrder(
              Tuple.tuple(true, new BigDecimal("5500.00")),
              Tuple.tuple(false, new BigDecimal("5.00")));
    }

    @Test
    void twoDigitYear_isParsedAs2019() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      assertThat(txns).extracting(ParsedTransaction::buchungsdatum).allMatch(d -> d.getYear() == 2019);
    }
  }

  @Nested
  class Ubs {

    @Test
    void extractsAllRows_andSumsMatchUmsatztotal() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      assertThat(txns).hasSize(22);
      // Gedruckte "Umsatztotal"-Zeile: Belastung 26'402.75 / Gutschrift 40'950.00.
      assertThat(sum(txns, false)).isEqualByComparingTo("26402.75");
      assertThat(sum(txns, true)).isEqualByComparingTo("40950.00");
    }

    @Test
    void descendingStatement_directionDerivedAgainstOlderRow() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      // Der Auszug ist absteigend sortiert; jeder Saläreingang ist eine Gutschrift.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("Saläreingang"))
          .hasSize(6)
          .allSatisfy(
              t -> {
                assertThat(t.isIncome()).isTrue();
                assertThat(t.betrag()).isEqualByComparingTo("6800.00");
              });
      // Ein wiederkehrender Dauerauftrag ist eine Belastung.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("Dauerauftrag"))
          .hasSize(6)
          .allSatisfy(t -> assertThat(t.isIncome()).isFalse());
      // Die älteste Buchung wird gegen den Anfangssaldo (5'000.00) aufgelöst.
      assertThat(txns)
          .filteredOn(t -> t.buchungsdatum().equals(LocalDate.of(2021, 1, 5)))
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.buchungstext()).isEqualTo("Postüberweisung");
                assertThat(t.isIncome()).isFalse();
                assertThat(t.betrag()).isEqualByComparingTo("1250.30");
              });
    }

    @Test
    void apostropheThousandsSeparator_isParsed() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("Ihr Auftrag"))
          .singleElement()
          .satisfies(t -> assertThat(t.betrag()).isEqualByComparingTo("12500.00"));
    }

    @Test
    void allDates_areParsedAs2021() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      assertThat(txns).extracting(ParsedTransaction::buchungsdatum).allMatch(d -> d.getYear() == 2021);
    }
  }

  // --- helpers ----------------------------------------------------------------------------------

  private static BigDecimal sum(List<ParsedTransaction> txns, boolean income) {
    return txns.stream()
        .filter(t -> t.isIncome() == income)
        .map(ParsedTransaction::betrag)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static byte[] bytes(String classpathResource) {
    try (InputStream in =
        SwissBankStatementParserFixtureTest.class.getResourceAsStream(classpathResource)) {
      if (in == null) {
        throw new IllegalStateException("Fixture nicht im Classpath: " + classpathResource);
      }
      return in.readAllBytes();
    } catch (java.io.IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
