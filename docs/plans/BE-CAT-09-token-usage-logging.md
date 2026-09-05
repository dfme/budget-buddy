# [BE-CAT-09] Token-Verbrauch und Kosten der Claude-Kategorisierung loggen

- **Issue:** [#243](https://github.com/dfme/budget-buddy/issues/243)
- **Task-ID:** `BE-CAT-09`
- **Branch:** `feature/BE-CAT-09-token-usage-logging`
- **Story:** — (kein `us-*`-Label)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-02

## Problem

`ClaudeCategorizationService` loggt Erfolg und Misserfolg eines Bündel-Calls, aber nie dessen
Verbrauch. `response.usage()` steckt bereits in der Antwort und wird verworfen. Damit ist der
laufende API-Kostenverlauf unbeobachtbar — bei einem MVP mit Budget ein blinder Fleck, und
ausgerechnet an der Stelle, die ADR-14 aus Kostengründen gebündelt hat.

## Vorabklärung: Gibt die API die Kosten selbst zurück?

**Nein, nicht pro Request.** `com.anthropic.models.messages.Usage` (SDK 2.31.0) führt acht Felder —
`inputTokens`, `outputTokens`, `cacheCreation`, `cacheCreationInputTokens`,
`cacheReadInputTokens`, `serverToolUse`, `serviceTier`, `inferenceGeo` — und keinen Geldbetrag.
Das ist keine Lücke dieses einen Typs: `BetaManagedAgentsSpanModelUsage` hat dieselben
Token-Zähler und ebenfalls keinen Betrag. Die einzigen Treffer auf `cost|price|billing` im Jar
sind `BillingError` und `BetaManagedAgentsBillingError` — Fehlertypen, keine Kostendaten.

Echte, abgerechnete Beträge liefert die Admin-API (`GET /v1/organizations/cost_report`), aber
tagesaggregiert, organisationsweit, ohne SDK-Anbindung und nur gegen einen Admin-Key
(`sk-ant-admin01-…`), der laut Doku für Einzelaccounts nicht verfügbar ist. Für eine Zahl neben
dem Import, zu dem sie gehört, ist das unbrauchbar — und ein zweites, höher privilegiertes Secret
in der Render-Umgebung wäre für eine Log-Zeile ein schlechter Tausch.

Bleiben genau zwei Optionen: eigene Schätzung aus Tokens × konfiguriertem Preis, oder keine Zahl.
Der Cost Report bleibt die Autorität, an der sich die Schätzung notfalls einmalig verifizieren
lässt — genau deshalb muss sie nicht raffiniert sein, sondern ehrlich oder still.

## Entscheide

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Kostenschätzung | ja, als optionales Feld `cost_usd` | Vom Issue als Option genannt. Tokens sind eine Messung, Kosten eine Ableitung aus einer Fremdannahme — die Leitplanken unten sorgen dafür, dass die Zeile nie falsch behauptet. |
| Preisquelle | Konfiguration `anthropic.api.pricing[<modell>]` | Eine Preisänderung ist damit eine Config-Änderung neben dem Modell-Schalter, kein Deployment. Bracket-Notation, weil Spring Map-Keys mit Bindestrich sonst normalisiert. |
| Lookup-Schlüssel | **zweistufig**: Modell aus der Response, dann als Fallback `properties.model()` | `claude-haiku-4-5` ist vor der 4.6-Generation ein Alias und löst auf `claude-haiku-4-5-20251001` auf — genau das kommt in der Response zurück. Ein einstufiger Lookup über die Response träfe den in der Config stehenden Alias nie. |
| Kein Preis hinterlegt | `cost_usd` entfällt, Tokens bleiben | Fehlen statt lügen. Ein Modellwechsel ohne Preispflege degradiert sichtbar auf das, was ohne dieses Feature dastünde. |
| Formel nicht anwendbar | `cost_usd` entfällt, wenn Cache-Tokens ≠ 0 **oder** `service_tier` ausserhalb «standard» | Beide sind anders bepreist (Cache-Read 10 %, Batch 50 %). Eine Bedingung deckt beide bekannten Wege ab, auf denen die Formel still danebenläge. |
| Währung | USD, keine CHF-Umrechnung | Anthropic rechnet in USD. Ein FX-Kurs wäre eine zweite Fremdannahme mit Verfallsdatum. |
| Datentyp | `BigDecimal`, Scale 6, `HALF_UP` | ADR-9 — Geldbeträge nie `double`. Ein Bündel kostet Bruchteile eines Cents. |
| Aggregation pro Import | nicht umsetzen | Das Issue schliesst Persistierung und Dashboard aus; eine Summe in `ImportJobRunner` wäre ein fremdes Modul. |
| Log-Level | `log.info` | Drei Zeilen pro 108-Zeilen-Import — kein Volumenproblem, in Prod sichtbar. |
| Fehlerrobustheit | Auslesen in `try/catch RuntimeException` → `log.debug` | Eine Diagnosezeile darf nie ein Bündel kosten. Flöge sie durch `categorizeAll`, verlöre sie alle restlichen Bündel. |
| MDC | keine User-ID im Text | `docs/CONVENTIONS.md:203` — sie steht via MDC ohnehin jeder Zeile voran. |

## Log-Zeile

```
Claude-Kategorisierung: model=claude-haiku-4-5-20251001 transaktionen=20 input_tokens=412 output_tokens=147 cost_usd=0.001147
```

Ohne anwendbaren Preis endet die Zeile nach `output_tokens`. `key=value` bleibt in beiden Fällen
in den Render-Logs filter- und summierbar.

## Betroffene Dateien

**Neu**

- `backend/src/main/java/com/budgetbuddy/categorization/ModelPricing.java`
- `backend/src/test/java/com/budgetbuddy/categorization/ModelPricingTest.java`

**Geändert**

- `backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java`
- `backend/src/main/java/com/budgetbuddy/categorization/AnthropicProperties.java`
- `backend/src/main/resources/application.properties`
- `backend/src/test/java/com/budgetbuddy/categorization/ClaudeCategorizationServiceTest.java`
- `docs/plans/README.md`

**Bewusst nicht geändert**

- `CategorizationLogRedactionTest` — deckt die neue Zeile über drei bestehende Erfolgspfad-Tests
  via `assertRedacted()` bereits ab.

## Implementierungsschritte

1. Preis gegen die Live-Preisliste verifizieren und mit Datum als Default in
   `application.properties` schreiben. (Erledigt: 02.09.2026, $1 / $5 pro MTok für Haiku 4.5.)
2. `ModelPricing` als Record `(BigDecimal inputPerMTok, BigDecimal outputPerMTok)` mit
   `costFor(long inputTokens, long outputTokens)`; Summe auf Scale 6, `HALF_UP`.
3. `AnthropicProperties` um `Map<String, ModelPricing> pricing` erweitern — Kompakt-Konstruktor
   (`null` → `Map.of()`) plus 2-Argument-Konvenienzkonstruktor, damit die beiden bestehenden
   Testaufrufe unverändert bleiben.
4. `logTokenUsage(response, batchSize)` in `categorizeBatch` direkt nach `recordSuccess()`
   aufrufen — der Punkt, an dem feststeht, dass ein Request Kosten verursacht hat.
5. In `logTokenUsage`: Modell und `usage()` lesen, Preis zweistufig nachschlagen, Guard gegen
   Cache-Tokens und Nicht-Standard-Tier, Zeile mit oder ohne `cost_usd`; alles in
   `try/catch RuntimeException` mit `LogRedaction.describe(e)`.
6. Klassen-Javadoc ergänzen: ADR-14 hat gebündelt, *weil* Kosten zählen — ab hier sind sie messbar.
7. Prüfen, dass Breaker-Skip und «kein API-Key» keine Zeile schreiben — dort entstehen keine Kosten.

## Test-Strategie (Unit)

| Test | Nachweis |
| ---- | -------- |
| `logsTokenUsageAfterASuccessfulCall` | INFO-Zeile mit `input_tokens=50`, `output_tokens=3`, Modell, `transaktionen=1` → AC 1 + AC 3 |
| `logsEstimatedCostWhenAPriceIsConfigured` | `cost_usd` bei 50/3 Tokens und 1.00/5.00 USD |
| `findsThePriceViaTheConfiguredAliasWhenTheResponseReportsADatedModelId` | Response `claude-haiku-4-5-20251001`, Config `claude-haiku-4-5` → Kosten trotzdem da |
| `omitsCostWhenNoPriceIsConfiguredForTheModel` | Zeile ohne `cost_usd`, Tokens bleiben |
| `omitsCostWhenTheResponseReportsCacheTokens` | `cacheReadInputTokens` gesetzt → kein `cost_usd` |
| `omitsCostWhenTheResponseReportsANonStandardServiceTier` | `service_tier=batch` → kein `cost_usd` |
| `logsTokenUsagePerBatchNotPerTransaction` | 41 Transaktionen → genau 3 Zeilen; belegt zugleich AC 2 |
| `doesNotLogTokenUsageWhenTheCallFails` | `AnthropicException` → keine Zeile |
| `doesNotLogTokenUsageWhenTheBreakerIsOpen` | Breaker offen → keine Zeile |
| `ModelPricingTest` | Formel und Rundung isoliert |

Appender: `ThreadScopedLogAppender` (BE-CAT-07) statt `ListAppender` — der Logger ist prozessweit
geteilt und der Service läuft produktiv in `@Async`-Importjobs; ein `anyMatch` könnte sonst durch
eine fremde Zeile fälschlich grün werden.

Zusätzlich laufen mit: `CategorizationLogRedactionTest`, `HybridCategorizationServiceTest`.

## Acceptance Criteria (aus #243)

1. Nach jedem erfolgreichen Claude-Call (Kategorisierung) werden Input- und Output-Tokens sowie
   das verwendete Modell geloggt.
2. Kein zusätzlicher API-Call nötig — `usage()` steckt bereits in der bestehenden Response.
3. Test deckt ab, dass das Logging bei einem erfolgreichen Call ausgelöst wird.

Die Kostenschätzung geht über die ACs hinaus und wird im PR-Body als bewusste Erweiterung
deklariert.
