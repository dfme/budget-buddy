import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Hinweis-Banner der Design-Variante A (FE-UI-03).
 *
 * <p>`role="status"` meldet den Text Screenreadern höflich (nicht unterbrechend). Der
 * Inhalt kommt per Content-Projection.
 */
@Component({
  selector: 'app-notice',
  template: '<ng-content />',
  styleUrl: './notice.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'status',
    '[class.notice--info]': "variant() === 'info'",
  },
})
export class Notice {
  /** `warning` (Default, warm) oder `info` (Akzent). */
  readonly variant = input<'warning' | 'info'>('warning');
}
