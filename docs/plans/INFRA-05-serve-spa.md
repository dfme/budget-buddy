# INFRA-05 — Angular-SPA aus dem Spring-Boot-JAR ausliefern

**Issue:** #38
**Task-ID:** INFRA-05
**Branch:** `feature/INFRA-05-serve-spa`

## Kontext / Entscheide

- INFRA-04 (#37) bündelt den Angular-Build bereits ins JAR: Maven-Profil `prod` →
  `frontend-maven-plugin` (`ng build`) → `maven-resources-plugin` kopiert
  `frontend/dist/budgetbuddy/browser` → `classes/static/`. Damit sind AC #1, #2, #4
  bereits erfüllt.
- **Echter Rest = AC #3:** `SecurityConfig` sperrt mit `.anyRequest().authenticated()`
  alles ausser Swagger/Health. `GET /` und die statischen Assets liefern daher **401** —
  die gebündelte SPA ist nicht erreichbar. Zusätzlich fehlt SPA-Deep-Link-Fallback
  (Hard-Reload von `/dashboard` → 404/401).
- **`-Pprod`-Gating bleibt** (Team-Entscheid): Plain `mvn package` bleibt backend-only/schnell;
  `ng build` läuft nur im Prod-Build. AC #1 gilt darüber als erfüllt.
- **Default-Deny bleibt bewahrt:** Da die API-Endpoints keinen `/api`-Prefix haben
  (z.B. `/users/me`), werden statische Pfade + die aktuellen SPA-Routen explizit
  ge-whitelistet (nur GET), statt GET pauschal zu öffnen. Begründung: bei einer
  Finanz-App ist versehentliche Exposition Risiko #2 (Datenleck). Neue Frontend-Routen
  müssen künftig in `SecurityConfig` + `SpaForwardController` ergänzt werden
  (im Code kommentiert).

## Betroffene Files

### Geändert
- `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java` — statische Pfade +
  SPA-Routen (`/`, `/index.html`, `/*.js`, `/*.css`, `/*.ico`, `/*.txt`, `/assets/**`,
  `/dashboard/**`, `/login/**`) auf `permitAll` (nur GET).

### Neu
- `backend/src/main/java/com/budgetbuddy/config/SpaForwardController.java` — leitet SPA-Routen
  per `forward:/index.html` weiter (Deep-Link/Hard-Reload). `@Hidden` (kein API-Endpoint → nicht in Swagger).
- `backend/src/test/resources/static/index.html` — Test-Fixture. Im Dev/Test-Build gibt es kein
  `static/index.html` (entsteht erst bei `-Pprod`); die Fixture wird nicht ins Prod-JAR gepackt.
- `backend/src/test/java/com/budgetbuddy/config/SpaRoutingTest.java` — `@SpringBootTest` + MockMvc.

## Implementierungsschritte
1. `SecurityConfig`: `STATIC_PATHS` einführen, `.requestMatchers(HttpMethod.GET, STATIC_PATHS).permitAll()`
   vor `.anyRequest().authenticated()`.
2. `SpaForwardController` mit `@GetMapping({"/dashboard/**","/login/**"})` → `forward:/index.html`.
3. Test-Fixture `index.html`.
4. `SpaRoutingTest`.

## Test-Strategie (JUnit — DoF: „Playwright oder JUnit")
`SpaRoutingTest`:
- `GET /` (unauth) → 200, liefert index.html (AC #3, Happy Path).
- `GET /dashboard` (unauth) → 200, `forwardedUrl("/index.html")` (Deep-Link-Fallback).
- statisches Asset (`GET /*.js`) → 200 (Assets erreichbar).
- `GET /users/me` (unauth) → 401 (API bleibt geschützt — Regression-Guard).
- `GET /actuator/health` → 200 (unverändert).

Zusätzlich: `./mvnw test` grün. `-Pprod package` (baut Angular) zur Voll-Verifikation optional.

## Acceptance Criteria (aus Issue)
- [x] `mvn package` führt ng build automatisch aus → via `-Pprod` (INFRA-04, Team-Entscheid)
- [x] Angular-Build-Output liegt in `static/` → INFRA-04
- [ ] Spring Boot liefert index.html unter `/` → **dieser PR**
- [x] Kein separater Web-Server nötig in Produktion → INFRA-04
