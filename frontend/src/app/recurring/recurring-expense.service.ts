import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { RecurringExpenseResponse } from './recurring-expense.model';

/**
 * Zentraler State und Kapselung von `GET /api/recurring-expenses` und
 * `POST /api/recurring-expenses/{id}/dismiss` (FE-REC-01, BE-REC-02).
 *
 * <p>Hält den State selbst, wie `NotificationService`: die Liste wird von zwei Stellen gelesen —
 * der Abo-Übersicht und der Teaser-Card auf dem Dashboard — und beide sollen denselben Stand
 * zeigen. Anders als dort kein Bündeln gleichzeitiger Requests: die beiden Consumer sind nie
 * zugleich gemountet.
 */
@Injectable({ providedIn: 'root' })
export class RecurringExpenseService {
  private readonly http = inject(HttpClient);

  private readonly expensesState = signal<RecurringExpenseResponse[]>([]);

  /** Erkannte Abos des eingeloggten Users, in der vom Backend gelieferten Reihenfolge. */
  readonly expenses = this.expensesState.asReadonly();

  /** Abgeleitet: Anzahl erkannter Abos für die Teaser-Card. */
  readonly count = computed(() => this.expenses().length);

  /** Lädt die Abo-Übersicht neu. */
  load(): Observable<RecurringExpenseResponse[]> {
    return this.http
      .get<RecurringExpenseResponse[]>('/api/recurring-expenses')
      .pipe(tap((expenses) => this.expensesState.set(expenses)));
  }

  /**
   * Markiert einen Eintrag als «Kein Abo» und nimmt ihn aus dem State.
   *
   * <p>Kein Reload: die Antwort trägt `status=DISMISSED`, und `GET` liefert nur `DETECTED` —
   * der Eintrag wäre nach einem Reload ohnehin weg. Lokal zu entfernen zeigt dasselbe Ergebnis
   * ohne zweiten Request.
   */
  dismiss(id: number): Observable<RecurringExpenseResponse> {
    return this.http
      .post<RecurringExpenseResponse>(`/api/recurring-expenses/${id}/dismiss`, {})
      .pipe(
        tap(() => {
          this.expensesState.update((list) => list.filter((expense) => expense.id !== id));
        }),
      );
  }

  /**
   * Leert den State ohne Backend-Call. Wird beim Logout aufgerufen (`Shell.logout`) — sonst
   * zeigte die Teaser-Card nach einem Login-Wechsel in derselben Tab-Session kurz die Zahl des
   * vorherigen Users. Analog `NotificationService.clear`.
   */
  clear(): void {
    this.expensesState.set([]);
  }
}
