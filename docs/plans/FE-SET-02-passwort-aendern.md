# [FE-SET-02] Passwort ändern

- **Issue:** [#178](https://github.com/dfme/budget-buddy/issues/178)
- **Task-ID:** `FE-SET-02`
- **Branch:** `feature/FE-SET-02-passwort-aendern`
- **Story:** US-14 — Passwort, Einkommen und Erscheinungsbild ändern
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-26

## Kontext

`PUT /api/users/me/password` (BE-AUTH-09, `UserController.java:85-98`) ist bereits fertig und
gemergt. Der Einstellungen-Screen (FE-SET-01) ist ein Gerüst mit drei leeren `app-card`-Abschnitten
(„Passwort", „Einkommen", „Erscheinungsbild"). Diese Story füllt nur die „Passwort"-Card.

## Betroffene Files

- `frontend/src/app/auth/auth.service.ts` — neue Methode `changePassword()`
- `frontend/src/app/auth/auth.service.spec.ts` — Test dafür
- `frontend/src/app/settings/settings.ts` — Reactive Form + Submit-Logik
- `frontend/src/app/settings/settings.html` — Formular in der „Passwort"-Card
- `frontend/src/app/settings/settings.scss` — Form-Spacing (analog `login.scss`)
- `frontend/src/app/settings/settings.spec.ts` — erweitert um die drei geforderten Testfälle

## Entscheide

**Request-Feldnamen deutsch, wörtlich aus dem Backend übernommen.** `ChangePasswordRequest`
(`ChangePasswordRequest.java:14-18`) erwartet `aktuellesPasswort` / `neuesPasswort` — als einziger
Endpoint im Frontend mit deutschen Feldnamen (Ausnahme neben `betrag` bei `updateIncome`). Der
Service-Methodenname bleibt deutsch konsistent zum DTO, damit Body und Aufruf nicht auseinanderlaufen.

**Kein State-Update im `AuthService`.** Der Endpoint antwortet 200 ohne Body
(`@ApiResponse(responseCode = "200", ..., content = {})`); anders als bei `updateIncome`/
`completeOnboarding` gibt es kein aktualisiertes `User`-Profil zu übernehmen.

**400-Fehlertext hartkodiert, nicht aus dem Response-Body gelesen** — analog `Login`
(`err.status === 401 ? '...' : '...'`) und `Register`. Die AC verlangt exakt "Aktuelles Passwort
falsch"; das ist zugleich die einzige 400-Ursache, die die UI überhaupt erreicht, weil
`Validators.minLength(8)` das zu kurze neue Passwort schon clientseitig blockiert (wie bei
`Register.password`) — der entsprechende Backend-Zweig in `UserExceptionHandler.
handleValidationError` bleibt damit ungenutzt und braucht keine eigene UI-Meldung.

**Erfolg zeigt eine Inline-Bestätigung statt Navigation** — anders als `Login`/`Register`, analog
`FixedCostWizard.submit()`: Formular wird mit `form.reset()` geleert, `app-notice variant="info"`
(`role="status"`) erscheint, kein Reload/Redirect. Beide Meldungs-Signale werden vor jedem neuen
Versuch zurückgesetzt (derselbe Grund wie in `fixed-cost-wizard.ts:133-136`: sonst stünde die alte
Erfolgsmeldung neben einem neuen laufenden Request).

**Formular liegt innerhalb der bestehenden `app-card title="Passwort"`**, keine zweite verschachtelte
Card — anders als `login.html`/`register.html`, die ihre eigene Card mitbringen.

## Implementierungsschritte

1. `AuthService.changePassword(aktuellesPasswort: string, neuesPasswort: string): Observable<void>`
   — `this.http.put<void>('/api/users/me/password', { aktuellesPasswort, neuesPasswort })`.
2. `Settings`-Komponente:
   - `FormBuilder.nonNullable.group({ aktuellesPasswort: ['', Validators.required], neuesPasswort: ['', [Validators.required, Validators.minLength(8)]] })`
   - Signals: `submitting`, `errorMessage`, `saved`
   - Feld-Fehler-Getter je Feld (analog `login.ts` `passwordError()`)
   - `submit()`: `takeUntilDestroyed(this.destroyRef)`, Meldungen zurücksetzen, bei Erfolg
     `form.reset()` + `saved.set(true)`, bei 400 feste Meldung „Aktuelles Passwort falsch", sonst
     generische Fehlermeldung
3. Template: `app-field` + `input appInput type="password"` für beide Felder, `app-notice` für
   Erfolg (`variant="info"`) und Fehler (`variant="error"`), `button appButton` für Submit,
   `[disabled]` solange `form.invalid` oder `submitting()`.
4. `settings.scss`: `form { display: flex; flex-direction: column; gap: $sp-4; }` (identisch
   `login.scss`).

## Test-Strategie

- `AuthService.changePassword`: PUT-Request mit korrekter URL und korrektem Body (deutsche Feldnamen).
- `Settings`:
  - Happy Path: Submit → PUT-Request → `flush(null)` (200 ohne Body) → Felder geleert, Notice
    `role="status"` mit Bestätigungstext, `submitting()` wieder `false`
  - Falsches aktuelles Passwort: `flush({ message: 'Aktuelles Passwort falsch' }, { status: 400 })`
    → Notice `role="alert"` mit exakt diesem Text, User bleibt auf der Seite (kein Navigate)
  - Zu kurzes neues Passwort: `component.submit()` mit `neuesPasswort` < 8 Zeichen → kein Request
    (`httpMock.expectNone`), Formular bleibt `invalid`
  - Bestehender Test „rendert die drei leeren Abschnitte" wird an den jetzt gefüllten
    Passwort-Abschnitt angepasst

## Acceptance Criteria (aus Issue #178)

- [ ] Reactive Form mit „Aktuelles Passwort" und „Neues Passwort" (min. 8 Zeichen); Submit ist
      deaktiviert, solange das Formular ungültig ist oder ein Request läuft
- [ ] Erfolgreiches Speichern zeigt eine In-App-Bestätigung und leert die Felder
- [ ] Antwortet das Backend mit `400` wegen falschem aktuellem Passwort, erscheint „Aktuelles
      Passwort falsch" am Formular — der User bleibt eingeloggt
- [ ] Passwörter stehen zu keinem Zeitpunkt in `localStorage`, `sessionStorage` oder einem Log (ADR-7)
- [ ] Tests decken ab: Happy Path, falsches aktuelles Passwort, zu kurzes neues Passwort
