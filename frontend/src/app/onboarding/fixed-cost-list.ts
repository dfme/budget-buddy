import { CurrencyPipe, formatCurrency } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  LOCALE_ID,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { IncomeCard } from '../income/income-card';
import { RecurringExpenseList } from '../recurring/recurring-expense-list';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Field } from '../shared/field/field';
import { Input } from '../shared/input/input';
import { Modal } from '../shared/modal/modal';
import { Notice } from '../shared/notice/notice';
import {
  FixedCostDetail,
  FixedCostSummary,
  INTERVALL_OPTIONS,
  Intervall,
} from './fixed-cost.model';
import { FixedCostService } from './fixed-cost.service';
import { MIN_BETRAG_CHF, maxTwoDecimals, nonBlank } from './fixed-cost.validators';

/**
 * Die Seite «Budget» (`/budget`, FE-FC-09): ganz oben der eingebettete {@link IncomeCard} (bis
 * FE-FC-09 der Abschnitt «Einkommen», FE-SET-03, in den Einstellungen), darunter die Fixkosten-Übersicht mit Bearbeiten
 * und Löschen (FE-FC-03, US-03), darunter der Abschnitt «Erkannte Abos» (FE-FC-05, US-08). Das
 * Total aus Fixkosten und erkannten Abos (FE-FC-07) stand bis FE-STS-06 zwischen Einkommen und
 * Fixkosten; seither steht es als Card auf dem Dashboard und verlinkt hierher.
 *
 * <p>Sowohl der Abo- als auch der Einkommens-Abschnitt sind eingebettete Komponenten mit eigenem
 * State und eigenem Request — {@link RecurringExpenseList} bzw. {@link IncomeCard}, Letztere auch
 * im Onboarding-Wizard eingebettet. Diese Klasse lädt, bearbeitet und löscht nur Fixkosten, wie
 * vor FE-FC-05; die Zusammenführung ist eine Frage der Seite, nicht der Daten (kein
 * Datenmodell-Merge, siehe #338).
 *
 * <p>Lädt `GET /api/fixed-costs` beim Start in ein einziges {@link summary}-Signal — Positionen,
 * Monatssumme, Einkommen und `exceedsIncome` kommen serverseitig bereits berechnet zusammen
 * (`FixedCostSummaryResponse`), damit stimmen Tabelle und Warnung immer überein. Jede
 * schreibende Aktion (Bearbeiten, Löschen) lädt danach neu, statt den State lokal
 * fortzuschreiben: `summeMonatlich` und `exceedsIncome` hängen von allen Positionen ab, ein
 * lokales Update müsste dieselbe Rechnung duplizieren, die das Backend schon macht.
 *
 * <p>Bearbeiten klappt die betroffene Zeile in ein vorausgefülltes Formular auf — dieselben
 * Validatoren wie im Onboarding-Wizard ({@link nonBlank}, {@link maxTwoDecimals}), aus
 * `fixed-cost.validators.ts` geteilt statt dupliziert. Löschen fragt über {@link Modal} nach,
 * bevor `DELETE /api/fixed-costs/{id}` läuft.
 */
@Component({
  selector: 'app-fixed-cost-list',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    RouterLink,
    Button,
    Card,
    Field,
    IncomeCard,
    Input,
    Modal,
    Notice,
    RecurringExpenseList,
  ],
  templateUrl: './fixed-cost-list.html',
  styleUrl: './fixed-cost-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FixedCostList implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly fixedCosts = inject(FixedCostService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly locale = inject(LOCALE_ID);

  /** Auswahl des Intervall-Dropdowns in der Bearbeiten-Form. */
  readonly intervallOptions = INTERVALL_OPTIONS;

  /** Positionen, Monatssumme, Einkommen und Warn-Flag — `null`, solange nicht geladen. */
  readonly summary = signal<FixedCostSummary | null>(null);

  /** `true`, solange die Liste (noch) lädt. */
  readonly loading = signal(true);

  /** Fehlermeldung, falls das Laden fehlschlägt. */
  readonly errorMessage = signal<string | null>(null);

  /** ID der Position, die gerade im Bearbeiten-Formular steht — `null`, wenn keine. */
  readonly editingId = signal<number | null>(null);

  /** `true`, solange der Bearbeiten-Request läuft — sperrt den Speichern-Button. */
  readonly editSubmitting = signal(false);

  /** Fehlermeldung des Bearbeiten-Formulars oder `null`. */
  readonly editError = signal<string | null>(null);

  /** Position, für die die Löschen-Bestätigung offen steht — `null`, wenn keine. */
  readonly pendingDelete = signal<FixedCostDetail | null>(null);

  /** `true`, solange der Löschen-Request läuft. */
  readonly deleting = signal(false);

  /** Fehlermeldung nach fehlgeschlagenem Löschen oder `null`. */
  readonly deleteError = signal<string | null>(null);

  readonly editForm = this.fb.nonNullable.group({
    bezeichnung: ['', [nonBlank]],
    betrag: [
      null as number | null,
      [Validators.required, Validators.min(MIN_BETRAG_CHF), maxTwoDecimals],
    ],
    intervall: ['monatlich' as Intervall, [Validators.required]],
  });

  ngOnInit(): void {
    this.load();
  }

  /** Anzeigetext (mit Umlaut) für ein Intervall-Wire-Format. */
  intervallLabel(value: Intervall): string {
    return this.intervallOptions.find((option) => option.value === value)?.label ?? value;
  }

  /**
   * Unterzeile unter der Bezeichnung (FE-FC-08): `CHF 335.00 · jährlich`, bei monatlichen
   * Positionen nur `monatlich` — der Betrag wäre derselbe wie der Monatsbetrag daneben. Betrag
   * und Intervall haben seit FE-FC-08 keine eigenen Spalten mehr.
   *
   * <p>Hier statt im Template: ein `@if` innerhalb der Zeile setzte einen führenden Leerraum vor
   * den Betrag. Formatiert wie der `CurrencyPipe` im Template (`'CHF' : 'symbol' : '1.2-2'`).
   */
  subline(item: FixedCostDetail): string {
    const intervall = this.intervallLabel(item.intervall);
    if (item.intervall === 'monatlich') {
      return intervall;
    }
    return `${formatCurrency(item.betrag, this.locale, 'CHF', 'CHF', '1.2-2')} · ${intervall}`;
  }

  /** Fehlermeldung fürs Bezeichnungs-Feld der Bearbeiten-Form oder `null`. */
  bezeichnungError(): string | null {
    const control = this.editForm.controls.bezeichnung;
    if (!control.touched || control.valid) {
      return null;
    }
    return control.hasError('required') ? 'Bezeichnung ist erforderlich.' : null;
  }

  /** Fehlermeldung fürs Betrags-Feld der Bearbeiten-Form oder `null`. */
  betragError(): string | null {
    const control = this.editForm.controls.betrag;
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

  /** Öffnet die Bearbeiten-Form für `item`, vorausgefüllt mit den aktuellen Werten. */
  startEdit(item: FixedCostDetail): void {
    this.editError.set(null);
    this.editForm.reset({
      bezeichnung: item.bezeichnung,
      betrag: item.betrag,
      intervall: item.intervall,
    });
    this.editingId.set(item.id);
  }

  /** Schliesst die Bearbeiten-Form ohne zu speichern. */
  cancelEdit(): void {
    this.editingId.set(null);
    this.editError.set(null);
  }

  /** Speichert die Bearbeiten-Form für die Position `id` und lädt die Liste danach neu. */
  saveEdit(id: number): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.editError.set(null);
    this.editSubmitting.set(true);

    const { bezeichnung, betrag, intervall } = this.editForm.getRawValue();
    this.fixedCosts
      .update(id, { bezeichnung: bezeichnung.trim(), betrag: betrag as number, intervall })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.editSubmitting.set(false);
          this.editingId.set(null);
          this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.editSubmitting.set(false);
          this.editError.set(
            err.status === 400
              ? 'Die Eingaben wurden vom Server abgelehnt. Bitte prüfe Bezeichnung, Betrag und Intervall.'
              : 'Aktualisieren fehlgeschlagen. Bitte versuche es später erneut.',
          );
        },
      });
  }

  /** Öffnet die Löschen-Bestätigung für `item`. */
  requestDelete(item: FixedCostDetail): void {
    this.deleteError.set(null);
    this.pendingDelete.set(item);
  }

  /** Schliesst die Löschen-Bestätigung ohne zu löschen. */
  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  /** Löscht die Position der offenen Bestätigung und lädt die Liste danach neu. */
  confirmDelete(): void {
    const item = this.pendingDelete();
    if (!item || this.deleting()) {
      return;
    }
    this.deleting.set(true);
    this.fixedCosts
      .delete(item.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deleting.set(false);
          this.pendingDelete.set(null);
          this.load();
        },
        error: () => {
          this.deleting.set(false);
          this.pendingDelete.set(null);
          this.deleteError.set('Löschen fehlgeschlagen. Bitte versuche es später erneut.');
        },
      });
  }

  /**
   * Lädt die Fixkosten neu, nachdem die eingebettete {@link IncomeCard} ein neues Einkommen
   * gespeichert hat: `monthlyIncome` und `exceedsIncome` kommen im selben Response wie die
   * Positionen, die Warnung hängt also an diesem Request.
   */
  reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.fixedCosts
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => {
          this.loading.set(false);
          this.summary.set(summary);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set(
            'Fixkosten konnten nicht geladen werden. Bitte versuche es später erneut.',
          );
        },
      });
  }
}
