# [INFRA-17] SPA-Routen /import, /categories, /register per Deep-Link nicht erreichbar

- **Issue:** [#126](https://github.com/dfme/budget-buddy/issues/126)
- **Task-ID:** `INFRA-17`
- **Branch:** `fix/INFRA-17-api-prefix-spa-catchall`
- **Story:** — (kein us-\*-Label; Bug-Fix an der SPA-Routing-Infrastruktur, betrifft alle Stories mit Frontend-Routen)
- **Sprint:** unbekannt (kein Board-Zugriff in dieser Session — Scope `project` fehlt am Token)
- **Bestätigt am:** 2026-08-20

## Ausgangslage

Der ursprüngliche Befund (Hard-Reload/Deep-Link auf `/register`, `/categories`, `/import` liefert
401/404 statt der SPA) ist bereits mit INFRA-14 (PR #120, gemerged 2026-07-31) behoben —
`SecurityConfig.SPA_GET_PATHS` leitet seither aus `SpaForwardController.CLIENT_ROUTE_PATTERNS`
ab. PR #171 (FE-CAT-04, gemerged 2026-08-15) bestätigt das im Text: *"das konkrete Symptom ist
mit INFRA-14 behoben ... Offen geblieben ist der strukturelle Teil (API unter `/api/**`)."*

Der Kommentar von dfme auf #126 weist ausdrücklich darauf hin, dass die 1-SP-Schätzung nur
Option 1 (Routen einzeln nachtragen) abdeckt und wer das Ticket zieht, die Option zuerst
festlegen soll. Entscheid mit dem User: **Option 2** — API unter `/api/**`, alles andere per
Catch-all auf `index.html` forwarden. Das löst das Doppelpflege-Problem strukturell (AC5) statt
es nur für die aktuell bekannten Routen zu flicken, und schliesst nebenbei die `/styleguide`-Lücke
aus AC1 automatisch mit (kein enumerierter Ausschluss mehr nötig — der clientseitige
`devOnlyGuard` versteckt den Inhalt in Prod weiterhin).

## Entscheidungen

### 1. Alle REST-Endpoints unter `/api/**`

Jeder der 6 Controller (`AuthController`, `UserController`, `BudgetController`,
`FixedCostController`, `PdfImportController`, `TransactionCategoryController`,
`TransactionListController`, `TransactionSummaryController`) bekommt `/api` vor sein bisheriges
`@RequestMapping`-Präfix: `/auth`→`/api/auth`, `/users/me`→`/api/users/me`, `/budget`→`/api/budget`,
`/fixed-costs`→`/api/fixed-costs`, `/import`→`/api/import`, `/transactions`→`/api/transactions`.

### 2. `SecurityConfig`: enumerierte Listen weg, stattdessen Präfix-Regel

`SPA_ASSET_GET_PATHS`/`SPA_GET_PATHS` entfallen vollständig. Neue Regelkette:

```
PUBLIC_PATHS (/api/auth/**, Swagger, /actuator/health, /actuator/info, /error) → permitAll
/api/**                                                                        → authenticated
GET /**  (alles andere: Assets, SPA-Shell, jeder Deep-Link)                    → permitAll
anyRequest()                                                                   → authenticated
```

### 3. `SpaForwardController`: Catch-all statt Enumeration

`CLIENT_ROUTE_PATTERNS` entfällt. Zwei `@GetMapping`-Patterns mit Negative-Lookahead auf die
bekannten Nicht-SPA-Top-Segmente (`api|actuator|error|v3|swagger-ui`) und Ausschluss gepunkteter
Dateinamen (damit echte statische Assets weiter vom Resource-Handler bedient werden):

- ein Segment: `/dashboard`, `/login`, `/register`, `/styleguide`, …
- verschachtelt: `/categories/lebensmittel`-artige Kind-Routen (im alten Javadoc bereits als
  zukünftiger Bedarf vermerkt, jetzt strukturell abgedeckt statt durch manuelles Nachtragen)

**Bewusst kein `ErrorController`/404-Fallback-Ansatz:** Das würde dieselbe `/error`-Maschinerie
anfassen, an der FE-PDF-02s 408/409-Dispatch bereits hängt (`PdfImportErrorDispatchIntegrationTest`)
— unnötiges Regressionsrisiko. Der GetMapping-Catch-all lässt diesen Pfad unangetastet.

**Validierung des Precedence-Risikos:** Spring bevorzugt bei der Handler-Auflösung die
spezifischste Route (`/api/fixed-costs/{id}` schlägt den generischen Catch-all), das ist
dokumentiertes Verhalten, aber empirisch statt nur in der Theorie geprüft — volle Testsuite nach
der Umsetzung laufen lassen, bis grün (siehe Teststrategie).

## Betroffene Dateien

**Backend (Umbenennung + Neubau):**
- 6 Controller-Klassen (nur `@RequestMapping` auf Klassenebene)
- `SecurityConfig.java`, `SpaForwardController.java` (Neubau wie oben)
- ~12 Testdateien mit hartkodierten alten Pfaden: `PdfImportControllerIntegrationTest`,
  `TransactionListControllerIntegrationTest`, `TransactionCategoryControllerIntegrationTest`,
  `PdfImportErrorDispatchIntegrationTest`, `BudgetControllerIntegrationTest`,
  `FixedCostControllerIntegrationTest`, `UserControllerTest`, `PdfImportOversizeIntegrationTest`,
  `PdfImportTimeoutIntegrationTest`, `TransactionSummaryControllerIntegrationTest`,
  `AuthControllerTest`, plus `FixedCostOpenApiTest`, `TransactionListOpenApiTest`
  (OpenAPI-Pfaderwartungen)
- `SpaRoutingTest.java` — Neubau auf `@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `TestRestTemplate` (AC4), neue Regressionstests: Actuator/Swagger/`/v3`/`/error` nicht vom
  Catch-all verschluckt, verschachtelte Route wird geforwardet, `/styleguide` wird geforwardet

**Frontend:**
- `proxy.conf.json` — 6 Einträge → 1 Eintrag (`/api`)
- `proxy.conf.spec.ts` — Neubau für die Ein-Präfix-Welt
- 6 Service-Dateien (`auth.service.ts`, `safe-to-spend.service.ts`, `fixed-cost.service.ts`,
  `transaction.service.ts`, `pdf-import.service.ts`, `transaction-summary.service.ts`) + zugehörige
  `.spec.ts`
- `auth-error.interceptor.ts` (`AUTH_BOOTSTRAP_PATHS`) + Spec

**E2E:**
- `spa-routing.spec.ts`, `auth.spec.ts`, `auth.fixture.ts`, `e2e/README.md`

## Nicht angefasst

- ADR-3 (`docs/adr/ADR-3-rest-vs-graphql.md:43`) erwähnt `/transactions` nur illustrativ
  (Over-Fetching-Beispiel), keine Aussage über den aktuellen Pfad — keine Korrektur nötig.
- `docs/plans/*.md` (historische Pläne) bleiben unverändert — sie beschreiben den Stand zum
  Zeitpunkt ihrer Entstehung, siehe `docs/plans/README.md`.

## Teststrategie

- Backend: `SpaRoutingTest` (RANDOM_PORT) + neue Shadowing-Regressionstests; alle
  Pfad-Literale in bestehenden Integrationstests aktualisiert
- Frontend: bestehende Vitest-Specs für Services/Interceptor aktualisiert; `proxy.conf.spec.ts`
  neu
- E2E: bestehende Playwright-Suite auf neue Pfade aktualisiert
- Volle Validierung nach Implementierung: `./mvnw -Pprod verify`, `ng build`, `npm test`
  (Frontend + E2E) — iterativ bis grün, insbesondere zur empirischen Absicherung des
  Precedence-Risikos aus Entscheidung 3

## Acceptance Criteria (aus Issue #126)

- [ ] Hard-Reload/Deep-Link auf `/import`, `/categories`, `/register`, `/styleguide` liefert die
      SPA aus (200 text/html)
- [ ] `/register` ist ohne Authentifizierung erreichbar
- [ ] `POST /api/import/pdf` bleibt authentifizierungspflichtig, kein Forward auf `index.html`
- [ ] Automatisierter Test mit `@SpringBootTest(webEnvironment = RANDOM_PORT)` statt MockMvc,
      deckt mind. eine geschützte und eine öffentliche Route ab
- [ ] Doppelpflege zwischen `SecurityConfig` und `SpaForwardController` strukturell beseitigt
      (kein enumeriertes Listen-Paar mehr)
