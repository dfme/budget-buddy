import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';

import { User } from './user.model';

/**
 * Zentraler Auth-State und Kapselung der `/auth`- und `/users/me`-Endpoints (US-01).
 *
 * <p>Der Login-Status liegt als Signal vor; `isAuthenticated` ist davon abgeleitet.
 * Es gibt bewusst keinen Token-/Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly currentUserState = signal<User | null>(null);

  /** Aktuell eingeloggter User oder `null`, wenn anonym. */
  readonly currentUser = this.currentUserState.asReadonly();

  /** Abgeleitet: `true`, sobald ein User geladen ist. */
  readonly isAuthenticated = computed(() => this.currentUserState() !== null);

  /** Legt ein Konto an; bei Erfolg setzt das Backend das JWT-Cookie und wir den State. */
  register(email: string, password: string): Observable<User> {
    return this.http
      .post<User>('/auth/register', { email, password })
      .pipe(tap((user) => this.currentUserState.set(user)));
  }

  /** Loggt ein; bei Erfolg setzt das Backend das JWT-Cookie und wir den State. */
  login(email: string, password: string): Observable<User> {
    return this.http
      .post<User>('/auth/login', { email, password })
      .pipe(tap((user) => this.currentUserState.set(user)));
  }

  /** Loggt aus; das Backend invalidiert das Cookie (Max-Age=0), wir leeren den State. */
  logout(): Observable<void> {
    return this.http
      .post<void>('/auth/logout', {})
      .pipe(tap(() => this.currentUserState.set(null)));
  }

  /**
   * Stellt den State nach einem Reload wieder her. Ein gültiges Cookie liefert das
   * Profil; ohne Login antwortet das Backend mit 401 — dann bleibt der State `null`,
   * ohne dass ein Fehler propagiert wird.
   */
  loadCurrentUser(): Observable<User | null> {
    return this.http.get<User>('/users/me').pipe(
      tap((user) => this.currentUserState.set(user)),
      catchError(() => {
        this.currentUserState.set(null);
        return of(null);
      }),
    );
  }
}
