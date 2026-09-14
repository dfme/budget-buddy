# [FE-SET-05] Konto löschen: Aktion in den Einstellungen

- **Issue:** [#299](https://github.com/dfme/budget-buddy/issues/299)
- **Task-ID:** `FE-SET-05`
- **Branch:** `feature/FE-SET-05-delete-account`
- **Story:** US-02 — Datenschutz-Consent
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-14

## Entscheide

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Fehlertext bei 400 | fixes „Passwort falsch" statt der Backend-`message` | Das Backend liefert „Aktuelles Passwort falsch" (`InvalidCurrentPasswordException.java:13`). AC4 verlangt wörtlich „Passwort falsch", und im Löschdialog gibt es kein „neues" Passwort, zu dem „aktuelles" den Gegensatz bildete. Vom User bestätigt. |
| Bestätigung auf `/login` | Angular Navigation-State (`{ state: { accountDeleted: true } }`) | Ein Query-Param wäre bookmarkbar und überlebte einen Reload — „Konto gelöscht" stünde dann auf einem frisch aufgerufenen Login. Storage scheidet nach ADR-7 aus. Der Navigation-State trägt nur ein Boolean, nie das Passwort. |
| `Modal` erweitern | additiver Input `confirmDisabled` (Default `false`) | AC1 verlangt einen deaktivierbaren Submit; `Modal` kann das heute nicht. Additiv — die drei bestehenden Aufworte (`pdf-upload`, `fixed-cost-list`, `styleguide`) bleiben unverändert. Eine eigene Dialog-Implementierung in `Settings` würde Fokus-Falle und Escape-Handling duplizieren. |
| Initialfokus im Dialog | bleibt auf „Abbrechen" | `modal.html:24-26` begründet das ausdrücklich: ein versehentliches Enter direkt nach dem Öffnen darf nicht die Aktion auslösen, die der Dialog absichert. Eine Änderung wäre eine Scope-Erweiterung an einer geteilten Komponente. |
| Kein E2E-Test | Vitest/TestBed | Ein E2E-Lauf würde den Seed-User echt löschen und die Suite von ihrer Ausführungsreihenfolge abhängig machen. |

## Betroffene Dateien

### Ändern

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/app/auth/auth.service.ts` | `deleteAccount(passwort)`: `DELETE /api/users/me` mit Body, leert bei Erfolg `currentUserState` (analog `logout()`) |
| `frontend/src/app/shared/modal/modal.ts` | Input `confirmDisabled` |
| `frontend/src/app/shared/modal/modal.html` | `[disabled]` am Bestätigen-Button |
| `frontend/src/app/settings/settings.ts` | Dialog-State, Passwort-Control, `confirmDelete()` |
| `frontend/src/app/settings/settings.html` | Card „Konto löschen" + Dialog |
| `frontend/src/app/settings/settings.scss` | Stil der Lösch-Card |
| `frontend/src/app/auth/login.ts` | Signal `accountDeleted` aus dem Navigation-State |
| `frontend/src/app/auth/login.html` | Bestätigungs-Notice |

### Tests erweitern

`auth.service.spec.ts`, `modal.spec.ts`, `settings.spec.ts`, `login.spec.ts` — keine neuen Dateien.

## Implementierungsschritte

1. `AuthService.deleteAccount(passwort: string): Observable<void>` — `http.delete<void>('/api/users/me', { body: { passwort } })` mit `tap(() => currentUserState.set(null))`.
2. `Modal`: `confirmDisabled = input(false, { transform: booleanAttribute })`, gebunden an `[disabled]` des Bestätigen-Buttons.
3. `Settings`: Signals `deleteDialogOpen`, `deleteSubmitting`, `deleteErrorMessage` plus `deletePasswordForm`. `confirmDelete()` ruft den Service, leert bei Erfolg zusätzlich den `NotificationService` (wie `shell.ts:143-156`) und navigiert mit `state: { accountDeleted: true }` auf `/login`. Abbrechen und Escape sind gesperrt, solange der Request läuft.
4. `settings.html`: Card „Konto löschen" mit Warntext und Button; `@if (deleteDialogOpen())` rendert `<app-modal confirmLabel="Konto löschen" [confirmDisabled]="…">` mit projiziertem Passwort-`app-field` und Fehler-`app-notice`.
5. `Login`: Signal `accountDeleted` aus `router.getCurrentNavigation()?.extras.state`, rendert eine `app-notice variant="info"`.

## Test-Strategie (Vitest + Angular TestBed)

| AC | Test |
| -- | ---- |
| AC1 | Card und Button vorhanden, Dialog erst nach Klick; Submit deaktiviert bei leerem Feld; Submit deaktiviert während laufendem Request |
| AC2 | Bestätigen sendet `DELETE /api/users/me`, `req.request.body` enthält das Passwort |
| AC3 | Integrationstest mit `App`-Root-Fixture (Muster aus `settings.spec.ts:570-600`): echte Navigation, `router.url === '/login'`, Bestätigung im DOM |
| AC4 | 400 → „Passwort falsch" im Dialog, kein `navigate`, `currentUser()` bleibt gesetzt |
| AC5 | Expliziter Test: `localStorage` und `sessionStorage` bleiben nach dem Löschen unverändert — sonst wäre dieses AC nur durch eine Suche belegt und gälte damit als unbelegt |
| — | `Modal`: `confirmDisabled` wirkt, Default bleibt aktiv; `Login`: ohne Navigation-State keine Bestätigung (Regressionsschutz) |

## Acceptance Criteria (aus dem Issue)

- [ ] Einstellungs-Screen zeigt eine Aktion „Konto löschen", die einen Bestätigungsdialog mit Passwort-Eingabe öffnet; Submit ist deaktiviert, solange das Feld leer ist oder ein Request läuft
- [ ] Bestätigtes Löschen ruft `DELETE /api/users/me` mit dem eingegebenen Passwort im Body auf
- [ ] Erfolgreiche Löschung navigiert zur Login-Seite und zeigt dort eine Bestätigung, dass das Konto gelöscht wurde
- [ ] Antwortet das Backend mit `400` wegen falschem Passwort, erscheint „Passwort falsch" am Dialog — der User bleibt eingeloggt und im Einstellungs-Screen
- [ ] Passwort steht zu keinem Zeitpunkt in `localStorage`, `sessionStorage` oder einem Log (ADR-7)
- [ ] Tests decken ab: Happy Path, falsches Passwort

## Analyse-Notiz zu AC5

Das AC ist such-förmig. Nach der Regel aus dem Skill wurde die Suche eng (AC-Wortlaut) und breit
(Persistenz und Ausgabe allgemein) ausgeführt:

- eng: `grep -rn "localStorage\|sessionStorage" frontend/src/app`
- breit: `grep -rnE "localStorage|sessionStorage|indexedDB|document\.cookie|console\.(log|warn|error|info|debug)|history\.(push|replace)State|queryParams" frontend/src/app`

Die Treffermengen unterscheiden sich nur um `queryParams` in `dashboard.ts` und
`category-overview.ts` (Monatswahl, kein Passwortbezug). `localStorage` wird ausschliesslich vom
Theme benutzt (`theme.ts:111`); `sessionStorage`, `console.*` und `document.cookie` kommen im
Frontend nirgends vor. **Kein Delta**, das eine Scope-Erweiterung auslöste. Die breite Suche hat
aber den Entscheid gegen den Query-Param oben mitbegründet.
