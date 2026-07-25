# [INFRA-11] ANTHROPIC_API_KEY: Verifikation dokumentieren + automatischer Startup-Check

- **Issue:** #76 (Label: `enhancement`, `us-05`; Milestone: Sprint 3)
- **Task-ID:** INFRA-11
- **Branch:** `feature/INFRA-11-verifikation-doku`
- **Basis:** `origin/main`
- **PR:** #97 (offen)

## Scope-Erweiterung gegenüber dem ursprünglichen Issue

#76 war ursprünglich als **reine Konfiguration ohne Code** angelegt (Key im Render-Dashboard
setzen). In der Umsetzung erweitert auf zwei Teile, damit INFRA-11 produktiv nachweisbar wird
und nicht nur ein manueller Task ohne Code-Beitrag bleibt:

1. **Verifikations-Doku** in der README (was heisst „der Key greift produktiv?").
2. **Automatischer Startup-Check**, der die **Gültigkeit** des Keys bei jedem Start prüft.

Der Issue-Text von #76 wird entsprechend angepasst (siehe Abschnitt „Issue-Scope-Anpassung").

## Teil 1 — Verifikations-Doku (bereits erledigt, in PR #97)

README-Abschnitt „`ANTHROPIC_API_KEY` verifizieren (nach dem Setzen in Render)":

- **Check 1 (Log-Signal):** INFO `Anthropic-Client initialisiert …` vs. WARN `… nicht gesetzt`.
- **Check 2 (funktionaler Test):** unbekannte Transaktion hochladen → darf nicht `Sonstiges`
  sein; deckt implizit das **Guthaben** ab.

Status: committed, Teil des offenen PR #97.

## Teil 2 — Automatischer Startup-Check der Key-Gültigkeit (neu)

### Kontext

`AnthropicConfig` loggt beim Start nur, ob ein Key **gesetzt** ist — nicht, ob er **gültig** ist.
Ein vertippter/widerrufener Key sieht am Startup gesund aus und fällt erst beim ersten echten
Import auf. Dieser Check hebt den Startup-Log von „Key gesetzt?" auf „Key gültig?".

### Entscheidungen

- **Variante C — Gratis-Call `client.models().list()`** (`GET /v1/models`), nicht der bezahlte
  `messages`-Ping. Begründung (aus der Diskussion zu #76):
  - Guthaben-Check am Startup hat geringen Mehrwert: ein leeres Guthaben zeigt sich ohnehin beim
    ersten Import (WARN + Fallback) und ist über Check 2 (funktionaler Test) abgedeckt; ein
    Startup-Snapshot garantiert das Guthaben zur Laufzeit sowieso nicht.
  - Die wahrscheinlichste Fehlkonfiguration ist ein falscher/widerrufener Key — den fängt
    `models().list()` (→ 401) zum Nulltarif.
- **Kein Flag, keine Property-Änderung.** Gratis-Call ⇒ kein Grund zum Schalten. Läuft **immer,
  wenn ein `AnthropicClient`-Bean existiert** (= Key gesetzt); ohne Key passiert nichts.
- **Nicht blockierend, nur Log.** Asynchron nach `ApplicationReadyEvent` via
  `CompletableFuture.runAsync`; ein Fehler verhindert den Start nie (Fallback-Philosophie wie der
  Import selbst, Churn-Risiko #1 aus CLAUDE.md).
- **Prüft Key-Gültigkeit, nicht Guthaben** — bewusst; so im Javadoc und in der README benannt.

### Log-Ausgänge

| Ausgang | Level | Meldung (sinngemäß) |
|---|---|---|
| OK (200) | INFO | „Anthropic-Healthcheck OK — API-Key gültig." |
| `UnauthorizedException` (401) | WARN | „API-Key ungültig/widerrufen — Kategorisierung läuft produktiv im Fallback ('Sonstiges')." |
| sonstige `AnthropicException` | WARN | „Anthropic-API nicht erreichbar (…)." |

## Betroffene Files

| Datei | Änderung |
|---|---|
| `backend/README.md` | Teil 1 (in #97) + kleiner Zusatz zum automatischen Check |
| `backend/src/main/java/com/budgetbuddy/categorization/AnthropicStartupHealthCheck.java` | neu |
| `backend/src/test/java/com/budgetbuddy/categorization/AnthropicStartupHealthCheckTest.java` | neu |

Bewusst **nicht** geändert: `AnthropicProperties`, `application.properties`,
`application-prod.properties`, `ClaudeCategorizationServiceTest` (kein Flag → keine
Konstruktor-/Property-Änderung).

## Issue-Scope-Anpassung (#76)

Vor der Umsetzung von Teil 2 wird der Issue-Text angepasst (der Nutzer hat das freigegeben):

- Neuen Acceptance-Criteria-Punkt ergänzen: „Beim Start prüft die App automatisch die
  **Gültigkeit** des Keys und loggt das Ergebnis (INFO bei gültig, WARN bei 401)."
- Die Durchstreichungen „n/a: reine Konfiguration, kein Code / kein PR" in der Definition of
  Done relativieren: INFRA-11 liefert nun **Code + PR** (der Verifikations-Teil), der
  Guthaben-/Kostenträger-Teil bleibt manuell/organisatorisch.

## Implementierungsschritte

1. Issue #76 anpassen (neuer AC + DoD-Korrektur).
2. `AnthropicStartupHealthCheck` (`@Component`, `@EventListener(ApplicationReadyEvent.class)`):
   - `AnthropicClient` via `ObjectProvider` injizieren.
   - Bean fehlt → return. Bean da → `CompletableFuture.runAsync(this::ping)`.
   - `ping()`: `client.models().list()`, Ergebnis loggen, nie werfen.
3. Unit-Test.
4. README-Zusatz (automatischer Check, Abgrenzung zum Guthaben-/funktionalen Check).
5. Voller Testlauf `./mvnw test`.
6. Commit + Push in den bestehenden Branch → aktualisiert PR #97.

## Test-Strategie

Reine Unit-Tests mit gemocktem `AnthropicClient` (Mockito), analog zu
`ClaudeCategorizationServiceTest` — kein echter API-Call in CI:

- Kein Client-Bean (`getIfAvailable()` → `null`) → `ping` nicht ausgelöst, `models()` nie
  aufgerufen, kein Throw.
- `ping()` bei vorhandenem Client → `models().list()` genau einmal aufgerufen.
- `models().list()` wirft `AnthropicException` → `ping()` wirft nicht durch (nur Log).

## Acceptance Criteria (INFRA-11, erweitert)

Aus #76 (bestehen):

- [ ] `ANTHROPIC_API_KEY` ist im Render-Dashboard gesetzt (manuell/organisatorisch).
- [ ] Log meldet nicht mehr `… nicht gesetzt`.
- [ ] Eine unbekannte Transaktion wird produktiv nicht als `Sonstiges` kategorisiert.
- [ ] Der Key steht nirgends im Git.

Neu (dieser Umsetzung):

- [ ] README dokumentiert Check 1 (Log) + Check 2 (funktional) — erledigt in PR #97.
- [ ] Bei gültigem Key erscheint beim Start eine INFO-Zeile „…OK — API-Key gültig".
- [ ] Bei ungültigem/widerrufenem Key erscheint eine WARN-Zeile (401), der Start bricht nicht ab.
- [ ] Der Check blockiert den Applikationsstart nicht (asynchron) und prüft die Key-Gültigkeit
      (nicht das Guthaben — das bleibt Check 2).
- [ ] `./mvnw test` grün; kein echter Netzwerk-/API-Call in den Tests.
