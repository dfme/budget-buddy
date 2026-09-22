import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

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

function toRow(expense: RecurringExpenseResponse): ExpenseRow {
  return {
    ...expense,
    // `firstDetectedMonth` kommt als `YYYY-MM` aus der Datenbank; die Wache ist nur die
    // Absicherung gegen «Invalid Date» im Label, falls das Format doch einmal abweicht.
    sinceLabel: isValidMonth(expense.firstDetectedMonth)
      ? `seit ${formatMonth(expense.firstDetectedMonth)}`
      : `seit ${expense.firstDetectedMonth}`,
  };
}

/**
 * Abschnitt «Erkannte Abos» auf `/ausgaben`: erkannte wiederkehrende Ausgaben mit «Neu»-Label
 * und «Kein Abo»-Button (FE-REC-01, US-08), darunter die verneinten Einträge in einem eigenen
 * Abschnitt «Kein Abo» (FE-NOTIF-03).
 *
 * <p>Bis FE-FC-05 war das eine eigene Seite unter `/abos`. Seither bettet `FixedCostList` die
 * Komponente unter der Fixkosten-Tabelle ein — manuell erfasste Fixkosten und automatisch
 * erkannte Abos sind verwandte Inhalte, und seit FE-FC-05 wirken beide gleich auf den
 * Safe-to-Spend. Die Komponente blieb eigenständig statt ins Fixkosten-Template zu wandern: sie
 * hat eigenen Lade- und Fehlerzustand, eigene Tests, und `RecurringExpenseService` zählt dieselbe
 * Liste weiterhin für die Teaser-Card des Dashboards.
 *
 * <p>Jede Zeile ist eine erkannte <em>Gruppe</em> — derselbe Empfänger, in mindestens zwei
 * aufeinanderfolgenden Monaten mit demselben Betrag belastet. Die Einzelbuchungen zeigt die
 * Übersicht nicht; dafür steht der erste Monat der Reihe («seit …») in der Zeile.
 *
 * <p>Der Abschnitt «Kein Abo» ist der Grund, warum der Klick auf eine
 * `RECURRING_EXPENSE_DETECTED`-Benachrichtigung immer ein Ziel hat: die Benachrichtigung bleibt
 * in der Glocke stehen, auch wenn der Eintrag inzwischen verneint wurde (BE-REC-03 markiert sie
 * nur als gelesen). Stünde der Eintrag dann nirgends, landete der Klick auf einer Seite ohne
 * ihn — genau das schliesst #333 AC1 aus. US-08 AC3 («wird aus der Abo-Übersicht entfernt»)
 * heisst seither: aus der Liste der Abos, nicht von der Seite. Ohne verneinte Einträge fehlt der
 * Abschnitt ganz. Der Klick führt seit FE-FC-05 auf die Seite mit diesem Abschnitt (seit FE-FC-07
 * `/ausgaben`).
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

  /** `true`, solange die Liste (noch) lädt. */
  readonly loading = signal(true);

  /** Fehlermeldung, falls das Laden fehlschlägt. */
  readonly errorMessage = signal<string | null>(null);

  /** ID des Eintrags, dessen «Kein Abo»-Request gerade läuft — `null`, wenn keiner. */
  readonly dismissingId = signal<number | null>(null);

  /** Fehlermeldung des letzten fehlgeschlagenen «Kein Abo», oder `null`. */
  readonly dismissErrorMessage = signal<string | null>(null);

  /**
   * Die Abo-Zeilen fürs Template, mit fertigem «seit»-Label. Als `computed` statt Methodenaufruf
   * im Template — dieselbe Begründung wie bei `Dashboard.totalRows`.
   */
  readonly rows = computed<readonly ExpenseRow[]>(() =>
    this.recurringExpenses.detected().map(toRow),
  );

  /**
   * Die verneinten Einträge für den Abschnitt «Kein Abo» (FE-NOTIF-03) — dieselbe Zeilenform,
   * aber ohne «Neu» und ohne Button: `isNew` ist nach dem Dismiss immer `false`, und einen
   * bereits verneinten Eintrag noch einmal zu verneinen wäre nur der idempotente Backend-Call.
   */
  readonly dismissedRows = computed<readonly ExpenseRow[]>(() =>
    this.recurringExpenses.dismissed().map(toRow),
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
   * <p>Bei Erfolg ersetzt der Service den Eintrag im State durch die Antwort (`DISMISSED`); die
   * Zeile wandert damit aus {@link rows} nach {@link dismissedRows}. Bei einem Fehler bleibt sie
   * stehen, ein erneuter Klick versucht es wieder.
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
