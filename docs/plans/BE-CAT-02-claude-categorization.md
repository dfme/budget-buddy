# [BE-CAT-02] ClaudeCategorizationService

| Feld | Wert |
| ---- | ---- |
| Issue | [#15](https://github.com/dfme/budget-buddy/issues/15) |
| Task-ID | `BE-CAT-02` |
| Branch | `feature/BE-CAT-02-claude-categorization` |
| User Story | US-05 — Transaktionen kategorisieren |
| Depends on | #14 (BE-CAT-01) — `CategorizationPort` + `LookupTableService`, erledigt |

---

## Ziel

Zweite Stufe der Hybrid-Kategorisierung (ADR-6): Transaktionen, die der
`LookupTableService` nicht zuordnen kann, gehen an die Claude API. Implementiert
`CategorizationPort` via Anthropic Java SDK, mit 10s-Timeout und Fallback auf
`Sonstiges`.

---

## Entscheide

### 1. Modell: `claude-haiku-4-5` statt `claude-haiku-3-5-20241022`

Issue #15 und CLAUDE.md nennen `claude-haiku-3-5-20241022`. Diese ID existiert
nicht — die real gemeinte Variante hiess `claude-3-5-haiku-20241022`
(Komponenten-Reihenfolge vertauscht) und wurde am **19.02.2026 abgeschaltet**.
Beide Strings liefern heute 404, d. h. eine wortgetreue Umsetzung des Issues
würde einen Service bauen, der ausnahmslos in den `Sonstiges`-Fallback läuft.

Ersatz ist `claude-haiku-4-5` — direkter Nachfolger, gleiche Rolle: schnell und
günstig für Single-Label-Klassifikation.

### 2. Fallback: `Optional.of(Category.SONSTIGES)`

Der Port gibt `Optional<Category>` zurück; `Optional.empty()` heisst bei
`LookupTableService` „kann ich nicht zuordnen, eskaliere an die nächste Stufe".

`ClaudeCategorizationService` ist die **letzte** Stufe der Kette. Bei jedem
Fehler gibt er `Optional.of(SONSTIGES)` zurück statt `empty()` — so erfüllt er
das Akzeptanzkriterium wörtlich und der spätere Orchestrator (BE-CAT-03) braucht
keine eigene Fallback-Logik.

Ausnahme: Blank-Input wird auf `Optional.empty()` kurzgeschlossen, konsistent zu
`LookupTableService` — für einen leeren Text gibt es nichts zu kategorisieren,
und ein API-Call wäre Verschwendung.

### 3. API-Key: kein `@NotBlank`

Anders als bei `JwtProperties` (Fail-fast, weil ein fehlendes JWT-Secret ein
Sicherheitsproblem wäre) darf ein fehlender `ANTHROPIC_API_KEY` den Start
**nicht** verhindern — sonst kann niemand ohne Anthropic-Account entwickeln.
Ohne Key wird der Client-Bean nicht gebaut, der Service degradiert auf
`Sonstiges`.

### 4. `maxRetries(1)` statt SDK-Default 2

`maxRetries` und Timeout gelten **pro HTTP-Request, also pro Transaktion** — es
gibt keinen Batch-Call. Siehe Entscheid 6.

Der Retry ist **nicht** als Latenzschutz gedacht — das leistet der Circuit
Breaker (Entscheid 6). Retry und Breaker lösen verschiedene Probleme:

- **Retry** hilft bei einem *einzelnen* Aussetzer auf *dieser* Transaktion
  (429 Rate Limit, 529 überlastet, 500).
- **Breaker** hilft bei *anhaltendem* Ausfall. Gegen einen einmaligen Blip tut
  er nichts.

Der Retry ist hier vor allem nötig, damit ein **Rate-Limit den Breaker nicht
fälschlich auslöst**: Der Import feuert ~20 Calls in schneller Folge; auf einem
niedrigen Anthropic-Tier ist eine 429-Serie realistisch, erst recht bei
parallelen Nutzern. Drei 429er hintereinander würden den Breaker öffnen — und
dessen Blast Radius ist nicht eine Transaktion, sondern der **ganze restliche
Import** (60s lang alles auf `Sonstiges`, ohne einen einzigen Call). Die API
wäre dabei gesund gewesen und hätte nur „langsamer bitte" gesagt. Das SDK
respektiert bei 429 den `retry-after`-Header.

Abwägung gegenüber `maxRetries(0)`:

| Szenario | `maxRetries(0)` | `maxRetries(1)` |
| --- | --- | --- |
| Harter Ausfall (Connection refused) | Breaker öffnet nach ~30s | Breaker öffnet nach ~60s |
| 429-Serie bei gesunder API | Breaker öffnet fälschlich → Rest-Import auf `Sonstiges` | Retries greifen, Breaker bleibt zu |
| Einzelner 529-Blip | Diese Transaktion → `Sonstiges` | Korrekte Kategorie |

Kosten: **30s mehr im Totalausfall** (60s statt 30s Worst Case). Beide Werte
sind für einen einmaligen PDF-Upload verkraftbar; ein fälschlich auf
`Sonstiges` gekippter Import kostet den Nutzer dagegen manuelle
Korrekturarbeit (US-05).

Der SDK-Default 2 wäre mit 90s Worst Case zu viel des Guten.

### 6. Circuit Breaker gegen kumulierte Wartezeit

Da `categorize(String)` genau eine Transaktion nimmt, ruft der Import die
Methode pro unbekannter Transaktion einmal auf. Auf einen realen Kontoauszug
hochgerechnet:

| | Kontoauszug mit ~80 Transaktionen |
| --- | --- |
| Lookup deckt ab (~75%) | 60 Transaktionen, 0 API-Calls |
| Gehen an Claude (~25%) | ~20 Calls |
| Normalfall (~1s/Call) | ~20s |
| Claude nicht erreichbar | 20 × 20s = **~7 Minuten** |

Der Upload-Endpoint ist synchron (CLAUDE.md → „Backend: Import Flow"). Ein
Ausfall der Anthropic-API würde also nicht sauber auf `Sonstiges` degradieren,
sondern den Request minutenlang blockieren — exakt Churn-Risiko #1. Der Fallback
greift pro Transaktion korrekt, aber in Summe ist er wirkungslos.
`maxRetries(0)` würde das nur halbieren, nicht lösen.

**Entscheid:** Circuit Breaker direkt im `ClaudeCategorizationService`, nicht
erst im Orchestrator (BE-CAT-03). Damit ist der Service von sich aus sicher —
unabhängig davon, wer ihn später in welcher Schleife aufruft.

Verhalten:

- **CLOSED** — Normalbetrieb. Jeder Fehler erhöht einen Zähler, jeder Erfolg
  setzt ihn auf 0 zurück.
- **3 Fehler in Folge → OPEN** — für 60s werden alle Calls sofort und ohne
  HTTP-Request mit `Sonstiges` beantwortet.
- **Nach 60s → HALF-OPEN** — der nächste Call ist ein Trial. Erfolg schliesst
  den Breaker, Fehler öffnet ihn für weitere 60s.

Worst Case sinkt damit von ~7 Minuten auf **3 × 20s = ~60s**, unabhängig von
der Anzahl Transaktionen.

Handgeschrieben (~20 Zeilen, `AtomicInteger` + `AtomicLong`), **kein
Resilience4j** — die Library steht nicht im Tech-Stack und wäre für einen
einzelnen Breaker Overengineering (vgl. CLAUDE.md → „Bewusst weggelassen").
Der Service ist ein Singleton, also muss der Zustand thread-safe sein. Für die
Testbarkeit der Cooldown-Logik wird eine `java.time.Clock` injiziert statt
`System.currentTimeMillis()` direkt zu lesen.

### 5. Modell-Referenzen in Dokumentation

Repo-weite Suche nach der veralteten ID ergab vier Fundstellen. Drei davon
liegen unter Pfaden aus `.claudeignore` und werden **nicht** angefasst:

| Ort | Entscheid |
| --- | --- |
| `CLAUDE.md:143` | Korrigieren — lebende Anweisung |
| `CLAUDE.md:144` | Korrigieren — `claude-sonnet-4-20250514` ist ebenfalls deprecated (Abkündigung war 15.06.2026), Nachfolger `claude-sonnet-5`. Betrifft US-09, wird auf Wunsch hier mitgenommen. |
| `docs/modules/Modul3/openapi.yaml:194` | **Nicht anfassen** — Kursabgabe Modul 3 (liegt zwischen den Übungs-`.docx`), eingefrorener Stand. Die lebende API-Spec generiert Springdoc aus Annotationen. |
| `docs/prompts/03_01_prompt_adr_daniel.md:167` | **Nicht anfassen** — Protokoll eines eingegebenen Prompts |
| `docs/prompts/03_02_prompt_uebungen_api_first_design_sergio.md:291` | **Nicht anfassen** — dito |

`docs/adr/ADR-6-hybrid-categorization.md` nennt nur generisch „Claude Haiku"
ohne Versions-Pin — inhaltlich weiterhin korrekt, keine Änderung nötig.

---

## Betroffene Files

### Neu

| File | Zweck |
| ---- | ----- |
| `backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java` | `CategorizationPort`-Implementierung via Claude API |
| `backend/src/main/java/com/budgetbuddy/categorization/AnthropicProperties.java` | `@ConfigurationProperties(prefix = "anthropic.api")` — `key`, `model` |
| `backend/src/main/java/com/budgetbuddy/config/AnthropicConfig.java` | `AnthropicClient`-Bean mit Timeout + Retries |
| `backend/src/test/java/com/budgetbuddy/categorization/ClaudeCategorizationServiceTest.java` | Unit-Tests mit gemocktem `AnthropicClient` |

### Geändert

| File | Änderung |
| ---- | -------- |
| `backend/src/main/resources/application.properties` | `anthropic.api.model=claude-haiku-4-5` ergänzen |
| `CLAUDE.md` | Zeile 143: `claude-haiku-4-5`; Zeile 144: `claude-sonnet-5` |

---

## Implementierungsschritte

1. **`AnthropicProperties`** — validiertes Record mit `key` und `model`.
   Default-Modell `claude-haiku-4-5` im Compact-Constructor, falls Property
   fehlt. Kein `@NotBlank` auf `key` (siehe Entscheid 3).

2. **`AnthropicConfig`** — baut den `AnthropicClient`:
   ```java
   AnthropicOkHttpClient.builder()
       .apiKey(properties.key())
       .timeout(Duration.ofSeconds(10))
       .maxRetries(1)
       .build();
   ```
   Bean wird bei leerem Key nicht erzeugt → `ObjectProvider`/`Optional`-Injection
   im Service.

3. **`ClaudeCategorizationService implements CategorizationPort`**
   - Prompt generiert die Kategorienliste **aus dem `Category`-Enum**, nicht
     hardcodiert — so kann Prompt und Enum nicht auseinanderlaufen (erfüllt AC
     „Prompt enthält alle 13 Kategorien" dauerhaft, nicht nur heute).
   - `MessageCreateParams.builder().model(...).maxTokens(...).system(...).addUserMessage(...)`
   - Antwort trimmen → `Category.fromLabel(...)`

4. **Fehlerpfade** → alle `Optional.of(SONSTIGES)`, jeweils geloggt:
   - `AnthropicException` (Basisklasse — deckt Timeout, IO und HTTP-Fehler ab)
   - `IllegalArgumentException` aus `fromLabel` (Modell halluziniert Kategorie)
   - leere oder nicht-Text-Antwort
   - kein Client konfiguriert (fehlender API-Key)

5. **Blank-Input** → `Optional.empty()`, kein API-Call.

6. **Circuit Breaker** (siehe Entscheid 6) — `AtomicInteger consecutiveFailures`
   + `AtomicLong openUntilEpochMillis`, `Clock` injiziert. Schwellwert 3,
   Cooldown 60s. Nur Infrastruktur-Fehler (`AnthropicException`) zählen als
   Fehler — eine halluzinierte Kategorie ist eine gültige Antwort und darf den
   Breaker nicht öffnen.

---

## Test-Strategie

Unit-Tests (JUnit 5 + Mockito + AssertJ), analog `LookupTableServiceTest`:

| Test | Erwartung |
| ---- | --------- |
| Happy Path — gemockte Antwort `"Lebensmittel"` | `Category.LEBENSMITTEL` |
| `AnthropicException` beim Call | `Optional.of(SONSTIGES)`, kein Throw |
| Modell liefert unbekanntes Label | `Optional.of(SONSTIGES)` |
| Kein Client konfiguriert | `Optional.of(SONSTIGES)`, kein API-Call |
| Prompt enthält alle 13 Kategorien | via `ArgumentCaptor` auf `MessageCreateParams` |
| Blank-Input | `Optional.empty()`, kein API-Call |
| **Breaker:** 3 Fehler in Folge, dann 4. Call | kein API-Call mehr, sofort `SONSTIGES` |
| **Breaker:** Erfolg nach 2 Fehlern | Zähler zurückgesetzt, Breaker bleibt CLOSED |
| **Breaker:** Nach Cooldown (Fake-`Clock`) | Trial-Call geht raus, Erfolg schliesst Breaker |
| **Breaker:** Trial-Call scheitert | Breaker öffnet sofort wieder, kein weiterer Call |
| **Breaker:** Halluzinierte Kategorie | zählt **nicht** als Fehler, Breaker bleibt CLOSED |

Zusätzlich `AnthropicConfigTest` (`@SpringBootTest`, 3 Tests): verifiziert **beide**
Konfigurationspfade. Der Pfad *mit* Key ist der Produktionspfad, wird aber von keinem
anderen Test berührt — in Test und CI ist nie ein `ANTHROPIC_API_KEY` gesetzt, die
Suite durchläuft also sonst ausschliesslich den keylosen Fall. Ohne diesen Test würde
ein Fehler in der Client-Konstruktion erst beim Deployment auffallen.

**Kein Integrationstest gegen die echte API** — bräuchte einen gültigen Key,
wäre in CI nicht reproduzierbar und würde pro Lauf Kosten verursachen.

Coverage-Ziel `categorization/`: 90%+ (siehe CLAUDE.md → Testing).

---

## Acceptance Criteria (aus Issue #15)

- [ ] Bekannte Transaktion wird korrekt kategorisiert via Claude API
- [ ] `AnthropicException` führt zu Rückgabe von `Sonstiges` (kein Crash)
- [ ] Timeout ist auf 10s gesetzt
- [ ] API-Key wird aus Umgebungsvariable gelesen, nie hardcodiert
- [ ] Prompt enthält alle 13 Kategorien aus CLAUDE.md

## Definition of Done

- [ ] Code ist reviewed (mind. 1 Approval im PR)
- [ ] `mvn package` und `ng build` laufen fehlerfrei durch
- [ ] ~~Neue API-Endpoints sind in Swagger UI sichtbar~~ — **n/a**: dieses Ticket
      fügt keinen Endpoint hinzu
- [ ] Happy Path ist durch automatisierten Test abgedeckt (JUnit)
- [ ] Alle Acceptance Criteria erfüllt

---

## Offene Punkte für Folge-Tickets

- **[INFRA-11](https://github.com/dfme/budget-buddy/issues/76) — `ANTHROPIC_API_KEY` in
  Render hinterlegen** (Sprint 3). Dieses Ticket liefert den Code, der den Key liest,
  nicht den Key selbst. Ohne ihn läuft die deployte App im Fallback-Modus, US-05 ist
  produktiv also erst mit INFRA-11 erfüllt. Bewusst ausgelagert statt hier gelöst: Ein
  PR kann keinen Key liefern, und die offene Frage ist organisatorisch (Account,
  Kostenträger, Render-Zugriff) — als Fussnote in diesem PR hätte sie den Merge
  scheinbar blockiert und wäre nach dem Merge verloren gegangen.

- **BE-CAT-03** — Orchestrator, der `LookupTableService` → `ClaudeCategorizationService`
  verkettet. Dieses Ticket liefert nur die zweite Stufe; die beiden
  `CategorizationPort`-Beans müssen dort per `@Qualifier` unterschieden werden.

- **Import-Latenz im Normalfall** — auch mit Circuit Breaker bleiben ~20
  sequenzielle Calls à ~1s = **~20s Wartezeit** beim Upload, wenn die API
  gesund ist. Der Breaker schützt nur gegen den Ausfall, nicht gegen die
  Normal-Latenz. Bewusst **nicht** in diesem Ticket gelöst: erst messen, wenn
  der Flow steht. CLAUDE.md sieht den Upgrade-Pfad bereits vor (`@Async` +
  `ImportJob`-Entity + Status-Polling `GET /import/{jobId}/status`).
  Alternativen für das Folge-Ticket: Batch-Prompt (mehrere Transaktionen pro
  Call — bräuchte eine neue Port-Methode und änderte den in BE-CAT-01
  beschlossenen Contract) oder parallele Calls (Rate-Limit-Risiko).
  Eigenes Issue anlegen, sobald BE-CAT-03 gemergt ist.
