/**
 * Ein Kategorie-Eintrag im Ausgaben-Summary (Spiegel des Backend-DTOs
 * `CategorySummaryItem`, BE-CAT-05).
 *
 * Beträge kommen als JSON-Zahl über die REST-Grenze: das Backend nutzt
 * `BigDecimal`, serialisiert aber ohne String-Serializer — Jackson liefert
 * daher `number`, nicht `string`.
 */
export interface CategorySummaryItem {
  /** Kategorie-Label (deutsch, z. B. `"Lebensmittel"`). */
  category: string;
  /** Summe der Ausgaben dieser Kategorie in CHF. */
  amount: number;
  /** Anzahl Transaktionen dieser Kategorie im Monat. */
  count: number;
  /** Prozentanteil an den Gesamtausgaben (Skala 2, z. B. `74.05`). */
  percentage: number;
}

/**
 * Antwort von `GET /transactions/summary?month=YYYY-MM` (Spiegel des Backend-DTOs
 * `CategorySummaryResponse`, BE-CAT-05).
 */
export interface CategorySummary {
  /** Abgefragter Monat im Format `YYYY-MM`. */
  month: string;
  /** Summe aller Ausgaben des Monats in CHF. */
  totalAmount: number;
  /** Gesamtzahl der Ausgaben-Transaktionen im Monat. */
  totalCount: number;
  /** Kategorie-Einträge, absteigend nach Betrag. Leer, wenn keine Ausgaben. */
  categories: CategorySummaryItem[];
}
