# FE-AUTH-01 — AuthService (Signal-State + /auth-Calls)

- **Issue:** #53
- **Task-ID:** FE-AUTH-01
- **User Story:** US-01 (Konto erstellen und einloggen)
- **Branch:** `feature/FE-AUTH-01-auth-service`
- **Sprint:** 2

## Ziel

Zentraler `AuthService` im Feature-Folder `frontend/src/app/auth/`, der den Auth-State
via Angular Signals hält und die bestehenden Backend-Endpoints (BE-AUTH-02/03) kapselt.
Grundlage für Login, Register, Guard und Logout. Kein Bearer-/Token-Handling — das
httpOnly-Cookie wird durch den bestehenden `credentialsInterceptor` automatisch
mitgesendet (ADR-7).

## Backend-Contract (gegen Sprint-1-Code verifiziert)

| Call            | Endpoint              | Body                  | Response                          |
| --------------- | --------------------- | --------------------- | --------------------------------- |
| register        | `POST /auth/register` | `{ email, password }` | `201` + `UserProfileResponse`     |
| login           | `POST /auth/login`    | `{ email, password }` | `200` + `UserProfileResponse`     |
| logout          | `POST /auth/logout`   | —                     | `204` No Content                  |
| loadCurrentUser | `GET /users/me`       | —                     | `200` + Profil, `401` wenn anonym |

`UserProfileResponse` = `{ id: number, email: string, monthlyIncome: number | null, onboardingCompleted: boolean }`.

## Entscheidungen

- File `auth.service.ts` (Type-Suffix wie bei bestehendem `credentials.interceptor.ts`).
- `@Injectable({ providedIn: 'root' })`.
- State: privates `signal<User | null>(null)`, exponiert als readonly `currentUser` +
  `isAuthenticated = computed(...)`.
- Methoden geben **Observables** zurück (Komponenten subscriben); State-Update via RxJS `tap`.
- `loadCurrentUser`: bei `401` Fehler abfangen → State `null`, `of(null)` zurückgeben
  (Reload im anonymen Zustand darf nicht werfen).
- Relative URLs (`/auth/...`, `/users/me`) — same-origin in Prod (SPA im JAR gebündelt).

## Betroffene / neue Files

- **NEU** `frontend/src/app/auth/user.model.ts` — `User`-Interface
- **NEU** `frontend/src/app/auth/auth.service.ts` — Service
- **NEU** `frontend/src/app/auth/auth.service.spec.ts` — Vitest-Tests

## Implementierungsschritte

1. `User`-Interface anlegen (spiegelt `UserProfileResponse`).
2. `AuthService` mit Signal-State, `register`/`login`/`logout`/`loadCurrentUser`.
3. Vitest-Unit-Tests mit gemocktem `HttpClient` (`HttpTestingController`).
4. Lokaler Review + `ng build`.

## Test-Strategie

- `login`/`register`: URL + Body korrekt, setzt `currentUser` & `isAuthenticated`.
- `logout`: trifft Endpoint, State zurück auf `null`/`false`.
- `loadCurrentUser`: `200` stellt State wieder her; `401` bleibt `null` ohne Fehler.
- Initialer State ist unauthentifiziert.

## Acceptance Criteria (aus Issue)

- [ ] `AuthService` in `auth/` als `@Injectable({ providedIn: 'root' })`
- [ ] `register`/`login`/`logout` gegen `/auth/*`
- [ ] `currentUser` (Signal) + `isAuthenticated` (computed) spiegeln den Login-State
- [ ] `loadCurrentUser()` stellt State nach Reload über `GET /users/me` wieder her
- [ ] Kein manueller Token-/Header-Code — Cookie via bestehendem Interceptor
- [ ] Unit-Test (Vitest) für login/register/logout mit gemocktem HttpClient
