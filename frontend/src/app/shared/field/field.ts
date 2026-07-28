import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
} from '@angular/core';

/**
 * Formularfeld der Design-Variante A (FE-UI-03): Label + Eingabe + optionale Fehlermeldung.
 *
 * <p>Die eigentliche Eingabe (z. B. ein `<input appInput>`) kommt per Content-Projection —
 * so behält der Consumer volle Kontrolle über `formControlName`, `type`, `autocomplete` usw.
 * Das Label wird über {@link inputId} per `for` mit der Eingabe verknüpft, die Fehlermeldung
 * per `aria-describedby` (a11y): so hört sie, wer im Feld steht, beim Fokus — bewusst ohne
 * Live-Region, denn eine Feldvalidierung soll den Screenreader nicht unterbrechen. Assertiv
 * ist nur der Formular-Fehler nach fehlgeschlagenem Submit ({@link Notice} mit `variant="error"`).
 *
 * <pre>&lt;app-field label="E-Mail" inputId="email" [error]="emailError()"&gt;
 *   &lt;input appInput id="email" type="email" formControlName="email" /&gt;
 * &lt;/app-field&gt;</pre>
 */
@Component({
  selector: 'app-field',
  templateUrl: './field.html',
  styleUrl: './field.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Field {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Sichtbarer Feldtitel, z. B. `"E-Mail"`. */
  readonly label = input.required<string>();

  /** `id` der projizierten Eingabe — verknüpft das `<label for>` damit (a11y). */
  readonly inputId = input.required<string>();

  /** Fehlermeldung unter dem Feld oder `null`/leer, wenn kein Fehler vorliegt. */
  readonly error = input<string | null>();

  /** `id` der Fehlermeldung — Ziel des `aria-describedby` an der Eingabe. */
  readonly errorId = computed(() => `${this.inputId()}-error`);

  constructor() {
    // Die Eingabe ist content-projected, `aria-describedby` lässt sich also nicht im
    // Template setzen. Das Steuerelement wird generisch gesucht statt über
    // `contentChild(Input)` — so trägt das Feld auch `<select>`/`<textarea>`, ohne eine
    // Abhängigkeit auf `appInput` zu erzwingen.
    effect(() => {
      const control = this.host.nativeElement.querySelector('input, select, textarea');
      if (!control) {
        return;
      }
      if (this.error()) {
        control.setAttribute('aria-describedby', this.errorId());
        control.setAttribute('aria-invalid', 'true');
      } else {
        control.removeAttribute('aria-describedby');
        control.removeAttribute('aria-invalid');
      }
    });
  }
}
