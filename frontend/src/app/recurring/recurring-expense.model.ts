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
  /** Betrag der jüngsten erkannten Belastung, in CHF. */
  amount: number;
  /** `GET` liefert nur `DETECTED`; `DISMISSED` kommt nur als Antwort von `dismiss` zurück. */
  status: 'DETECTED' | 'DISMISSED';
  /** Erster Monat der Abo-Reihe in den Daten, als `YYYY-MM`. */
  firstDetectedMonth: string;
  createdAt: string;
  /** `true`, solange die zugehörige Benachrichtigung ungelesen ist — trägt das «Neu»-Label. */
  isNew: boolean;
}
