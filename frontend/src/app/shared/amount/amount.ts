import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatSwissAmount } from '../format';

/**
 * Betrags-/CHF-Anzeige der Design-Variante A (FE-UI-03).
 *
 * <p>Schweizer Format `1'234.56` mit `tabular-nums`. Positiv/negativ wird **nicht nur**
 * über Farbe unterschieden: das Vorzeichen (`+` / `−`) steht sichtbar davor und geht per
 * `aria-label` auch an Screenreader — so bleibt die Information bei Rot-Grün-Schwäche
 * erhalten (AC / ADR-Designprinzip).
 */
@Component({
  selector: 'app-amount',
  templateUrl: './amount.html',
  styleUrl: './amount.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.amount--positive]': 'value() > 0',
    '[class.amount--negative]': 'value() < 0',
    '[attr.aria-label]': 'ariaLabel()',
  },
})
export class Amount {
  /** Betrag; Vorzeichen entscheidet über positiv/negativ. */
  readonly value = input.required<number>();

  /** Stellt `CHF` voran (z. B. für Hero-Beträge). */
  readonly showCurrency = input(false);

  /**
   * Unterdrückt das `+` bei positiven Beträgen — für Kontostände (z. B. Safe-to-Spend), bei
   * denen ein positiver Wert der Normalfall ist, nicht eine Veränderung. Das Minuszeichen bei
   * negativen Beträgen bleibt davon unberührt: das ist immer eine relevante Abweichung.
   */
  readonly hidePositiveSign = input(false);

  /** `+` für positive, `−` (Minuszeichen) für negative Beträge, sonst leer. */
  readonly sign = computed(() => {
    const v = this.value();
    if (v > 0) {
      return this.hidePositiveSign() ? '' : '+';
    }
    return v < 0 ? '−' : '';
  });

  /** Betrag im Schweizer Format, ohne Vorzeichen. */
  readonly formatted = computed(() => formatSwissAmount(this.value()));

  protected readonly ariaLabel = computed(() => {
    const v = this.value();
    const direction = v < 0 ? 'minus ' : v > 0 && !this.hidePositiveSign() ? 'plus ' : '';
    return `${direction}${this.formatted()} Franken`;
  });
}
