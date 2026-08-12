import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, of, shareReplay, tap } from 'rxjs';

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

  /**
   * Laufendes `GET /users/me` aus {@link ensureCurrentUser}, oder `null`, wenn keines läuft.
   * Existiert nur, um gleichzeitige Aufrufer denselben Request teilen zu lassen.
   */
  private profileRequest: Observable<User | null> | null = null;

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

  /**
   * Schliesst das Onboarding ab (`POST /users/me/onboarding-complete`, US-03, FE-FC-02).
   *
   * <p>Die Antwort wird in den State geschrieben, nicht bloss quittiert: der
   * `onboardingGuard` liest `onboardingCompleted` aus genau diesem Signal. Bliebe dort
   * der alte Wert stehen, würde die anschliessende Navigation aufs Dashboard sofort
   * wieder in den Wizard umgeleitet.
   *
   * <p>Der Endpoint ist idempotent (`UserController.completeOnboarding`) — ein zweiter
   * Aufruf ist kein Fehler und liefert dasselbe Profil.
   */
  completeOnboarding(): Observable<User> {
    return this.http
      .post<User>('/users/me/onboarding-complete', {})
      .pipe(tap((user) => this.currentUserState.set(user)));
  }

  /** Loggt aus; das Backend invalidiert das Cookie (Max-Age=0), wir leeren den State. */
  logout(): Observable<void> {
    return this.http
      .post<void>('/auth/logout', {})
      .pipe(tap(() => this.currentUserState.set(null)));
  }

  /**
   * Leert den Auth-State ohne Backend-Call. Wird vom `authErrorInterceptor`
   * genutzt, wenn ein 401 (abgelaufenes/fehlendes Cookie) auf einem geschützten
   * Call auftritt — der Server hat die Session bereits invalidiert.
   */
  resetState(): void {
    this.currentUserState.set(null);
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

  /**
   * Das Profil des eingeloggten Users — aus dem State, sonst per `GET /users/me` nachgeladen.
   *
   * <p>Gemeinsame Grundlage von `authGuard` und `onboardingGuard`: beide hängen am selben
   * Route-Eintrag und brauchen dasselbe Profil.
   *
   * <p><strong>Gleichzeitige Aufrufer teilen sich einen Request.</strong> Angular führt die
   * Guards eines `canActivate`-Arrays nebenläufig aus, nicht nacheinander: beide sehen den
   * State noch leer und lösen je ein eigenes `GET /users/me` aus. Ein Cache-Check allein
   * greift dagegen nicht, weil zum Zeitpunkt des zweiten Aufrufs noch keine Antwort da ist,
   * die er cachen könnte. `shareReplay` bündelt sie deshalb auf einen Request; das Feld wird
   * nach Abschluss geleert, damit ein späterer Aufruf wieder frisch lädt statt eine veraltete
   * Antwort zu wiederholen.
   */
  ensureCurrentUser(): Observable<User | null> {
    const user = this.currentUserState();
    if (user !== null) {
      return of(user);
    }
    this.profileRequest ??= this.loadCurrentUser().pipe(
      finalize(() => (this.profileRequest = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.profileRequest;
  }
}
