/**
 * Zustand des Monats, für den eine Safe-to-Spend-Antwort gilt (BE-STS-06, US-12).
 *
 * `CLOSED` ist ein vergangener Monat: dort wird bewusst nicht gerechnet, die
 * Ansicht zeigt "Abgeschlossen" statt eines Betrags. Für Monate in der Zukunft
 * gibt es keinen Wert — die beantwortet das Backend mit HTTP 400.
 */
export type SafeToSpendStatus = 'OPEN' | 'CLOSED';

/**
 * Antwort von `GET /api/budget/safe-to-spend` (Spiegel des Backend-DTOs
 * `SafeToSpendResponse`, BE-STS-03).
 *
 * Beträge kommen als JSON-Zahl über die REST-Grenze: das Backend nutzt
 * `BigDecimal`, serialisiert aber ohne String-Serializer — Jackson liefert
 * daher `number`, nicht `string`.
 */
export interface SafeToSpendResponse {
  /** Wöchentlicher Betrag in CHF, oder `null` genau dann, wenn `noIncome` true ist. */
  amount: number | null;
  /**
   * Verbleibende Wochen im laufenden Monat (inkl. heute, aufgerundet), mindestens 1
   * — solange `status` `'OPEN'` ist. Bei `'CLOSED'` ist der Wert `0`.
   */
  weeksLeft: number;
  /** `true`, wenn `amount` negativ ist (Budget überzogen). */
  negative: boolean;
  /** `true`, wenn der User kein Monatseinkommen hinterlegt hat. */
  noIncome: boolean;
  /** Heuristischer Einkommens-Vorschlag, nur gesetzt wenn `noIncome` und ein Muster gefunden wurde. */
  incomeSuggestion: number | null;
  /**
   * `'OPEN'` für den laufenden Monat — alle übrigen Felder tragen dann ihre oben
   * beschriebene Bedeutung. `'CLOSED'` für einen vergangenen Monat: `amount` und
   * `incomeSuggestion` sind `null`, `weeksLeft` ist `0`, beide Flags sind `false`.
   *
   * Die Anzeige des `'CLOSED'`-Falls im Dashboard kommt mit FE-STS-04; das Feld steht
   * hier bereits, weil diese Datei den Backend-DTO spiegelt und `GET
   * /api/budget/safe-to-spend` es seit BE-STS-06 immer mitliefert.
   */
  status: SafeToSpendStatus;
}
