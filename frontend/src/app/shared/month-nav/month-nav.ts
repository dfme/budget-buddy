import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Monatswechsler der Design-Variante A (US-12, FE-UI-03): «‹ Label ›».
 *
 * <p>Rein präsentational — hält keinen Monatszustand, sondern meldet nur `prev`/`next`.
 * Die Pfeil-Buttons tragen aussagekräftige `aria-label`s und lassen sich einzeln sperren
 * (z. B. «nächster Monat» am aktuellen Monat).
 */
@Component({
  selector: 'app-month-nav',
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

  /** Nutzer will einen Monat zurück. */
  readonly prev = output<void>();

  /** Nutzer will einen Monat vor. */
  readonly next = output<void>();
}
