import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

import { categoryIcon } from '../category';

/**
 * Auswählbarer Chip der Design-Variante A (FE-UI-03) — z. B. Kategorie-Auswahl im
 * Korrektur-Dialog (US-05).
 *
 * <p>Attribut auf nativem `<button>`: `aria-pressed` spiegelt den Auswahlzustand, Fokus
 * und Tastatur kommen vom Element selbst.
 *
 * <pre>&lt;button appChip [selected]="true"&gt;Lebensmittel&lt;/button&gt;</pre>
 *
 * <p>Seit BE-CAT-10 kann der Chip zusätzlich das Kategorie-Icon voranstellen. Der Slug ist
 * optional und der Chip bleibt ohne ihn ein allgemeiner Chip: Er wird nicht nur für Kategorien
 * verwendet, und eine Pflichtangabe hätte jede andere Verwendung gebrochen.
 *
 * <pre>&lt;button appChip category="lebensmittel"&gt;Lebensmittel&lt;/button&gt;</pre>
 */
@Component({
  selector: 'button[appChip]',
  template: `
    @if (icon(); as glyph) {
      <span class="chip__icon" aria-hidden="true">{{ glyph }}</span>
    }
    <ng-content />
  `,
  styleUrl: './chip.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    type: 'button',
    '[class.chip--selected]': 'selected()',
    '[attr.aria-pressed]': 'selected()',
  },
})
export class Chip {
  /** `true`, wenn der Chip ausgewählt ist. */
  readonly selected = input(false, { transform: booleanAttribute });

  /**
   * Kategorie-Slug, z. B. `"lebensmittel"`. Ohne ihn — und bei unbekanntem Slug — rendert der
   * Chip kein Icon und verhält sich exakt wie vor BE-CAT-10.
   */
  readonly category = input<string>();

  /** Glyph der Kategorie, oder `undefined`. */
  protected readonly icon = computed(() => categoryIcon(this.category()));
}
