/**
 * Der Monatsbegriff des Frontends: das Format `YYYY-MM`, seine Prüfung, seine Arithmetik und
 * sein Label.
 *
 * <p><strong>Warum das hier steht und nicht je Komponente.</strong> Die Regeln lagen als
 * `private static` in `CategoryOverview`. Mit FE-STS-04 braucht das Dashboard dieselben — und
 * der erste Anlauf (PR #280) hatte sie kopiert. Zwei Definitionen von «welche Monate es gibt»
 * laufen auseinander: eine Seite akzeptierte dann einen Monat, den die andere ablehnt, und
 * sichtbar würde das erst im Deep-Link zwischen beiden Seiten. Beide Aufrufer ziehen deshalb aus
 * dieser Datei.
 *
 * <p>Reine Funktionen ohne Angular-Bindung — kein Service, kein Injectable. Es gibt keinen
 * Zustand zu halten und nichts zu injizieren; ein Service verlangte in jedem Test ein TestBed für
 * eine Datumsrechnung.
 */

/**
 * Zulässiger `month`-Wert: vierstelliges Jahr, Monat `01`–`12`.
 *
 * <p>Ein unbrauchbarer Wert darf nicht bis {@link formatMonth} durchkommen — `new Date(NaN)`
 * erzeugte dort ein «Invalid Date» als Überschrift. Einstellige Monate (`2026-1`) fallen
 * bewusst durch: das Backend erwartet `YYYY-MM` und legte `2026-1` anders aus.
 */
export const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

/** Baut `YYYY-MM` aus Jahr und 1-basiertem Monat. */
export function toMonthString(year: number, monthNumber: number): string {
  return `${year}-${String(monthNumber).padStart(2, '0')}`;
}

/** Der laufende Monat als `YYYY-MM`, in der Zeitzone des Browsers. */
export function currentMonth(): string {
  const now = new Date();
  return toMonthString(now.getFullYear(), now.getMonth() + 1);
}

/**
 * Verschiebt einen Monat um `delta` Monate — negativ zurück, positiv vor.
 *
 * <p>Über `new Date(year, monthNumber - 1 + delta, 1)`: der Date-Konstruktor normalisiert einen
 * Monatsindex ausserhalb `0..11` selbst über die Jahresgrenze, weshalb hier keine eigene
 * Modulo-Rechnung steht. Tag 1, damit die Verschiebung nie an einem Monatsende hängen bleibt.
 *
 * @param month Ausgangsmonat als `YYYY-MM`.
 * @param delta Anzahl Monate; `-1` ist der Vormonat.
 */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthNumber] = month.split('-').map(Number);
  const shifted = new Date(year, monthNumber - 1 + delta, 1);
  return toMonthString(shifted.getFullYear(), shifted.getMonth() + 1);
}

/**
 * Menschlich lesbares Label eines Monats, z. B. `"Juli 2026"`.
 *
 * <p>`de-CH` über `Intl`, damit die Monatsnamen nicht als Liste im Code stehen.
 *
 * @param month Monat als `YYYY-MM`; ein Wert, der {@link MONTH_PATTERN} nicht entspricht, ergibt
 *     hier «Invalid Date» — Aufrufer prüfen vorher mit {@link isValidMonth}.
 */
export function formatMonth(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number);
  const date = new Date(year, monthNumber - 1, 1);
  return new Intl.DateTimeFormat('de-CH', { month: 'long', year: 'numeric' }).format(date);
}

/**
 * `true`, wenn `month` ein brauchbarer Monatswert ist: Format stimmt **und** er liegt nicht in
 * der Zukunft.
 *
 * <p>Beide Bedingungen zusammen, weil beide Aufrufer sie zusammen brauchen und ein Zukunftsmonat
 * genauso unbrauchbar ist wie ein kaputtes Format: Buchungen gibt es dort keine, der Stepper
 * verbietet den Weg dorthin, und `GET /api/budget/safe-to-spend` antwortet mit 400. Getrennt
 * geführt stünde die Und-Verknüpfung an jeder Aufrufstelle erneut — und genau dort ist sie in
 * PR #280 schon einmal auseinandergelaufen.
 *
 * <p>Der Vergleich `month <= currentMonth()` ist eine Zeichenketten-Vergleich und darf das sein:
 * `YYYY-MM` ist lexikografisch in derselben Ordnung wie chronologisch — nur deshalb funktioniert
 * auch das `.sort()` der Dropdown-Liste.
 *
 * @param month zu prüfender Wert, `null` erlaubt (ergibt `false`).
 */
export function isValidMonth(month: string | null | undefined): month is string {
  return month != null && MONTH_PATTERN.test(month) && month <= currentMonth();
}
