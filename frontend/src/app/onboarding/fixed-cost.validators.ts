import { AbstractControl, ValidatorFn } from '@angular/forms';

/**
 * Kleinster erfassbarer Betrag in CHF. CHF-Beträge sind rappengenau (ADR-9), ein Rappen ist
 * damit die kleinste Einheit über null — die Regel «Betrag > 0» aus US-03 als konkreter Wert,
 * den `Validators.min` prüfen kann.
 */
export const MIN_BETRAG_CHF = 0.01;

/**
 * Wie {@link Validators.required}, verwirft aber auch reinen Leerraum.
 *
 * <p>`Validators.required` prüft nur `value.length === 0` — `'   '` wäre damit gültig. Ein
 * leeres `bezeichnung` würde als benannte Position durchgehen, weil `fixed_costs.bezeichnung`
 * `VARCHAR NOT NULL` ist und den leeren String annimmt.
 *
 * <p>Bewusst mit dem Fehlerschlüssel `required` statt `pattern`: so bleibt die bestehende
 * Meldung in den Formularen zuständig.
 */
export const nonBlank: ValidatorFn = (control) =>
  String(control.value ?? '').trim() ? null : { required: true };

/**
 * Lässt höchstens zwei Nachkommastellen zu — CHF ist rappengenau (ADR-9).
 *
 * <p>`step="0.01"` im Template ist wegen `novalidate` nur ein Hinweis, und Angular validiert
 * `step` nicht: ohne diesen Validator liefe `10.999` bis in den Request und würde in
 * `DECIMAL(10,2)` still gerundet. Stilles Runden ist bei Geldbeträgen die unangenehme Variante,
 * deshalb der Abbruch vor dem Request. Die Server-Validierung bleibt davon unberührt — ein
 * Client-Check ersetzt sie nicht.
 *
 * <p>Geprüft wird auf der Dezimaldarstellung statt über `value * 100`, weil binäre Gleitkomma-
 * Arithmetik genau die Rundungsfehler erzeugt, die hier gefunden werden sollen (`10.999 * 100`
 * ergibt `1099.9000000000001`).
 */
export const maxTwoDecimals: ValidatorFn = (control) => {
  const value = control.value;
  if (value === null || value === '') {
    return null;
  }
  const [, decimals = ''] = String(value).split('.');
  return decimals.length <= 2 ? null : { maxDecimals: true };
};

/**
 * Fehlermeldung fürs Bezeichnungs-Feld oder `null`, solange es gültig oder unberührt ist.
 *
 * <p>Geteilt von Wizard, Inline-Edit und Neue-Position-Dialog (FE-FC-10) — die drei Formulare
 * prüfen dieselben Regeln und sollen sie mit denselben Worten melden. Bis FE-FC-10 stand die
 * Logik als Kopie in jeder Komponente.
 */
export function bezeichnungErrorMessage(control: AbstractControl): string | null {
  if (!control.touched || control.valid) {
    return null;
  }
  return control.hasError('required') ? 'Bezeichnung ist erforderlich.' : null;
}

/**
 * Fehlermeldung fürs Betrags-Feld oder `null`, solange es gültig oder unberührt ist — passend zu
 * `Validators.required`, `Validators.min(MIN_BETRAG_CHF)` und {@link maxTwoDecimals}. Geteilt wie
 * {@link bezeichnungErrorMessage}.
 */
export function betragErrorMessage(control: AbstractControl): string | null {
  if (!control.touched || control.valid) {
    return null;
  }
  if (control.hasError('required')) {
    return 'Betrag ist erforderlich.';
  }
  if (control.hasError('min')) {
    return 'Betrag muss grösser als 0 sein.';
  }
  if (control.hasError('maxDecimals')) {
    return 'Betrag darf höchstens zwei Nachkommastellen haben.';
  }
  return null;
}
