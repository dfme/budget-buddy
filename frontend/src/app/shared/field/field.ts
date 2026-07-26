import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Formularfeld der Design-Variante A (FE-UI-03): Label + Eingabe + optionale Fehlermeldung.
 *
 * <p>Die eigentliche Eingabe (z. B. ein `<input appInput>`) kommt per Content-Projection —
 * so behält der Consumer volle Kontrolle über `formControlName`, `type`, `autocomplete` usw.
 * Das Label wird über {@link inputId} per `for` mit der Eingabe verknüpft (a11y); die
 * Fehlermeldung meldet sich als `role="alert"`.
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
  /** Sichtbarer Feldtitel, z. B. `"E-Mail"`. */
  readonly label = input.required<string>();

  /** `id` der projizierten Eingabe — verknüpft das `<label for>` damit (a11y). */
  readonly inputId = input.required<string>();

  /** Fehlermeldung unter dem Feld oder `null`/leer, wenn kein Fehler vorliegt. */
  readonly error = input<string | null>();
}
