# [E2E-FC-02] Playwright: Onboarding-Abschluss (Wizard → Dashboard)

- **Issue:** [#190](https://github.com/dfme/budget-buddy/issues/190)
- **Task-ID:** `E2E-FC-02`
- **Branch:** `feature/E2E-FC-02-onboarding-abschluss`
- **Story:** US-03 — Fixkosten-Wizard
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-04

## Ausgangslage

`finishOnboarding()` (`fixed-cost-wizard.ts:179-198`) ruft `POST /users/me/onboarding-complete` und
navigiert danach aufs Dashboard — erst dadurch lässt der `onboardingGuard` die Route passieren.
Dieser Übergang ist E2E nirgends abgedeckt.

Der Grund steht in E2E-FC-01 (#123, PR #189): dessen AC schreibt die `authenticatedPage`-Fixture
vor, und die schliesst das Onboarding schon per API ab (`auth.fixture.ts:84`). Beim Teststart steht
`onboardingCompleted` damit auf `true` — der Übergang `false → true` ist über diese Fixture
**prinzipiell nicht beobachtbar**. Der Hauptaufwand dieses Tasks ist deshalb nicht der Test,
sondern die Fixture-Variante.

Die Zeilenverweise des Issues wurden gegengeprüft und stimmen alle: `auth.spec.ts:29` und `:76`
(Redirect nach Registrierung bzw. Login), `auth.fixture.ts:84` (unbedingter
`onboarding-complete`-Aufruf), `fixed-cost-wizard.ts:26-30` (`hasSaved` steuert nur die
Beschriftung). Es gibt kein Scope-Delta gegenüber den ACs.

## Entscheide

### 1. Gemeinsamer Register-Helper statt Duplikat

`registerViaApi(context, testUser)` wird als Modulfunktion herausgezogen. `authenticatedContext`
ruft sie und danach unverändert `onboarding-complete`; die neue `freshUserContext` ruft nur sie.

AC 1 verlangt, dass der bestehende `authenticatedPage`-Pfad «unverändert» bleibt. Gelesen als
**Verhalten**, nicht als Bytes: Das Alternativ-Vorgehen — den Register-Block duplizieren — erfüllte
den Wortlaut enger, hinterliesse aber zwei Kopien desselben Aufrufs samt Assertion, die
auseinanderlaufen können, sobald `POST /api/auth/register` einmal ein Feld mehr verlangt.

Absicherung: Alle sechs bestehenden Specs hängen an `authenticatedPage`. Eine Regression an dieser
Fixture färbt die Suite sofort rot; der volle `npm test`-Lauf ist damit der Nachweis für AC 1.

### 2. Neue Datei statt Anhängen

`e2e/tests/onboarding-completion.spec.ts`. Der Docblock von `fixed-cost-wizard.spec.ts` erklärt
ausdrücklich den Einstieg über `authenticatedPage` und begründet, warum der Redirect dort kein
Thema ist. Tests mit der gegenteiligen Vorbedingung daneben zu setzen würde genau diese Erklärung
entwerten. Getrennte Datei, getrennte Vorbedingung.

### 3. Reload als Hauptnachweis für AC 4

Ein Reload wirft den `AuthService`-State weg und lässt den `onboardingGuard` gegen
`GET /api/users/me` neu entscheiden. Er belegt damit den **persistierten** Übergang und nicht bloss
einen In-Memory-Zustand, der eine Client-Navigation überlebt. Eine zweite Navigation auf
`/dashboard` kommt daneben — AC 4 nennt beides als zulässig.

### 4. API-Gegenprobe zusätzlich, nicht als Ersatz

`GET /api/users/me` → `onboardingCompleted === true` in beiden Wegen. Ohne sie sagt ein Fehlschlag
nur «URL ist /onboarding statt /dashboard», und ob der Request nie hinausging oder der Guard falsch
entschied, bliebe offen.

### 5. Zwei Tests sind kein Konventionsbruch

`docs/CONVENTIONS.md` (Testing: Frameworks) verlangt pro Must-Have-Story **1 Happy Path + 1
Fehlerpfad**. US-03 hat beides bereits aus E2E-FC-01. Die zwei Wege hier kommen obendrauf — die
Konvention ist ein Minimum, keine Obergrenze, und das Issue verlangt beide Wege ausdrücklich als
eigene ACs.

### 6. Kein Produktionscode

Weder `frontend/` noch `backend/` werden angefasst. Wizard, Guard und
`POST /users/me/onboarding-complete` sind fertig; dieser Task belegt sie nur.

## Betroffene Dateien

| Datei | Änderung |
| ----- | -------- |
| `e2e/fixtures/auth.fixture.ts` | `registerViaApi`-Helper herausziehen; `freshUserContext` und `freshUserPage` ergänzen |
| `e2e/tests/onboarding-completion.spec.ts` | **neu** — Weg A und Weg B |
| `e2e/README.md` | Fixture-Liste (nennt heute nur `authenticatedPage`, `authenticatedContext`, `testUser`) und Scope-Abschnitt nachziehen |

## Implementierungsschritte

1. `registerViaApi` als Modulfunktion; `authenticatedContext` ruft sie und danach unverändert
   `onboarding-complete`.
2. `freshUserContext` und `freshUserPage` ergänzen — mit Javadoc dazu, **warum** es sie gibt, und
   mit dem Hinweis, dass beide Context-Fixtures auf derselben eingebauten `context`-Fixture sitzen
   und deshalb nicht in einem Test kombiniert werden dürfen.
3. `onboarding-completion.spec.ts` mit beiden Wegen schreiben.
4. `e2e/README.md` nachziehen.
5. `npm run typecheck` und `npm test` in `e2e/` gegen ein lokal gebautes `-Pprod`-JAR.

## Test-Strategie

Reines E2E, zwei Tests.

### Weg A — mit gespeicherter Position

| Schritt | Assertion | AC |
| ------- | --------- | -- |
| `goto('/dashboard')` als frischer User | landet auf `/onboarding` | Ausgangszustand: belegt, dass die Fixture wirklich nicht onboardet ist |
| Position erfassen und speichern | Erfolgs-Notice erscheint | Vorbedingung für `hasSaved` |
| Abschluss-Button | trägt «Fertig — weiter zum Dashboard» | 2 |
| Klick | URL ist `/dashboard`, Dashboard-Überschrift sichtbar | 2 |
| `reload()` | bleibt `/dashboard` | 4 |
| erneutes `goto('/dashboard')` | bleibt `/dashboard` | 4 |
| `GET /api/users/me` | `onboardingCompleted === true` | Diagnose |

### Weg B — ohne Position

Gleicher Ablauf ohne Speichern; der Button trägt «Keine Fixkosten — weiter zum Dashboard». Dass die
Beschriftung eine andere ist und die Wirkung dieselbe, ist genau die Aussage aus
`fixed-cost-wizard.ts:26-30`, die bisher nur im Unit-Test belegt war.

### Zeichensatz-Falle

Beide Button-Namen enthalten einen **Geviertstrich** (U+2014), keinen ASCII-Bindestrich. Er kommt
als `—`-Escape in die Datei — dieselbe Begründung wie beim U+2019 in
`fixed-cost-wizard.spec.ts`: als literales Zeichen ist er im Quelltext von `-` kaum zu
unterscheiden, und genau diese Verwechslung wäre die Falle, die der Test sonst selbst
hineinschreibt.

### AC 5 und 6

Ergeben sich aus der Ablage: `testDir: './tests'` (`playwright.config.ts:19`) nimmt die Datei auf,
und der CI-Job `E2E (Playwright)` ruft `npm test` (`.github/workflows/build.yml`) — kein neuer Job.

## Acceptance Criteria (aus dem Issue)

- [ ] Fixture-Variante für eine eingeloggte Session **ohne** abgeschlossenes Onboarding; der
      bestehende `authenticatedPage`-Pfad bleibt unverändert (US-04…US-06 hängen daran)
- [ ] Weg A: frisches Konto → Wizard → mindestens eine Position speichern → Abschluss-Button
      («Fertig — weiter zum Dashboard») → Dashboard wird erreicht und der `onboardingGuard` leitet
      nicht mehr in den Wizard zurück
- [ ] Weg B: frisches Konto → Wizard → **ohne** Position direkt abschliessen («Keine Fixkosten —
      weiter zum Dashboard») → dasselbe Ergebnis
- [ ] Gegenprobe, dass der Übergang echt ist: ein Reload bzw. eine erneute Navigation auf
      `/dashboard` landet danach nicht wieder auf `/onboarding`
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npm test` in `e2e/`
- [ ] Läuft im bestehenden CI-Job `E2E (Playwright)` mit — kein neuer Job nötig
