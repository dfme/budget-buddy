import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { Theme } from '../core/theme/theme';
import { SafeToSpendService } from '../dashboard/safe-to-spend.service';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { formatSwissAmount } from '../shared/format';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';
import { Segment, SegmentOption } from '../shared/segment/segment';

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
 * <p>Route, Navigation und drei Abschnitts-Cards — «Passwort» (FE-SET-02), «Einkommen»
 * (FE-SET-03) und «Erscheinungsbild» (FE-SET-04) — sind alle gefüllt.
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
  private readonly safeToSpend = inject(SafeToSpendService);
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
          // Der Endpoint liefert 400 sowohl für ein falsches aktuelles Passwort als auch für
          // Bean-Validation-Fehler auf neuesPasswort (z. B. Leerzeichen-only, das clientseitig
          // an minLength(8) vorbeikommt, weil Validators.required nicht trimmt) — beide Fälle
          // liefern denselben Body {message: string}, der nie eine Nutzereingabe wiederholt.
          const message =
            err.status === 400 ? (err.error?.message as string | undefined) : undefined;
          this.passwordErrorMessage.set(
            message ?? 'Passwort konnte nicht geändert werden. Bitte versuche es später erneut.',
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
    // Ein Nutzer, der das Feld nach erfolgreichem Speichern oder einer Fehlermeldung erneut
    // ändert, ohne abzuschicken, soll die alte Meldung nicht mehr sehen — sie beträfe dann einen
    // Betrag, der so nie gespeichert wurde.
    this.incomeForm.controls.betrag.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.incomeSaved.set(false);
        this.incomeErrorMessage.set(null);
      });

    // Ein bereits erfasstes Einkommen macht den Call überflüssig: das Backend liefert für diesen
    // Fall laut SafeToSpendResponse-Doku ohnehin immer `incomeSuggestion: null`.
    if (this.auth.currentUser()?.monthlyIncome != null) {
      return;
    }

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
    // `maxDecimals` zuerst: bei z. B. 0.005 schlagen beide Validatoren an, aber „muss grösser
    // als 0 sein" ist irreführend für einen Betrag, der grösser als 0 ist — die eigentliche
    // Verletzung ist die Nachkommastellen-Regel.
    if (control.hasError('maxDecimals')) {
      return 'Betrag darf höchstens zwei Nachkommastellen haben.';
    }
    if (control.hasError('min')) {
      return 'Betrag muss grösser als 0 sein.';
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
