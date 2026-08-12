# [FE-FC-02] Route Guard für Onboarding

- **Issue:** [#25](https://github.com/dfme/budget-buddy/issues/25)
- **Task-ID:** `FE-FC-02`
- **Branch:** `feature/FE-FC-02-onboarding-guard`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-12

## Ausgangslage

Beide Vorbedingungen liegen auf `main`:

- **#24 (FE-FC-01)** ist gemergt — `frontend/src/app/onboarding/fixed-cost-wizard.ts` erfasst
  Positionen und ruft `POST /fixed-costs`. Der Wizard hat heute keinen Abschluss und keinen Zwang;
  beides ist ausdrücklich diesem Task zugewiesen (Kommentar am Component, Zeile 67–69).
- **#12 (BE-FC-03)** ist gemergt — `POST /users/me/onboarding-complete` existiert
  (`UserController.java:60`), ist idempotent und liefert das aktualisierte `UserProfileResponse`
  zurück. `GET /users/me` liefert `onboardingCompleted` bereits seit BE-AUTH-02.

Frontend-seitig ist `onboardingCompleted` im `User`-Model vorhanden
(`frontend/src/app/auth/user.model.ts:10`), wird aber nirgends ausgewertet.

Backend braucht **keine** Änderung: `/onboarding` steht bereits in
`SpaForwardController.CLIENT_ROUTE_PATTERNS` (Zeile 50–52), Deep-Link und Hard-Reload auf die
Wizard-Route funktionieren also schon.

## Scope-Erweiterung gegenüber den Issue-ACs

Die ACs des Issues nennen als Abschluss-Aktion nur den «Keine Fixkosten»-Button.
`docs/requirements/US-03-fixkosten-wizard.md:11` verlangt den Abschluss aber bei
«mindestens ein Eintrag gespeichert **oder** explizit 'Keine Fixkosten' bestätigt».

Der Unterschied ist nicht kosmetisch: weder `POST /fixed-costs` noch der `FixedCostService`
setzen `onboardingCompleted` (nachgeprüft: `completeOnboarding` kommt im Backend ausschliesslich
in `auth/UserService.java:57` vor). Ohne diese Erweiterung sperrte der neue Guard genau die
Nutzer dauerhaft im Wizard ein, die ihre Fixkosten korrekt erfasst haben.

**Entscheid des Users:** mitfixen und im PR-Body deklarieren.

Ebenfalls entschieden: `/onboarding` bleibt für bereits onboardete Nutzer per Direkt-Link
erreichbar. `US-03:19` («Wizard wird nicht mehr angezeigt») bezieht sich auf den Zwang, nicht auf
eine Sperre — und solange die Fixkosten-Liste aus #26 (FE-FC-03) fehlt, ist der Wizard der einzige
Weg, später eine Position nachzutragen.

## Entscheide

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Wo greift der Guard? | An `dashboard`, `categories`, `import`, zusätzlich zu `authGuard`. Nicht an `/onboarding` selbst. | Ein Guard an der Wizard-Route selbst wäre eine Endlosschleife. `login`/`register` sind anonym, `styleguide` hängt am `devOnlyGuard`. Die Wildcard-Route `**` leitet auf `dashboard` und ist damit mit abgedeckt. |
| Woher kommt der Onboarding-Status? | `GET /users/me` über den `AuthService` (AC3). | Einzige Quelle; das Profil liegt nach dem Laden als Signal vor. |
| Doppelter Request? | Neu `AuthService.ensureCurrentUser()`: liefert den geladenen User oder lädt ihn nach. `authGuard` wird auf dieselbe Methode umgestellt. | Ohne das gäbe es zwei Guards mit je eigener «geladen oder nachladen»-Logik am selben Route-Eintrag — und beim ersten Aufruf potenziell zwei `GET /users/me`. |
| `user === null` im `onboardingGuard`? | `true` zurückgeben. | Die Entscheidung über anonyme Nutzer gehört dem `authGuard`, der am selben `canActivate`-Array hängt und `/login` liefert. Ein zweiter Redirect hier wäre eine zweite Wahrheit über denselben Fall. |
| Abschluss-Button | Ein Button unter dem Formular. Label: «Keine Fixkosten — weiter zum Dashboard», nach der ersten gespeicherten Position «Fertig — weiter zum Dashboard». | Beide Fälle lösen dieselbe Aktion aus (`POST /users/me/onboarding-complete` + Navigation). Ein zweiter Button wäre dieselbe Aktion mit zwei Beschriftungen. |
| State nach dem POST | `AuthService.completeOnboarding()` schreibt die Antwort in `currentUserState`. | Zwingend: sonst steht `onboardingCompleted: false` noch im Signal und der Guard wirft den Nutzer bei der Navigation auf `/dashboard` sofort in den Wizard zurück. |

## Betroffene Files

**Neu**

| Datei | Inhalt |
| ----- | ------ |
| `frontend/src/app/core/guards/onboarding.guard.ts` | `onboardingGuard` |
| `frontend/src/app/core/guards/onboarding.guard.spec.ts` | Tests dazu |

**Geändert**

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/app/auth/auth.service.ts` | `ensureCurrentUser()`, `completeOnboarding()` |
| `frontend/src/app/auth/auth.service.spec.ts` | Tests für beide Methoden |
| `frontend/src/app/core/guards/auth.guard.ts` | nutzt `ensureCurrentUser()` statt eigener Fallunterscheidung |
| `frontend/src/app/core/guards/auth.guard.spec.ts` | erste Assertion: der Guard liefert jetzt ein Observable statt literal `true` |
| `frontend/src/app/app.routes.ts` | `onboardingGuard` an drei Routes |
| `frontend/src/app/onboarding/fixed-cost-wizard.ts` | Abschluss-Aktion, Signals, Navigation |
| `frontend/src/app/onboarding/fixed-cost-wizard.html` | Button + Fehler-Notice |
| `frontend/src/app/onboarding/fixed-cost-wizard.spec.ts` | Tests für die Abschluss-Aktion |

## Implementierungsschritte

1. `AuthService.ensureCurrentUser(): Observable<User | null>` — `of(cached)`, sonst
   `loadCurrentUser()`.
2. `AuthService.completeOnboarding(): Observable<User>` — `POST /users/me/onboarding-complete`,
   Antwort per `tap` in `currentUserState`.
3. `authGuard` auf `ensureCurrentUser()` umstellen; Verhalten bleibt identisch.
4. `onboardingGuard` schreiben: `onboardingCompleted` → `true`, sonst
   `router.createUrlTree(['/onboarding'])`; `null` → `true` (siehe Entscheide).
5. `app.routes.ts`: `canActivate: [authGuard, onboardingGuard]` an `dashboard`, `categories`,
   `import`.
6. Wizard: Signals `hasSaved`, `completing`, `completeError`; Methode `finishOnboarding()`;
   Button und Fehler-Notice im Template.

## Test-Strategie

Vitest + Angular TestBed, `HttpTestingController` — dieselbe Bauweise wie `auth.guard.spec.ts`.

| Datei | Fälle |
| ----- | ----- |
| `onboarding.guard.spec.ts` | nicht onboardet → `UrlTree('/onboarding')` · onboardet → `true` · bereits geladener State → **kein** HTTP-Call · leerer State → `GET /users/me` wird abgesetzt (Beleg für AC3) |
| `auth.service.spec.ts` | `completeOnboarding` postet auf `/users/me/onboarding-complete` und aktualisiert `currentUser` auf `onboardingCompleted: true` · `ensureCurrentUser` liefert Cache ohne Request und lädt sonst nach |
| `fixed-cost-wizard.spec.ts` | Button postet und navigiert auf `/dashboard` · Label wechselt nach erfolgreichem Speichern · Fehler zeigt Notice und navigiert **nicht** |
| `app.routes` (in `onboarding.guard.spec.ts`) | die drei geschützten Routes tragen den Guard, `/onboarding` nicht |

E2E bleibt bei #123 (E2E-FC-01) — eigenes Issue, eigener Branch, eigener PR.

Verifikation: `npm test` und `npm run build` in `frontend/`. Das Backend ist unberührt,
`mvn package` bleibt unverändert.

## Nachtrag aus der Umsetzung

Der Plan ging davon aus, Angular führe die Guards eines `canActivate`-Arrays **nacheinander**
aus und breche beim ersten `UrlTree` ab. Das stimmt nicht: der Navigationstest in
`onboarding.guard.spec.ts` deckte auf, dass `authGuard` und `onboardingGuard` **nebenläufig**
laufen — beide sahen den State leer und setzten je ein eigenes `GET /users/me` ab.

Zwei Konsequenzen, beide umgesetzt:

1. `ensureCurrentUser()` bündelt gleichzeitige Aufrufer per `shareReplay` auf **einen** Request.
   Ein reiner Cache-Check genügt nicht — zum Zeitpunkt des zweiten Aufrufs gibt es noch keine
   Antwort, die er cachen könnte.
2. Das Verhalten im Anonymfall hängt nicht mehr an einer Reihenfolge: der `onboardingGuard`
   äussert dort gar keine Meinung (`true`), womit der `UrlTree` des `authGuard` der einzige
   bleibt und unabhängig davon gewinnt, wer zuerst fertig ist.

## Acceptance Criteria (aus #25)

- [ ] Nicht-ongeboardeter User wird auf Wizard-Route umgeleitet
- [ ] 'Keine Fixkosten'-Button setzt `onboarding_completed` via `POST /users/me/onboarding-complete`
- [ ] Guard prüft Onboarding-Status via `GET /users/me`

Zusätzlich (Scope-Erweiterung, siehe oben):

- [ ] Nach mindestens einer gespeicherten Position schliesst derselbe Button das Onboarding ab
      (`US-03:11`)
