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
  host: {
    // `title` ist zugleich ein globales HTML-Attribut: schreibt ein Aufrufort ihn statisch
    // (`title="…"`), setzt Angular den Input *und* lässt das Attribut im DOM stehen. Das gäbe
    // einen nativen Tooltip über der ganzen Karte und einen Accessible Name auf dem Host, der
    // die sichtbare Überschrift (`.card__title`) doppelt vorträgt. Hier entfernt (FE-UI-08,
    // analog zu Notice/Modal aus #181).
    '[attr.title]': 'null',
  },
})
export class Card {
  /** Optionaler Kartentitel. Ohne Titel und Meta entfällt der Kopf ganz. */
  readonly title = input<string>();

  /** Optionale Meta-Angabe rechts im Kopf (z. B. ein Zeitraum). */
  readonly meta = input<string>();
}
