import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Registrierungs-Formular (US-01). Reactive Form mit E-Mail + Passwort (≥ 8 Zeichen),
 * die den {@link AuthService} nutzt. Bei Erfolg legt das Backend das Konto an, setzt
 * das JWT-Cookie und wir leiten aufs Dashboard weiter. Eine bereits vergebene E-Mail
 * (409) wird als eindeutige Meldung angezeigt.
 *
 * <p>Kein Token-/Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Fehlermeldung unterhalb des Formulars oder `null`, wenn kein Fehler vorliegt. */
  readonly errorMessage = signal<string | null>(null);

  /** `true`, solange ein Register-Request läuft — sperrt den Submit-Button. */
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    const { email, password } = this.form.getRawValue();
    this.auth.register(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          err.status === 409
            ? 'E-Mail bereits vergeben'
            : 'Registrierung fehlgeschlagen. Bitte versuche es später erneut.',
        );
      },
    });
  }
}
