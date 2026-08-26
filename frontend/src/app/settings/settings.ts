import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';

/**
 * Einstellungen-Screen (FE-SET-01, US-14).
 *
 * <p>Route, Navigation und drei Abschnitts-Cards. Nur die «Passwort»-Card hat Inhalt
 * (FE-SET-02); Einkommen (FE-SET-03) und Erscheinungsbild (FE-SET-04) bleiben leer, bis
 * die jeweiligen Tasks sie füllen.
 *
 * <p>Kein Token- oder Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Component({
  selector: 'app-settings',
  imports: [ReactiveFormsModule, Card, Field, Input, Notice, Button],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Settings {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  /** `true`, sobald das Passwort in dieser Sitzung zuletzt erfolgreich geändert wurde. */
  readonly passwordSaved = signal(false);

  /** Fehlermeldung nach fehlgeschlagenem Passwort-Submit oder `null`. */
  readonly passwordErrorMessage = signal<string | null>(null);

  /** `true`, solange ein Passwort-Request läuft — sperrt den Submit-Button. */
  readonly passwordSubmitting = signal(false);

  readonly passwordForm = this.fb.nonNullable.group({
    aktuellesPasswort: ['', [Validators.required]],
    neuesPasswort: ['', [Validators.required, Validators.minLength(8)]],
  });

  /** Fehlermeldung fürs Feld "Aktuelles Passwort" oder `null`, solange gültig oder unberührt. */
  aktuellesPasswortError(): string | null {
    const control = this.passwordForm.controls.aktuellesPasswort;
    if (!control.touched || control.valid) {
      return null;
    }
    return 'Aktuelles Passwort ist erforderlich.';
  }

  /** Fehlermeldung fürs Feld "Neues Passwort" oder `null`, solange gültig oder unberührt. */
  neuesPasswortError(): string | null {
    const control = this.passwordForm.controls.neuesPasswort;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Neues Passwort ist erforderlich.';
    }
    if (control.hasError('minlength')) {
      return 'Passwort muss mindestens 8 Zeichen lang sein.';
    }
    return null;
  }

  submitPassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    // Beide Meldungen zurücksetzen: sonst stünde nach einem zweiten Versuch die alte
    // Erfolgsmeldung neben dem laufenden Request (analog fixed-cost-wizard.ts).
    this.passwordSaved.set(false);
    this.passwordErrorMessage.set(null);
    this.passwordSubmitting.set(true);

    const { aktuellesPasswort, neuesPasswort } = this.passwordForm.getRawValue();
    this.auth
      .changePassword(aktuellesPasswort, neuesPasswort)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.passwordSubmitting.set(false);
          this.passwordSaved.set(true);
          this.passwordForm.reset({ aktuellesPasswort: '', neuesPasswort: '' });
        },
        error: (err: HttpErrorResponse) => {
          this.passwordSubmitting.set(false);
          this.passwordErrorMessage.set(
            err.status === 400
              ? 'Aktuelles Passwort falsch'
              : 'Passwort konnte nicht geändert werden. Bitte versuche es später erneut.',
          );
        },
      });
  }
}
