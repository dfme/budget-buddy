/**
 * Eine einzelne Transaktion (Spiegel des Backend-DTOs `TransactionResponse`).
 *
 * <p>Geliefert als Teil einer {@link TransactionPage} von `GET /api/transactions?month=YYYY-MM` und
 * zurückgegeben von `PUT /api/transactions/{id}/category`.
 *
 * <p>`betrag` kommt als JSON-Zahl über die REST-Grenze — dieselbe Begründung wie bei
 * {@link CategorySummaryItem}: das Backend nutzt `BigDecimal`, serialisiert aber ohne
 * String-Serializer.
 */
export interface Transaction {
  /** Datenbank-ID — Adresse für `PUT /api/transactions/{id}/category`. */
  id: number;
  /** Buchungsdatum als ISO-Datum, z. B. `"2026-07-20"`. */
  buchungsdatum: string;
  /**
   * Buchungstext des Kontoauszugs. Bei Kartenzahlungen der Händler, bei allen anderen
   * Buchungsarten nur die Zahlungsart — PostFinance schreibt hier `"LASTSCHRIFT"`, `"TWINT"` oder
   * `"GIRO POST"`. Wer die Gegenpartei sucht, findet sie in {@link buchungsdetails}.
   */
  buchungstext: string;
  /**
   * Gegenpartei und Verwendungszweck aus den Detailzeilen des Auszugs, mit `\n` verbunden
   * (BE-PDF-07) — z. B. `"MUSTER, LEA\nSACKGELD LEA"`.
   *
   * <p>`null` bedeutet zweierlei und lässt sich nicht auflösen: Die Buchung hatte keine
   * Detailzeilen, oder sie wurde vor BE-PDF-07 importiert. Ein Backfill ist ausgeschlossen, weil
   * die Zeilen nur im Quell-PDF stehen. Die Anzeige lässt die zweite Zeile in beiden Fällen weg —
   * ein Platzhalter behauptete, es gebe keine Gegenpartei.
   */
  buchungsdetails: string | null;
  /** Positive Magnitude in CHF; die Richtung steht in {@link income}. */
  betrag: number;
  /** `true` = Gutschrift, `false` = Belastung. In der Kategorie-Übersicht immer `false`. */
  income: boolean;
  /**
   * `true`, wenn {@link income} eine Annahme des PDF-Parsers ist und kein Befund (BE-PDF-10).
   *
   * <p>Der Parser leitet die Richtung aus dem Saldo ab. Wo das nicht gelingt — mehrdeutiges
   * Saldo-Delta, fehlender Anfangssaldo, zu grosser Buchungsblock —, übernimmt er die Buchung
   * konservativ als Belastung. Ist in Wahrheit eine Gutschrift darunter, ist ihr Vorzeichen
   * gedreht und Safe-to-Spend fällt zu tief aus.
   *
   * <p>Die Anzeige markiert solche Buchungen und lässt die Richtung über
   * `PUT /api/transactions/{id}/direction` korrigieren. Nach der Korrektur ist der Wert `false` —
   * auch dann, wenn der Nutzer die angenommene Richtung bloss bestätigt hat.
   */
  directionUncertain: boolean;
  /**
   * Kategorie-Label (deutsch, z. B. `"Lebensmittel"`). Nie `null`: das Backend liefert für
   * noch nicht kategorisierte Buchungen `"Sonstiges"`, damit das Dropdown eine Vorauswahl hat.
   */
  category: string;
}

/**
 * Eine Seite der Transaktionsliste (Spiegel des Backend-DTOs `TransactionListResponse`,
 * FE-CAT-05/US-13).
 *
 * <p>`GET /api/transactions` liefert seit FE-CAT-05 nicht mehr alle Buchungen eines Monats auf einmal,
 * sondern ein Fenster daraus — US-13 schliesst den ungepaginierten Vollload aus.
 */
export interface TransactionPage {
  /** Die Buchungen dieser Seite, absteigend nach Buchungsdatum. */
  transactions: Transaction[];
  /**
   * `true`, wenn hinter dieser Seite weitere Buchungen folgen. Steuert, ob der
   * «Weitere laden»-Button erscheint.
   */
  hasMore: boolean;
}
