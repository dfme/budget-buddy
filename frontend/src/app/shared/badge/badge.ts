import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { categoryIcon } from '../category';

/**
 * Kategorie-Badge der Design-Variante A (FE-UI-03): Icon + Label.
 *
 * <p>Die Icon-/Textfarbe kommt ausschliesslich aus den `--cat-<slug>`-Tokens
 * (`data-cat`-Attribut → generierte Regeln in `badge.scss`, gespeist aus der
 * `$categories`-Map). Ist der Slug unbekannt/leer, bleibt es ein neutraler Punkt.
 *
 * <p>Seit BE-CAT-10 steht an der Stelle des Farbpunkts das Kategorie-Icon. Der Punkt ist damit
 * nicht verschwunden, sondern zum Fallback geworden: Er erscheint genau dann, wenn kein Icon
 * auflösbar ist. Beides gleichzeitig zu zeigen wären drei Signale in einer Pille, die keine
 * zwei Zeilen hoch sein soll.
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

  /** Glyph der Kategorie, oder `undefined` bei unbekanntem/fehlendem Slug. */
  protected readonly icon = computed(() => categoryIcon(this.category()));
}
