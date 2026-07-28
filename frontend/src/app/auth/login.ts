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
 * Login-Formular (US-01). Reactive Form mit E-Mail + Passwort, die den
 * {@link AuthService} nutzt. Bei Erfolg Redirect aufs Dashboard; bei falschen
 * Credentials (401) eine bewusst unspezifische Meldung, die nicht verrät, ob die
 * E-Mail existiert.
 *
 * <p>Kein Token-/Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, Card, Field, Input, Notice, Button],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Fehlermeldung unterhalb des Formulars oder `null`, wenn kein Fehler vorliegt. */
  readonly errorMessage = signal<string | null>(null);

  /** `true`, solange ein Login-Request läuft — sperrt den Submit-Button. */
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
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
    return 'Passwort ist erforderlich.';
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          err.status === 401
            ? 'E-Mail oder Passwort falsch'
            : 'Login fehlgeschlagen. Bitte versuche es später erneut.',
        );
      },
    });
  }
}
