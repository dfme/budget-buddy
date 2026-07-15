# [FE-AUTH-05] Logout-Button + Nav-Anbindung

- **Issue:** #57
- **Task-ID:** FE-AUTH-05
- **User Story:** US-01 (Konto erstellen und einloggen)
- **Branch:** `feature/FE-AUTH-05-logout-button`
- **Depends on:** #53 (AuthService)

## Entscheide

- **Ort des Buttons:** im `App`-Header (`frontend/src/app/app.html`) — die geteilte Shell
  über allen Routes, also die passende „Nav". Sichtbar nur wenn `auth.isAuthenticated()`.
- **Logout-Ablauf:** Klick → `auth.logout()` (`POST /auth/logout`) → Auth-State wird geleert
  (passiert bereits im Service-`tap`) → Redirect `/login`. Bei Netzwerkfehler zusätzlich
  `auth.resetState()` + trotzdem Redirect, damit der lokale State garantiert leer ist.

## Betroffene Files

| File | Änderung |
|------|----------|
| `frontend/src/app/app.ts` | `AuthService` + `Router` injecten, `isAuthenticated` exposen, `logout()`-Methode |
| `frontend/src/app/app.html` | Logout-Button im Header, `@if (isAuthenticated())` |
| `frontend/src/app/app.scss` | leichtes Header-/Button-Styling (Datei aktuell leer) |
| `frontend/src/app/app.spec.ts` | Unit-Tests für Logout erweitern |

## Implementierungsschritte

1. `App` um `auth`/`router`-Injection, `isAuthenticated`-Getter und `logout()` erweitern.
2. Header-Template: Logout-Button conditional rendern.
3. SCSS: Header als Flex-Row (Titel links, Button rechts).
4. Tests schreiben.

## Test-Strategie (Vitest / TestBed)

- Button **nicht** sichtbar wenn nicht eingeloggt.
- Button **sichtbar** wenn eingeloggt.
- Klick → `POST /auth/logout` (Methode + Body), State wird `null`, Redirect nach `/login`.
- Fehlerfall: State trotzdem geleert + Redirect nach `/login`.

## Acceptance Criteria (aus Issue #57)

- [ ] Logout-Button nur sichtbar wenn `isAuthenticated` true
- [ ] Klick ruft `POST /auth/logout` (via AuthService), Cookie serverseitig invalidiert (`Max-Age=0`)
- [ ] Auth-State (`currentUser`) lokal zurückgesetzt, Redirect auf `/login`
- [ ] Nach Logout kein Zugriff auf geschützte Routes ohne erneuten Login (Zusammenspiel FE-AUTH-04)
- [ ] Unit-Test (Vitest) für Logout-Ablauf

## Hinweis DoD

`mvn package` und Swagger/OpenAPI-Endpoints sind hier N/A — reiner Frontend-Task ohne neue
Backend-Endpoints. Relevant: `ng build` + Vitest laufen fehlerfrei.
