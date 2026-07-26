import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { CATEGORIES } from '../shared/category';
import { Amount } from '../shared/amount/amount';
import { Badge } from '../shared/badge/badge';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Chip } from '../shared/chip/chip';
import { Meter } from '../shared/meter/meter';
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
  imports: [Amount, Badge, Button, Card, Chip, Meter, MonthNav, Notice, Segment],
  templateUrl: './styleguide.html',
  styleUrl: './styleguide.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Styleguide {
  private readonly document = inject(DOCUMENT);

  /** Alle 13 Kategorien für die Badge-Galerie. */
  readonly categories = CATEGORIES;

  /**
   * Dev-only Theme-Zustand. Initialisiert aus dem aktuellen `data-theme` auf `<html>`,
   * damit der Button den echten Zustand zeigt (Default: hell).
   */
  readonly theme = signal<'light' | 'dark'>(
    this.document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light',
  );

  /** Schaltet `data-theme` auf `<html>` zwischen hell und dunkel um (nur für den Review). */
  toggleTheme(): void {
    const next = this.theme() === 'dark' ? 'light' : 'dark';
    this.document.documentElement.setAttribute('data-theme', next);
    this.theme.set(next);
  }

  readonly segmentOptions: readonly SegmentOption[] = [
    { value: 'all', label: 'Alle' },
    { value: 'expense', label: 'Ausgaben' },
    { value: 'income', label: 'Einnahmen' },
  ];
  readonly segmentValue = signal('all');

  readonly chipSelected = signal(true);

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
