package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

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
  private static final String POST_JAHR = "/pdf/Post_Kontoauszug_2025_240_Buchungen.pdf";
  private static final String POST_JULI = "/pdf/Post_Kontoauszug_2026_Juli_20_Buchungen.pdf";

  @Nested
  class Viseca {

    @Test
    void april_extractsAllRows_andExpensesMatchTotalKarte() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_APRIL));

      assertThat(txns).hasSize(12);
      // Belastungssumme = im PDF gedruckte Zeile "Total Karte Mastercard Silber ... 1'025.85".
      assertThat(sum(txns, false)).isEqualByComparingTo("1025.85");
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

      // "BKG*HOTEL BELLEVUE, Amsterdam NL EUR 250.00 238.55" -> der letzte Betrag ist der
      // CHF-Betrag; Fremdbetrag und Währungscode gehören nicht in den Buchungstext.
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().startsWith("BKG*HOTEL"))
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.buchungstext()).isEqualTo("BKG*HOTEL BELLEVUE, Amsterdam NL");
                assertThat(t.betrag()).isEqualByComparingTo("238.55");
                assertThat(t.isIncome()).isFalse();
                assertThat(t.buchungsdatum()).isEqualTo(LocalDate.of(2025, 4, 6));
              });
    }

    @Test
    void april_printedMerchantCategory_isKeptAsDetail_cardNoiseIsNot() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_APRIL));

      // Viseca druckt unter jeder Buchung seine eigene Händlerkategorie — ein Gratis-Signal
      // für die Kategorisierung (US-05).
      assertThat(byText(txns, "Restaurant Rosengarten, Bern CH").details())
          .containsExactly("Restaurants");
      // Umrechnungskurs-/Gebührenzeilen enthalten Beträge und gehören nicht in den Text.
      assertThat(byText(txns, "BKG*HOTEL BELLEVUE, Amsterdam NL").details())
          .containsExactly("Hotels");
      // Maskierte Kartennummer und Limite unter der Zahlungszeile sind reines Rauschen.
      assertThat(byText(txns, "Ihre Zahlung - Danke").details()).isEmpty();
    }

    @Test
    void juni_extractsAllRows_andSumsMatchPrintedTotals() {
      List<ParsedTransaction> txns = parser.parse(bytes(KREDITKARTE_JUNI));

      assertThat(txns).hasSize(13);
      assertThat(sum(txns, false)).isEqualByComparingTo("814.30"); // "Total Karte ... 814.30"
      assertThat(sum(txns, true)).isEqualByComparingTo("1025.85"); // "Ihre Zahlung - Danke"
    }
  }

  @Nested
  class PostFinance {

    @Test
    void extractsAllRows_andSumsMatchPrintedTotalLine() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      assertThat(txns).hasSize(13);
      // Gedruckte "Total"-Zeile: Gutschrift 12 489.10 / Lastschrift 2 243.90.
      assertThat(sum(txns, true)).isEqualByComparingTo("12489.10");
      assertThat(sum(txns, false)).isEqualByComparingTo("2243.90");
    }

    @Test
    void spaceThousandsSeparator_isParsed() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // "GUTSCHRIFT 4 589.10" -> Leerzeichen als Tausendertrennzeichen.
      assertThat(onDate(txns, LocalDate.of(2019, 9, 9)))
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

    @Test
    void merchantReachesTheCategorizationInput_throughTheDetailLines() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // PostFinance schreibt in die Buchungszeile die ZAHLUNGSART; der Händler steht darunter.
      // Beides zusammen ist der Input für die Kategorisierung (US-05, Lookup + Claude API).
      assertThat(txns)
          .extracting(ParsedTransaction::buchungstext)
          .containsOnly(
              "GIRO POST", "KAUF/DIENSTLEISTUNG", "GIRO INTERNATIONAL", "LASTSCHRIFT",
              "GUTSCHRIFT", "ESR", "TWINT", "KONTOÜBERTRAG AUF", "PREIS FÜR");
      assertThat(txns)
          .extracting(ParsedTransaction::fullText)
          .anySatisfy(t -> assertThat(t).contains("MIGROS M BERN"))
          .anySatisfy(t -> assertThat(t).contains("SWISSCOM (SCHWEIZ) AG"))
          .anySatisfy(t -> assertThat(t).contains("CSS VERSICHERUNG AG"))
          .anySatisfy(t -> assertThat(t).contains("SBB CFF FFS BERN"))
          .anySatisfy(t -> assertThat(t).contains("COOP-4321 BERN"));
      // Und keiner dieser Händler steht in der Buchungszeile.
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.buchungstext())
                      .doesNotContain("MIGROS", "SWISSCOM", "CSS", "SBB", "COOP"));
    }

    @Test
    void transferBookings_carryPayeeInDetails_soCategorizationHasSomethingToWorkWith() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // Bei Überweisungen trägt die Buchungszeile nur den Buchungstyp — ohne die Detailzeilen
      // hätten weder Lookup noch Claude einen Anhaltspunkt und alles würde "Sonstiges" (ADR-6).
      assertThat(byText(txns, "ESR").details()).containsExactly("Stadtwerke Bern");
      assertThat(byText(txns, "GIRO INTERNATIONAL").details())
          .containsExactly("Amazon EU S.a.r.l.", "Luxembourg");
      assertThat(byText(txns, "GIRO INTERNATIONAL").fullText())
          .isEqualTo("GIRO INTERNATIONAL Amazon EU S.a.r.l. Luxembourg");
    }

    @Test
    void counterpartyIbanAndLabelLines_areNotTreatedAsDetails() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // Unter "GIRO POST" stehen im PDF IBAN, Empfänger und Zweck; die IBAN trägt nichts zur
      // Kategorisierung bei, "ABSENDER:" ist ein reines Label.
      // ("GIRO POST" kommt zweimal vor — Miete am 02.09., Kautionsrückzahlung am 25.09.)
      assertThat(onDate(txns, LocalDate.of(2019, 9, 2)).details())
          .containsExactly("Muster Immobilien AG", "MIETE SEPTEMBER 2019");
      assertThat(txns)
          .allSatisfy(t -> assertThat(t.details()).noneMatch(d -> d.startsWith("CH")));
      assertThat(txns).allSatisfy(t -> assertThat(t.details()).doesNotContain("ABSENDER:"));
    }

    @Test
    void pageFurniture_neverReachesTheCategorizationInput() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST));

      // Total-Zeile, Grussformel und Rechtshinweis stehen nach der letzten Buchung.
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.fullText())
                      .doesNotContain(
                          "Total", "Kontostand", "Freundliche", "genehmigt", "PostFinance AG"));
    }
  }

  @Nested
  class Ubs {

    @Test
    void extractsAllRows_andSumsMatchUmsatztotal() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      assertThat(txns).hasSize(28);
      // Gedruckte "Umsatztotal"-Zeile: Belastung 26'970.40 / Gutschrift 40'950.00.
      assertThat(sum(txns, false)).isEqualByComparingTo("26970.40");
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

    @Test
    void merchantTexts_areExtractedVerbatimForCategorization() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      // Händler-Buchungstexte müssen unverändert ankommen — Input für die Kategorisierung (US-05).
      assertThat(txns)
          .extracting(ParsedTransaction::buchungstext)
          .contains(
              "Kartenzahlung Migros Zuerich",
              "Kartenzahlung Coop Pronto",
              "Kartenzahlung SBB Billettautomat",
              "LSV Swisscom AG",
              "LSV CSS Kranken-Versicherung",
              "TWINT Kiosk Bahnhof");
    }

    @Test
    void pageBreak_doesNotAttachHeaderOfPage2ToLastBookingOfPage1() {
      List<ParsedTransaction> txns = parser.parse(bytes(UBS));

      // Der Auszug ist zweiseitig; Seite 2 beginnt erneut mit Adresse, IBAN und Spaltenkopf.
      // Ohne Seitenreset landete das alles in den details der letzten Buchung von Seite 1.
      assertThat(txns).allSatisfy(t -> assertThat(t.details()).isEmpty());
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.fullText())
                      .doesNotContain(
                          "UBS Switzerland",
                          "IBAN",
                          "Angezeigt",
                          "Seite",
                          "Umsatztotal",
                          "Anfangssaldo"));
    }
  }

  /**
   * Jahresauszug PostFinance: 240 Buchungen über zwölf Monate auf sieben Seiten.
   *
   * <p>Der 110-Buchungen-Auszug (Raiffeisen) beantwortet die Frage nach der <em>Länge</em>. Dieser
   * hier beantwortet die nach der <em>Mischung</em>: Er ist auf eine Lookup-Quote von 60% gebaut,
   * damit auch die Claude-Stufe der Hybrid-Kette spürbar Last bekommt (ADR-6). Die Quote selbst
   * hängt an den Flyway-Seeds und wird deshalb dort geprüft, wo eine Datenbank läuft — in {@link
   * PdfLookupCoverageIntegrationTest}. Hier steht, was ohne DB nachweisbar ist: Vollständigkeit,
   * Beträge, Richtungen und die Sauberkeit des Kategorisierungs-Inputs.
   */
  @Nested
  class PostFinanceJahresauszug {

    @Test
    void extractsAllRows_andSumsMatchPrintedTotalLine() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      assertThat(txns).hasSize(240);
      // Gedruckte "Total"-Zeile: Gutschrift 52 020.00 / Lastschrift 29 034.00.
      assertThat(sum(txns, true)).isEqualByComparingTo("52020.00");
      assertThat(sum(txns, false)).isEqualByComparingTo("29034.00");
    }

    @Test
    void printedClosingBalance_matchesTheChainOfAllTwoHundredForty() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      // Anfangs-Kontostand 8 450.00, gedruckter Schluss-Kontostand 31 436.00. Die Probe fasst
      // alle 240 Zeilen zu einer einzigen Zahl zusammen: Fehlt eine Buchung oder kippt eine
      // Richtung, geht sie nicht auf.
      BigDecimal expected = new BigDecimal("8450.00").add(sum(txns, true)).subtract(sum(txns, false));
      assertThat(expected).isEqualByComparingTo("31436.00");
    }

    @Test
    void statementSpansTheWholeCalendarYear() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      assertThat(txns).extracting(ParsedTransaction::buchungsdatum).allMatch(d -> d.getYear() == 2025);
      assertThat(txns)
          .extracting(t -> t.buchungsdatum().getMonthValue())
          .containsOnly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
      // Jeder Monat trägt gleich viele Buchungen — Voraussetzung dafür, dass der
      // Monatsvergleich (US-10) an dieser Fixture überhaupt etwas zu vergleichen hat.
      assertThat(txns)
          .filteredOn(t -> t.buchungsdatum().getMonthValue() == 2)
          .hasSize(20);
    }

    @Test
    void everyLookupCandidateSitsInADetailLine_notInTheBookingLine() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      // Die Eigenschaft, ohne die die 60%-Quote dieses Auszugs nichts über den Parser aussagt:
      // Kein Händlername steht in der Buchungszeile. Jeder Treffer muss den Weg durch
      // Kartennummer, IBAN, Anschrift und Label-Zeilen bis in fullText() überlebt haben.
      assertThat(txns)
          .extracting(ParsedTransaction::buchungstext)
          .containsOnly(
              "GIRO POST", "KAUF/DIENSTLEISTUNG", "LASTSCHRIFT", "TWINT", "ESR", "GOOGLE PAY",
              "KAUF/ONLINE-SHOPPING VOM", "BARBEZUG", "GUTSCHRIFT", "PREIS FÜR");
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.buchungstext())
                      .doesNotContain(
                          "MIGROS", "COOP", "DENNER", "ALDI", "LIDL", "SBB", "SWISSCOM", "CSS",
                          "NETFLIX", "SPOTIFY", "DIGITEC", "GALAXUS", "ZALANDO"));
      // Und die Detailzeilen tragen sie tatsächlich — hier über einen ganzen Monat geprüft.
      assertThat(txns)
          .filteredOn(t -> t.buchungsdatum().getMonthValue() == 1)
          .extracting(ParsedTransaction::fullText)
          .anySatisfy(t -> assertThat(t).contains("MIGROS M BERN WANKDORF"))
          .anySatisfy(t -> assertThat(t).contains("COOP-1234 BERN"))
          .anySatisfy(t -> assertThat(t).contains("SBB CFF FFS BERN"))
          .anySatisfy(t -> assertThat(t).contains("ALDI SUISSE BERN"));
    }

    @Test
    void recurringFixedCosts_repeatWithIdenticalAmountInEveryMonth() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      // Die Eigenschaft, an der US-08 (Abo-Erkennung) hängt: gleicher Text, gleicher Betrag,
      // zwölfmal. Alles andere im Auszug schwankt bewusst von Monat zu Monat.
      // Wiedererkannt wird über die DETAILZEILE, nicht die Buchungszeile — in ihr steht bei
      // allen fünf nur "LASTSCHRIFT" bzw. "GIRO POST".
      assertRecurringMonthly(txns, "CSS VERSICHERUNG AG", "320.50");
      assertRecurringMonthly(txns, "SWISSCOM (SCHWEIZ) AG", "65.00");
      assertRecurringMonthly(txns, "NETFLIX INTERNATIONAL BV", "20.90");
      assertRecurringMonthly(txns, "SPOTIFY AB", "12.95");
      assertRecurringMonthly(txns, "MUSTER IMMOBILIEN AG", "1250.00");
    }

    @Test
    void monthEndBlock_resolvesSalaryAndFeeFromSaldoDelta() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      // Am Monatsletzten stehen Lohngutschrift und Kontoführungsgebühr in EINEM Saldo-Block;
      // nur die Gebührenzeile trägt einen Saldo, und sie trägt kein eigenes Datum. Beide
      // Richtungen müssen zwölfmal unterschiedlich aufgelöst werden.
      assertThat(txns)
          .filteredOn(t -> t.details().stream().anyMatch(d -> d.startsWith("LOHN ")))
          .hasSize(12)
          .allSatisfy(
              t -> {
                assertThat(t.buchungstext()).isEqualTo("GUTSCHRIFT");
                assertThat(t.isIncome()).isTrue();
                assertThat(t.betrag()).isEqualByComparingTo("4250.00");
              });
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("PREIS FÜR"))
          .hasSize(12)
          .allSatisfy(
              t -> {
                assertThat(t.isIncome()).isFalse();
                assertThat(t.betrag()).isEqualByComparingTo("5.00");
              });
      // Die Gebührenzeile hat kein gedrucktes Datum: sie übernimmt das der Lohnzeile.
      assertThat(txns)
          .filteredOn(t -> t.buchungsdatum().equals(LocalDate.of(2025, 2, 28)))
          .extracting(ParsedTransaction::isIncome, ParsedTransaction::betrag)
          .containsExactlyInAnyOrder(
              Tuple.tuple(true, new BigDecimal("4250.00")),
              Tuple.tuple(false, new BigDecimal("5.00")));
    }

    @Test
    void singleRowCredits_areRecognisedAsIncome() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      // Drei Rückerstattungen stehen allein in ihrem Saldo-Block (Blockgrösse 1). Ohne sie
      // liefe die Richtungserkennung im ganzen Auszug nur über gemischte Blöcke.
      assertThat(txns)
          .filteredOn(t -> t.details().contains("STEUERVERWALTUNG KT. BERN"))
          .hasSize(3)
          .allSatisfy(
              t -> {
                assertThat(t.buchungstext()).isEqualTo("GUTSCHRIFT");
                assertThat(t.isIncome()).isTrue();
                assertThat(t.betrag()).isEqualByComparingTo("340.00");
                assertThat(t.details()).hasSize(2).last(as(STRING)).startsWith("RUECKERSTATTUNG");
              });
    }

    @Test
    void sevenPageBreaks_leaveNoHeaderOrFooterInTheCategorizationInput() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      // Sieben Seiten heissen sechs Kopf- und sieben Fusszeilen. Die Seitennummer steht im
      // extrahierten Text direkt hinter der letzten Buchung ihrer Seite und hing vor dem Filter
      // in DETAIL_NOISE an genau einer Buchung pro Seite.
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.fullText())
                      .doesNotContain(
                          "Seite",
                          "Übertrag",
                          "Privatkonto",
                          "IBAN",
                          "Kontoauszug",
                          "Kontostand",
                          "Total",
                          "PostFinance"));
    }

    @Test
    void transferBookings_carryPayeeInDetails() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JAHR));

      // "ESR" und "GIRO POST" sagen für sich genommen nichts — ohne die Detailzeile fielen sie
      // in beiden Stufen der Kette auf Sonstiges (ADR-6).
      assertThat(txns)
          .filteredOn(t -> t.buchungstext().equals("ESR"))
          .hasSize(12)
          .allSatisfy(t -> assertThat(t.details()).containsExactly("STADTWERKE BERN"));
      assertThat(onDate(txns, LocalDate.of(2025, 1, 1)).fullText())
          .isEqualTo("GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025");
    }

    private void assertRecurringMonthly(
        List<ParsedTransaction> txns, String detailLine, String betrag) {
      assertThat(txns)
          .filteredOn(t -> t.details().contains(detailLine))
          .as("wiederkehrende Buchung '%s'", detailLine)
          .hasSize(12)
          .allSatisfy(t -> assertThat(t.betrag()).isEqualByComparingTo(betrag))
          .extracting(t -> t.buchungsdatum().getMonthValue())
          .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }
  }

  /**
   * Der layouttreue PostFinance-Auszug: 20 Buchungen, Juli 2026, drei Seiten.
   *
   * <p>Nachgezogen an einem realen Auszug. Er misst weder Menge noch Lookup-Quote — dafür gibt es
   * den 240er-Jahresauszug — sondern hält das <em>Satzbild</em> fest, an dem die beiden anderen
   * PostFinance-Fixtures vorbeigehen: Der Buchungstext trägt die <b>Zahlungsart</b>, der Händler
   * steht in den Detailzeilen darunter, verschüttet unter Kartennummer, IBAN, Anschrift und
   * Label-Zeilen.
   *
   * <p>Das ist kein kosmetischer Unterschied. In dieser Form hängt jeder Lookup-Treffer und jeder
   * brauchbare Claude-Prompt daran, dass die sprechende Detailzeile den Weg durch das Rauschen
   * und durch {@code MAX_DETAIL_LINES} überlebt — die Eigenschaft, die die anderen Fixtures
   * gar nicht erst auf die Probe stellen.
   */
  @Nested
  class PostFinanceJuli {

    @Test
    void extractsAllRows_andSumsMatchPrintedTotalLine() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      assertThat(txns).hasSize(20);
      // Gedruckte "Total"-Zeile: Gutschrift 4 430.00 / Lastschrift 2 140.55.
      assertThat(sum(txns, true)).isEqualByComparingTo("4430.00");
      assertThat(sum(txns, false)).isEqualByComparingTo("2140.55");
      // Anfangs-Kontostand 2 480.00, gedruckter Schluss-Kontostand 4 769.45.
      assertThat(new BigDecimal("2480.00").add(sum(txns, true)).subtract(sum(txns, false)))
          .isEqualByComparingTo("4769.45");
    }

    @Test
    void bookingTextCarriesThePaymentType_neverTheMerchant() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      // Die definierende Eigenschaft dieses Layouts — und der Punkt, an dem die anderen
      // PostFinance-Fixtures danebenliegen.
      assertThat(txns)
          .extracting(ParsedTransaction::buchungstext)
          .containsOnly(
              "KONTOÜBERTRAG AUF", "GOOGLE PAY", "LASTSCHRIFT", "KAUF/ONLINE-SHOPPING VOM",
              "TWINT", "GUTSCHRIFT", "KAUF/DIENSTLEISTUNG", "ESR", "PREIS FÜR");
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.buchungstext())
                      .doesNotContain("MIGROS", "COOP", "ZALANDO", "SWISSCOM", "CSS", "DIGITEC"));
    }

    @Test
    void merchantReachesTheCategorizationInput_onlyThroughTheDetailLines() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      // Kartenzahlung: Der Händler steht im PDF auf der VIERTEN Detailzeile, hinter Label,
      // Datum und maskierter Kartennummer.
      assertThat(onDate(txns, LocalDate.of(2026, 7, 4)).fullText())
          .contains("MIGROS M BERN WANKDORF")
          .doesNotContain("XXXX");
      // Online-Kauf: der längste Block des Layouts (acht Zeilen) — übrig bleibt der Händler.
      assertThat(onDate(txns, LocalDate.of(2026, 7, 11)).details()).containsExactly("ZALANDO SE");
      // Lastschrift mit Firmenanschrift: Strasse und Ort fallen weg, der Name bleibt.
      assertThat(onDate(txns, LocalDate.of(2026, 7, 24)).details())
          .containsExactly("CSS VERSICHERUNG AG");
    }

    @Test
    void purposeLineSurvivesTheLabelsAboveIt() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      // Dauerauftrag: Auftragsnummer, IBAN und "SENDER REFERENZ:" belegten vorher die drei
      // Plätze — der Zweck fiel weg. Vier Buchungen teilen diesen Block.
      assertThat(txns)
          .filteredOn(t -> t.details().contains("SACKGELD LEA"))
          .hasSize(4)
          .allSatisfy(t -> assertThat(t.details()).containsExactly("MUSTER, LEA", "SACKGELD LEA"));
      // Gutschrift: Absenderanschrift raus, Zweck hinter "MITTEILUNGEN:" rein.
      assertThat(onDate(txns, LocalDate.of(2026, 7, 18)).details())
          .containsExactly("MUSTER, ANNA", "RUECKZAHLUNG FERIENKASSE");
    }

    @Test
    void wrappedMessageText_isKeptAsPrinted() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      // PostFinance bricht das Mitteilungsfeld mitten im Wort um. Der Parser fügt nichts
      // zusammen — beide Bruchstücke landen als eigene Detailzeilen im Prompt.
      assertThat(onDate(txns, LocalDate.of(2026, 7, 28)).details())
          .containsExactly("MUSTER CONSULTING GMBH", "LOHN JULI 2026 SOWIE SPE", "SENVERGUETUNG");
    }

    @Test
    void zeroAmountFee_isParsed_andLeavesItsBlockPartnerResolvable() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      // Der letzte Tagesblock: 18.40 ohne Saldo, abgeschlossen von der kostenlosen
      // Gebührenzeile. Vor der Sonderbehandlung von 0.00 galt der ganze Block als mehrdeutig.
      assertThat(txns)
          .filteredOn(t -> t.buchungsdatum().equals(LocalDate.of(2026, 7, 31)))
          .extracting(ParsedTransaction::buchungstext, ParsedTransaction::betrag,
              ParsedTransaction::isIncome)
          .containsExactlyInAnyOrder(
              Tuple.tuple("KAUF/DIENSTLEISTUNG", new BigDecimal("18.40"), false),
              Tuple.tuple("PREIS FÜR", new BigDecimal("0.00"), false));
    }

    @Test
    void valutaDifferentFromBookingDate_doesNotShiftTheBooking() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      // Bei Kartenzahlungen liegt die Valuta vor dem Buchungsdatum. Gebucht wird auf das
      // Buchungsdatum — an ihm hängt die Monatszuordnung (US-12).
      assertThat(onDate(txns, LocalDate.of(2026, 7, 22)).details())
          .containsExactly("DIGITEC GALAXUS AG", "ZUERICH (CH)");
      assertThat(txns).extracting(ParsedTransaction::buchungsdatum)
          .allMatch(d -> d.getMonthValue() == 7 && d.getYear() == 2026);
    }

    @Test
    void threePagesOfFurniture_neverReachTheCategorizationInput() {
      List<ParsedTransaction> txns = parser.parse(bytes(POST_JULI));

      // Drei Seiten heissen zwei Kopfwiederholungen und drei Fusszeilen — letztere inklusive
      // der Drucksteuerzeile, die ein betragsähnliches Token trägt.
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.fullText())
                      .doesNotContain(
                          "Seite", "00656", "IBAN", "CH11", "CH44", "CH77", "CH88",
                          "Kontonummer", "Kontostand", "Total", "PostFinance",
                          "ABSENDER", "MITTEILUNGEN", "SENDER REFERENZ", "PAYMENT ID",
                          "BESTELLNUMMER", "N/A", "XXXX", "DAUERAUFTRAG"));
      // Und keine Postanschrift einer Gegenpartei (BE-PDF-06).
      assertThat(txns)
          .allSatisfy(
              t ->
                  assertThat(t.fullText())
                      .doesNotContain("BAHNHOFSTRASSE", "MUSTERWEG", "TRIBSCHENSTRASSE",
                          "8000 ZUERICH", "6002 LUZERN", "3050 BERN"));
    }
  }

  // --- helpers ----------------------------------------------------------------------------------

  /** Die eindeutige Transaktion mit diesem Buchungstext — schlägt fehl, wenn es nicht genau eine gibt. */
  private static ParsedTransaction byText(List<ParsedTransaction> txns, String buchungstext) {
    List<ParsedTransaction> matches =
        txns.stream().filter(t -> t.buchungstext().equals(buchungstext)).toList();
    assertThat(matches).as("Buchung '%s'", buchungstext).hasSize(1);
    return matches.getFirst();
  }

  /** Die eindeutige Transaktion an diesem Datum — für Buchungstexte, die mehrfach vorkommen. */
  private static ParsedTransaction onDate(List<ParsedTransaction> txns, LocalDate date) {
    List<ParsedTransaction> matches =
        txns.stream().filter(t -> t.buchungsdatum().equals(date)).toList();
    assertThat(matches).as("Buchung am %s", date).hasSize(1);
    return matches.getFirst();
  }

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
