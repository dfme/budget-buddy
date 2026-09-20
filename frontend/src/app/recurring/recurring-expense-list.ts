import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { Amount } from '../shared/amount/amount';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { formatMonth, isValidMonth } from '../shared/month';
import { Notice } from '../shared/notice/notice';
import { RecurringExpenseResponse } from './recurring-expense.model';
import { RecurringExpenseService } from './recurring-expense.service';

/** Eine Zeile der Übersicht, angereichert um das, was das Template braucht. */
interface ExpenseRow extends RecurringExpenseResponse {
  /** Menschlich lesbares Label des ersten Monats, z. B. `"seit Juli 2026"`. */
  sinceLabel: string;
}

/**
 * Liest den `ref`-Query-Parameter als Zeilen-ID (FE-NOTIF-03). Alles, was keine positive ganze
 * Zahl ist, gilt als nicht gesetzt — der Parameter kommt aus der URL und damit potenziell von
 * Hand. Der Leerstring ist der Grund für die Untergrenze: `Number('')` ist `0` und käme sonst als
 * gültige ID durch, obwohl `?ref=` gar keinen Eintrag benennt.
 */
function parseReferencedId(raw: string | null): number | null {
  if (raw === null || raw.trim() === '') {
    return null;
  }
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

/**
 * Abo-Übersicht: erkannte wiederkehrende Ausgaben mit «Neu»-Label und «Kein Abo»-Button
 * (FE-REC-01, US-08).
 *
 * <p>Jede Zeile ist eine erkannte <em>Gruppe</em> — derselbe Empfänger, in mindestens zwei
 * aufeinanderfolgenden Monaten mit demselben Betrag belastet. Die Einzelbuchungen zeigt die
 * Übersicht nicht; dafür steht der erste Monat der Reihe («seit …») in der Zeile.
 *
 * <p>Der State liegt im {@link RecurringExpenseService}, weil die Teaser-Card des Dashboards
 * dieselbe Liste zählt. Hier liegt nur, was allein diese Seite betrifft: Lade- und
 * Fehlerzustand sowie die ID des Eintrags, dessen «Kein Abo» gerade läuft.
 *
 * <p>«Kein Abo» fragt nicht nach, anders als das Löschen einer Fixkosten-Position: der Eintrag
 * geht nicht verloren, er wechselt auf `DISMISSED`, und das Backend ist idempotent. Ein Modal
 * schützte hier vor nichts.
 */
@Component({
  selector: 'app-recurring-expense-list',
  imports: [Amount, Button, Card, Notice, RouterLink],
  templateUrl: './recurring-expense-list.html',
  styleUrl: './recurring-expense-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecurringExpenseList {
  private readonly recurringExpenses = inject(RecurringExpenseService);
  private readonly route = inject(ActivatedRoute);

  /** `true`, solange die Liste (noch) lädt. */
  readonly loading = signal(true);

  /** Fehlermeldung, falls das Laden fehlschlägt. */
  readonly errorMessage = signal<string | null>(null);

  /** ID des Eintrags, dessen «Kein Abo»-Request gerade läuft — `null`, wenn keiner. */
  readonly dismissingId = signal<number | null>(null);

  /** Fehlermeldung des letzten fehlgeschlagenen «Kein Abo», oder `null`. */
  readonly dismissErrorMessage = signal<string | null>(null);

  /**
   * Die Zeilen fürs Template, mit fertigem «seit»-Label. Als `computed` statt Methodenaufruf im
   * Template — dieselbe Begründung wie bei `Dashboard.totalRows`.
   */
  readonly rows = computed<readonly ExpenseRow[]>(() =>
    this.recurringExpenses.expenses().map((expense) => ({
      ...expense,
      // `firstDetectedMonth` kommt als `YYYY-MM` aus der Datenbank; die Wache ist nur die
      // Absicherung gegen «Invalid Date» im Label, falls das Format doch einmal abweicht.
      sinceLabel: isValidMonth(expense.firstDetectedMonth)
        ? `seit ${formatMonth(expense.firstDetectedMonth)}`
        : `seit ${expense.firstDetectedMonth}`,
    })),
  );

  /**
   * ID des Eintrags, wegen dem die Seite geöffnet wurde — aus `?ref=` (FE-NOTIF-03). `null`, wenn
   * die Seite direkt angesteuert wurde.
   *
   * <p>Reaktiv aus `queryParamMap` statt einmalig im Konstruktor gelesen: Die Glocke sitzt in der
   * App-Shell und ist auch auf `/abos` selbst sichtbar. Ein Klick dort wechselt nur den
   * Query-Parameter, und bei gleichbleibender Route baut der Router die Komponente nicht neu —
   * ein einmaliges Auslesen bliebe auf dem Wert der ersten Navigation stehen.
   */
  readonly referencedId = toSignal(
    this.route.queryParamMap.pipe(map((params) => parseReferencedId(params.get('ref')))),
    { initialValue: null },
  );

  /**
   * `true`, wenn die Seite wegen eines bestimmten Eintrags geöffnet wurde, dieser aber nicht in
   * der Liste steht (FE-NOTIF-03) — in der Praxis, weil er inzwischen «Kein Abo» ist:
   * {@link RecurringExpenseService} lädt nur `DETECTED`-Einträge.
   *
   * <p>Die beiden ersten Bedingungen sind nicht kosmetisch. Während des Ladens ist {@link rows}
   * leer, jeder Verweis liefe dann ins Leere; und ein Ladefehler heisst «unbekannt», nicht
   * «verneint» — ohne diese Wachen behauptete der Hinweis in beiden Fällen etwas, das er nicht
   * weiss.
   */
  readonly referencedEntryMissing = computed(
    () =>
      !this.loading() &&
      this.errorMessage() === null &&
      this.referencedId() !== null &&
      !this.rows().some((row) => row.id === this.referencedId()),
  );

  constructor() {
    this.recurringExpenses.load().subscribe({
      next: () => this.loading.set(false),
      error: (_err: HttpErrorResponse) => {
        this.errorMessage.set('Die Abo-Übersicht konnte nicht geladen werden.');
        this.loading.set(false);
      },
    });
  }

  /**
   * Markiert einen Eintrag als «Kein Abo» (`POST /api/recurring-expenses/{id}/dismiss`).
   *
   * <p>Nur ein Request zur Zeit: solange einer läuft, sind alle «Kein Abo»-Buttons gesperrt.
   * Das hält den Fehlerzustand eindeutig — eine Meldung für genau den Eintrag, der noch da ist.
   *
   * <p>Bei Erfolg nimmt der Service den Eintrag aus dem State; die Zeile verschwindet damit aus
   * {@link rows}. Bei einem Fehler bleibt sie stehen, ein erneuter Klick versucht es wieder.
   */
  dismiss(expense: RecurringExpenseResponse): void {
    if (this.dismissingId() !== null) {
      return;
    }
    this.dismissingId.set(expense.id);
    this.dismissErrorMessage.set(null);

    this.recurringExpenses.dismiss(expense.id).subscribe({
      next: () => this.dismissingId.set(null),
      error: (_err: HttpErrorResponse) => {
        this.dismissErrorMessage.set(
          `«${expense.payeeKey}» konnte nicht als Kein Abo markiert werden.`,
        );
        this.dismissingId.set(null);
      },
    });
  }
}
