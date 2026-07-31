# [INFRA-14] Playwright-E2E-Setup aufsetzen

- **Issue:** [#91](https://github.com/dfme/budget-buddy/issues/91)
- **Task-ID:** `INFRA-14`
- **Branch:** `feature/INFRA-14-playwright-e2e-setup`
- **Bestätigt am:** 2026-07-30

## Ausgangslage

Die Definition of Done verlangt „Happy Path ist durch automatisierten Test abgedeckt (Playwright
oder JUnit)". Für Frontend-Stories ist der Punkt nicht erfüllbar: `e2e/` existiert nicht, es gibt
weder Playwright-Dependency noch CI-Job. Aufgefallen im Review zu #90 (FE-CAT-01).

## Entscheide

### 1. E2E-Target: Single JAR, prod-nah

Tests laufen gegen **ein** JAR auf `http://localhost:8080` — dasselbe Artefakt, das Render
deployt (`./mvnw -Pprod package`, SPA in `BOOT-INF/static/`).

```
Playwright (chromium) ──► :8080 ──┬── /index.html   SPA aus BOOT-INF/static
                                  └── /auth, /users API
              SQLITE_DB_PATH=<frische Datei je Run>
```

Begründung: same-origin, daher verhält sich das `SameSite=Strict`-Cookie exakt wie in Produktion;
nur eine `baseURL`; und es ist das Artefakt, das ausgeliefert wird.

Verworfen: `ng serve` (:4200) + separates Backend (:8080) über `proxy.conf.json`. Spiegelt zwar
das dokumentierte Dev-Setup und baut das Backend ohne Node, testet aber nicht das ausgelieferte
Artefakt — der Dev-Proxy überdeckt SPA-Routing-Lücken (siehe Entscheid 2).

### 2. Scope-Erweiterung: SPA-Deep-Link-Lücke wird mitgefixt

Beim Prüfen, wie die Acceptance Criteria überhaupt verifizierbar sind, fiel auf: der
SPA-Deep-Link-Registry deckt nur `/dashboard` und `/login` ab. Nachweis gegen das laufende
Deployment am 2026-07-30:

```
$ for p in / /login /register /categories /import; do
    curl -s -o /dev/null -w '%{http_code}' https://budgetbuddy-0myo.onrender.com$p
  done

/           200
/login      200
/register   401
/categories 401
/import     401
```

Ursache: `SecurityConfig.SPA_GET_PATHS` und `SpaForwardController` listen die Routen nicht, also
greift `anyRequest().authenticated()` und der `HttpStatusEntryPoint` antwortet mit 401.

Entscheid: in diesem PR mitfixen, Scope-Erweiterung im PR-Body deklarieren. Ohne den Fix kann der
Auth-Test nicht via `goto('/register')` starten — und es ist genau die Bug-Klasse, für die E2E
existiert.

`/styleguide` bleibt bewusst draussen: die Route hängt am `devOnlyGuard`
(`frontend/src/app/app.routes.ts:33-37`) und soll in Produktion nicht erreichbar sein.

### 3. CI-Einbindung: dritter Job in `build.yml`

`build.yml` ist laut eigenem Kommentar Single Source of Truth und wird von `ci.yml`
(Pull Request) und `cd.yml` (Push auf main) aufgerufen. E2E gated damit auch den Render-Deploy.

Verworfen: eigener `e2e.yml` mit `on: pull_request`. Hält die CD-Pipeline schneller, aber ein Push
auf main würde deployen, ohne dass E2E gelaufen ist.

### 4. `JWT_SECRET` in Tests

Braucht ≥32 Zeichen (`JwtProperties.java:22-24`). In CI zufällig via `openssl rand -hex 32`.
Lokal ein klar benannter Fallback-Literal in `playwright.config.ts` — kein echtes Credential, es
signiert ausschliesslich Tokens gegen eine Wegwerf-SQLite-Datei.

## Betroffene Files

### Neu

| File | Zweck |
| --- | --- |
| `e2e/package.json` + `package-lock.json` | `@playwright/test`, Scripts (`test`, `test:ui`) |
| `e2e/playwright.config.ts` | `webServer` (JAR), `baseURL`, chromium, `workers: 1` |
| `e2e/tsconfig.json` | TS-Config für die Tests |
| `e2e/fixtures/auth.fixture.ts` | Auth-Fixture für das httpOnly-JWT-Cookie |
| `e2e/tests/auth.spec.ts` | Register → Login → geschützte Route + Fehlerpfad |
| `e2e/README.md` | lokaler Ablauf (JAR bauen → `npm test`) |
| `e2e/.gitignore` | `node_modules/`, `test-results/`, `playwright-report/` |

### Geändert

| File | Änderung |
| --- | --- |
| `.github/workflows/build.yml` | dritter Job `e2e` neben `backend`/`frontend` |
| `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java` | `SPA_GET_PATHS` += `/register/**`, `/categories/**`, `/import/**` |
| `backend/src/main/java/com/budgetbuddy/config/SpaForwardController.java` | `@GetMapping` um dieselben Routen erweitern |
| `backend/src/test/java/com/budgetbuddy/config/SpaRoutingTest.java` | `@ParameterizedTest` über alle SPA-Routen statt nur `/dashboard` |

## Implementierungsschritte

1. **SPA-Deep-Link-Lücke schliessen**: `/register`, `/categories`, `/import` in `SecurityConfig`
   und `SpaForwardController` nachtragen; `SpaRoutingTest` auf eine parametrisierte Liste
   umstellen, damit die nächste neue Route nicht wieder stillschweigend 401 liefert.
2. **`e2e/`-Setup**: eigenes npm-Projekt, nicht an `frontend/package.json` angehängt — E2E testet
   beide Seiten, CLAUDE.md verlangt die Trennung ausserhalb von `backend/` und `frontend/`.
3. **`playwright.config.ts`**: `webServer` startet das JAR mit `SQLITE_DB_PATH` auf eine frische
   Datei je Run und `JWT_SECRET` aus der Umgebung. `reuseExistingServer` lokal an, in CI aus.
   `workers: 1` — SQLite hat einen einzigen Writer, parallele Register-Calls würden `SQLITE_BUSY`
   riskieren.
4. **Auth-Fixture**: `context.request.post('/auth/register', …)` teilt den Cookie-Jar mit dem
   BrowserContext — so landet das httpOnly-Cookie im Browser, ohne dass JS es je sieht. Die
   Fixture liefert `authenticatedPage` + den erzeugten `testUser` (Unique-E-Mail je Test, da die
   DB innerhalb eines Runs geteilt ist).
5. **`build.yml`**: Job `e2e` — JDK 25 + Node 22, `./mvnw -Pprod -DskipTests package`,
   `npx playwright install --with-deps chromium`, `npm test`. `JWT_SECRET` per
   `openssl rand -hex 32` im Job erzeugt. Report als Artifact bei Fehlschlag.
6. **Rot-Nachweis**: siehe Test-Strategie.

## Test-Strategie

| Stufe | Test | Beweist |
| --- | --- | --- |
| E2E | Register über das Formular → `/dashboard` | Browser-Start, Backend erreichbar, Cookie wird gesetzt |
| E2E | Cookie-Assertion: `jwt` ist `httpOnly` + `sameSite: Strict` | ADR-7 im echten Browser, nicht nur im Unit-Test |
| E2E | Login über das Formular in frischem Context → `/dashboard` | Login-Pfad getrennt vom Register-Pfad |
| E2E | `/dashboard` ohne Cookie → Redirect `/login` (Fehlerpfad) | `authGuard` greift end-to-end |
| E2E | `authenticatedPage`-Fixture öffnet `/dashboard` direkt | die Fixture ist als Vorbedingung für US-03…US-06 nutzbar |
| Integration | `SpaRoutingTest` parametrisiert über alle SPA-Routen | die 401-Lücke bleibt zu |

**Rot-Nachweis (AC 5).** Der `e2e`-Job läuft nur `on: pull_request`, der Nachweis ist also erst
nach PR-Erstellung möglich. Ablauf: temporärer Commit mit gebrochener Assertion → roten Run
abwarten und URL festhalten → Revert-Commit → grünen Run festhalten. Beide Run-URLs kommen in den
PR-Body.

## Abweichungen während der Umsetzung

Vier Punkte kamen erst beim Bauen und Verifizieren heraus:

1. **Port 8081 statt 8080, `reuseExistingServer: false`.** Ursprünglich war 8080 mit
   `reuseExistingServer: !CI` geplant. Beim Verifizieren adoptierte Playwright eine fremde
   Instanz auf 8080, deren DB-Datei der Reset kurz zuvor gelöscht hatte — Ergebnis waren fünf
   irreführende Fehlschläge, die nichts mit dem Code zu tun hatten. Eigener Port plus kein Reuse:
   ein besetzter Port ist jetzt ein lauter Startfehler statt stiller Kontamination.

2. **DB-Reset nicht als `globalSetup`.** Playwright startet den `webServer` als Plugin *vor*
   `globalSetup`. Der Reset hätte die Datei unter der laufenden Instanz weg-unlinkt statt den
   Zustand zurückzusetzen. Er läuft jetzt beim Laden der Config, im Hauptprozess (Worker erkennbar
   an `TEST_WORKER_INDEX`).

3. **Exakte Route-Patterns statt `/**`** — sicherheitsrelevant. `/import` ist gleichzeitig
   Frontend-Route und API-Prefix (`PdfImportController`). `GET /import/**` wäre in
   `SecurityConfig` `permitAll` und hätte den in CLAUDE.md geplanten
   `GET /import/{jobId}/status` bei seiner Einführung ohne Auth lesbar gemacht (Risiko #2).
   `CLIENT_ROUTE_PATTERNS` listet deshalb exakte Pfade; zwei Regression-Tests halten das fest.

4. **Zwei Ergänzungen ohne Plan-Vorlage:** `npm run typecheck` (Playwright transpiliert TS, prüft
   aber keine Typen — der Schritt fand sofort einen echten Fehler) und
   `tests/spa-routing.spec.ts`, das die Deep-Link-Status-Codes gegen das echte Artefakt prüft.
   Letzteres ist die dauerhafte Form der Verifikation, die sonst ein Einmal-`curl` gewesen wäre —
   und schliesst die Lücke, dass `SpaRoutingTest` (MockMvc) gegen Test-Fixtures statt gegen den
   echten Angular-Build läuft.

## Acceptance Criteria (aus #91)

- [ ] `e2e/`-Verzeichnis mit Playwright-Setup gemäss CLAUDE.md (E2E-Verzeichnisstruktur),
      ausserhalb von `backend/` und `frontend/`
- [ ] Tests laufen gegen laufendes Frontend **und** Backend
- [ ] Auth-Fixture für das httpOnly-JWT-Cookie vorhanden — jeder der späteren Must-Have-Tests
      braucht sie als Vorbedingung
- [ ] E2E-Job ist in GitHub Actions eingebunden und läuft bei jedem PR
- [ ] Der CI-Job wird nachweislich **rot**, wenn ein Test fehlschlägt (einmal verifiziert)
- [ ] Ein Auth-basierter E2E-Test (Register → Login → geschützte Route) läuft grün und beweist
      Cookie-Handling im Testkontext + CI-Rot-bei-Fehlschlag

**Nicht Teil dieses Issues:** die acht Must-Have-Story-Fälle (je Happy Path + Fehlerpfad für
US-03, US-04, US-05, US-06). Folgearbeit, sobald das Setup steht.

## Definition of Done

- [ ] Code ist reviewed (mind. 1 Approval im PR)
- [ ] `mvn package` und `ng build` laufen fehlerfrei durch
- [ ] ~~Neue API-Endpoints sind in Swagger UI sichtbar~~ — n/a, keine neuen Endpoints
- [ ] Happy Path ist durch automatisierten Test abgedeckt (Playwright oder JUnit)
- [ ] Alle Acceptance Criteria oben sind abhakbar erfüllt
