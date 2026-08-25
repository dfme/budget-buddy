import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Theme } from '../core/theme/theme';
import { Card } from '../shared/card/card';
import { Segment, SegmentOption } from '../shared/segment/segment';

/**
 * Einstellungen-Screen (FE-SET-01, US-14).
 *
 * <p>Route, Navigation und die Abschnitts-Cards. „Erscheinungsbild" ist mit FE-SET-04
 * gefüllt; Passwort ändern (FE-SET-02) und Einkommen ändern (FE-SET-03) folgen noch.
 */
@Component({
  selector: 'app-settings',
  imports: [Card, Segment],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Settings {
  /** Quelle und Ziel der Theme-Wahl; das Template liest `preference()` daraus. */
  protected readonly theme = inject(Theme);

  /** Die drei Optionen des Abschnitts „Erscheinungsbild". */
  protected readonly themeOptions: readonly SegmentOption[] = [
    { value: 'light', label: 'Hell' },
    { value: 'dark', label: 'Dunkel' },
    { value: 'system', label: 'System' },
  ];

  /**
   * Übernimmt die Wahl aus dem Segment-Umschalter.
   *
   * <p>Die Bindung ist bewusst einweg plus Event statt `[(value)]`: {@link Theme} nimmt
   * Änderungen nur über {@link Theme#select} an, weil dort neben dem Signal auch der
   * `localStorage` geschrieben wird. {@link Segment} tippt seinen Wert als `string` —
   * alles ausserhalb der drei Optionen kann nur aus einem Programmierfehler stammen und
   * wird ignoriert, statt einen ungültigen Zustand ins Theme zu tragen.
   */
  protected selectTheme(value: string | undefined): void {
    if (value === 'light' || value === 'dark' || value === 'system') {
      this.theme.select(value);
    }
  }
}
