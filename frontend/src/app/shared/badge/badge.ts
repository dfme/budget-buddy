import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Kategorie-Badge der Design-Variante A (FE-UI-03): Farbpunkt + Label.
 *
 * <p>Die Punkt-/Textfarbe kommt ausschliesslich aus den `--cat-<slug>`-Tokens
 * (`data-cat`-Attribut → generierte Regeln in `badge.scss`, gespeist aus der
 * `$categories`-Map). Ist der Slug unbekannt/leer, bleibt es ein neutraler Punkt.
 */
@Component({
  selector: 'app-badge',
  templateUrl: './badge.html',
  styleUrl: './badge.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-cat]': 'category()',
  },
})
export class Badge {
  /** Kategorie-Slug, z. B. `"lebensmittel"` (siehe {@link CATEGORIES}). */
  readonly category = input<string>();

  /** Anzeigetext, z. B. `"Lebensmittel"`. */
  readonly label = input.required<string>();
}
