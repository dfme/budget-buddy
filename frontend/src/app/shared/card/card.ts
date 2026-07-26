import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Container-Karte der Design-Variante A (FE-UI-03).
 *
 * <p>Optionaler Kopf aus Titel + Meta; der Inhalt kommt per Content-Projection.
 * Zusätzliche Kopf-Elemente (z. B. ein Aktions-Button) lassen sich über den Slot
 * `[card-actions]` projizieren.
 */
@Component({
  selector: 'app-card',
  templateUrl: './card.html',
  styleUrl: './card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Card {
  /** Optionaler Kartentitel. Ohne Titel und Meta entfällt der Kopf ganz. */
  readonly title = input<string>();

  /** Optionale Meta-Angabe rechts im Kopf (z. B. ein Zeitraum). */
  readonly meta = input<string>();
}
