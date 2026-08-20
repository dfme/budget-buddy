import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';
import { INTERVALL_OPTIONS, Intervall } from './fixed-cost.model';
import { FixedCostService } from './fixed-cost.service';
import { MIN_BETRAG_CHF, maxTwoDecimals, nonBlank } from './fixed-cost.validators';

/**
 * Erfassungsformular für eine Fixkosten-Position (FE-FC-01, US-03).
 *
 * <p>Reactive Form mit `Bezeichnung`, `Betrag` (CHF > 0) und `Intervall`; jedes Feld meldet
 * seinen Fehler inline unter sich, sobald es berührt und ungültig ist. Nach erfolgreichem
 * Absenden bleibt das Formular stehen und wird geleert, damit mehrere Positionen
 * hintereinander erfassbar sind — Lara erfasst im Onboarding typischerweise Miete,
 * Krankenkasse und Handy am Stück.
 *
 * <p>Der Abschluss des Onboardings hängt seit FE-FC-02 (#25) hier: ein Button unter dem
 * Formular ruft `POST /api/users/me/onboarding-complete` und navigiert aufs Dashboard. Er
 * deckt beide Wege aus US-03 ab — «Keine Fixkosten» bestätigen und «mindestens ein
 * Eintrag gespeichert»; unterschieden werden sie nur durch die Beschriftung, die Aktion
 * ist dieselbe.
 *
 * <p>Bewusst <em>nicht</em> Teil dieser Komponente: die Liste mit Bearbeiten/Löschen
 * (FE-FC-03, #26). Der Name «Wizard» benennt die Komponente, nicht den Ablauf.
 *
 * <p>Kein Token- oder Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Component({
  selector: 'app-fixed-cost-wizard',
  imports: [ReactiveFormsModule, Card, Field, Input, Notice, Button],
  templateUrl: './fixed-cost-wizard.html',
  styleUrl: './fixed-cost-wizard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FixedCostWizard {
  private readonly fb = inject(FormBuilder);
  private readonly fixedCosts = inject(FixedCostService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Auswahl des Intervall-Dropdowns — Wert und Anzeigetext (mit Umlaut) getrennt. */
  readonly intervallOptions = INTERVALL_OPTIONS;

  /** Bezeichnung der zuletzt gespeicherten Position oder `null`, solange keine gespeichert ist. */
  readonly savedBezeichnung = signal<string | null>(null);

  /** Fehlermeldung nach fehlgeschlagenem Submit oder `null`. */
  readonly errorMessage = signal<string | null>(null);

  /** `true`, solange ein Request läuft — sperrt den Submit-Button. */
  readonly submitting = signal(false);

  /**
   * `true`, sobald in dieser Sitzung mindestens eine Position gespeichert wurde.
   *
   * <p>Steuert ausschliesslich die Beschriftung des Abschluss-Buttons, nicht seine Wirkung:
   * beide Wege aus US-03 lösen denselben Request aus. Der Wert ist bewusst sitzungslokal
   * und wird nicht aus `GET /api/fixed-costs` abgeleitet — ein Request nur für die Wortwahl
   * eines Buttons wäre nicht zu rechtfertigen, und die bereits erfassten Positionen zeigt
   * ohnehin erst die Liste aus FE-FC-03 (#26).
   */
  readonly hasSaved = signal(false);

  /** `true`, solange der Abschluss-Request läuft — sperrt den Abschluss-Button. */
  readonly completing = signal(false);

  /** Fehlermeldung nach fehlgeschlagenem Abschluss oder `null`. */
  readonly completeError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    bezeichnung: ['', [nonBlank]],
    // `betrag` ist im Formular `number | null`: ein leeres <input type="number"> liefert
    // null, nicht ''. Ohne den Null-Typ würde `required` bei leerem Feld nicht greifen.
    betrag: [
      null as number | null,
      [Validators.required, Validators.min(MIN_BETRAG_CHF), maxTwoDecimals],
    ],
    // `required` ist hier reine Absicherung und kann heute nicht feuern: das Feld startet auf
    // `monatlich`, und ein natives <select> ohne Leer-Option kann keinen leeren Wert annehmen.
    // Entsprechend zeigt das Template unter dem Intervall auch keine Fehlermeldung an. Käme
    // später eine Platzhalter-Option («bitte wählen») dazu, hielte der Validator das Formular
    // ungültig — dann gehört auch eine Meldung dazu.
    intervall: ['monatlich' as Intervall, [Validators.required]],
  });

  /** Fehlermeldung fürs Bezeichnungs-Feld oder `null`, solange gültig oder unberührt. */
  bezeichnungError(): string | null {
    const control = this.form.controls.bezeichnung;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Bezeichnung ist erforderlich.';
    }
    return null;
  }

  /** Fehlermeldung fürs Betrags-Feld oder `null`, solange gültig oder unberührt. */
  betragError(): string | null {
    const control = this.form.controls.betrag;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Betrag ist erforderlich.';
    }
    if (control.hasError('min')) {
      return 'Betrag muss grösser als 0 sein.';
    }
    if (control.hasError('maxDecimals')) {
      return 'Betrag darf höchstens zwei Nachkommastellen haben.';
    }
    return null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // Beide Meldungen zurücksetzen: sonst stünde nach einem zweiten Versuch die alte
    // Erfolgsmeldung neben dem laufenden Request.
    this.savedBezeichnung.set(null);
    this.errorMessage.set(null);
    this.submitting.set(true);

    // `betrag` ist im Formulartyp `number | null`; hier nicht mehr; `Validators.required` und
    // der Invalid-Check oben schliessen null aus. Der Cast hält den Request-Typ ehrlich, statt
    // `null` in den Contract zu lassen.
    const { bezeichnung, betrag, intervall } = this.form.getRawValue();
    this.fixedCosts
      .create({ bezeichnung: bezeichnung.trim(), betrag: betrag as number, intervall })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.savedBezeichnung.set(created.bezeichnung);
          this.hasSaved.set(true);
          // Leeren statt `reset()`: das Intervall soll wieder auf dem Default stehen und
          // nicht auf `null` fallen, sonst ist das Dropdown nach dem Speichern leer.
          this.form.reset({ bezeichnung: '', betrag: null, intervall: 'monatlich' });
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(
            err.status === 400
              ? 'Die Eingaben wurden vom Server abgelehnt. Bitte prüfe Bezeichnung, Betrag und Intervall.'
              : 'Speichern fehlgeschlagen. Bitte versuche es später erneut.',
          );
        },
      });
  }

  /**
   * Schliesst das Onboarding ab und navigiert aufs Dashboard (US-03).
   *
   * <p>Deckt beide Wege ab, mit denen der Wizard laut US-03 verlassen werden darf: die
   * ausdrückliche «Keine Fixkosten»-Bestätigung und den Abschluss nach mindestens einer
   * gespeicherten Position. Das Backend setzt `onboardingCompleted`, der `AuthService`
   * übernimmt das aktualisierte Profil in den State — erst dadurch lässt der
   * `onboardingGuard` die Navigation aufs Dashboard passieren.
   *
   * <p>Bei einem Fehler bleibt der Nutzer im Wizard und sieht eine Meldung: eine
   * Navigation trotz gescheitertem Abschluss würde der Guard sofort zurückdrehen und
   * sähe für den Nutzer wie ein Sprung ins Leere aus.
   */
  finishOnboarding(): void {
    this.completeError.set(null);
    this.completing.set(true);

    this.auth
      .completeOnboarding()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.completing.set(false);
          this.router.navigate(['/dashboard']);
        },
        error: () => {
          this.completing.set(false);
          this.completeError.set(
            'Onboarding konnte nicht abgeschlossen werden. Bitte versuche es später erneut.',
          );
        },
      });
  }
}
