package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

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
  void parse_continuationLine_landsInDetailsNotInBuchungstext() {
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 ONLINE SHOP BESTELLUNG 20.00 480.00",
                "REF NR 123456 ABCDEF"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t -> {
              // buchungstext bleibt die Buchungszeile selbst — die Fortsetzungszeile daneben.
              assertThat(t.buchungstext()).isEqualTo("ONLINE SHOP BESTELLUNG");
              assertThat(t.details()).containsExactly("REF NR 123456 ABCDEF");
              // Die Kategorisierung bekommt beides.
              assertThat(t.fullText()).isEqualTo("ONLINE SHOP BESTELLUNG REF NR 123456 ABCDEF");
            });
  }

  @Test
  void parse_singleLineAddress_isDiscardedButLowercaseChInPurposeSurvives() {
    // BE-PDF-13: Die kombinierte Adressform (Strasse+PLZ+Ort bzw. Firmenanschrift mit
    // Länderkürzel CH) verschwindet aus den Detailzeilen. Das CH ist case-sensitiv (?-i:CH) —
    // ein kleingeschriebenes "ch" mitten in einer Zweckzeile darf keine Adresse vortäuschen und
    // die Zeile nicht mitreissen.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 ZAHLUNG MUSTER GMBH 20.00 480.00",
                "Bahnhofstrasse 1 CH 8000 Zürich",
                "zahlung via ch 1234 aktion"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t -> {
              // Die CH-Firmenanschrift ist raus.
              assertThat(t.details()).doesNotContain("Bahnhofstrasse 1 CH 8000 Zürich");
              assertThat(t.fullText()).doesNotContain("Bahnhofstrasse", "8000 Zürich");
              // Die Zweckzeile mit kleinem "ch" bleibt — sie ist keine Adresse.
              assertThat(t.details()).contains("zahlung via ch 1234 aktion");
            });
  }

  @Test
  void parse_pageFurniture_isNotAttachedToBooking() {
    // Summen-, Gruss- und Rechtszeilen nach der letzten Buchung dürfen weder im buchungstext
    // noch in den details landen — sie wären sonst Input für die Kategorisierung.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 MIGROS MMM BERN 20.00 480.00",
                "Filiale Marktgasse",
                "Total 20.00 480.00",
                "Freundliche Gruesse",
                "Bitte pruefen Sie den Kontoauszug. Ohne Gegenbericht innert 30 Tagen genehmigt.",
                "Seite 1/1"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t -> {
              assertThat(t.details()).containsExactly("Filiale Marktgasse");
              assertThat(t.fullText())
                  .doesNotContain("Freundliche", "Gegenbericht", "Seite", "Total");
            });
  }

  @Test
  void parse_pageNumberFooter_isNotAttachedToBooking_evenWithoutATotalsLine() {
    // Der Fall, den parse_pageFurniture_isNotAttachedToBooking NICHT abdeckt: Auf jeder Seite
    // ausser der letzten steht die Seitennummer direkt hinter der letzten Buchung, ohne dass
    // eine Total-Zeile den Buchungsteil vorher schliesst. Sie ist kurz, hat kein Datum und
    // keinen Betrag — ohne eigenen Filter zählt sie als Detailzeile und hängt an genau einer
    // Buchung pro Seite im Kategorisierungs-Input. Aufgefallen an der 240-Buchungen-Fixture.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 MIGROS MMM BERN 20.00 480.00",
                "Filiale Marktgasse",
                "Seite 1/4"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t -> {
              assertThat(t.details()).containsExactly("Filiale Marktgasse");
              assertThat(t.fullText()).isEqualTo("MIGROS MMM BERN Filiale Marktgasse");
            });
  }

  @Test
  void parse_pageNumberFooter_isFilteredInItsCommonSpellings() {
    // PostFinance druckt "Seite: 1 / 7", Raiffeisen "Seite 1/4", andere "Seite 1 von 4".
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 MIGROS MMM BERN 20.00 480.00",
                "Seite: 1 / 7",
                "Seite 2 von 4"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(t -> assertThat(t.details()).isEmpty());
  }

  @Test
  void parse_maskedCardNumber_isNotADetail_inPostFinanceSpelling() {
    // PostFinance druckt "KARTEN NR. XXXX4417" — ohne Wortgrenze zwischen X und Ziffer. Die
    // frühere Fassung des Filters (\bXXXX\b) traf nur Visecas "… XXXX 5446" und liess die
    // Kartennummer jeder PostFinance-Kartenzahlung in den Prompt.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 GOOGLE PAY 20.00 480.00",
                "KARTEN NR. XXXX4417",
                "MIGROS M BERN WANKDORF"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(t -> assertThat(t.details()).containsExactly("MIGROS M BERN WANKDORF"));
  }

  @Test
  void parse_labelLine_isDropped_soItsValueGetsTheSlot() {
    // Der Kern des Abschneide-Problems: Auftragsnummer, IBAN und das Label "SENDER REFERENZ:"
    // belegten die drei Plätze, und der Verwendungszweck darunter — die einzige Zeile mit
    // Kategorisierungswert — fiel weg.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 LASTSCHRIFT 20.00 480.00",
                "DAUERAUFTRAG: 90-11223344",
                "CH7709000000850055555",
                "MUSTER, LEA",
                "SENDER REFERENZ:",
                "SACKGELD LEA"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(t -> assertThat(t.details()).containsExactly("MUSTER, LEA", "SACKGELD LEA"));
  }

  @Test
  void parse_counterpartyAddress_isDropped_butTheNameSurvives() {
    // Strasse und Ort tragen nichts zur Kategorie bei, belegen aber zwei der drei Plätze — und
    // sind das Personendatum, das am wenigsten im Claude-Prompt zu suchen hat (BE-PDF-06).
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 GUTSCHRIFT 20.00 520.00",
                "ABSENDER:",
                "MUSTER CONSULTING GMBH",
                "BAHNHOFSTRASSE 1",
                "8000 ZUERICH",
                "MITTEILUNGEN:",
                "LOHN APRIL 2024"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(
            t ->
                assertThat(t.details())
                    .containsExactly("MUSTER CONSULTING GMBH", "LOHN APRIL 2024"));
  }

  @Test
  void parse_purposeLineEndingInAYear_isNotMistakenForAnAddress() {
    // Gegenprobe zum Adressfilter: Eine gewöhnliche Zweckzeile mit Jahreszahl darf nicht
    // herausfallen.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 LASTSCHRIFT 20.00 480.00",
                "RECHNUNG 2024"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(t -> assertThat(t.details()).containsExactly("RECHNUNG 2024"));
  }

  @Test
  void parse_opaqueReference_isNotADetail_butAMerchantNameIs() {
    // Nach dem Label-Filter würde der WERT unter "PAYMENT ID" den Platz belegen, den vorher das
    // Label belegte — für die Kategorisierung derselbe Nullwert.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 KAUF/ONLINE-SHOPPING VOM 20.00 480.00",
                "ZALANDO SE",
                "N/A",
                "PAYMENT ID",
                "250704111222333444AB",
                "BESTELLNUMMER",
                "C040725R010A"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(t -> assertThat(t.details()).containsExactly("ZALANDO SE"));
  }

  @Test
  void parse_zeroAmountBooking_doesNotMakeItsBlockAmbiguous() {
    // Eine kostenlose Gebührenzeile (0.00) verschiebt den Saldo nicht und hat damit keine
    // bestimmbare Richtung: +0.00 und -0.00 lösen beide auf. Ohne Sonderbehandlung gälte der
    // GANZE Block als mehrdeutig und auch die 20.00 daneben verlöre ihre Richtung — auf echten
    // PostFinance-Auszügen steht so eine Zeile am Monatsende.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 100.00",
                "30.09.19 GUTSCHRIFT 20.00 30.09.19",
                "PREIS FÜR 0.00 30.09.19 120.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .extracting(
            ParsedTransaction::buchungstext,
            ParsedTransaction::isIncome,
            ParsedTransaction::directionUncertain)
        .containsExactly(tuple("GUTSCHRIFT", true, false), tuple("PREIS FÜR", false, false));
  }

  @Test
  void parse_detailsAreCapped_soRunawayTextCannotFloodTheCategorizationInput() {
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Saldovortrag 500.00",
                "10.04.2024 10.04.2024 ONLINE SHOP BESTELLUNG 20.00 480.00",
                "Zeile A",
                "Zeile B",
                "Zeile C",
                "Zeile D"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions).singleElement()
        .satisfies(t -> assertThat(t.details()).containsExactly("Zeile A", "Zeile B", "Zeile C"));
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
  void parse_ambiguousPostFinanceBlock_defaultsAllToDebitAndMarksThemUncertain() {
    // AC4 (BE-PDF-10): gemischter Block am selben Tag, zu dessen Delta mehrere Kombinationen
    // passen. Delta 0 mit zwei gleichen Beträgen: +50-50 und -50+50 sind beide gültig. Eine
    // willkürliche Zuweisung wäre potenziell falsch — der Parser fällt auf Belastung zurück,
    // warnt, UND markiert beide Buchungen zur Prüfung durch den Nutzer. Ohne die Markierung wäre
    // eine darunter versteckte Gutschrift im Betrag doppelt falsch und Safe-to-Spend zu tief.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 100.00",
                "05.09.19 BUCHUNG A 50.00 05.09.19",
                "BUCHUNG B 50.00 05.09.19 100.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .hasSize(2)
        .allSatisfy(
            t -> {
              assertThat(t.isIncome()).isFalse();
              assertThat(t.directionUncertain()).isTrue();
            });
  }

  @Test
  void parse_partiallyAmbiguousPostFinanceBlock_keepsTheBookingAllSolutionsAgreeOn() {
    // BE-PDF-10: Passen mehrere Kombinationen, sind sie sich über einzelne Buchungen oft trotzdem
    // einig. 100/50/50 bei Delta -100 lösen -100+50-50 und -100-50+50 beide auf: Die 100 ist in
    // JEDER Lösung eine Belastung und damit eindeutig bestimmt; offen sind nur die beiden 50.
    // Der frühere Code verwarf den ganzen Block und hätte alle drei markiert.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 1'000.00",
                "05.09.19 BUCHUNG A 100.00 05.09.19",
                "BUCHUNG B 50.00 05.09.19",
                "BUCHUNG C 50.00 05.09.19 900.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .extracting(
            ParsedTransaction::buchungstext,
            ParsedTransaction::isIncome,
            ParsedTransaction::directionUncertain)
        .containsExactly(
            tuple("BUCHUNG A", false, false),
            tuple("BUCHUNG B", false, true),
            tuple("BUCHUNG C", false, true));
  }

  @Test
  void parse_unresolvablePostFinanceDelta_marksEveryBookingOfTheBlockUncertain() {
    // Keine Kombination trifft das Delta: ±50±30 liefert 80, 20, -20 oder -80, nie 0. Der Block
    // ist damit nicht auflösbar — anderer Weg in denselben Fallback, gleiche Markierung.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 100.00",
                "05.09.19 BUCHUNG A 50.00 05.09.19",
                "BUCHUNG B 30.00 05.09.19 100.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .hasSize(2)
        .allSatisfy(t -> assertThat(t.directionUncertain()).isTrue());
  }

  @Test
  void parse_oversizedPostFinanceBlock_marksEveryBookingUncertainInsteadOfTrying() {
    // Über 16 Buchungen im Block wird die Kombinatorik gar nicht erst versucht (2^n). Auch dieser
    // Weg endet bei Belastung — der Nutzer erfährt jetzt davon.
    List<String> lines = new java.util.ArrayList<>();
    lines.add("PostFinance AG");
    lines.add("01.09.19 Kontostand 1'000.00");
    lines.add("05.09.19 BUCHUNG 01 10.00 05.09.19");
    for (int i = 2; i <= 16; i++) {
      lines.add(String.format("BUCHUNG %02d 10.00 05.09.19", i));
    }
    lines.add("BUCHUNG 17 10.00 05.09.19 830.00");

    List<ParsedTransaction> transactions = parser.parse(pdfWithLines(lines));

    assertThat(transactions)
        .hasSize(17)
        .allSatisfy(t -> assertThat(t.directionUncertain()).isTrue());
  }

  @Test
  void parse_postFinanceBookingsBeforeFirstBalance_areMarkedUncertain() {
    // Buchungen vor der ersten Kontostand-Zeile: Der Saldo am Ende des Blocks ist bekannt, der
    // davor nicht — ohne beide Enden gibt es kein Delta. Dieser Zweig kehrte vor BE-PDF-10 stumm
    // zurück und war der einzige Fallback ganz ohne Spur.
    byte[] pdf =
        pdfWithLines(
            List.of("PostFinance AG", "05.09.19 BUCHUNG OHNE ANKER 50.00 05.09.19 950.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.isIncome()).isFalse();
              assertThat(t.directionUncertain()).isTrue();
            });
  }

  @Test
  void parse_postFinanceBookingsWithoutClosingBalance_areMarkedUncertain() {
    // Der letzte Block bricht am Ende des Auszugs ab, bevor eine Saldo-Zeile ihn schliesst. Die
    // Buchung davor hat ihren Saldo und bleibt eindeutig — nur die abgeschnittene wird markiert.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 100.00",
                "05.09.19 MIT SALDO 50.00 05.09.19 50.00",
                "06.09.19 OHNE SALDO 30.00 06.09.19"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .extracting(ParsedTransaction::buchungstext, ParsedTransaction::directionUncertain)
        .containsExactly(tuple("MIT SALDO", false), tuple("OHNE SALDO", true));
  }

  @Test
  void parse_statementWithoutSaldovortrag_marksOnlyTheFirstBookingUncertain() {
    // Raiffeisen/generisches Layout ohne Saldovortrag-Zeile: Der ersten Buchung fehlt der
    // Vorgänger-Saldo als Anker. Ab der zweiten ist der Saldo der Vorzeile der Anker — sie ist
    // wieder eindeutig und darf nicht mitmarkiert werden.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "01.03.2024 01.03.2024 MIGROS MMM BERN 45.60 954.40",
                "05.03.2024 05.03.2024 SWISSCOM AG RECHNUNG 89.00 865.40"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .extracting(ParsedTransaction::buchungstext, ParsedTransaction::directionUncertain)
        .containsExactly(tuple("MIGROS MMM BERN", true), tuple("SWISSCOM AG RECHNUNG", false));
  }

  @Test
  void parse_ubsStatementWithoutAnfangssaldo_marksOnlyTheOldestBookingUncertain() {
    // UBS ist absteigend sortiert, gerechnet wird von unten. Ohne Anfangssaldo-Zeile fehlt der
    // ältesten (untersten) Buchung der Vorgänger-Saldo; alle darüber haben ihn.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "UBS Switzerland AG",
                "Kontobewegungen",
                "05.03.2024 NEUERE BUCHUNG 40.00 05.03.2024 860.00",
                "01.03.2024 AELTESTE BUCHUNG 100.00 01.03.2024 900.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .extracting(ParsedTransaction::buchungstext, ParsedTransaction::directionUncertain)
        .containsExactly(tuple("NEUERE BUCHUNG", false), tuple("AELTESTE BUCHUNG", true));
  }

  @Test
  void parse_zeroAmountBookingInAmbiguousBlock_isNeverMarkedUncertain() {
    // Eine 0.00-Zeile hat keine bestimmbare Richtung, aber ihr Vorzeichen ist folgenlos: Sie
    // verschiebt weder Saldo noch Safe-to-Spend. Sie zu markieren erzeugte eine Rückfrage an den
    // Nutzer, deren Beantwortung nichts ändert — die beiden 50 daneben bleiben unsicher.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 100.00",
                "05.09.19 BUCHUNG A 50.00 05.09.19",
                "BUCHUNG B 50.00 05.09.19",
                "PREIS FÜR 0.00 05.09.19 100.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .extracting(ParsedTransaction::buchungstext, ParsedTransaction::directionUncertain)
        .containsExactly(
            tuple("BUCHUNG A", true), tuple("BUCHUNG B", true), tuple("PREIS FÜR", false));
  }

  @Test
  void parse_resolvedPostFinanceBlock_leavesNothingMarked() {
    // Gegenprobe zu allen Markierungs-Tests: Ein eindeutig auflösbarer gemischter Block darf
    // keine einzige Buchung zur Prüfung stellen, sonst wäre die Prüfliste wertlos.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "01.09.19 Kontostand 100.00",
                "05.09.19 GUTSCHRIFT 200.00 05.09.19",
                "BELASTUNG 50.00 05.09.19 250.00"));

    List<ParsedTransaction> transactions = parser.parse(pdf);

    assertThat(transactions)
        .extracting(
            ParsedTransaction::buchungstext,
            ParsedTransaction::isIncome,
            ParsedTransaction::directionUncertain)
        .containsExactly(tuple("GUTSCHRIFT", true, false), tuple("BELASTUNG", false, false));
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

  @Test
  void parse_textWithoutRecognizableBooking_throwsUnsupportedStatementFormatException() {
    // BE-PDF-04 (#83): Text vorhanden, aber kein Layout-Regex greift (hier: ISO-Datumsformat
    // statt dd.MM.yyyy). Vorher kam still eine leere Liste zurück — für die Nutzerin sah das
    // wie "Upload erfolgreich, 0 Transaktionen" aus.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Musterbank AG - Kontoauszug Maerz 2024",
                "Datum Beschreibung Betrag Saldo",
                "2024-03-01 MIGROS MMM BERN 45.60 954.40",
                "2024-03-05 SWISSCOM AG RECHNUNG 89.00 865.40"));

    assertThatThrownBy(() -> parser.parse(pdf))
        .isInstanceOf(UnsupportedStatementFormatException.class)
        // Subtyp von PdfParseException: bestehende Aufrufer-Verträge bleiben gültig.
        .isInstanceOf(PdfParseException.class);
  }

  @Test
  void parse_genericStatementWithSaldovortragButNoBookings_returnsEmptyList() {
    // BE-PDF-05 (#95): Format erkannt (Saldovortrag-Zeile), aber keine einzige Buchung —
    // ein Konto ohne Bewegung im Monat. Das ist ein Erfolg mit 0 Transaktionen, kein
    // nicht unterstütztes Format.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Kontoauszug Maerz 2024",
                "Saldovortrag 1'000.00",
                "Schlusssaldo 1'000.00"));

    assertThat(parser.parse(pdf)).isEmpty();
  }

  @Test
  void parse_genericStatementWithAnfangssaldoButNoBookings_returnsEmptyList() {
    // BE-PDF-05: auch eine Anfangssaldo-Zeile ist ein positives Format-Signal.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "Kontoauszug April 2024",
                "Anfangssaldo 250.00",
                "Schlusssaldo 250.00"));

    assertThat(parser.parse(pdf)).isEmpty();
  }

  @Test
  void parse_headerSignatureStatementWithoutBookings_returnsEmptyList() {
    // BE-PDF-05: Kopfzeilen-Signatur (PostFinance) erkannt, keine Buchungen — kein Fehler.
    byte[] pdf =
        pdfWithLines(
            List.of(
                "PostFinance AG",
                "Kontoauszug Maerz 2024",
                "Kontostand 1'000.00"));

    assertThat(parser.parse(pdf)).isEmpty();
  }

  @Test
  void parse_pdfWithoutTextLayer_throwsMissingTextLayerException() {
    // Gescanntes PDF: Seite vorhanden, aber kein extrahierbarer Text. Muss vom unbekannten
    // Layout unterscheidbar sein — die Nutzermeldung ist eine andere ("bitte aus dem
    // E-Banking herunterladen statt scannen").
    byte[] pdf = pdfWithLines(List.of());

    assertThatThrownBy(() -> parser.parse(pdf))
        .isInstanceOf(MissingTextLayerException.class)
        .isInstanceOf(PdfParseException.class)
        .isNotInstanceOf(UnsupportedStatementFormatException.class);
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
