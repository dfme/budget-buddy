import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Basis-Button der Design-Variante A (US-übergreifend, FE-UI-03).
 *
 * <p>Bewusst als Attribut-Selektor auf nativem `<button>`/`<a>`: so bleiben
 * Tastaturbedienung, Fokus, `type`- und `disabled`-Semantik erhalten (a11y), und der
 * sichtbare Fokus-Ring aus dem Token-Fundament greift ohne Zutun.
 *
 * <pre>&lt;button appButton variant="primary" block&gt;Speichern&lt;/button&gt;</pre>
 */
@Component({
  selector: 'button[appButton], a[appButton]',
  template: '<ng-content />',
  styleUrl: './button.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.btn--primary]': "variant() === 'primary'",
    '[class.btn--ghost]': "variant() === 'ghost'",
    '[class.btn--block]': 'block()',
  },
})
export class Button {
  /** Optische Variante: gefüllt (`primary`) oder umrandet-transparent (`ghost`). */
  readonly variant = input<'primary' | 'ghost'>('primary');

  /** `true` streckt den Button auf die volle Breite des Containers. */
  readonly block = input(false, { transform: booleanAttribute });
}
