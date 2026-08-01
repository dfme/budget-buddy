import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, Signal, signal } from '@angular/core';

import { CATEGORY_SLUGS } from '../category';

/**
 * Die für Charts benötigten Token-Farben, zum Zeitpunkt des Lesens aufgelöst.
 *
 * <p>Alle Werte stammen aus den CSS Custom Properties des aktiven Themes
 * (`styles.scss`) — es gibt in den Chart-Komponenten keine eigenen Farbwerte.
 */
export interface ChartPalette {
  /** `--c-ink` — Tooltip-Grund. */
  readonly ink: string;
  /** `--c-ink-2` — Achsenbeschriftung. */
  readonly ink2: string;
  /** `--c-surface` — Kartenfläche; Tooltip-Text und Donut-Trennlinie. */
  readonly surface: string;
  /** `--c-line` — Gitterlinien. */
  readonly line: string;
  /** `--c-line-strong` — neutrale Balken (Historie). */
  readonly lineStrong: string;
  /** `--c-accent` — hervorgehobener Balken (laufender Monat). */
  readonly accent: string;
  /** Kategorie-Slug → Farbe aus `--cat-<slug>`, für alle 13 Kategorien. */
  readonly categories: Readonly<Record<string, string>>;
  /** Effektive Schriftfamilie des Dokuments; leer, wenn (noch) nicht ermittelbar. */
  readonly fontFamily: string;
}

/**
 * Liefert die Token-Farben für Chart.js als Signal — inklusive Aktualisierung beim
 * Theme-Wechsel (FE-UI-05).
 *
 * <p>Ein Canvas kennt keine CSS-Variablen: Chart.js braucht aufgelöste Farbwerte. Die
 * werden hier einmal pro Theme aus `getComputedStyle(<html>)` gelesen. Wechselt
 * `data-theme` auf `<html>`, meldet ein `MutationObserver` das und das Signal liefert die
 * Farben des neuen Themes — die `computed()`-Chart-Configs der Komponenten bauen sich
 * dadurch von selbst neu auf. Der Prototyp löst dasselbe über ein `themechange`-Event
 * (`design/variant-a/charts.js`); in Angular ist das Signal das Äquivalent ohne globales
 * Event und ohne manuelles Zerstören der Chart-Instanzen.
 */
@Injectable({ providedIn: 'root' })
export class ChartTheme {
  private readonly document = inject(DOCUMENT);

  /**
   * Zähler, der bei jedem Theme-Wechsel hochgezählt wird. `getComputedStyle` ist keine
   * reaktive Quelle — dieser Zähler macht das Neulesen für `computed()` beobachtbar.
   */
  private readonly revision = signal(0);

  /** Token-Farben des aktiven Themes. */
  readonly palette: Signal<ChartPalette> = computed(() => {
    this.revision();
    return this.readPalette();
  });

  constructor() {
    const observer = new MutationObserver(() => this.revision.update((value) => value + 1));
    observer.observe(this.document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme'],
    });
    inject(DestroyRef).onDestroy(() => observer.disconnect());
  }

  private readPalette(): ChartPalette {
    const root = this.document.documentElement;
    const style = getComputedStyle(root);
    const read = (name: string): string => style.getPropertyValue(name).trim();

    const categories: Record<string, string> = {};
    for (const slug of CATEGORY_SLUGS) {
      categories[slug] = read(`--cat-${slug}`);
    }

    return {
      ink: read('--c-ink'),
      ink2: read('--c-ink-2'),
      surface: read('--c-surface'),
      line: read('--c-line'),
      lineStrong: read('--c-line-strong'),
      accent: read('--c-accent'),
      categories,
      // Die Schriftfamilie ist eine SCSS-Variable ($ff-base) und liegt auf `body`.
      // Von dort gelesen statt im TypeScript wiederholt — ein zweiter Font-Stack im
      // Code würde beim nächsten Design-Wechsel garantiert auseinanderlaufen.
      fontFamily: getComputedStyle(this.document.body).fontFamily,
    };
  }
}
