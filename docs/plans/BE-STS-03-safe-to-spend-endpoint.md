# [BE-STS-03] GET /budget/safe-to-spend

- **Issue:** [#23](https://github.com/dfme/budget-buddy/issues/23)
- **Task-ID:** `BE-STS-03`
- **Branch:** `feature/BE-STS-03-safe-to-spend-endpoint`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-12

---

## Ausgangslage

Das Issue ist ein **reiner Endpoint-PR**. Sowohl die Berechnung als auch das Antwort-DTO liegen
bereits auf `main`:

- `SafeToSpendService.calculate(long userId)` aus [#21](https://github.com/dfme/budget-buddy/issues/21)
  (BE-STS-01) — Formel, Divisor, Flags, Mandantentrennung über Ports.
- `SafeToSpendResponse` mit allen fünf von #23 geforderten Feldern
  (`SafeToSpendResponse.java:38-43`); `incomeSuggestion` kam mit
  [#22](https://github.com/dfme/budget-buddy/issues/22) (BE-STS-02) dazu.

Es fehlt genau die HTTP-Kante. Kein Service-Change, kein DTO-Change.

## Entscheide

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Feldname des Negativ-Flags | `negative`, nicht `isNegative` | Contract-Anpassung aus dem Review von [#155](https://github.com/dfme/budget-buddy/pull/155), festgehalten im Kommentar an #23. Jackson serialisiert Record-Komponenten unter ihrem Namen; `isNegative` stünde im selben Objekt neben `noIncome` und brächte zwei Konventionen. Im DTO bereits so umgesetzt — dieser PR ändert daran nichts, hält den Namen aber per Test fest. |
| `docs/prompts/04_01_prompt_lab1_l1_jason.md:96` | bleibt bei `isNegative` | Archiviertes Prompt-Protokoll, laut Kommentar an #23 bewusst nicht rückwirkend geändert. Massgeblich ist `SafeToSpendResponse`. |
| OpenAPI-Assertions | im `BudgetControllerIntegrationTest`, **nicht** in einer eigenen Testklasse | Abweichung vom Vorbild `FixedCostOpenApiTest`. Für drei jsonPath-Zeilen einen zweiten Spring-Context samt `@DirtiesContext` hochzufahren kostet CI-Zeit ohne Erkenntnisgewinn; `/v3/api-docs` ist öffentlich und aus demselben MockMvc erreichbar. Bei #12 war die Trennung sinnvoll, weil dort fünf Endpoints plus Schema-Details geprüft werden. |
| `SecurityConfig` | unverändert | `/budget/safe-to-spend` fällt automatisch unter `anyRequest().authenticated()` (`SecurityConfig.java:95`) und steht weder in `PUBLIC_PATHS` noch in `SpaForwardController.CLIENT_ROUTE_PATTERNS`. Eine Ergänzung wäre eine Freigabe, keine Absicherung. |

## Betroffene Files

**Neu:**

- `backend/src/main/java/com/budgetbuddy/budget/BudgetController.java`
- `backend/src/test/java/com/budgetbuddy/budget/BudgetControllerIntegrationTest.java`

**Geändert:** keine Produktivdatei. Dazu dieser Plan und die Zeile in `docs/plans/README.md`.

## Implementierungsschritte

1. `BudgetController` anlegen: `@RestController`, `@RequestMapping("/budget")`,
   `@Tag(name = "Budget", …)`.
2. `GET /safe-to-spend` mit `@AuthenticationPrincipal Long userId` → `safeToSpendService.calculate(userId)`.
   Der Controller reicht die User-ID nur durch und trifft keine eigene Zugriffsentscheidung — dasselbe
   Muster wie `FixedCostController`.
3. `@Operation` + `@ApiResponses` (200, 401) analog `TransactionSummaryController.java:33-44`. Die
   Beschreibung erklärt die drei Zustände (Betrag / negativ / kein Einkommen), damit die Swagger-UI
   ohne Blick in den Service lesbar ist.
4. Klassen-Javadoc: Verweis auf `SafeToSpendService` für die Formel, keine Doppelung der Fachlogik.

## Test-Strategie

`BudgetControllerIntegrationTest` — `@SpringBootTest` + MockMvc gegen Testcontainers-PostgreSQL,
eigene DB via `PostgresTestDatabase.register(...)` und `@DirtiesContext`. Die `Clock` ist als
`@MockitoBean` auf einen festen Stichtag gestellt; sonst hinge `weeksLeft` am Kalendertag des
CI-Laufs. Seeding per `JdbcTemplate` wie in `SafeToSpendServiceIntegrationTest` — ein Zugriff über
`UserRepository`/`TransactionRepository` wäre der modulübergreifende Zugriff, den CLAUDE.md untersagt.

| Test | belegt |
| ---- | ------ |
| Happy Path 200, alle fünf Felder mit erwarteten Werten | AC1, AC2, AC3 |
| `amount` ist JSON-Zahl (kein String); der Key heisst `negative`, `isNegative` kommt im Body nicht vor | AC2 + Contract aus dem Kommentar an #23 |
| `noIncome`-Fall: `amount` als `null` **vorhanden** statt weggelassen, `incomeSuggestion` gefüllt | AC1 — es gibt kein globales `NON_NULL`, das muss festgehalten werden |
| Negativ-Fall: `negative: true` | AC1 |
| Mandantentrennung: zweiter User mit eigenen Fixkosten und Transaktionen im selben Monat; beide sehen nur ihre Zahlen | Security-Matrix Punkt 1 |
| Ohne JWT-Cookie → 401 | Security-Matrix Punkt 2 |
| OpenAPI: `$.paths['/budget/safe-to-spend'].get` existiert, `.summary` nicht leer, Response-`$ref` = `SafeToSpendResponse` | AC4 |

Kein Unit-Test: der Controller enthält keine Logik, die ein Mockito-Test belegen könnte und die
Integration nicht schon abdeckt. Kein E2E — der E2E-Pfad für US-06 ist ein eigenes Issue.

## Acceptance Criteria (aus #23)

- [ ] Response enthält alle 5 definierten Felder
- [ ] `amount` ist BigDecimal in CHF
- [ ] `weeksLeft` ist korrekt berechnet (verbleibende Wochen im Monat)
- [ ] Endpoint in Swagger UI mit Response-Schema dokumentiert
