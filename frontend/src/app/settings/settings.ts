import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { SafeToSpendService } from '../dashboard/safe-to-spend.service';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { formatSwissAmount } from '../shared/format';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';

/**
 * Lässt höchstens zwei Nachkommastellen zu — CHF ist rappengenau (ADR-9).
 *
 * <p>Absichtlich lokal statt aus `onboarding/fixed-cost.validators.ts` importiert: die
 * Feature-Ordner bleiben damit unabhängig (Konvention "Feature-Struktur nach Domäne").
 */
const maxTwoDecimals: ValidatorFn = (control) => {
  const value = control.value;
  if (value === null || value === '') {
    return null;
  }
  const [, decimals = ''] = String(value).split('.');
  return decimals.length <= 2 ? null : { maxDecimals: true };
};

/**
 * Einstellungen-Screen (FE-SET-01, US-14).
 *
 * <p>Route, Navigation und drei Abschnitts-Cards. Nur die «Einkommen»-Card hat Inhalt
 * (FE-SET-03); Passwort (FE-SET-02) und Erscheinungsbild (FE-SET-04) bleiben leer, bis die
 * jeweiligen Tasks sie füllen.
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
  private readonly safeToSpend = inject(SafeToSpendService);
  private readonly destroyRef = inject(DestroyRef);

  /** `true`, sobald das Einkommen in dieser Sitzung zuletzt erfolgreich gespeichert wurde. */
  readonly incomeSaved = signal(false);

  /** Fehlermeldung nach fehlgeschlagenem Einkommen-Submit oder `null`. */
  readonly incomeErrorMessage = signal<string | null>(null);

  /** `true`, solange ein Einkommen-Request läuft — sperrt den Submit-Button. */
  readonly incomeSubmitting = signal(false);

  /**
   * Aus den Gutschriften abgeleiteter Vorschlag (BE-STS-02), oder `null`, wenn keiner
   * vorliegt. Das Backend liefert ihn laut `SafeToSpendResponse` nur, solange kein Einkommen
   * erfasst ist — ein zusätzlicher Guard dagegen ist deshalb nicht nötig.
   */
  readonly incomeSuggestion = signal<number | null>(null);

  readonly incomeForm = this.fb.nonNullable.group({
    // `betrag` bleibt ohne `required`: das Feld ist laut AC1 optional, leer lassen heisst
    // "automatische Schätzung verwenden" (US-06).
    betrag: [
      this.auth.currentUser()?.monthlyIncome ?? (null as number | null),
      [Validators.min(0.01), maxTwoDecimals],
    ],
  });

  constructor() {
    // Läuft unabhängig vom aktuellen Einkommen: liegt bereits eines vor, liefert das Backend
    // laut SafeToSpendResponse-Doku ohnehin `incomeSuggestion: null`.
    this.safeToSpend
      .getSafeToSpend()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.incomeSuggestion.set(response.incomeSuggestion),
        // Der Vorschlag ist ein Nice-to-have der Card, kein Ladepfad des Screens — ein
        // Fehlschlag hier darf das Formular nicht blockieren.
        error: () => this.incomeSuggestion.set(null),
      });
  }

  /**
   * Vorschlagssatz analog zum Dashboard (FE-STS-03), oder `null`, wenn es nichts vorzuschlagen
   * gibt.
   */
  incomeSuggestionText(): string | null {
    const suggestion = this.incomeSuggestion();
    if (suggestion === null) {
      return null;
    }
    return `Regelmässige Gutschrift von ${formatSwissAmount(suggestion)} CHF erkannt — als Monatseinkommen übernehmen?`;
  }

  /** Fehlermeldung fürs Betrags-Feld oder `null`, solange gültig oder unberührt. */
  incomeError(): string | null {
    const control = this.incomeForm.controls.betrag;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('min')) {
      return 'Betrag muss grösser als 0 sein.';
    }
    if (control.hasError('maxDecimals')) {
      return 'Betrag darf höchstens zwei Nachkommastellen haben.';
    }
    return null;
  }

  /** Übernimmt den Vorschlag ins Feld und speichert ihn sofort — wie beim Dashboard (FE-STS-03). */
  applyIncomeSuggestion(): void {
    const suggestion = this.incomeSuggestion();
    if (suggestion === null || this.incomeSubmitting()) {
      return;
    }
    this.incomeForm.controls.betrag.setValue(suggestion);
    this.submitIncome();
  }

  submitIncome(): void {
    if (this.incomeForm.invalid) {
      this.incomeForm.markAllAsTouched();
      return;
    }

    const betrag = this.incomeForm.controls.betrag.value;
    // Leeres Feld ist gültig (kein Validator schlägt an), aber es gibt nichts zu speichern —
    // das Backend lehnt ein fehlendes `betrag` ohnehin ab (BE-AUTH-08).
    if (betrag === null) {
      return;
    }

    // Beide Meldungen zurücksetzen: sonst stünde nach einem zweiten Versuch die alte
    // Erfolgsmeldung neben dem laufenden Request.
    this.incomeSaved.set(false);
    this.incomeErrorMessage.set(null);
    this.incomeSubmitting.set(true);

    this.auth
      .updateIncome(betrag)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.incomeSubmitting.set(false);
          this.incomeSaved.set(true);
          this.incomeSuggestion.set(null);
        },
        error: (err: HttpErrorResponse) => {
          this.incomeSubmitting.set(false);
          // `message` ist laut IncomeErrorResponse-Doku zur direkten Anzeige gedacht.
          this.incomeErrorMessage.set(
            err.status === 400 && err.error?.message
              ? err.error.message
              : 'Einkommen konnte nicht gespeichert werden. Bitte versuche es später erneut.',
          );
        },
      });
  }
}
