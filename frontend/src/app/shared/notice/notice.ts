import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Variante des Hinweis-Banners. */
export type NoticeVariant = 'warning' | 'info' | 'error';

/**
 * Icon je Variante. Zeichen statt Icon-Font oder SVG — dieselbe Wahl wie im Rest der App
 * (`shell.html`, `pdf-upload.html`). `!` ist aus der Design-Baseline übernommen
 * (`design/variant-a/index.html`, `transactions.html`); `⚠` und `ℹ` wären naheliegender,
 * werden aber auf macOS/iOS und Android häufig als farbige Emoji gerendert und brächen
 * damit aus der Farbgebung der Variante aus.
 */
const ICONS: Record<NoticeVariant, string> = {
  warning: '!',
  info: 'i',
  error: '✕',
};

/**
 * Hinweis-Banner der Design-Variante A (FE-UI-03). Der Inhalt kommt per Content-Projection,
 * Icon und optionaler Titel liefert die Komponente selbst (FE-UI-07).
 *
 * <p>Die Rolle leitet sich aus der Variante ab: `error` meldet sich als `role="alert"`
 * (assertiv — unterbricht den Screenreader, richtig für einen fehlgeschlagenen Submit),
 * `warning` und `info` als `role="status"` (höflich, nicht unterbrechend). Das Icon ist
 * `aria-hidden` und trägt die Variante deshalb nicht doppelt vor.
 */
@Component({
  selector: 'app-notice',
  templateUrl: './notice.html',
  styleUrl: './notice.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.role]': "variant() === 'error' ? 'alert' : 'status'",
    // `title` ist zugleich ein globales HTML-Attribut: schreibt ein Aufrufort ihn statisch
    // (`title="…"`), setzt Angular den Input *und* lässt das Attribut im DOM stehen. Das
    // gäbe einen nativen Tooltip über dem ganzen Banner und — schwerer wiegend — einen
    // Accessible Name auf der Live-Region, der den sichtbaren Titel doppelt. Hier entfernt.
    '[attr.title]': 'null',
    '[class.notice--info]': "variant() === 'info'",
    '[class.notice--error]': "variant() === 'error'",
  },
})
export class Notice {
  /** `warning` (Default, warm), `info` (Akzent) oder `error` (rot, assertiv). */
  readonly variant = input<NoticeVariant>('warning');

  /** Optionaler Titel. Ist er gesetzt, steht er über dem projizierten Inhalt. */
  readonly title = input<string>();

  /** Zeichen zur aktuellen Variante — der Aufrufort schreibt kein Icon mehr selbst. */
  readonly icon = computed(() => ICONS[this.variant()]);
}
