/**
 * Die Kennzahlen eines Monats (Spiegel des Backend-Records `MonthlyTotals`, BE-STS-07, US-12).
 *
 * Beträge kommen als JSON-Zahl über die REST-Grenze: das Backend nutzt `BigDecimal`,
 * serialisiert aber ohne String-Serializer — Jackson liefert daher `number`, nicht `string`.
 *
 * **Alle drei Beträge sind gemeinsam `null`**, wenn der Monat keine einzige Buchung trägt. Das ist
 * nicht dasselbe wie `0` und wird in der Übersicht als `–` dargestellt: eine Null behauptete
 * erfasste Nullbeträge. Ein Monat mit ausschliesslich Gutschriften trägt entsprechend
 * `expenses: 0`, weil es dort Buchungen *gibt* und die Summe der Belastungen wirklich null ist.
 */
export interface MonthlyTotals {
  /** Monat im Format `YYYY-MM`. */
  month: string;
  /** Summe der Gutschriften des Monats in CHF, oder `null` — siehe oben. */
  income: number | null;
  /**
   * Summe der Belastungen des Monats in CHF, oder `null`.
   *
   * Betragsgleich mit `CategorySummary.totalAmount` desselben Monats: dieselbe Auswahlregel im
   * Backend, ohne Abzug der per Dauerauftrag bezahlten Fixkosten (ADR-13 wirkt allein im
   * Safe-to-Spend).
   */
  expenses: number | null;
  /**
   * `income − expenses` in CHF, oder `null`.
   *
   * Kommt **vom Backend** und wird hier nie gerechnet: zwei JSON-`number` zu subtrahieren ist
   * genau die Gleitkomma-Rechnung, die ADR-9 für Geldbeträge ausschliesst. Negativ, wenn im
   * Monat mehr ausgegeben als eingenommen wurde.
   */
  difference: number | null;
}
