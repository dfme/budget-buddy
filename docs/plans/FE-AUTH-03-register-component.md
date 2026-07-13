# FE-AUTH-03 — Register-Component (Reactive Form)

- **Issue:** #55
- **Task-ID:** FE-AUTH-03
- **Story:** US-01 (Konto erstellen und einloggen)
- **Branch:** `feature/FE-AUTH-03-register-component`

## Entscheidungen

- Redirect nach erfolgreicher Registrierung auf `/dashboard` (analog Login-Component;
  die Registrierung loggt via httpOnly-Cookie direkt ein).
- Component spiegelt das etablierte Login-Pattern: OnPush, Signals, `fb.nonNullable.group`,
  `ReactiveFormsModule`. Kein Token-/Header-Code (ADR-7).
- `AuthService.register()` existiert bereits (#53) und setzt den User-State bei Erfolg.

## Betroffene Files

Neu:
- `frontend/src/app/auth/register.ts`
- `frontend/src/app/auth/register.html`
- `frontend/src/app/auth/register.scss`
- `frontend/src/app/auth/register.spec.ts`

Ändern:
- `frontend/src/app/app.routes.ts` — Lazy-Route `register` ergänzen

## Implementierungsschritte

1. `register.ts`: OnPush-Component, Signals `errorMessage`/`submitting`. Form:
   `email` (`required`, `email`), `password` (`required`, `minLength(8)`).
   `submit()`: invalid → `markAllAsTouched()`; sonst `auth.register()` → Erfolg:
   `router.navigate(['/dashboard'])`; Fehler: `409` → „E-Mail bereits vergeben", sonst generisch.
2. `register.html`: Formular analog Login, feldspezifische Fehler inkl. `minlength`,
   `role="alert"`-Fehlerbox, Submit mit Ladezustand, Link zurück zum Login.
3. `register.scss`: an `login.scss` angelehnt.
4. `app.routes.ts`: Route `register` (lazy `loadComponent`).

## Test-Strategie (Vitest)

- Kein Backend-Call bei leerem Formular.
- Kein Backend-Call bei Passwort < 8 Zeichen (Client-Validierung).
- Happy Path: gültige Eingabe → `POST /auth/register` (korrekter Body) → `flush(User)` →
  Redirect `/dashboard`, kein Fehler.
- 409-Fehlerpfad: → Meldung „E-Mail bereits vergeben", kein Redirect, `submitting` false.

## Acceptance Criteria (aus Issue)

- [ ] Reactive Form mit E-Mail + Passwort, Client-Validierung: Passwort ≥ 8 Zeichen, E-Mail-Format
- [ ] Erfolgreiche Registrierung → Konto angelegt, Redirect
- [ ] Bereits vergebene E-Mail (409) → Meldung „E-Mail bereits vergeben"
- [ ] Link/Umschaltung zurück zum Login (FE-AUTH-02)
- [ ] OnPush + Signals
- [ ] Unit-Test (Vitest) für Happy Path + 409-Fehlerpfad
