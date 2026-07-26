import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Fortschrittsbalken der Design-Variante A (FE-UI-03) — z. B. «Woche im Monat» oder
 * verbrauchtes Budget.
 *
 * <p>Der Track ist ein `role="progressbar"` mit `aria-valuenow/min/max`. Der Wert wird auf
 * 0–100 begrenzt. `variant="negative"` färbt die Füllung in die Warnfarbe (z. B.
 * Budget überschritten) — ergänzt durch das `aria-label`, nicht nur über die Farbe.
 */
@Component({
  selector: 'app-meter',
  templateUrl: './meter.html',
  styleUrl: './meter.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Meter {
  /** Fortschritt in Prozent (0–100); Werte ausserhalb werden begrenzt. */
  readonly value = input.required<number>();

  /** `default` (Akzent) oder `negative` (Warnfarbe). */
  readonly variant = input<'default' | 'negative'>('default');

  /** Optionales Label links unter dem Balken. */
  readonly labelStart = input<string>();

  /** Optionales Label rechts unter dem Balken. */
  readonly labelEnd = input<string>();

  /** Beschreibung des Balkens für Screenreader. */
  readonly ariaLabel = input<string>();

  /** Auf 0–100 begrenzter Wert. */
  readonly clamped = computed(() => Math.max(0, Math.min(100, this.value())));
}
