# [BE-CAT-07] AnthropicStartupHealthCheckTest ist ordnungsabhängig und macht einen echten Netzwerk-Call

- **Issue:** [#162](https://github.com/dfme/budget-buddy/issues/162)
- **Task-ID:** `BE-CAT-07`
- **Branch:** `fix/BE-CAT-07-healthcheck-test-isolation`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-27

## Ursachenanalyse

Zwei unabhängige Defekte, die sich gegenseitig sichtbar machen. Keiner der beiden lässt sich
allein beheben, ohne den anderen stehen zu lassen.

### 1. Der echte Netzwerk-Call

`AnthropicConfigTest.WithApiKey` setzt `anthropic.api.key=sk-ant-test-key-not-real` und ist damit
die **einzige** Stelle im gesamten Testbaum, an der ein echter `AnthropicOkHttpClient` entsteht.
Belegt durch Ausschluss:

```
$ grep -rn "anthropic.api.key\|ANTHROPIC_API_KEY" src/test src/test/resources
src/test/java/com/budgetbuddy/config/AnthropicConfigTest.java:39:  "anthropic.api.key="
src/test/java/com/budgetbuddy/config/AnthropicConfigTest.java:61:  "anthropic.api.key=sk-ant-test-key-not-real"

$ grep -rn "AnthropicOkHttpClient" src/
src/main/java/com/budgetbuddy/config/AnthropicConfig.java:60   (Produktionscode)
```

Kette: `@SpringBootTest` startet einen vollen Kontext → `ApplicationReadyEvent` →
`AnthropicStartupHealthCheck.onApplicationReady()` → `CompletableFuture.runAsync(() -> ping(client))`
→ echter `GET https://api.anthropic.com/v1/models`. Das `Request failed` im Issue-Report ist der
Transport-Wrapper des Anthropic-SDK und damit der Beweis, dass der Call real war.

### 2. Die Ordnungsabhängigkeit — AC 1 trifft die Ursache nicht

Das Issue vermutet einen Appender, der „zwischen Testklassen nicht zurückgesetzt" wird. Das ist
nicht der Fall: `AnthropicStartupHealthCheckTest` legt in `@BeforeEach` einen frischen
`ListAppender` an und hängt ihn in `@AfterEach` wieder ab. Surefire läuft nicht parallel
(kein `parallel`-Element in `pom.xml`), Testklassen überlappen also nicht.

Der tatsächliche Mechanismus ist ein **Thread**, der seine Testklasse überlebt: `runAsync` läuft
auf dem gemeinsamen ForkJoinPool. Wenn der HTTP-Versuch aus (1) Sekunden später zurückkommt,
loggt er auf den prozessweit geteilten Logger `AnthropicStartupHealthCheck` — und trifft dort
denjenigen Appender, der in diesem Moment zufällig angehängt ist.

Daraus folgt die Korrektur an der Massnahme: ein *Reset* behebt nichts, weil der Appender bereits
frisch ist. Nötig ist eine **Thread-Eingrenzung** — der Appender darf nur aufnehmen, was der
Test-Thread selbst erzeugt hat. Das wirkt in beide Richtungen und verhindert auch ein künftig
fälschlich grünes `noneMatch`.

## Entscheide

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Netzwerk-Call unterbinden | `@ConditionalOnProperty` am Healthcheck, global via Surefire auf `false` | Strukturelle Garantie statt Disziplin: auch ein künftiger Test mit Key kann nicht mehr ins Netz. Folgt derselben Logik wie der bewusst weggelassene `testcontainers:junit-jupiter` in `pom.xml` — „fehlt die Möglichkeit, greift niemand versehentlich danach". Die Alternative (`@MockitoBean` nur in `WithApiKey`) wäre minimal, aber ab dem nächsten Test mit Key wieder offen. |
| `CategorizationLogRedactionTest` | mitfixen | Identisches Muster (geteilter Logger, kein Thread-Filter). Aktuell nicht betroffen, weil es andere Logger beobachtet — aber `ClaudeCategorizationService` läuft in `@Async`-Importjobs, die Konstellation ist also erreichbar. Diff dort: eine Zeile. Scope-Erweiterung wird im PR-Body deklariert. |
| Umfang des Test-Helpers | nur der Thread-Filter, kein attach/detach-Framework | Hält den Eingriff in den zweiten Test auf einer Zeile und damit reviewbar. |

## Betroffene Files

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/categorization/AnthropicStartupHealthCheck.java` | `@ConditionalOnProperty(name = "budgetbuddy.anthropic.startup-healthcheck.enabled", matchIfMissing = true)` + Javadoc-Absatz zum Warum |
| `backend/pom.xml` | Surefire `systemPropertyVariables`: Schalter global auf `false`, mit Kommentar analog zum `JWT_SECRET`-Block |
| `backend/src/main/resources/application.properties` | Property dokumentieren (Default `true`, nur Tests setzen `false`) |
| `backend/src/test/java/com/budgetbuddy/categorization/AnthropicStartupHealthCheckTest.java` | Thread-eingegrenzter Appender + Regressionstest `ignoresLogEventsFromForeignThreads` |
| `backend/src/test/java/com/budgetbuddy/categorization/CategorizationLogRedactionTest.java` | `new ListAppender<>()` → `new ThreadScopedLogAppender()` |
| `backend/src/test/java/com/budgetbuddy/config/AnthropicConfigTest.java` | Guard-Test „Healthcheck-Bean ist in Tests abwesend"; Klassen-Javadoc korrigieren (behauptet, in Tests sei nie ein Key gesetzt — genau diese Klasse setzt einen) |

### Neu

- `backend/src/test/java/com/budgetbuddy/support/ThreadScopedLogAppender.java`

## Implementierungsschritte

1. `ThreadScopedLogAppender` in `support/` anlegen — `ListAppender`, der in `append` den
   `getThreadName()` des Events gegen den Thread prüft, der ihn angelegt hat.
2. Beide Tests auf den neuen Appender umstellen. Die Feldtypen bleiben
   `ListAppender<ILoggingEvent>`, es ändert sich nur der Konstruktoraufruf.
3. Regressionstest in `AnthropicStartupHealthCheckTest`: ein Fremd-Thread loggt exakt die
   gemeldete Störzeile (`… nicht erreichbar …`) auf den Healthcheck-Logger; `logs.list` muss
   leer bleiben. Das reproduziert den Bleed und zäunt ihn ein.
4. `@ConditionalOnProperty` am Healthcheck, Schalter in `pom.xml`, Property in
   `application.properties` dokumentieren.
5. Guard-Test in `AnthropicConfigTest.WithApiKey`: `ObjectProvider<AnthropicStartupHealthCheck>`
   liefert `null`. Damit ist AC 2 durch einen Test belegt und nicht durch ein Grep.

## Test-Strategie

| Stufe | Test | Deckt AC |
| ----- | ---- | -------- |
| Unit | `ignoresLogEventsFromForeignThreads` (neu) | 1, 4 |
| Integration | Bean-Abwesenheit in `WithApiKey` (neu) | 2 |
| Lauf | `./mvnw package` | 3 |
| Lauf | `./mvnw test -Dsurefire.runOrder=random` **und** `-Dsurefire.runOrder=reversealphabetical` | 4 |
| Nachweis | `grep -rl "Anthropic-Healthcheck\|api.anthropic.com" target/surefire-reports/` — vorher Treffer, nachher leer | 2, 3 |

### Bekannte Grenze des Nachweises

AC 3 verlangt Grünsein *ohne Netzzugang zu `api.anthropic.com`*. Den Host wirklich zu blockieren
erfordert auf macOS einen `/etc/hosts`-Eintrag mit `sudo`. Der geführte Nachweis ist deshalb
strukturell: die Healthcheck-Bean ist in jedem Testkontext nachweislich abwesend, und kein
Surefire-Report enthält noch eine Healthcheck-Zeile. Damit existiert kein Codepfad mehr, der den
Call absetzen könnte.

## Acceptance Criteria (aus #162)

- [ ] Der Log-Appender wird pro Testklasse (bzw. pro Testfall) sauber zurückgesetzt, sodass
      `messagesAt(...)` nur Zeilen des laufenden Tests sieht
- [ ] Kein Test stellt einen echten HTTP-Request an `api.anthropic.com` — der Startup-Healthcheck
      läuft in Tests ausschliesslich gegen einen gemockten Client
- [ ] `./mvnw package` ist ohne Netzzugang zu `api.anthropic.com` reproduzierbar grün
- [ ] Die Test-Reihenfolge ändert das Ergebnis nicht (Gegenprobe: Lauf mit gemischter Reihenfolge)
