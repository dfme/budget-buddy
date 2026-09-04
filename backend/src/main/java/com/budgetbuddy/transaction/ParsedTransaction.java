package com.budgetbuddy.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Eine aus einem Bank-PDF extrahierte Transaktionszeile (BE-PDF-01).
 *
 * <p>Bewusst entkoppelt von der JPA-{@code Transaction}-Entity (BE-PDF-02): der Parser kennt keine
 * Persistenz und keinen User. Das Mapping auf die Entity erfolgt im PdfImportService.
 *
 * @param buchungsdatum Buchungsdatum der Transaktion.
 * @param buchungstext Text der Buchungszeile selbst. Bei Kartenzahlungen ist das bereits der
 *     Händler ({@code "Kartenzahlung Migros Zuerich"}), bei Überweisungen dagegen nur der
 *     Buchungs<em>typ</em> ({@code "ESR"}, {@code "GIRO POST"}) — der Empfänger steht dann in
 *     {@code details}.
 * @param details Die im PDF unter der Buchung eingerückten Fortsetzungszeilen: Empfänger, Referenz,
 *     Ort, bei Viseca die gedruckte Händlerkategorie. Bewusst getrennt gehalten statt an
 *     {@code buchungstext} angehängt — Konkatenieren wäre irreversibel, und US-13 (Anzeige des
 *     Empfängers) sowie US-08 (Abo-Erkennung über Monate) brauchen die Trennung. Nie {@code null},
 *     evtl. leer.
 * @param betrag Betrag als positiver {@link BigDecimal} (Magnitude, Skala 2) — niemals
 *     {@code double}/{@code float} (ADR-9). Die Richtung steht in {@code isIncome}.
 * @param isIncome {@code true} für Gutschriften (Einkommen), {@code false} für Belastungen.
 * @param directionUncertain {@code true}, wenn {@code isIncome} eine Annahme ist und kein Befund
 *     (BE-PDF-10). Der Parser leitet die Richtung aus dem Saldo ab; gelingt das nicht, übernimmt
 *     er die Buchung konservativ als Belastung und setzt dieses Flag. Es sagt nichts über den
 *     Betrag aus — nur, dass sein Vorzeichen ungeprüft ist.
 */
public record ParsedTransaction(
    LocalDate buchungsdatum,
    String buchungstext,
    List<String> details,
    BigDecimal betrag,
    boolean isIncome,
    boolean directionUncertain) {

  public ParsedTransaction {
    details = details == null ? List.of() : List.copyOf(details);
  }

  /**
   * Eine Transaktion mit gesicherter Richtung — {@code directionUncertain = false}.
   *
   * <p>Der Normalfall: Layouts mit einem Saldo je Zeile (UBS, Raiffeisen) und Viseca, wo ein
   * nachgestelltes {@code -} die Gutschrift explizit markiert, raten nie. Nur der
   * PostFinance-Pfad und die beiden Auszüge ohne Anfangssaldo kommen überhaupt in die Lage, das
   * Flag zu setzen.
   */
  public ParsedTransaction(
      LocalDate buchungsdatum,
      String buchungstext,
      List<String> details,
      BigDecimal betrag,
      boolean isIncome) {
    this(buchungsdatum, buchungstext, details, betrag, isIncome, false);
  }

  /**
   * Buchungstext und Detailzeilen als ein String — der Input für beide Stufen der
   * Hybrid-Kategorisierung (ADR-6).
   *
   * <p>Beide Stufen brauchen den vollen Kontext: der Lookup matcht Händler-Pattern per
   * {@code contains} (in {@code "ESR"} steckt keines, in {@code "ESR Stadtwerke Bern"} schon), und
   * der Claude-Prompt besteht aus genau diesem einen String. Ohne die Detailzeilen liefern beide
   * Stufen bei Überweisungen {@code Sonstiges}.
   */
  public String fullText() {
    return details.isEmpty() ? buchungstext : buchungstext + " " + String.join(" ", details);
  }

  /**
   * Die Detailzeilen als ein persistierbarer String, oder {@code null}, wenn es keine gibt
   * (BE-PDF-07).
   *
   * <p>Gegenstück zu {@link #fullText()}: dort geht es um den Input der Kategorisierung, hier um
   * das, was in {@code transactions.buchungsdetails} landet. Deshalb <em>ohne</em> den
   * Buchungstext — der steht in seiner eigenen Spalte und wäre dort ein Duplikat.
   *
   * <p>Verbunden mit {@code \n} statt einem Leerzeichen: Detailzeilen enthalten
   * konstruktionsbedingt keinen Zeilenumbruch, die Trennung überlebt das Persistieren damit
   * verlustfrei. Das ist genau die Eigenschaft, die das Javadoc dieses Records für US-08
   * (Abo-Erkennung) verlangt — ein Leerzeichen wäre die irreversible Konkatenation, die dort
   * ausgeschlossen wird.
   *
   * <p>{@code null} und nicht der Leerstring: In der Datenbank hält das «diese Buchung hatte keine
   * Detailzeilen» von «vor BE-PDF-07 importiert, deshalb leer» getrennt. Ein Leerstring könnte
   * beides bedeuten.
   */
  public String detailsAsText() {
    return details.isEmpty() ? null : String.join("\n", details);
  }
}
