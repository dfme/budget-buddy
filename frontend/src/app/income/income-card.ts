import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { SafeToSpendService } from '../dashboard/safe-to-spend.service';
import { MIN_BETRAG_CHF, maxTwoDecimals } from '../onboarding/fixed-cost.validators';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Field } from '../shared/field/field';
import { formatSwissAmount } from '../shared/format';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';

/**
 * Abschnitt «Einkommen» (FE-FC-09, vormals FE-SET-03 in den Einstellungen): eigenes Formular für
 * `monthlyIncome` (`PUT /users/me/income`), mit optionalem Vorschlag aus erkannten Gutschriften
 * (BE-STS-02).
 *
 * <p>Eingebettet auf zwei Seiten — `onboarding/fixed-cost-list.html` (Budget-Seite, `/budget`)
 * und `onboarding/fixed-cost-wizard.html` (Onboarding) — deshalb als eigene Komponente statt
 * dupliziertem Formular, analog {@link RecurringExpenseList}: eigener State, eigener Request,
 * kein Datenmodell-Merge mit der einbettenden Seite. Ursprünglich ein Abschnitt von
 * `settings.ts`; das Formular selbst ist seit dem Umzug unverändert.
 *
 * <p>Rendert seine Zwischenüberschrift «Einkommen» selbst, analog «Erfasste Fixkosten» auf der
 * Budget-Seite — beide Einbettungsorte bekommen damit dieselbe Überschriften-Ebene ohne eigenes
 * Markup.
 */
@Component({
  selector: 'app-income-card',
  imports: [ReactiveFormsModule, Card, Field, Input, Notice, Button],
  templateUrl: './income-card.html',
  styleUrl: './income-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncomeCard {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly safeToSpend = inject(SafeToSpendService);
  private readonly destroyRef = inject(DestroyRef);

  /** `true`, sobald das Einkommen in dieser Sitzung zuletzt erfolgreich gespeichert wurde. */
  readonly incomeSaved = signal(false);

  /**
   * Feuert nach jedem erfolgreichen Speichern. Für einbettende Seiten, die Daten anzeigen, die
   * vom Einkommen abhängen — auf der Budget-Seite die Warnung «Fixkosten übersteigen dein
   * Einkommen», die sonst bis zur nächsten Navigation die alten Zahlen zeigte. Ein Ereignis statt eines
   * Effekts auf `incomeSaved()`: Neuladen ist die Reaktion auf einen Speichervorgang, kein
   * Zustand.
   */
  readonly saved = output<void>();

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
      [Validators.min(MIN_BETRAG_CHF), maxTwoDecimals],
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
          this.saved.emit();
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
