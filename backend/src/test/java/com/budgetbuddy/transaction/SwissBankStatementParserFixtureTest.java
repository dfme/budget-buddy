package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integrationstests von {@link SwissBankStatementParser} gegen echte (anonymisierte) Bank-PDFs aus
 * {@code src/main/resources/pdf/}. Deckt die vier realen Layouts ab: Viseca-Kreditkarte,
 * PostFinance und UBS (plus die generische Raiffeisen-Logik in {@link
 * SwissBankStatementParserTest}).
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
  private static final String POST = "/pdf/Post_August_2017.pdf";
  private static final String UBS = "/pdf/UBS_Konto_Bewegungen_2018_Juli.pdf";

  @Nested
  class Viseca {

    @Test
    void april_extractsAllRows_andExpensesMatchTotalKarte() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_APRIL));

      assertThat(txns).hasSize(11);
      // Belastungssumme = im PDF gedruckte Zeile "Total Karte Mastercard Silber ... 2'094.00".
      assertThat(sum(txns, false)).isEqualByComparingTo("2094.00");
      // Genau eine Gutschrift: die Zahlung der Vormonatsrechnung (nachgestelltes "-").
      assertThat(txns)
          .filteredOn(ParsedTransaction::isIncome)
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.buchungstext()).isEqualTo("Ihre Zahlung - Danke");
                assertThat(t.betrag()).isEqualByComparingTo("166.05");
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

      // "... BKG*HOTEL AT BOOKING.C ... SEK 7'989.00 709.40" -> der letzte Betrag ist der CHF-Betrag.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().startsWith("BKG*HOTEL"))
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.betrag()).isEqualByComparingTo("709.40");
                assertThat(t.isIncome()).isFalse();
                assertThat(t.buchungsdatum()).isEqualTo(LocalDate.of(2025, 4, 6));
              });
    }

    @Test
    void juni_extractsAllRows_andSumsMatchPrintedTotals() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_JUNI));

      assertThat(txns).hasSize(12);
      assertThat(sum(txns, false)).isEqualByComparingTo("1490.75"); // "Total Karte ... 1'490.75"
      assertThat(sum(txns, true)).isEqualByComparingTo("1929.10"); // "Ihre Zahlung - Danke"
    }
  }

  @Nested
  class PostFinance {

    @Test
    void extractsAllRows_andSumsMatchPrintedTotalLine() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      assertThat(txns).hasSize(15);
      // Gedruckte "Total"-Zeile: Gutschrift 6 500.00 / Lastschrift 4 107.40.
      assertThat(sum(txns, true)).isEqualByComparingTo("6500.00");
      assertThat(sum(txns, false)).isEqualByComparingTo("4107.40");
    }

    @Test
    void spaceThousandsSeparator_isParsed() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // "GIRO AUS KONTO 84-571-2 4 500.00" -> Leerzeichen als Tausendertrennzeichen.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().startsWith("GIRO AUS KONTO 84-571-2"))
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.betrag()).isEqualByComparingTo("4500.00");
                assertThat(t.isIncome()).isTrue();
              });
    }

    @Test
    void mixedDayBlock_resolvesCreditAndDebitFromSaldoDelta() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // Am 31.08.: eine Gutschrift (Fremdbank) und eine Belastung (Kontoführungsgebühr) im selben
      // Saldo-Block. Die Richtungen müssen unterschiedlich aufgelöst werden.
      assertThat(txns)
          .filteredOn(t -> t.buchungsdatum().equals(LocalDate.of(2017, 8, 31)))
          .extracting(ParsedTransaction::isIncome, ParsedTransaction::betrag)
          .containsExactlyInAnyOrder(
              Tuple.tuple(true, new BigDecimal("1000.00")),
              Tuple.tuple(false, new BigDecimal("5.00")));
    }

    @Test
    void twoDigitYear_isParsedAs2017() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      assertThat(txns).extracting(ParsedTransaction::buchungsdatum).allMatch(d -> d.getYear() == 2017);
    }
  }

  @Nested
  class Ubs {

    @Test
    void extractsAllRows_andSumsMatchUmsatztotal() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      assertThat(txns).hasSize(38);
      // Gedruckte "Umsatztotal"-Zeile: Belastung 32'045.35 / Gutschrift 54'072.75.
      assertThat(sum(txns, false)).isEqualByComparingTo("32045.35");
      assertThat(sum(txns, true)).isEqualByComparingTo("54072.75");
    }

    @Test
    void descendingStatement_directionDerivedAgainstOlderRow() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      // Der Auszug ist absteigend sortiert; der Saläreingang ist die grösste Gutschrift.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("Saläreingang"))
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.isIncome()).isTrue();
                assertThat(t.betrag()).isEqualByComparingTo("12841.00");
                assertThat(t.buchungsdatum()).isEqualTo(LocalDate.of(2018, 4, 27));
              });
      // Ein wiederkehrender Dauerauftrag ist eine Belastung.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("Dauerauftrag"))
          .isNotEmpty()
          .allSatisfy(t -> assertThat(t.isIncome()).isFalse());
    }

    @Test
    void apostropheThousandsSeparator_isParsed() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("Ihr Auftrag"))
          .singleElement()
          .satisfies(t -> assertThat(t.betrag()).isEqualByComparingTo("20972.50"));
    }

    @Test
    void allDates_areParsedAs2018() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      assertThat(txns).extracting(ParsedTransaction::buchungsdatum).allMatch(d -> d.getYear() == 2018);
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
