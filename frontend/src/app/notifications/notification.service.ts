import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, shareReplay, tap } from 'rxjs';

import { NotificationResponse } from './notification.model';

/**
 * Zentraler State und Kapselung von `GET /api/notifications`, `POST /api/notifications/{id}/read`
 * (FE-NOTIF-01, BE-NOTIF-01) und `POST /api/notifications/read-all` (FE-NOTIF-04).
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

  /** Lädt die Benachrichtigungen neu; ein bereits laufender Request wird mitbenutzt. */
  load(): Observable<NotificationResponse[]> {
    this.loadRequest ??= this.startLoad();
    return this.loadRequest;
  }

  /**
   * Lädt neu und hängt sich dabei **nicht** an einen laufenden Request (FE-NOTIF-04). Für den
   * Aufruf nach einem abgeschlossenen Import: ein `GET`, das vor dem Abschluss losging, liefert
   * den Stand davor — genau das, was der Reload überholen soll. Die Antwort des alten Requests
   * kommt für dessen Abonnenten weiterhin an; sie ist nur nicht mehr die, auf die sich der
   * nächste Aufrufer von {@link load} setzt.
   */
  reload(): Observable<NotificationResponse[]> {
    this.loadRequest = this.startLoad();
    return this.loadRequest;
  }

  private startLoad(): Observable<NotificationResponse[]> {
    const request = this.http.get<NotificationResponse[]>('/api/notifications').pipe(
      tap((notifications) => this.notificationsState.set(notifications)),
      // Nur den eigenen Eintrag räumen: nach einem `reload()` hält `loadRequest` bereits den
      // jüngeren Request, und der darf vom Abschluss des älteren nicht verworfen werden.
      finalize(() => {
        if (this.loadRequest === request) {
          this.loadRequest = null;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return request;
  }

  /** Markiert eine Benachrichtigung als gelesen und schreibt die Antwort in den State. */
  markAsRead(id: number): Observable<NotificationResponse> {
    return this.http.post<NotificationResponse>(`/api/notifications/${id}/read`, {}).pipe(
      tap((updated) => {
        this.notificationsState.update((list) => list.map((n) => (n.id === id ? updated : n)));
      }),
    );
  }

  /**
   * Markiert alle Benachrichtigungen als gelesen (FE-NOTIF-04). Das Backend liefert die
   * vollständige Liste zurück; ein Nachladen entfällt.
   *
   * <p>Die Antwort wird **per ID in die bestehende Reihenfolge eingemischt**, nicht als Ganzes
   * übernommen: `GET` sortiert ungelesene zuerst, und nach dem Markieren degeneriert dieser
   * Schlüssel auf `createdAt` — eine ältere ungelesene neben neueren gelesenen würde im offenen
   * Dropdown springen, während der Nutzer hinsieht. Die nächste Navigation lädt ohnehin in
   * Backend-Reihenfolge. Einträge, die lokal noch fehlen, kommen hinten dazu.
   */
  markAllAsRead(): Observable<NotificationResponse[]> {
    return this.http.post<NotificationResponse[]>('/api/notifications/read-all', {}).pipe(
      tap((notifications) => {
        const byId = new Map(notifications.map((n) => [n.id, n]));
        this.notificationsState.update((list) => {
          const merged = list.map((n) => byId.get(n.id) ?? n);
          const known = new Set(list.map((n) => n.id));
          return [...merged, ...notifications.filter((n) => !known.has(n.id))];
        });
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
