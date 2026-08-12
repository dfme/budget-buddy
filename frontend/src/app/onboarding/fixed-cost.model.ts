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
 * Request-Body von `POST /fixed-costs` und `PUT /fixed-costs/{id}` (FE-FC-01, FE-FC-03).
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

/** Antwort von `POST /fixed-costs` — die angelegte Position inklusive vergebener ID. */
export interface FixedCost extends CreateFixedCostRequest {
  id: number;
}

/**
 * Eine Fixkosten-Position, wie sie `GET /fixed-costs` und `PUT /fixed-costs/{id}` liefern
 * (FE-FC-03) — zusätzlich zu {@link FixedCost} der auf einen Monat normalisierte Betrag, der
 * in die Safe-to-Spend-Rechnung eingeht. Spiegelt `FixedCostResponse` im Backend.
 */
export interface FixedCostDetail extends FixedCost {
  /** Auf einen Monat normalisierter Betrag in CHF — `betrag` ÷ 1, ÷ 3 bzw. ÷ 12 je nach Intervall. */
  monatsbetrag: number;
}

/**
 * Antwort von `GET /fixed-costs` (FE-FC-03) — die Positionen plus die daraus abgeleiteten
 * Werte für die Einkommens-Warnung aus US-03. Spiegelt `FixedCostSummaryResponse` im Backend.
 */
export interface FixedCostSummary {
  /** Alle Positionen des Users, stabil nach Anlage-Reihenfolge sortiert. Leer, wenn keine erfasst. */
  fixedCosts: FixedCostDetail[];
  /** Summe der `monatsbetrag` aller Positionen, Skala 2. Bei leerer Liste `0`. */
  summeMonatlich: number;
  /** Monatliches Einkommen des Users in CHF, oder `null`, solange keines erfasst ist. */
  monthlyIncome: number | null;
  /** `true`, wenn `summeMonatlich >= monthlyIncome` — dann kann kein Safe-to-Spend berechnet werden. */
  exceedsIncome: boolean;
}
