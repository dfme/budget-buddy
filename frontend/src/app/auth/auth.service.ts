import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, of, shareReplay, tap } from 'rxjs';

import { User } from './user.model';

/**
 * Zentraler Auth-State und Kapselung der `/api/auth`- und `/api/users/me`-Endpoints (US-01).
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
   * Laufendes `GET /api/users/me` aus {@link ensureCurrentUser}, oder `null`, wenn keines läuft.
   * Existiert nur, um gleichzeitige Aufrufer denselben Request teilen zu lassen.
   */
  private profileRequest: Observable<User | null> | null = null;

  /** Aktuell eingeloggter User oder `null`, wenn anonym. */
  readonly currentUser = this.currentUserState.asReadonly();

  /** Abgeleitet: `true`, sobald ein User geladen ist. */
  readonly isAuthenticated = computed(() => this.currentUserState() !== null);

  /**
   * Legt ein Konto an; bei Erfolg setzt das Backend das JWT-Cookie und wir den State.
   *
   * <p>`firstName`/`lastName` sind optional (BE-AUTH-05, #114) — ein leeres Formularfeld wird
   * hier nicht herausgefiltert, das Backend normalisiert Blank-Strings selbst zu `null`.
   */
  register(
    email: string,
    password: string,
    firstName: string | null = null,
    lastName: string | null = null,
  ): Observable<User> {
    return this.http
      .post<User>('/api/auth/register', { email, password, firstName, lastName })
      .pipe(tap((user) => this.currentUserState.set(user)));
  }

  /** Loggt ein; bei Erfolg setzt das Backend das JWT-Cookie und wir den State. */
  login(email: string, password: string): Observable<User> {
    return this.http
      .post<User>('/api/auth/login', { email, password })
      .pipe(tap((user) => this.currentUserState.set(user)));
  }

  /**
   * Schliesst das Onboarding ab (`POST /api/users/me/onboarding-complete`, US-03, FE-FC-02).
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
      .post<User>('/api/users/me/onboarding-complete', {})
      .pipe(tap((user) => this.currentUserState.set(user)));
  }

  /**
   * Setzt das Monatseinkommen (`PUT /api/users/me/income`, US-06, FE-STS-03).
   *
   * <p>Liegt hier und nicht im `SafeToSpendService`, weil `/api/users/me` zu diesem Service
   * gehört und `monthlyIncome` Teil des {@link User}-State ist: die Antwort wird wie bei
   * {@link completeOnboarding} in den State geschrieben. Ohne das bliebe dort der alte
   * Wert (`null`) stehen, während das Backend längst ein Einkommen kennt.
   *
   * <p>Das Backend prüft `betrag` seit BE-AUTH-08 im `UserService` (nicht mehr per
   * Bean-Validation am DTO) gegen vier Regeln und antwortet bei jeder Verletzung mit 400 und
   * dem Body `{ field: 'betrag', message: string }`:
   *
   * <ul>
   *   <li>vorhanden — `null` wird abgelehnt
   *   <li>`> 0`
   *   <li>höchstens zwei Nachkommastellen; angehängte Nullen zählen nicht (`100.000` gilt)
   *   <li>maximal `99'999'999.99` — die Kapazität von `numeric(10,2)`
   * </ul>
   *
   * <p>Ein nicht lesbarer Body (etwa `"12,50"` aus einem Formular mit Komma) liefert denselben
   * Body, ebenfalls mit `field: 'betrag'`. Wer hier ein Eingabefeld anbaut (US-14), kann die
   * `message` also direkt anzeigen, statt eine generische Meldung zu erfinden.
   */
  updateIncome(betrag: number): Observable<User> {
    return this.http
      .put<User>('/api/users/me/income', { betrag })
      .pipe(tap((user) => this.currentUserState.set(user)));
  }

  /**
   * Ändert das Passwort (`PUT /api/users/me/password`, US-14, FE-SET-02).
   *
   * <p>Feldnamen sind bewusst deutsch (`aktuellesPasswort`/`neuesPasswort`) — sie entsprechen
   * wörtlich {@code ChangePasswordRequest} im Backend. Kein State-Update: der Endpoint antwortet
   * 200 ohne Body, es gibt kein aktualisiertes Profil zu übernehmen.
   *
   * <p>Das Backend prüft `aktuellesPasswort` gegen den gespeicherten Hash und `neuesPasswort`
   * gegen die Mindestlänge (8 Zeichen); beide Verletzungen liefern 400 mit
   * `{ message: string }`. Bei falschem aktuellem Passwort lautet die Meldung wörtlich
   * "Aktuelles Passwort falsch" (AC aus US-14).
   */
  changePassword(aktuellesPasswort: string, neuesPasswort: string): Observable<void> {
    return this.http.put<void>('/api/users/me/password', { aktuellesPasswort, neuesPasswort });
  }

  /** Loggt aus; das Backend invalidiert das Cookie (Max-Age=0), wir leeren den State. */
  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', {})
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
    return this.http.get<User>('/api/users/me').pipe(
      tap((user) => this.currentUserState.set(user)),
      catchError(() => {
        this.currentUserState.set(null);
        return of(null);
      }),
    );
  }

  /**
   * Das Profil des eingeloggten Users — aus dem State, sonst per `GET /api/users/me` nachgeladen.
   *
   * <p>Gemeinsame Grundlage von `authGuard` und `onboardingGuard`: beide hängen am selben
   * Route-Eintrag und brauchen dasselbe Profil.
   *
   * <p><strong>Gleichzeitige Aufrufer teilen sich einen Request.</strong> Angular führt die
   * Guards eines `canActivate`-Arrays nebenläufig aus, nicht nacheinander: beide sehen den
   * State noch leer und lösen je ein eigenes `GET /api/users/me` aus. Ein Cache-Check allein
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
