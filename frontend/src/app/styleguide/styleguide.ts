import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { Theme } from '../core/theme/theme';
import { CATEGORIES } from '../shared/category';
import { Amount } from '../shared/amount/amount';
import { Badge } from '../shared/badge/badge';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { BarChart, BarPoint } from '../shared/chart/bar-chart';
import { DonutChart, DonutSlice } from '../shared/chart/donut-chart';
import { Chip } from '../shared/chip/chip';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Meter } from '../shared/meter/meter';
import { Modal } from '../shared/modal/modal';
import { MonthNav } from '../shared/month-nav/month-nav';
import { Notice } from '../shared/notice/notice';
import { Segment, SegmentOption } from '../shared/segment/segment';

/**
 * Dev-only Showcase («Kitchen-Sink») aller Shared-Basiskomponenten (FE-UI-03).
 *
 * <p>Nur über die Direkt-URL `/styleguide` erreichbar (nicht in der Navigation,
 * {@link devOnlyGuard} sperrt Prod). Dient dem visuellen Review und als lebender Nachweis,
 * dass die Komponenten auf den Tokens aus FE-UI-02 aufsetzen.
 */
@Component({
  selector: 'app-styleguide',
  imports: [
    Amount,
    Badge,
    BarChart,
    Button,
    Card,
    Chip,
    DonutChart,
    Field,
    Input,
    Meter,
    Modal,
    MonthNav,
    Notice,
    Segment,
  ],
  templateUrl: './styleguide.html',
  styleUrl: './styleguide.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Styleguide {
  private readonly themeService = inject(Theme);

  /** Alle 13 Kategorien für die Badge-Galerie. */
  readonly categories = CATEGORIES;

  /**
   * Dargestelltes Theme, für die Beschriftung des Review-Buttons.
   *
   * <p>Kommt seit FE-SET-04 aus {@link Theme} statt aus einem eigenen Signal. Vorher schrieb
   * der Toggle `data-theme` selbst — neben dem Service wären das zwei Schreiber auf einem
   * Attribut, und bei Präferenz „System" hätte der nächste Wechsel im Betriebssystem den
   * Toggle stillschweigend zurückgedreht.
   */
  readonly theme = this.themeService.resolved;

  /** Schaltet für den visuellen Review zwischen hell und dunkel um. */
  toggleTheme(): void {
    this.themeService.select(this.theme() === 'dark' ? 'light' : 'dark');
  }

  /**
   * Demo-Daten für die Charts (FE-UI-05), übernommen aus `design/variant-a/charts.js` —
   * damit sich der Angular-Port direkt mit dem Prototyp vergleichen lässt.
   */
  readonly demoSpending: readonly DonutSlice[] = [
    { slug: 'wohnen', label: 'Wohnen', value: 980.0 },
    { slug: 'lebensmittel', label: 'Lebensmittel', value: 412.65 },
    { slug: 'transport', label: 'Transport', value: 185.0 },
    { slug: 'versicherung', label: 'Versicherung', value: 168.4 },
    { slug: 'restaurant', label: 'Restaurant', value: 142.8 },
    { slug: 'gesundheit', label: 'Gesundheit', value: 108.0 },
    { slug: 'freizeit', label: 'Freizeit', value: 96.5 },
    { slug: 'shopping', label: 'Shopping', value: 78.9 },
    { slug: 'telekom', label: 'Telekom', value: 59.0 },
    { slug: 'sonstiges', label: 'Sonstiges', value: 34.15 },
  ];

  readonly demoMonths: readonly BarPoint[] = [
    { label: 'Feb', value: 2340.1 },
    { label: 'Mär', value: 2512.75 },
    { label: 'Apr', value: 2198.4 },
    { label: 'Mai', value: 2640.2 },
    { label: 'Jun', value: 2405.6 },
    { label: 'Jul', value: 2265.4 },
  ];

  readonly segmentOptions: readonly SegmentOption[] = [
    { value: 'all', label: 'Alle' },
    { value: 'expense', label: 'Ausgaben' },
    { value: 'income', label: 'Einnahmen' },
  ];
  readonly segmentValue = signal('all');

  readonly chipSelected = signal(true);

  /** Offen-Zustand des Modal-Beispiels — der Dialog selbst hält keinen (siehe {@link Modal}). */
  readonly modalOpen = signal(false);

  /** Kleiner Demo-Zustand für die MonthNav. */
  private readonly months = ['Mai 2026', 'Juni 2026', 'Juli 2026'];
  readonly monthIndex = signal(this.months.length - 1);
  readonly monthLabel = () => this.months[this.monthIndex()];

  prevMonth(): void {
    this.monthIndex.update((i) => Math.max(0, i - 1));
  }
  nextMonth(): void {
    this.monthIndex.update((i) => Math.min(this.months.length - 1, i + 1));
  }
}
