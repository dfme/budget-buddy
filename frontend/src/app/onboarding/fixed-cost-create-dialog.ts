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

import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Modal } from '../shared/modal/modal';
import { Notice } from '../shared/notice/notice';
import { INTERVALL_OPTIONS, Intervall } from './fixed-cost.model';
import { FixedCostService } from './fixed-cost.service';
import {
  MIN_BETRAG_CHF,
  betragErrorMessage,
  bezeichnungErrorMessage,
  maxTwoDecimals,
  nonBlank,
} from './fixed-cost.validators';

/**
 * Dialog «Neue Position» (FE-FC-10, US-03): legt eine Fixkosten-Position an, ohne die Seite zu
 * verlassen. Bis FE-FC-10 führten «+ Neue Position» und «Jetzt erfassen» auf der Budget-Seite per
 * `routerLink` in den Onboarding-Wizard. Seit FE-FC-13 öffnet ihn der gemeinsame
 * `FixedCostSection` auf `/budget` und im Wizard — der einzige Weg, eine Position anzulegen.
 *
 * <p>Wie {@link Modal} ohne eigenen Offen-Zustand: der Parent rendert den Dialog per `@if` und
 * entfernt ihn auf {@link saved} oder {@link cancelled}. Damit startet jedes Öffnen mit einem
 * frischen, leeren Formular — ohne Reset-Logik, die ein Feld vergessen könnte.
 *
 * <p>Dieselben Regeln wie der Inline-Edit: Validatoren und Fehlertexte kommen aus
 * `fixed-cost.validators.ts`. Das Formular ist über `confirmForm` mit dem Speichern-Button des
 * Modals verknüpft — Klick und Enter laufen beide über {@link submit}.
 *
 * <p>Nach dem Speichern emittiert der Dialog nur {@link saved}; das Neuladen der Liste ist Sache
 * des Parents, der Tabelle, Total und Einkommens-Warnung aus einem einzigen Request speist.
 */
@Component({
  selector: 'app-fixed-cost-create-dialog',
  imports: [ReactiveFormsModule, Field, Input, Modal, Notice],
  templateUrl: './fixed-cost-create-dialog.html',
  styleUrl: './fixed-cost-create-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FixedCostCreateDialog {
  private readonly fb = inject(FormBuilder);
  private readonly fixedCosts = inject(FixedCostService);
  private readonly destroyRef = inject(DestroyRef);

  /** Die Position wurde angelegt — der Parent schliesst den Dialog und lädt neu. */
  readonly saved = output<void>();

  /** Abgebrochen über «Abbrechen», Escape oder den Hintergrund, ohne Request. */
  readonly cancelled = output<void>();

  /** ID des Formulars, über die das Modal seinen Speichern-Button damit verknüpft. */
  readonly formId = 'fixed-cost-create-form';

  /** Auswahl des Intervall-Dropdowns. */
  readonly intervallOptions = INTERVALL_OPTIONS;

  /** `true`, solange der Request läuft — sperrt «Speichern» und das Abbrechen. */
  readonly submitting = signal(false);

  /** Fehlermeldung nach fehlgeschlagenem Speichern oder `null`. */
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    bezeichnung: ['', [nonBlank]],
    betrag: [
      null as number | null,
      [Validators.required, Validators.min(MIN_BETRAG_CHF), maxTwoDecimals],
    ],
    intervall: ['monatlich' as Intervall, [Validators.required]],
  });

  /** Fehlermeldung fürs Bezeichnungs-Feld oder `null`. */
  bezeichnungError(): string | null {
    return bezeichnungErrorMessage(this.form.controls.bezeichnung);
  }

  /** Fehlermeldung fürs Betrags-Feld oder `null`. */
  betragError(): string | null {
    return betragErrorMessage(this.form.controls.betrag);
  }

  /**
   * Legt die Position an. Ein ungültiges Formular zeigt seine Fehler und sendet nichts.
   *
   * <p>Der Guard auf {@link submitting} ist nicht doppelt zum gesperrten Button: ein gesperrter
   * Default-Button unterdrückt zwar auch das Enter, aber `ngSubmit` ist ein öffentlicher Einstieg
   * und soll nicht davon abhängen, wie der Browser Implicit Submission umsetzt.
   */
  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);

    const { bezeichnung, betrag, intervall } = this.form.getRawValue();
    this.fixedCosts
      .create({ bezeichnung: bezeichnung.trim(), betrag: betrag as number, intervall })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => {
          // Eingaben bleiben stehen: der Dialog bleibt offen, und das Formular wird nicht
          // angefasst — ein zweiter Versuch braucht kein erneutes Tippen.
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
   * Bricht ab — ausser während der Request läuft. Der Dialog würde sonst entfernt,
   * `takeUntilDestroyed` beendete die Subscription, die Position entstünde trotzdem, und die
   * Tabelle lüde nicht neu. Gleiches Muster wie `Settings.cancelDeleteDialog`.
   */
  cancel(): void {
    if (this.submitting()) {
      return;
    }
    this.cancelled.emit();
  }
}
