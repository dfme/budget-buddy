# [BE-CAT-11] Lerneffekt bereits bei erfolgreicher Claude-Kategorisierung, nicht erst bei manueller Korrektur

- **Issue:** [#314](https://github.com/dfme/budget-buddy/issues/314)
- **Task-ID:** `BE-CAT-11`
- **Branch:** `feature/BE-CAT-11-lerneffekt-claude-erfolg`
- **Story:** US-05 — Transaktionen in Kategorien sehen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-16

## Ausgangslage

`category_lookup` lernt heute ausschliesslich aus manuellen User-Korrekturen
(`TransactionCategoryService.updateCategory` → `CategoryLearningPort.learn`, BE-CAT-04).
`HybridCategorizationService:82-89` nimmt das Claude-Ergebnis entgegen und gibt es weiter, ohne
es je zurückzuschreiben — derselbe Händler löst beim nächsten Import wieder einen Call aus. Ein
von Claude korrekt kategorisierter, aber nie korrigierter Händler lernt damit nie.

Die Sperre dagegen sitzt in `CategorizationResult.Source`: `CLAUDE` deckt heute drei Dinge
gleichzeitig ab — die echte Modell-Antwort (`ClaudeCategorizationService:292`), den Fallback nach
`AnthropicException` (`:230`) und die Bündel-Vorbelegung, die bei unlesbarer Antwort oder
fehlender Nummer stehen bleibt (`:255`). `CLAUDE_SKIPPED` trennt nur den Fall ohne HTTP-Request
ab (offener Breaker, fehlender API-Key). Ohne feinere Unterscheidung liesse sich «erfolgreich
kategorisiert» nicht von «auf Sonstiges gefallen» trennen.

## Entscheide

| # | Frage | Entscheid | Begründung |
| - | ----- | --------- | ---------- |
| 1 | Wie wird die feinere Unterscheidung modelliert? | Neuer Enum-Wert `Source.CLAUDE_FALLBACK`. `CLAUDE` heisst ab jetzt **echte Modell-Antwort**, `CLAUDE_FALLBACK` **Request ging raus, kein brauchbares Ergebnis**, `CLAUDE_SKIPPED` bleibt **kein Request**. | Der Enum trägt bereits «wie kam das zustande»; `CLAUDE_SKIPPED` hat denselben Split schon vorgezeichnet. Ein zusätzliches `boolean`-Feld am Record hätte jeden der ~40 Konstruktoraufrufe in Main und Tests dreistellig gemacht und die Instrumentierung in `ImportJobRunner` blind für den Unterschied gelassen. |
| 2 | Wird ein *echtes* `Sonstiges` von Claude gelernt? | Nein. | Die Einfrier-Wirkung aus AC 2 ist dieselbe: der Händler wird künftig von der Lookup-Stufe vor Claude abgefangen und nie wieder neu bewertet — nur ohne Netzwerkfehler als Ursache. `Sonstiges` ist zudem kein Wissensgewinn gegenüber dem Fallback, den es ohnehin gibt. Preis: ein Call pro unklarem Händler pro Import. |
| 3 | Umgang mit der nDSG-Folge? | Umsetzen, #290 erweitern, Doku nachziehen. | `category_lookup` ist global (ohne `user_id`) und überlebt die Kontolöschung — offene Lücke #290. Bisher landet dort nur, was ein Nutzer aktiv korrigiert hat; künftig jeder von Claude kategorisierte Buchungstext, ungefragt und roh. Die Lücke wächst dadurch erheblich. `UserService:191` und der Swagger-Text in `UserController:117` benennen heute ausdrücklich «manuelle Kategorie-Korrekturen» als einzige Quelle und wären danach falsch. |
| 4 | Doku-Scope? | Alle fünf zusätzlich gefundenen Stellen mitbeheben, Erweiterung im PR-Body deklarieren. | AC 6 nennt nur ADR-6 Step 4. Die breite Gegensuche (`grep -rniE 'lerneffekt|lernt|learning|feedback loop'`) fand dieselbe Aussage an vier weiteren Stellen. Sie stehen zu lassen wäre genau das Muster, das bei #103/#115/#117 drei Runden gekostet hat. |

Verworfen zu Entscheid 3: **erst #290 lösen** (`user_id` an `category_lookup`) — Flyway-Migration,
geänderter Primärschlüssel und die offene Frage, ob der Lerneffekt global oder pro Nutzer gelten
soll; das ist ein eigener Task, nicht BE-CAT-11. Ebenfalls verworfen: **nur das maskierte Pattern
lernen** — `PromptSanitizer` entfernt genau die Zeichen, an denen gematcht wird, das senkte die
Trefferquote und machte den Claude-Lerneffekt inkompatibel zu den Patterns aus BE-CAT-04.

Nicht angefasst: `docs/presentations/*` und `docs/prompts/*`. Das sind historische Kursabgaben,
die den damaligen Stand festhalten — sie nachzuziehen hiesse, Abgaben rückwirkend zu ändern.

## Betroffene Dateien

### Backend Main

| Datei | Änderung |
| ----- | -------- |
| `categorization/CategorizationResult.java` | `CLAUDE_FALLBACK` ergänzen; Javadoc von `Source` und `CLAUDE` präzisieren — der Absatz «transportiert Herkunft, keine Qualität» stimmt für `CLAUDE` nicht mehr |
| `categorization/ClaudeCategorizationService.java` | `fallbackResult()` neben `claudeResult()`; die Fehlerpfade `:230` (`AnthropicException`) und `:255` (Bündel-Vorbelegung, deckt unlesbare Antwort und fehlende Nummer mit ab) auf `CLAUDE_FALLBACK`. `:292` (echter Eintrag) bleibt `CLAUDE`. Klassen-Javadoc nachziehen |
| `categorization/HybridCategorizationService.java` | **Kern:** `CategoryLearningPort` injizieren; nach Stufe 2 für jedes Ergebnis mit `source == CLAUDE && category != SONSTIGES` den **rohen** Text als Pattern lernen. `:88` Fallback auf `CLAUDE_FALLBACK`. Fehlerisolation: `learn` in try/catch — ein DB-Fehler darf den Import nicht abbrechen |
| `categorization/CategoryLearningPort.java` | Javadoc: zwei Quellen statt «vom User bestätigt» / «schreibt das `transaction`-Modul» |
| `categorization/CategoryLearningService.java` | Javadoc: «user-bestätigte Zuordnungen» → beide Quellen |
| `transaction/ImportJobRunner.java` | `switch` bei `:193` um `case CLAUDE_FALLBACK -> viaClaude++`. Ein Request ging raus, also zählt er für die Laufzeit wie `CLAUDE`; ohne diesen Zweig fiele der Fall still durch und die Import-Zahlen summierten sich nicht mehr auf |
| `auth/UserService.java:191`, `auth/UserController.java:117` | #290-Lückenbeschreibung: nicht mehr nur «manuelle Kategorie-Korrekturen». `UserController` ist der öffentlich sichtbare Swagger-Text |

### Backend Tests

| Datei | Änderung |
| ----- | -------- |
| `ClaudeCategorizationServiceTest.java` | Helper `fallback(...)`; die Fallback-Assertions (`:129`, `:222`, `:243-244`, `:323`, `:356`, `:414`, `:435`) von `claude(SONSTIGES)` auf `fallback(SONSTIGES)`. Erfolgspfade bleiben `claude(...)` |
| `HybridCategorizationServiceTest.java` | `@Mock CategoryLearningPort`; bestehende Erwartungen ergänzen plus die neuen Tests unten |
| `ImportJobRunnerTest.java` | `CLAUDE_FALLBACK`-Zählung abdecken, sofern dort `Source` konstruiert wird |

### Doku

| Datei | Änderung |
| ----- | -------- |
| `docs/adr/ADR-6-hybrid-categorization.md` | Step 4 (`:27`) → zwei Quellen; Consequences `:43` ebenfalls; Hinweis, dass Fehler-Fallbacks und `Sonstiges` ausgenommen sind, mit Begründung |
| `CLAUDE.md:25` | Tabellenzeile 3 «Manuelle Korrekturen» → beide Quellen, mit der Ausnahme |

### Neu

- `docs/plans/BE-CAT-11-lerneffekt-claude-erfolg.md` (diese Datei) + Index-Zeile in `docs/plans/README.md`

## Implementierungsschritte

1. `Source.CLAUDE_FALLBACK` einführen, die drei Claude-Werte im Javadoc gegeneinander abgrenzen.
2. `ClaudeCategorizationService`: `fallbackResult()`, Fehlerpfade umstellen.
3. `ImportJobRunner`: `switch` erweitern — **vor** Schritt 4, damit der Build zwischendurch nicht lügt.
4. `HybridCategorizationService`: `CategoryLearningPort` injizieren, Lernschleife nach Stufe 2, try/catch.
5. Tests anpassen und die neuen schreiben.
6. Javadoc an `CategoryLearningPort`, `CategoryLearningService`, `UserService`, `UserController`.
7. ADR-6 und `CLAUDE.md` präzisieren.
8. `./mvnw -f backend/pom.xml verify` mit `JAVA_HOME` auf JDK 25.
9. Folge-Issue an #290 anhängen.

## Test-Strategie

| Test | Ebene | Deckt ab |
| ---- | ----- | -------- |
| `lerntNachErfolgreicherClaudeKategorisierung` | Unit (`HybridCategorizationServiceTest`) | AC 1 |
| `zweiterImportTrifftDieLookupTabelleOhneClaudeCall` | Unit | **AC 4** — zwei `categorizeAll`-Läufe; der Lookup-Mock liefert im zweiten Lauf den gelernten Eintrag, `verify(claude, times(1))` |
| `offenerBreakerSchreibtKeinenLookupEintrag` | Unit | **AC 5** — `CLAUDE_SKIPPED` → `verifyNoInteractions(learningPort)` |
| `fehlgeschlagenerCallSchreibtKeinenLookupEintrag` | Unit | AC 2 — `CLAUDE_FALLBACK` |
| `echtesSonstigesWirdNichtGelernt` | Unit | Entscheid 2 |
| `fehlerBeimLernenBrichtDieKategorisierungNichtAb` | Unit | Fehlerisolation — `learn` wirft, die Ergebnisliste bleibt vollständig |
| `ClaudeCategorizationServiceTest` (bestehend) | Unit | Dass genau die Fehlerpfade `CLAUDE_FALLBACK` liefern und der Erfolgspfad `CLAUDE` |
| `TransactionCategoryServiceTest` (bestehend, unverändert) | Unit | **AC 3** — die manuelle Korrektur lernt weiterhin |

## Acceptance Criteria (aus #314)

- [ ] `HybridCategorizationService` ruft nach einer erfolgreichen Claude-Kategorisierung
      `CategoryLearningPort.learn(...)` auf und schreibt das Händler-Pattern in `category_lookup`
- [ ] Es wird **nicht** gelernt, wenn die Kategorie nur ein Fehler-Fallback ist (offener Circuit
      Breaker, fehlender API-Key, fehlgeschlagener/unlesbarer Call, im Bündel fehlende Nummer)
- [ ] Bestehender Lerneffekt bei manueller Korrektur (`TransactionCategoryService`, BE-CAT-04)
      bleibt unverändert; beide Quellen schreiben in dieselbe Tabelle, eine manuelle Korrektur
      überschreibt einen zuvor aus Claude gelernten Eintrag (Upsert-Semantik bleibt)
- [ ] Unit-Test: Claude kategorisiert einen unbekannten Händler erfolgreich → ein zweiter Import
      desselben Händlertexts trifft die Lookup-Tabelle, ohne erneut Claude aufzurufen
- [ ] Unit-Test: ein Fehler-Fallback (z. B. offener Breaker) schreibt keinen Lookup-Eintrag
- [ ] ADR-6 («Step 4») entsprechend präzisiert: Lerneffekt jetzt aus zwei Quellen
