import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Notice } from '../shared/notice/notice';
import { INTERVALL_OPTIONS, Intervall } from './fixed-cost.model';
import { FixedCostService } from './fixed-cost.service';

/**
 * Kleinster erfassbarer Betrag in CHF. CHF-Beträge sind rappengenau (ADR-9), ein Rappen ist
 * damit die kleinste Einheit über null — die Regel «Betrag > 0» aus US-03 als konkreter Wert,
 * den `Validators.min` prüfen kann.
 */
const MIN_BETRAG_CHF = 0.01;

/**
 * Erfassungsformular für eine Fixkosten-Position (FE-FC-01, US-03).
 *
 * <p>Reactive Form mit `Bezeichnung`, `Betrag` (CHF > 0) und `Intervall`; jedes Feld meldet
 * seinen Fehler inline unter sich, sobald es berührt und ungültig ist. Nach erfolgreichem
 * Absenden bleibt das Formular stehen und wird geleert, damit mehrere Positionen
 * hintereinander erfassbar sind — Lara erfasst im Onboarding typischerweise Miete,
 * Krankenkasse und Handy am Stück.
 *
 * <p>Bewusst <em>nicht</em> Teil dieser Komponente: der Onboarding-Zwang samt
 * «Keine Fixkosten»-Bestätigung (FE-FC-02, #25) und die Liste mit Bearbeiten/Löschen
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
  private readonly destroyRef = inject(DestroyRef);

  /** Auswahl des Intervall-Dropdowns — Wert und Anzeigetext (mit Umlaut) getrennt. */
  readonly intervallOptions = INTERVALL_OPTIONS;

  /** Bezeichnung der zuletzt gespeicherten Position oder `null`, solange keine gespeichert ist. */
  readonly savedBezeichnung = signal<string | null>(null);

  /** Fehlermeldung nach fehlgeschlagenem Submit oder `null`. */
  readonly errorMessage = signal<string | null>(null);

  /** `true`, solange ein Request läuft — sperrt den Submit-Button. */
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    bezeichnung: ['', [Validators.required]],
    // `betrag` ist im Formular `number | null`: ein leeres <input type="number"> liefert
    // null, nicht ''. Ohne den Null-Typ würde `required` bei leerem Feld nicht greifen.
    betrag: [null as number | null, [Validators.required, Validators.min(MIN_BETRAG_CHF)]],
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
    return null;
  }

  /** Fehlermeldung fürs Intervall-Feld oder `null`, solange gültig oder unberührt. */
  intervallError(): string | null {
    const control = this.form.controls.intervall;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Intervall ist erforderlich.';
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
}
