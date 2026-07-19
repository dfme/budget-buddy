/**
 * Ein Kategorie-Eintrag im Ausgaben-Summary (Spiegel des Backend-DTOs
 * `CategorySummaryItem`, BE-CAT-05).
 *
 * Beträge kommen als String über die REST-Grenze (Backend `BigDecimal`), um
 * Gleitkomma-Rundungsfehler zu vermeiden — die Anzeige nutzt den {@link CurrencyPipe}
 * bzw. formatiert den Prozentwert direkt.
 */
export interface CategorySummaryItem {
  /** Kategorie-Label (deutsch, z. B. `"Lebensmittel"`). */
  category: string;
  /** Summe der Ausgaben dieser Kategorie in CHF, als Dezimalstring. */
  amount: string;
  /** Anzahl Transaktionen dieser Kategorie im Monat. */
  count: number;
  /** Prozentanteil an den Gesamtausgaben, als Dezimalstring (Skala 2). */
  percentage: string;
}

/**
 * Antwort von `GET /transactions/summary?month=YYYY-MM` (Spiegel des Backend-DTOs
 * `CategorySummaryResponse`, BE-CAT-05).
 */
export interface CategorySummary {
  /** Abgefragter Monat im Format `YYYY-MM`. */
  month: string;
  /** Summe aller Ausgaben des Monats in CHF, als Dezimalstring. */
  totalAmount: string;
  /** Gesamtzahl der Ausgaben-Transaktionen im Monat. */
  totalCount: number;
  /** Kategorie-Einträge, absteigend nach Betrag. Leer, wenn keine Ausgaben. */
  categories: CategorySummaryItem[];
}
