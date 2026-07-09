# [FE-AUTH-02] Login-Component (Reactive Form)

- **Issue:** #54
- **User Story:** [US-01](../requirements/US-01-konto-login.md)
- **Task-ID:** FE-AUTH-02
- **Branch:** `feature/FE-AUTH-02-login-component`
- **Depends on:** #53 (AuthService, gemergt)

## Entscheide

- Login-Component ersetzt die Platzhalter-`login.ts` im Feature-Folder `frontend/src/app/auth/`.
- Reactive Form (`FormGroup`) mit Feldern E-Mail + Passwort; Client-Validierung: Pflichtfelder + E-Mail-Format.
- OnPush + Signals; UI-State (`errorMessage`, `submitting`) als Signals.
- Kein Token-/Header-Code — httpOnly-Cookie via bestehendem `credentialsInterceptor` (ADR-7).
- Fehlerbehandlung: 401 → „E-Mail oder Passwort falsch" (keine Auskunft, ob E-Mail existiert); andere Fehler → generische Meldung.
- Erfolg → `Router.navigate(['/dashboard'])`.
- Register-Link zeigt vorausschauend auf `/register` (FE-AUTH-03 / #55 ergänzt die Route).
- Kein neuer Backend-Endpoint → Swagger-Punkt der DoD entfällt (rein Frontend).

## Betroffene Files

| Datei | Aktion |
|-------|--------|
| `frontend/src/app/auth/login.ts` | Ersetzen — Reactive Form, Signals, AuthService-Anbindung |
| `frontend/src/app/auth/login.html` | Ersetzen — Formular, Validierungs-/Fehlermeldungen, Register-Link |
| `frontend/src/app/auth/login.scss` | Anpassen — schlichtes Formular-Layout |
| `frontend/src/app/auth/login.spec.ts` | Neu — Vitest Unit-Test (Happy Path + Fehlerpfad) |

## Implementierungsschritte

1. `login.ts`: Standalone `Login`, OnPush, `imports: [ReactiveFormsModule, RouterLink]`. FormGroup via `FormBuilder` (email: required+email, password: required). Inject `AuthService` + `Router`. Signals `errorMessage`, `submitting`. `submit()`: invalid → `markAllAsTouched()`; sonst `login(...)` → success: navigate `/dashboard`; error: 401 → Meldung, sonst generisch; `submitting` zurücksetzen.
2. `login.html`: `[formGroup]`, Feld-Validierungshinweise (touched), Fehler-Banner aus `errorMessage()`, Submit-Button `[disabled]` bei `submitting()`, `routerLink="/register"`.
3. `login.scss`: minimales Styling.
4. `login.spec.ts`: HttpTestingController + gespyter `Router.navigate`.

## Test-Strategie

- **Happy Path:** gültige Eingaben → `POST /auth/login` geflusht → `Router.navigate(['/dashboard'])`.
- **Fehlerpfad:** 401 → `errorMessage()` = „E-Mail oder Passwort falsch", kein Redirect.
- **Validierung:** leeres Formular → kein HTTP-Call, Form invalid.

## Acceptance Criteria (aus Issue #54)

- [ ] Reactive Form E-Mail + Passwort, Client-Validierung (Pflichtfelder, E-Mail-Format)
- [ ] Erfolgreicher Login → Redirect auf `/dashboard`
- [ ] Falsche Credentials (401) → „E-Mail oder Passwort falsch", keine Auskunft ob E-Mail existiert
- [ ] Link/Umschaltung zur Registrierung (FE-AUTH-03)
- [ ] OnPush + Signals; Platzhalter-`login.ts` wird ersetzt
- [ ] Unit-Test (Vitest) für Happy Path + Fehlerpfad
