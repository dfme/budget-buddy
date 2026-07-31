# E2E-Tests (Playwright)

End-to-End-Tests für BudgetBuddy — testen Frontend und Backend gemeinsam und liegen deshalb
ausserhalb von `backend/` und `frontend/` (INFRA-14, Issue
[#91](https://github.com/dfme/budget-buddy/issues/91)).

## Wogegen getestet wird

Gegen **ein** JAR aus dem Maven-Profil `prod`: es liefert die Angular-SPA aus
`BOOT-INF/static/` und die REST-API vom selben Origin — genau das Artefakt, das auf Render
deployt wird (ADR-10, Single-Artifact).

```
Playwright (chromium) ──► localhost:8081 ──┬── /index.html   SPA aus BOOT-INF/static
                                           └── /auth, /users API
```

Same-Origin ist hier nicht bloss bequem: nur so verhält sich das `SameSite=Strict`-JWT-Cookie im
Test wie in Produktion (ADR-7). Ein `ng serve` mit Dev-Proxy davor würde diese Semantik verdecken
— und hätte die 401-Lücke bei `/register` nicht gefunden, die dieses Setup aufgedeckt hat.

**Identisch ist das Artefakt, nicht die Laufzeitkonfiguration.** Die Testinstanz startet ohne
`SPRING_PROFILES_ACTIVE=prod`; auf Render setzt dieses Profil zusätzlich `app.cookie.secure=true`
(`application-prod.properties`). Von den drei Cookie-Eigenschaften prüfen die E2E-Tests deshalb
nur `HttpOnly` und `SameSite=Strict`.

Das `Secure`-Flag lässt sich hier grundsätzlich nicht prüfen: ein `Secure`-Cookie über
`http://localhost` würde der Browser gar nicht erst speichern — der Test wäre nicht strenger,
sondern kaputt. Abgedeckt ist es stattdessen als Unit-Test in
`backend/src/test/java/com/budgetbuddy/auth/JwtCookieFactoryTest.java`, der die Factory direkt mit
beiden Konfigurationswerten prüft.

**Port 8081, nicht 8080.** 8080 gehört dem Dev-Backend. Die Suite setzt die Datenbank vor jedem
Lauf zurück und darf deshalb nie an einer fremden Instanz hängen. Aus demselben Grund ist
`reuseExistingServer` auch lokal `false`: ein besetzter Port soll ein lauter Startfehler sein,
keine stille Kontamination.

Die Testinstanz benutzt eine eigene SQLite-Datei unter `.tmp/e2e.db` (gitignored) — die
Dev-Datenbank im Repo-Root wird nicht angefasst.

## Lokal ausführen

**Voraussetzung: Java 25 im `PATH`.** Playwright startet das JAR mit `java -jar`, also mit der
Standard-JVM der Shell — nicht mit der, die Maven benutzt. Mit einem älteren Default-JDK bricht
der Start mit `UnsupportedClassVersionError` ab (im Playwright-Output sichtbar, weil das
Backend-Log durchgereicht wird). Prüfen mit `java -version`.

```bash
# 1. Backend-JAR mit gebündelter SPA bauen (nur nach Code-Änderungen nötig)
cd backend && ./mvnw -Pprod -DskipTests package

# 2. Tests laufen lassen — Playwright startet und stoppt das JAR selbst
cd ../e2e
npm ci
npx playwright install chromium   # einmalig
npm test
```

`-Pprod` ist nicht optional: ohne das Profil enthält das JAR keine SPA. Der erste Test (»liefert
die im JAR gebündelte SPA aus«) schlägt dann als Einziger fehl und nennt genau diese Ursache.

| Befehl | Zweck |
| --- | --- |
| `npm test` | ganze Suite headless |
| `npm run test:ui` | Playwright-UI-Mode, zum Debuggen einzelner Tests |
| `npm run report` | HTML-Report des letzten Laufs öffnen |
| `npm run typecheck` | `tsc --noEmit` — Playwright typisiert selbst nicht, CI prüft das separat |

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `tests/auth.spec.ts` | Register → Login → geschützte Route, Cookie-Flags, Fehlerpfad |
| `tests/spa-routing.spec.ts` | Deep-Link-Status-Codes des Artefakts (SPA offen, API geschützt) |
| `fixtures/auth.fixture.ts` | Auth-Fixture: eingeloggte Session als Vorbedingung |
| `support/backend.ts` | Port, Pfade, JAR-Auflösung, DB-Reset der Testinstanz |
| `playwright.config.ts` | Runner-Konfiguration inkl. `webServer` |

## Die Auth-Fixture benutzen

Alle Must-Have-Stories (US-03…US-06) setzen eine eingeloggte Session voraus. Statt sich in jedem
Test durchs Login-Formular zu klicken — womit ein Bug im Auth-UI die halbe Suite rot färben würde
—, registriert die Fixture über die API:

```ts
import { expect, test } from '../fixtures/auth.fixture';

test('Fixkosten-Wizard speichert einen Posten', async ({ authenticatedPage }) => {
  await authenticatedPage.goto('/onboarding');
  // … die Session steht schon
});
```

Verfügbare Fixtures: `authenticatedPage` (Page mit gültigem Cookie), `authenticatedContext` (der
zugehörige BrowserContext, z. B. für `context.cookies()`) und `testUser` (die erzeugten
Credentials).

Das httpOnly-Cookie kommt über `context.request.post('/auth/register', …)` in den Browser:
`context.request` teilt den Cookie-Jar mit dem BrowserContext. `document.cookie` oder
`addInitScript` sind kein Ersatz — das Cookie ist per Definition für JS unsichtbar (ADR-7).

## CI

Der Job `E2E (Playwright)` in [`.github/workflows/build.yml`](../.github/workflows/build.yml)
läuft bei jedem Pull Request (via `ci.yml`) **und** vor jedem Deploy auf main (via `cd.yml`) —
ein Push auf main soll nicht ohne E2E deployen. Bei Fehlschlag lädt der Job den HTML-Report als
Artifact `playwright-report` hoch (7 Tage).

`JWT_SECRET` wird im Job zufällig erzeugt (`openssl rand -hex 32`) und ist bewusst kein
Repo-Secret: der Wert signiert nur Tokens gegen die Wegwerf-Datenbank des Runs. Lokal greift ein
klar benannter Fallback in `support/backend.ts`, damit ein Lauf ohne Env-Setup funktioniert.

## Scope

Enthalten ist der Auth-Flow als Verifikation der Harness. Die acht Must-Have-Story-Fälle (je
Happy Path + Fehlerpfad für US-03, US-04, US-05, US-06) sind Folgearbeit — der
US-03-Happy-Path hängt am Fixkosten-Feature, weil er dessen Wizard und Endpoints voraussetzt.

Chromium genügt für den MVP: geprüft werden Flows und Cookie-Handling, nicht
Rendering-Unterschiede.
