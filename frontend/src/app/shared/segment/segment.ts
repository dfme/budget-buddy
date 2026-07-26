import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/** Eine Option des Segment-Umschalters. */
export interface SegmentOption {
  readonly value: string;
  readonly label: string;
}

/**
 * Segmentierter Umschalter der Design-Variante A (FE-UI-03) — z. B. für Filter.
 *
 * <p>Zweiweg-Bindung über `value` (`model`). Die Optionen sind native Buttons mit
 * `aria-pressed`; Fokus/Tastatur kommen vom nativen Element.
 */
@Component({
  selector: 'app-segment',
  templateUrl: './segment.html',
  styleUrl: './segment.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Segment {
  /** Auswählbare Optionen. */
  readonly options = input.required<readonly SegmentOption[]>();

  /** Aktuell gewählter Wert (Zweiweg-Bindung). */
  readonly value = model<string>();

  /** Beschriftung der Gruppe für Screenreader. */
  readonly ariaLabel = input<string>();

  select(value: string): void {
    this.value.set(value);
  }
}
