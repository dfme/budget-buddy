import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Basis-Button der Design-Variante A (US-übergreifend, FE-UI-03).
 *
 * <p>Bewusst als Attribut-Selektor auf nativem `<button>`/`<a>`: so bleiben
 * Tastaturbedienung, Fokus, `type`- und `disabled`-Semantik erhalten (a11y), und der
 * sichtbare Fokus-Ring aus dem Token-Fundament greift ohne Zutun.
 *
 * <pre>&lt;button appButton variant="primary" block&gt;Speichern&lt;/button&gt;</pre>
 *
 * <p>Ein projiziertes `<svg>` landet als Icon vor dem Label (FE-FC-08). Das Label steckt in einem
 * eigenen `<span class="btn__label">` aus diesem Template, nicht im projizierten Inhalt: unter
 * emulierter Kapselung erreicht `button.scss` nur Elemente des eigenen Templates, und
 * `iconOnlyMobile` muss das Label verstecken können. Das Icon trägt deshalb seine Grösse selbst
 * (`width`/`height` am `<svg>`) und `aria-hidden="true"` — es ist Schmuck, der Name kommt aus dem
 * Label oder einem `aria-label`.
 *
 * <pre>&lt;button appButton variant="ghost" iconOnlyMobile [attr.aria-label]="'Löschen: ' + name"&gt;
 *   &lt;svg aria-hidden="true" …&gt;…&lt;/svg&gt;Löschen
 * &lt;/button&gt;</pre>
 */
@Component({
  selector: 'button[appButton], a[appButton]',
  template: '<ng-content select="svg" /><span class="btn__label"><ng-content /></span>',
  styleUrl: './button.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.btn--primary]': "variant() === 'primary'",
    '[class.btn--ghost]': "variant() === 'ghost'",
    '[class.btn--block]': 'block()',
    '[class.btn--icon-only-mobile]': 'iconOnlyMobile()',
  },
})
export class Button {
  /** Optische Variante: gefüllt (`primary`) oder umrandet-transparent (`ghost`). */
  readonly variant = input<'primary' | 'ghost'>('primary');

  /** `true` streckt den Button auf die volle Breite des Containers. */
  readonly block = input(false, { transform: booleanAttribute });

  /**
   * `true` zeigt unter `$bp-desktop` nur das Icon als 44×44px-Fläche; das Label bleibt für
   * Screenreader im Accessibility-Tree, ist aber unsichtbar. Ab `$bp-desktop` stehen Icon und
   * Label nebeneinander. Ohne projiziertes `<svg>` sinnlos — ohne sichtbaren Text braucht der
   * Button dann ein Icon. Weil das sichtbare Label wegfällt, gehört ein `aria-label` dazu, das den
   * Kontext trägt («Löschen: Miete»), sobald derselbe Button mehrfach auf der Seite steht, und ein
   * `title` mit dem Label als Tooltip für die Maus. Beides setzt die aufrufende Stelle: nur sie
   * kennt den Kontext, und ein Label im Wartezustand («Wird entfernt …») ändert sich mit ihr.
   */
  readonly iconOnlyMobile = input(false, { transform: booleanAttribute });
}
