# [E2E-AUTH-01] Playwright: Register → Login → Logout (Happy + Fehlerpfad)

- **Issue:** [#58](https://github.com/dfme/budget-buddy/issues/58)
- **Task-ID:** `E2E-AUTH-01`
- **Branch:** `feature/E2E-AUTH-01-register-login-logout`
- **Story:** US-01 — Konto erstellen und einloggen
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-26

## Kontext

Baut auf der E2E-Harness aus #91 auf (Setup, CI-Job, Auth-Fixture,
`e2e/tests/auth.spec.ts`). Der dortige Verifikations-Test deckt bereits ab:

- Register übers Formular → Onboarding-Wizard
- Login übers Formular → Onboarding-Wizard
- JWT-Cookie-Flags (`httpOnly`, `SameSite=Strict`)
- geschützte Route ohne Cookie → Redirect `/login`
- Auth-Fixture liefert eine eingeloggte, onboardete Session

Nicht abgedeckt und Gegenstand dieses Tasks: der Weg von einem **onboardeten** Konto über
Login bis zum Dashboard, der Logout selbst, und der Fehlerpfad mit falschen Credentials.
Beides wird als Erweiterung derselben Datei umgesetzt, nicht als neue Datei — die
bestehenden Fixtures und Konventionen (API-Registrierung über die `request`-Fixture, damit
der Browser anonym bleibt) werden wiederverwendet.

## Entscheide

- Kein neues Test-File: Erweiterung von `e2e/tests/auth.spec.ts` innerhalb des bestehenden
  `Auth-Flow`-Describe-Blocks.
- Logout-Button-Selektor: `getByRole('button', { name: 'Abmelden' })`. Eindeutig, weil das
  mobile Account-Menü (`shell.html`, `@if (accountMenuOpen())`) nur im DOM steht, wenn es
  geöffnet wurde — im Test bleibt es geschlossen, also existiert nur der Sidebar-Button
  (`nav__logout`).
- Fehlermeldung-Selektor: `getByRole('alert')`, weil `app-notice[variant="error"]`
  `role="alert"` setzt (`notice.ts:34`). Robuster als Text-Suche allein und deckt gleichzeitig
  die Accessibility-Semantik ab.
- Für den Happy Path wird das Konto per API registriert und direkt per API onboarded
  (`POST /api/users/me/onboarding-complete`), dann übers UI-Formular eingeloggt — so bleibt
  der Login-Schritt echtes UI-Verhalten, ohne den bereits getesteten Register-Wizard-Weg zu
  duplizieren.

## Betroffene Files

- `e2e/tests/auth.spec.ts` — 2 neue Tests
- `e2e/README.md` — Zeile zu `auth.spec.ts` in der Aufbau-Tabelle aktualisieren

## Implementierungsschritte

1. Test „Happy Path: Login (onboardetes Konto) → Dashboard sichtbar → Logout → zurück auf
   Login": Registrierung + Onboarding-Abschluss per API, Login per UI-Formular, Assert
   `/dashboard` + Heading „Dashboard", Klick auf „Abmelden", Assert `/login` + Heading
   „Login", danach erneuter Versuch `page.goto('/dashboard')` muss auf `/login`
   zurückleiten (Redirect-Nachweis nach Logout).
2. Test „Fehlerpfad: Login mit falschen Credentials zeigt Meldung, kein Dashboard-Zugriff":
   Registrierung per API, Login-Versuch mit falschem Passwort per UI, Assert
   `getByRole('alert')` zeigt „E-Mail oder Passwort falsch", Assert URL bleibt `/login`,
   Assert kein `jwt`-Cookie gesetzt.
3. `e2e/README.md` Zeile zu `auth.spec.ts` ergänzen (Logout + neuer Fehlerpfad erwähnen).

## Test-Strategie

Playwright E2E ist hier die gesamte Abnahme (DoD des Issues). Lokale Ausführung gegen das
`-Pprod`-JAR auf Port 8081:

```bash
docker compose up -d
cd backend && ./mvnw -Pprod -DskipTests package
cd ../e2e && npx playwright test
```

## Acceptance Criteria (aus Issue #58)

- [ ] Happy Path: Register → Login → Dashboard sichtbar → Logout → zurück auf Login
- [ ] Fehlerpfad: Login mit falschen Credentials → Meldung „E-Mail oder Passwort falsch",
      kein Dashboard-Zugriff
- [ ] Nach Logout ist der direkte Aufruf einer geschützten Route nicht möglich (Redirect
      `/login`)
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npx playwright test`
