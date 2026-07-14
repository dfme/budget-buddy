# FE-AUTH-04 — authGuard + 401-Redirect für geschützte Routes

- **Issue:** #56
- **Task-ID:** FE-AUTH-04
- **User Story:** [US-01](../requirements/US-01-konto-login.md)
- **Branch:** `feature/FE-AUTH-04-authguard-401-redirect`
- **Depends on:** #53 (AuthService, gemergt)

## Entscheide

1. **Guard-Logik:** Ist `isAuthenticated()` bereits `true` → Zugriff sofort. Sonst
   `loadCurrentUser()` (stellt State nach Reload wieder her). Liefert es einen User →
   `true`, sonst `UrlTree` auf `/login`.
2. **401-Interceptor Scope:** Kein Redirect bei `/auth/login`, `/auth/register`,
   `/users/me` (erwartete/Bootstrap-401 → sonst Loop). Alle anderen 401 → Auth-State
   reset + Redirect `/login`.
3. Anpassungen an diesem Scope werden als neues Issue geführt (Absprache mit User).

## Betroffene / neue Files

**Neu:**
- `frontend/src/app/core/guards/auth.guard.ts`
- `frontend/src/app/core/guards/auth.guard.spec.ts`
- `frontend/src/app/core/interceptors/auth-error.interceptor.ts`
- `frontend/src/app/core/interceptors/auth-error.interceptor.spec.ts`

**Geändert:**
- `frontend/src/app/app.routes.ts` — `canActivate: [authGuard]` an `/dashboard`
- `frontend/src/app/app.config.ts` — `authErrorInterceptor` registrieren
- `frontend/src/app/auth/auth.service.ts` — öffentliche `resetState()`-Methode

## Implementierungsschritte

1. `resetState()` im `AuthService` ergänzen.
2. `authErrorInterceptor` (401 → reset + Redirect, mit Endpoint-Ausschluss).
3. `authGuard` (`CanActivateFn`, async über `loadCurrentUser`).
4. Guard an `/dashboard` in `app.routes.ts` registrieren.
5. Interceptor in `app.config.ts` registrieren (nach `credentialsInterceptor`).
6. Vitest-Specs für Guard und Interceptor.

## Test-Strategie (Vitest)

- **Guard:** authentifiziert → `true`; nicht auth + `/users/me` liefert User → `true`;
  nicht auth + `/users/me` 401 → Redirect `/login`.
- **Interceptor:** 401 auf geschütztem Call → reset + navigate `/login`; 401 auf
  `/auth/login` → kein Redirect; Nicht-401 → durchgereicht.

## Acceptance Criteria

- [ ] `authGuard` schützt `/dashboard`; nicht eingeloggt → Redirect `/login`
- [ ] Eingeloggter Nutzer erreicht geschützte Routes ohne erneuten Login (State via `loadCurrentUser`)
- [ ] `401`-Antworten setzen den Auth-State zurück und leiten auf `/login` um
- [ ] Guard in `app.routes.ts` an geschützten Routes registriert
- [ ] Unit-Test (Vitest) für zugelassenen und abgewiesenen Zugriff
