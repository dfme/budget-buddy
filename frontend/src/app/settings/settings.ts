import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { Theme } from '../core/theme/theme';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';
import { Segment, SegmentOption } from '../shared/segment/segment';

/**
 * Einstellungen-Screen (FE-SET-01, US-14).
 *
 * <p>Route, Navigation und drei Abschnitts-Cards. «Passwort» (FE-SET-02) und
 * «Erscheinungsbild» (FE-SET-04) haben Inhalt; Einkommen (FE-SET-03) bleibt leer, bis der
 * Task sie füllt.
 *
 * <p>Kein Token- oder Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Component({
  selector: 'app-settings',
  imports: [ReactiveFormsModule, Card, Field, Input, Notice, Button, Segment],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Settings {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  /** Quelle und Ziel der Theme-Wahl; das Template liest `preference()` daraus. */
  protected readonly theme = inject(Theme);

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
