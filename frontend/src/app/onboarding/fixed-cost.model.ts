/**
 * Zahlungsintervall einer Fixkosten-Position (US-03).
 *
 * Die Werte sind bewusst ASCII und kleingeschrieben — sie sind identisch mit dem, was
 * `Intervall.getLabel()` im Backend liefert und in `fixed_costs.intervall` liegt. Der
 * Anzeigetext «jährlich» mit Umlaut ist Sache des Templates: ein Umlaut im Wire-Format
 * würde Encoding-Fallen über Datenbank, JSON und E2E-Assertions hinweg eröffnen.
 */
export type Intervall = 'monatlich' | 'quartalsweise' | 'jaehrlich';

/** Ein Eintrag des Intervall-Dropdowns: gesendeter Wert plus sichtbarer Text. */
export interface IntervallOption {
  value: Intervall;
  label: string;
}

/**
 * Die drei Intervalle in der Reihenfolge, in der sie im Dropdown erscheinen — aufsteigend
 * nach Periodenlänge. Deckungsgleich mit dem Backend-Enum `Intervall`.
 */
export const INTERVALL_OPTIONS: readonly IntervallOption[] = [
  { value: 'monatlich', label: 'monatlich' },
  { value: 'quartalsweise', label: 'quartalsweise' },
  { value: 'jaehrlich', label: 'jährlich' },
];

/**
 * Request-Body von `POST /fixed-costs` (FE-FC-01).
 *
 * **Unbestätigter Contract.** Der Endpoint existiert noch nicht — er kommt mit BE-FC-03
 * (#12). Die Form ist aus der Entity `FixedCost` und aus `Intervall.getLabel()` abgeleitet.
 * Weicht #12 davon ab, sind dieses Model und `FixedCostService` nachzuziehen.
 *
 * `betrag` ist eine JSON-Zahl, kein String: das Backend nutzt `BigDecimal` (ADR-9),
 * serialisiert aber ohne String-Serializer — Jackson liefert und erwartet damit `number`.
 */
export interface CreateFixedCostRequest {
  /** Freitext-Bezeichnung, z. B. `"Miete"`. Nicht leer. */
  bezeichnung: string;
  /** Betrag in CHF pro Intervall — positiv, nicht der normalisierte Monatsbetrag. */
  betrag: number;
  /** Zahlungsintervall. */
  intervall: Intervall;
}

/**
 * Antwort von `POST /fixed-costs` — die angelegte Position inklusive vergebener ID.
 *
 * Gleiche Einschränkung wie bei {@link CreateFixedCostRequest}: unbestätigt bis #12.
 */
export interface FixedCost extends CreateFixedCostRequest {
  id: number;
}
