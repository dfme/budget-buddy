# [INFRA-37] MDC einführen: User-ID in Logs für bessere Analysierbarkeit

- **Issue:** [#257](https://github.com/dfme/budget-buddy/issues/257)
- **Task-ID:** `INFRA-37`
- **Branch:** `feature/INFRA-37-mdc-logging`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-02

## Ausgangslage

Die User-ID steht heute nur dort im Log, wo sie von Hand in den Text geschrieben wurde —
sieben Stellen in `PdfImportService` und `ImportJobRunner` (`"… für User {}"`). Jede andere
Zeile desselben Requests trägt sie nicht, ein Aggregator kann die Zeilen eines Requests also
nicht zusammenführen, und jede neue Log-Zeile müsste die ID erneut manuell mitschleppen.

Es gibt keine `logback-spring.xml`; `application.properties` konfiguriert nur
`logging.level.*`. Der einzige `@Async`-Pfad ist `ImportJobRunner` auf dem
`importExecutor` aus `AsyncConfig`.

## Entscheide

| Punkt | Entscheid | Begründung |
| --- | --- | --- |
| MDC-Keys | `userId` **und** `requestId` | Bestätigt durch den User. `requestId` wird für jeden Request gesetzt (auch unauthentifiziert), `userId` nur nach erfolgreicher JWT-Validierung. Erst die `requestId` gruppiert *alle* Zeilen eines Requests — auch die vor und ohne Authentifizierung. |
| Manuelle `für User {}` | aus allen 7 Stellen entfernt | Bestätigt durch den User (AC-4). Sonst stünde die ID doppelt, und das Ziel des Issues — neue Log-Zeilen müssen sie nicht mehr mitgeben — wäre nur halb erreicht. |
| Log-Pattern | `logging.pattern.level` in `application.properties`, **kein** `logback-spring.xml` | `logging.pattern.level` ist Spring Boots dokumentierter Einhängepunkt für MDC und lässt das Default-Pattern intakt. Eine eigene Logback-XML nur für zwei `%X{}` schafft neue Wartungsfläche und muss bei jedem Spring-Boot-Upgrade gegen das mitgelieferte Default-Pattern gehalten werden. |
| Ort des Setzens | eigener `LoggingContextFilter` **vor** `JwtCookieAuthenticationFilter` | Das Aufräumen im `finally` muss die ganze Filterkette umschliessen, inklusive des JWT-Filters selbst. Ausserdem: ein Filter namens `JwtCookieAuthenticationFilter`, der nebenbei Request-IDs erzeugt, ist falsch benannt. Der JWT-Filter steuert nur die `userId` bei, weil dort — und nur dort — die validierte ID anfällt. |

## Betroffene Files

### Neu

- `backend/src/main/java/com/budgetbuddy/config/LogContext.java` — Key-Konstanten,
  `putUserId(long)`, `newRequestId()`, `clear()`
- `backend/src/main/java/com/budgetbuddy/config/LoggingContextFilter.java` —
  `OncePerRequestFilter`, setzt `requestId`, entfernt beide Keys im `finally`
- `backend/src/main/java/com/budgetbuddy/config/MdcTaskDecorator.java` — kopiert die MDC-Map
  des aufrufenden Threads in den Pool-Thread und räumt dort danach auf

### Geändert

- `backend/src/main/java/com/budgetbuddy/auth/JwtCookieAuthenticationFilter.java` —
  `LogContext.putUserId(userId)` im Erfolgspfad
- `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java` — Filter einhängen
- `backend/src/main/java/com/budgetbuddy/config/AsyncConfig.java` — `setTaskDecorator(...)`
- `backend/src/main/resources/application.properties` — `logging.pattern.level`
- `backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java` (3 Log-Zeilen)
- `backend/src/main/java/com/budgetbuddy/transaction/ImportJobRunner.java` (4 Log-Zeilen)
- `docs/CONVENTIONS.md` — Abschnitt «Backend: Logging-Kontext (MDC)» (die Konventionen sind mit `568ea93` aus `CLAUDE.md` hierher gezogen)

## Implementierungsschritte

1. `LogContext` mit den beiden Keys und `clear()`.
2. `LoggingContextFilter`: `requestId` (8 Hex-Zeichen) setzen, `chain.doFilter` in `try`,
   `LogContext.clear()` im `finally`. Javadoc hält die bekannte Grenze fest: Springs
   `/error`-Dispatch überspringt `OncePerRequestFilter` per Default, diese Zeilen tragen
   keinen MDC.
3. Filter in `SecurityConfig` vor den JWT-Filter hängen.
4. `JwtCookieAuthenticationFilter.authenticate` setzt zusätzlich `userId` — nur im
   Erfolgspfad, im `JwtException`-Zweig ausdrücklich nicht.
5. `MdcTaskDecorator` schreiben und in `AsyncConfig` verdrahten (AC-3).
6. `logging.pattern.level` setzen; gilt über die Basis-Datei auch für das prod-Profil.
7. Die 7 manuellen `für User {}` entfernen. Kein Test hängt an diesen Strings (geprüft:
   `grep -rn 'für User' backend/src/test` liefert nichts).
8. Abschnitt in `docs/CONVENTIONS.md` ergänzen.

## Test-Strategie

| Test | Deckt AC | Inhalt |
| --- | --- | --- |
| `config/LoggingContextFilterTest` (Unit) | 1, 2 | `requestId` während der Chain vorhanden; danach MDC leer; **auch wenn die Chain eine Exception wirft** |
| `auth/JwtCookieAuthenticationFilterTest` (bestehend, `@SpringBootTest` gegen die echte Chain) | 1 | Neuer Fall: Test-Controller loggt, `ThreadScopedLogAppender` fängt das Event, Assertion auf `getMDCPropertyMap()` → `userId=99`; ungültiges Cookie → kein `userId` |
| `config/MdcTaskDecoratorTest` (Unit) | 3 | MDC des Aufrufers landet im Pool-Thread; Pool-Thread ist danach wieder sauber (kein Leak in den nächsten Task) |
| `config/LogPatternMdcTest` (Unit) | 1 («sichtbar im Pattern») | liest `logging.pattern.level` aus `application.properties`, rendert ein Event mit gesetztem MDC durch eine `PatternLayout`, prüft beide IDs im Text — fängt ein späteres Löschen der `%X{}` ab |

Der Decorator-Test prüft bewusst direkt die MDC-Map statt Log-Output: `ThreadScopedLogAppender`
sieht per Konstruktion keine Events fremder Threads.

## Acceptance Criteria (aus dem Issue)

- [ ] Jede Log-Zeile innerhalb eines authentifizierten Requests enthält die User-ID
      (sichtbar im konfigurierten Log-Pattern), ohne dass die aufrufende Stelle sie übergibt.
- [ ] Der MDC-Eintrag wird nach Request-Ende zuverlässig entfernt (kein Leak in nachfolgende
      Requests auf demselben Thread) — inkl. Fehlerpfad (Exception in der Filter-Chain).
- [ ] Der `@Async`-Pfad (`ImportJobRunner`) trägt die User-ID ebenfalls.
- [ ] Bestehende manuelle User-ID-Erwähnungen werden nicht dupliziert.
- [ ] Test (Logback-Appender analog `CategorizationLogRedactionTest`) sichert ab, dass die
      User-ID im Log erscheint und nach Request-Ende verschwindet.

## Datenminimierung (nDSG)

Nur die interne User-ID geht in den MDC — kein Name, keine E-Mail. Die `requestId` ist eine
zufällige, nicht personenbezogene Kennung ohne Rückschluss auf den Nutzer. Damit bleibt die
Änderung konsistent mit der Redaktionspraxis aus BE-PDF-06.
