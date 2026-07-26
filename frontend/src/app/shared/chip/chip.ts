import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Auswählbarer Chip der Design-Variante A (FE-UI-03) — z. B. Kategorie-Auswahl im
 * Korrektur-Dialog (US-05).
 *
 * <p>Attribut auf nativem `<button>`: `aria-pressed` spiegelt den Auswahlzustand, Fokus
 * und Tastatur kommen vom Element selbst.
 *
 * <pre>&lt;button appChip [selected]="true"&gt;Lebensmittel&lt;/button&gt;</pre>
 */
@Component({
  selector: 'button[appChip]',
  template: '<ng-content />',
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
}
