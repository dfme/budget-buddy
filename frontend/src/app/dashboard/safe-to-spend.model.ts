/**
 * Antwort von `GET /budget/safe-to-spend` (Spiegel des Backend-DTOs
 * `SafeToSpendResponse`, BE-STS-03).
 *
 * Beträge kommen als JSON-Zahl über die REST-Grenze: das Backend nutzt
 * `BigDecimal`, serialisiert aber ohne String-Serializer — Jackson liefert
 * daher `number`, nicht `string`.
 */
export interface SafeToSpendResponse {
  /** Wöchentlicher Betrag in CHF, oder `null` genau dann, wenn `noIncome` true ist. */
  amount: number | null;
  /** Verbleibende Wochen im laufenden Monat (inkl. heute, aufgerundet, mindestens 1). */
  weeksLeft: number;
  /** `true`, wenn `amount` negativ ist (Budget überzogen). */
  negative: boolean;
  /** `true`, wenn der User kein Monatseinkommen hinterlegt hat. */
  noIncome: boolean;
  /** Heuristischer Einkommens-Vorschlag, nur gesetzt wenn `noIncome` und ein Muster gefunden wurde. */
  incomeSuggestion: number | null;
}
