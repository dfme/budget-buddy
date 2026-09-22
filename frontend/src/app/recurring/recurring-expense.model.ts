/**
 * Ein Eintrag der Abo-Übersicht — spiegelt das Backend-DTO `RecurringExpenseResponse`
 * (BE-REC-02, US-08).
 *
 * <p>Ein Eintrag steht für eine erkannte <em>Gruppe</em>: derselbe Empfänger, in mindestens zwei
 * aufeinanderfolgenden Monaten mit demselben Betrag (±2 %) belastet. Die Einzelbuchungen der
 * Gruppe sind nicht Teil der Antwort.
 */
export interface RecurringExpenseResponse {
  id: number;
  /** Normalisierter Empfänger, in Grossschreibung — so, wie das Backend ihn gruppiert hat. */
  payeeKey: string;
  /**
   * Betrag der jüngsten erkannten Belastung, in CHF — seit BE-REC-04 zieht ihn jeder Import nach,
   * ein Preissprung eingeschlossen.
   */
  amount: number;
  /**
   * `GET` liefert alle drei Status, jeder mit eigenem Abschnitt in der Übersicht:
   *
   * - `DETECTED` — laufendes Abo; mindert den Safe-to-Spend.
   * - `ENDED` — ausgelaufen (BE-REC-04): der Empfänger hat in den jüngsten Monaten der Historie
   *   nicht mehr abgebucht. Bleibt sichtbar, mindert aber nichts mehr.
   * - `DISMISSED` — per «Kein Abo» verneint (FE-NOTIF-03).
   */
  status: 'DETECTED' | 'DISMISSED' | 'ENDED';
  /** Erster Monat der Abo-Reihe in den Daten, als `YYYY-MM`. */
  firstDetectedMonth: string;
  createdAt: string;
  /** `true`, solange die zugehörige Benachrichtigung ungelesen ist — trägt das «Neu»-Label. */
  isNew: boolean;
}
