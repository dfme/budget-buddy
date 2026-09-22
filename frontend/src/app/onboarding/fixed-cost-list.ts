import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { RecurringExpenseList } from '../recurring/recurring-expense-list';
import { RecurringExpenseService } from '../recurring/recurring-expense.service';
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

/** Das Total der monatlichen fixen Ausgaben mit seinen beiden Summanden, in CHF (FE-FC-07). */
export interface MonthlyTotal {
  /** Monatssumme der erfassten Fixkosten-Positionen (`summeMonatlich` aus dem Backend). */
  fixedCosts: number;
  /** Summe der angezeigten erkannten Abos — ohne die per «Kein Abo» verneinten. */
  recurring: number;
  /** `fixedCosts + recurring`. */
  total: number;
}

/**
 * CHF → Rappen, gerundet. Beträge kommen als JSON-Zahlen mit zwei Nachkommastellen; in
 * IEEE-754 ist `27.92 + 17.9` aber `45.8199…`, und über viele Positionen sollen sich die
 * Rundungsfehler gar nicht erst aufsummieren. Addiert wird deshalb in ganzen Rappen.
 */
function toRappen(chf: number): number {
  return Math.round(chf * 100);
}

/**
 * Die Seite «Ausgaben» (`/ausgaben`, FE-FC-07): Übersicht aller Fixkosten-Positionen mit
 * Bearbeiten und Löschen (FE-FC-03, US-03), darunter der Abschnitt «Erkannte Abos» (FE-FC-05,
 * US-08), darüber das Total beider Abschnitte ({@link monthlyTotal}).
 *
 * <p>Der Abo-Abschnitt ist die eingebettete {@link RecurringExpenseList} — mit eigenem State und
 * eigenem Request. Diese Klasse lädt, bearbeitet und löscht nur Fixkosten, wie vor FE-FC-05; die
 * Zusammenführung ist eine Frage der Seite, nicht der Daten (kein Datenmodell-Merge, siehe #338).
 * Von den Abos liest sie genau zwei Dinge: die Beträge der erkannten Einträge aus dem
 * {@link RecurringExpenseService} und den Lade-/Fehlerzustand des Abschnitts — beides nur für
 * das Total.
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
  private readonly recurringExpenses = inject(RecurringExpenseService);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Der eingebettete Abschnitt «Erkannte Abos». Gebraucht für seinen Lade- und Fehlerzustand:
   * die Liste hält der Service, den Ladevorgang kennt nur die Komponente. `undefined`, bis die
   * View steht — das Total ist bis dahin `null`.
   */
  private readonly recurringSection = viewChild(RecurringExpenseList);

  /** Auswahl des Intervall-Dropdowns in der Bearbeiten-Form. */
  readonly intervallOptions = INTERVALL_OPTIONS;

  /** Positionen, Monatssumme, Einkommen und Warn-Flag — `null`, solange nicht geladen. */
  readonly summary = signal<FixedCostSummary | null>(null);

  /**
   * Total der monatlichen fixen Ausgaben (FE-FC-07, US-08): Fixkosten-Monatssumme plus die
   * Beträge der angezeigten erkannten Abos. `null`, solange einer der beiden Abschnitte noch
   * lädt oder nicht geladen werden konnte — dann fehlte ein Summand, und eine Zahl, die das
   * verschweigt, ist schlimmer als keine. Warum sie fehlt, sagt
   * {@link totalUnavailableReason} an derselben Stelle.
   *
   * <p>Einfache Addition, keine Deduplizierung. Der Safe-to-Spend rechnet anders: dort gilt ein
   * Abo mit betragsgleicher Fixkosten-Position als bereits erfasst und zählt nicht noch einmal
   * (`FixedCostDebitMatcher`, ADR-13, FE-FC-05). Hier zählt jedes angezeigte Abo — das Total
   * kann deshalb über der Safe-to-Spend-Minderung liegen, wenn Lara ein Abo auch manuell erfasst
   * hat. Das ist so gewollt (#355): die Seite zeigt, was sie zeigt, und addiert genau das.
   * Verneinte Abos («Kein Abo») sind nicht drin — `detected()` filtert sie schon im Service.
   */
  readonly monthlyTotal = computed<MonthlyTotal | null>(() => {
    const summary = this.summary();
    const section = this.recurringSection();
    if (
      summary === null ||
      section === undefined ||
      section.loading() ||
      section.errorMessage() !== null
    ) {
      return null;
    }
    const fixedCosts = toRappen(summary.summeMonatlich);
    const recurring = this.recurringExpenses
      .detected()
      .reduce((sum, expense) => sum + toRappen(expense.amount), 0);
    return {
      fixedCosts: fixedCosts / 100,
      recurring: recurring / 100,
      total: (fixedCosts + recurring) / 100,
    };
  });

  /**
   * Warum das Total fehlt — oder `null`, wenn es dasteht oder bloss noch geladen wird.
   *
   * <p>{@link monthlyTotal} auszublenden ist richtig, sobald ein Summand fehlt; ohne diesen Satz
   * verschwindet die Zahl aber kommentarlos, und die Ursache steht erst weit darunter (die
   * Abo-Meldung sogar erst unterhalb der Fixkosten-Tabelle). Erklärt wird die Lücke deshalb
   * dort, wo sie auffällt: an der Stelle der Card. Review-Befund zu #355.
   *
   * <p>Nur nach einem Fehlschlag, nicht während des Ladens: beide Abschnitte laden beim
   * Seitenaufbau, ein Hinweis auf die noch fehlende Zahl blitzte sonst bei jedem Besuch kurz auf.
   */
  readonly totalUnavailableReason = computed<string | null>(() => {
    const section = this.recurringSection();
    const fixedCostsFailed = this.errorMessage() !== null;
    const recurringFailed = section !== undefined && section.errorMessage() !== null;

    if (fixedCostsFailed && recurringFailed) {
      return 'Total nicht verfügbar — Fixkosten und Abos konnten nicht geladen werden.';
    }
    if (recurringFailed) {
      return 'Total nicht verfügbar — die erkannten Abos konnten nicht geladen werden.';
    }
    if (fixedCostsFailed) {
      return 'Total nicht verfügbar — die Fixkosten konnten nicht geladen werden.';
    }
    return null;
  });

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
