import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, shareReplay, tap } from 'rxjs';

import { NotificationResponse } from './notification.model';

/**
 * Zentraler State und Kapselung von `GET /api/notifications` und
 * `POST /api/notifications/{id}/read` (FE-NOTIF-01, BE-NOTIF-01).
 *
 * <p>Anders als `SafeToSpendService` (dort liegt der UI-State in der Komponente) hält dieser
 * Service den State selbst — die Glocke wird doppelt gerendert (mobile Topbar + Desktop-Sidebar,
 * analog Avatar/Initialen in `Shell`) und beide Instanzen zeigen denselben Stand.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);

  private readonly notificationsState = signal<NotificationResponse[]>([]);

  /**
   * Laufendes `GET /api/notifications` aus {@link load}, oder `null`, wenn keines läuft.
   * Existiert nur, damit gleichzeitige Aufrufer (die zwei Glocken-Instanzen laden beide beim
   * Mount und bei jeder Navigation) sich einen Request teilen — analog
   * `AuthService.ensureCurrentUser`.
   */
  private loadRequest: Observable<NotificationResponse[]> | null = null;

  /** Benachrichtigungen des eingeloggten Users, in der vom Backend gelieferten Reihenfolge. */
  readonly notifications = this.notificationsState.asReadonly();

  /** Abgeleitet: Anzahl ungelesener Benachrichtigungen für den Badge. */
  readonly unreadCount = computed(() => this.notifications().filter((n) => !n.read).length);

  /** Lädt die Benachrichtigungen neu. */
  load(): Observable<NotificationResponse[]> {
    this.loadRequest ??= this.http.get<NotificationResponse[]>('/api/notifications').pipe(
      tap((notifications) => this.notificationsState.set(notifications)),
      finalize(() => (this.loadRequest = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.loadRequest;
  }

  /** Markiert eine Benachrichtigung als gelesen und schreibt die Antwort in den State. */
  markAsRead(id: number): Observable<NotificationResponse> {
    return this.http.post<NotificationResponse>(`/api/notifications/${id}/read`, {}).pipe(
      tap((updated) => {
        this.notificationsState.update((list) =>
          list.map((n) => (n.id === id ? updated : n)),
        );
      }),
    );
  }

  /**
   * Leert den State ohne Backend-Call. Wird beim Logout aufgerufen (`Shell.logout`) — sonst
   * blieben die Benachrichtigungen des vorherigen Users kurz sichtbar, bevor der nächste Login
   * neu lädt (der Service ist `providedIn: 'root'` und überlebt einen Login-Wechsel in derselben
   * Tab-Session). Analog `AuthService.resetState`.
   */
  clear(): void {
    this.notificationsState.set([]);
  }
}
