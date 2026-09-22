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

  /**
   * Alle Einträge des eingeloggten Users, alle Status, in der vom Backend gelieferten
   * Reihenfolge (alphabetisch nach Empfänger). Die Consumer lesen {@link detected},
   * {@link ended} und {@link dismissed}; das Ganze ist hier nur der gemeinsame Ausgangspunkt.
   */
  readonly expenses = this.expensesState.asReadonly();

  /** Laufende Abos — die eigentliche Abo-Liste. */
  readonly detected = computed(() => this.expenses().filter((e) => e.status === 'DETECTED'));

  /**
   * Ausgelaufene Abos (BE-REC-04): der Empfänger hat in den jüngsten Monaten der Historie nicht
   * mehr abgebucht. Sie stehen in einem eigenen Abschnitt «Beendet» — sichtbar, weil ein
   * gekündigtes Abo eine Information ist und weil der Klick auf die zugehörige Benachrichtigung
   * sonst ins Leere führte (dieselbe Begründung wie bei {@link dismissed}, FE-NOTIF-03).
   *
   * <p>Sie zählen weder in {@link count} noch ins Total der fixen Ausgaben auf `/ausgaben`, weil
   * beide auf {@link detected} aufsetzen — dieselbe Grenze, die der Safe-to-Spend zieht.
   */
  readonly ended = computed(() => this.expenses().filter((e) => e.status === 'ENDED'));

  /**
   * Per «Kein Abo» verneinte Einträge — der Abschnitt «Kein Abo» der Übersicht (FE-NOTIF-03).
   * Sie bleiben sichtbar, damit der Klick auf eine Benachrichtigung zu einem inzwischen
   * verneinten Eintrag nicht auf einer Seite landet, auf der er fehlt.
   */
  readonly dismissed = computed(() => this.expenses().filter((e) => e.status === 'DISMISSED'));

  /**
   * Abgeleitet: Anzahl laufender Abos für die Teaser-Card — verneinte und ausgelaufene zählen
   * nicht mit.
   */
  readonly count = computed(() => this.detected().length);

  /** Lädt die Abo-Übersicht neu. */
  load(): Observable<RecurringExpenseResponse[]> {
    return this.http
      .get<RecurringExpenseResponse[]>('/api/recurring-expenses')
      .pipe(tap((expenses) => this.expensesState.set(expenses)));
  }

  /**
   * Markiert einen Eintrag als «Kein Abo» und ersetzt ihn im State durch die Antwort.
   *
   * <p>Kein Reload: die Antwort ist der Eintrag in seinem neuen Zustand (`status=DISMISSED`,
   * `isNew=false`) — genau das, was ein `GET` danach auch liefern würde. Ersetzt statt entfernt,
   * weil der Eintrag die Seite nicht verlässt, sondern nur den Abschnitt wechselt
   * (FE-NOTIF-03); die Position bleibt, das Backend sortiert nicht nach Status.
   */
  dismiss(id: number): Observable<RecurringExpenseResponse> {
    return this.http
      .post<RecurringExpenseResponse>(`/api/recurring-expenses/${id}/dismiss`, {})
      .pipe(
        tap((updated) => {
          this.expensesState.update((list) =>
            list.map((expense) => (expense.id === id ? updated : expense)),
          );
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
