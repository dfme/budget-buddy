import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Input } from '../input/input';

/** Ein wählbarer Monat im Direktsprung-Dropdown (FE-CAT-04). */
export interface MonthOption {
  /** Monat im Format `YYYY-MM` — der Wert, der nach aussen gemeldet wird. */
  value: string;
  /** Menschlich lesbares Label, z. B. `"Juli 2026"`. */
  label: string;
}

/**
 * Monatswechsler der Design-Variante A (US-12, FE-UI-03): «‹ Label ›», optional mit
 * Direktsprung-Dropdown (FE-CAT-04).
 *
 * <p>Rein präsentational — hält keinen Monatszustand, sondern meldet nur `prev`/`next`/`select`.
 * Die Pfeil-Buttons tragen aussagekräftige `aria-label`s und lassen sich einzeln sperren
 * (z. B. «nächster Monat» am aktuellen Monat).
 *
 * <p>Das Dropdown erscheint nur, wenn {@link months} Einträge enthält. So bleibt die Komponente
 * für Aufrufer ohne Monatsliste — etwa den Styleguide — unverändert der reine Stepper. Es ist ein
 * natives `<select>` mit `<label>`: damit sind Tastaturbedienung, Fokus und Beschriftung ohne
 * Nachbau gegeben, und die Auswahl bleibt auf Touch bedienbar.
 */
@Component({
  selector: 'app-month-nav',
  imports: [Input],
  templateUrl: './month-nav.html',
  styleUrl: './month-nav.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonthNav {
  /** Angezeigtes Label, z. B. `"Juli 2026"`. */
  readonly label = input.required<string>();

  /** Sperrt den Zurück-Pfeil. */
  readonly disablePrev = input(false);

  /** Sperrt den Vor-Pfeil (z. B. am aktuellen Monat). */
  readonly disableNext = input(false);

  /**
   * Direkt anspringbare Monate, neuester zuerst. Leer (Standard) blendet das Dropdown aus — der
   * Stepper allein bleibt dann übrig, wie vor FE-CAT-04.
   */
  readonly months = input<readonly MonthOption[]>([]);

  /**
   * Aktuell gewählter Monat als `YYYY-MM`. Steuert die Vorauswahl des Dropdowns; er sollte in
   * {@link months} enthalten sein, sonst zeigte das `<select>` einen fremden Wert an.
   */
  readonly selected = input<string>('');

  /** Nutzer will einen Monat zurück. */
  readonly prev = output<void>();

  /** Nutzer will einen Monat vor. */
  readonly next = output<void>();

  /** Nutzer hat einen Monat direkt gewählt; meldet `YYYY-MM`. */
  readonly select = output<string>();
}
