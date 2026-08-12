import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';
import { AuthService } from './auth.service';

/**
 * Registrierungs-Formular (US-01). Reactive Form mit E-Mail + Passwort (≥ 8 Zeichen),
 * die den {@link AuthService} nutzt. Bei Erfolg legt das Backend das Konto an, setzt
 * das JWT-Cookie und wir leiten je nach `onboardingCompleted` direkt auf den Wizard oder
 * das Dashboard weiter — ein frisches Konto hat nie `onboardingCompleted = true`, aber der
 * direkte Sprung erspart den Umweg über den `onboardingGuard`-Redirect (FE-FC-02). Eine
 * bereits vergebene E-Mail (409) wird als eindeutige Meldung angezeigt.
 *
 * <p>Kein Token-/Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, Card, Field, Input, Notice, Button],
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

  /** Fehlermeldung fürs E-Mail-Feld oder `null`, solange es gültig oder unberührt ist. */
  emailError(): string | null {
    const control = this.form.controls.email;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return 'E-Mail ist erforderlich.';
    }
    if (control.hasError('email')) {
      return 'Bitte eine gültige E-Mail-Adresse eingeben.';
    }
    return null;
  }

  /** Fehlermeldung fürs Passwort-Feld oder `null`, solange es gültig oder unberührt ist. */
  passwordError(): string | null {
    const control = this.form.controls.password;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Passwort ist erforderlich.';
    }
    if (control.hasError('minlength')) {
      return 'Passwort muss mindestens 8 Zeichen lang sein.';
    }
    return null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    const { email, password } = this.form.getRawValue();
    this.auth.register(email, password).subscribe({
      next: (user) => {
        this.submitting.set(false);
        this.router.navigate([user.onboardingCompleted ? '/dashboard' : '/onboarding']);
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
