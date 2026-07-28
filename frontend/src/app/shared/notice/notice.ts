import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Hinweis-Banner der Design-Variante A (FE-UI-03). Der Inhalt kommt per Content-Projection.
 *
 * <p>Die Rolle leitet sich aus der Variante ab: `error` meldet sich als `role="alert"`
 * (assertiv — unterbricht den Screenreader, richtig für einen fehlgeschlagenen Submit),
 * `warning` und `info` als `role="status"` (höflich, nicht unterbrechend).
 */
@Component({
  selector: 'app-notice',
  template: '<ng-content />',
  styleUrl: './notice.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.role]': "variant() === 'error' ? 'alert' : 'status'",
    '[class.notice--info]': "variant() === 'info'",
    '[class.notice--error]': "variant() === 'error'",
  },
})
export class Notice {
  /** `warning` (Default, warm), `info` (Akzent) oder `error` (rot, assertiv). */
  readonly variant = input<'warning' | 'info' | 'error'>('warning');
}
