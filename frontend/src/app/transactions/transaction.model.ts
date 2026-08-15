/**
 * Eine einzelne Transaktion (Spiegel des Backend-DTOs `TransactionResponse`).
 *
 * <p>Geliefert als Teil einer {@link TransactionPage} von `GET /transactions?month=YYYY-MM` und
 * zurückgegeben von `PUT /transactions/{id}/category`.
 *
 * <p>`betrag` kommt als JSON-Zahl über die REST-Grenze — dieselbe Begründung wie bei
 * {@link CategorySummaryItem}: das Backend nutzt `BigDecimal`, serialisiert aber ohne
 * String-Serializer.
 */
export interface Transaction {
  /** Datenbank-ID — Adresse für `PUT /transactions/{id}/category`. */
  id: number;
  /** Buchungsdatum als ISO-Datum, z. B. `"2026-07-20"`. */
  buchungsdatum: string;
  /** Buchungstext des Kontoauszugs, z. B. `"COOP PRONTO BERN"`. */
  buchungstext: string;
  /** Positive Magnitude in CHF; die Richtung steht in {@link income}. */
  betrag: number;
  /** `true` = Gutschrift, `false` = Belastung. In der Kategorie-Übersicht immer `false`. */
  income: boolean;
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
 * <p>`GET /transactions` liefert seit FE-CAT-05 nicht mehr alle Buchungen eines Monats auf einmal,
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
