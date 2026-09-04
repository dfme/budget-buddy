# [BE-PDF-11] Verwaiste Import-Jobs bleiben nach einem Neustart für immer auf RUNNING

- **Issue:** [#197](https://github.com/dfme/budget-buddy/issues/197)
- **Task-ID:** `BE-PDF-11`
- **Branch:** `fix/BE-PDF-11-verwaiste-import-jobs`
- **Story:** US-04 — PDF-Upload
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-04

## Ausgangslage

Stirbt die JVM während eines laufenden Kategorisierungslaufs, bleibt die Zeile in `import_jobs`
für immer auf `RUNNING`. Es gibt keinen Mechanismus, der sie danach noch bewegt.

Die Behauptung des Issues wurde in der Analyse gegengeprüft — eng und breit, wie es der
`/implement-issue`-Ablauf für Nachweise per Suche verlangt:

| Suche | Treffer |
| ----- | ------- |
| eng: `ApplicationRunner\|@Scheduled\|@EventListener\|CommandLineRunner` | `AnthropicStartupHealthCheck.java:63` |
| breit: `ApplicationReadyEvent\|ContextRefreshed\|InitializingBean\|@PostConstruct\|SmartLifecycle\|@EnableScheduling\|afterPropertiesSet` | dieselbe Datei, sonst nichts |

Beide Mengen sind deckungsgleich: Es gibt genau einen Startup-Hook im Backend, und
`@EnableScheduling` existiert nirgends — eine periodische Variante muss es mitbringen.

## Über das Issue hinaus gefunden: die 409-Sperre

Der Duplikatcheck fragt neben `transactions` auch `import_jobs`
(`PdfImportService.isDuplicate`, Zeile 182–185):

```java
return transactionRepository.existsByUserIdAndPdfSha256(userId, pdfSha256)
        || importJobRepository.existsByUserIdAndPdfSha256AndStatus(
                userId, pdfSha256, ImportJobStatus.RUNNING);
```

Eine verwaiste `RUNNING`-Zeile sperrt damit den **erneuten Import derselben Datei dauerhaft mit
409**. Der Schaden ist also nicht auf Statistik und Diagnose beschränkt, wie der Abschnitt
«Wirkung» des Issues annimmt: Der Nutzer kommt bis zum nächsten Deploy nicht weiter, und der
naheliegende Selbsthilfe-Versuch — dieselbe Datei nochmals hochladen — ist genau der, der
scheitert. Das ist der stärkste Einzelgrund für den periodischen Lauf und bekommt im
Integrationstest einen eigenen Nachweis.

## Entscheide

Die drei Punkte aus «Zu untersuchen» wurden vor der Umsetzung mit dem User geklärt:

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Start genügt, oder periodisch? | **Start + periodisch** | Beim Start genügt es für den Deploy-Fall. Ein `Error` im laufenden Betrieb hinterlässt die Zeile dagegen bis zum nächsten Neustart — und mit der 409-Sperre ist das kein reiner Diagnoseschaden. |
| Ist `FAILED` der richtige Endzustand? | **ja** | Der `ImportJobRunner` schreibt die Transaktionen erst im Abschlussblock. Bei einem Abbruch davor ist tatsächlich nichts persistiert; `FAILED` sagt die Wahrheit. |
| Neue Spalte `failure_reason`? | **nein** | AC3 verlangt ohnehin eine Logzeile; sie trägt Grund und Job-IDs. Eine Spalte hiesse Flyway `V08`, ein Entity-Feld und eine DTO-Entscheidung — für einen Wert, den kein Client anzeigt. |
| `ImportJobRunner` fängt nur `RuntimeException` | **im selben PR mitfixen** | Deklarierte Scope-Erweiterung. Der Cleaner räumt sonst nur die Folge ab, während die Ursache bestehen bleibt. |

## Neue Dateien

| Datei | Zweck |
| ----- | ----- |
| `backend/src/main/java/com/budgetbuddy/transaction/StaleImportJobCleaner.java` | Die Bereinigung: `@EventListener(ApplicationReadyEvent)` + `@Scheduled`, Kern in `cleanUpStaleJobs()` mit `int`-Rückgabe |
| `backend/src/main/java/com/budgetbuddy/config/SchedulingConfig.java` | `@EnableScheduling` — bewusst nicht in `AsyncConfig`, dessen Name deckt nur den Import-Pool |
| `backend/src/test/java/com/budgetbuddy/transaction/StaleImportJobCleanerTest.java` | Unit-Test (Mockito + `ThreadScopedLogAppender`) |
| `backend/src/test/java/com/budgetbuddy/transaction/StaleImportJobCleanerIntegrationTest.java` | Gegen echtes PostgreSQL + Flyway |

## Geänderte Dateien

| Datei | Änderung |
| ----- | -------- |
| `ImportJobRepository.java` | `List<ImportJob> findByStatusAndCreatedAtBefore(ImportJobStatus, Instant)` |
| `ImportJobRunner.java` (Catch in `run`) | zweiter Catch für `Error`: Job auf `FAILED`, dann **rethrow**; scheitert der DB-Write selbst, geht er als `addSuppressed` mit |
| `ImportJobStatus.java` | Javadoc von `FAILED`: deckt jetzt auch «durch Neustart abgebrochen» ab |
| `AsyncConfig.java` | Kommentar zeigt auf den Cleaner als zweite Verteidigungslinie, statt zu implizieren, der Grace-Shutdown genüge |
| `application.properties` | `budgetbuddy.import.stale-job-reserve-seconds`, `budgetbuddy.import.stale-job-scan-interval-seconds`, je mit Begründung |
| `docs/adr/ADR-14-asynchroner-pdf-import.md` | nur, falls eine Aussage über den Code durch die Änderung falsch wird — mit `file:line` belegt |

## Implementierungsschritte

1. `findByStatusAndCreatedAtBefore` ans Repository, mit Javadoc zur fehlenden User-Einschränkung
   (Systemlauf ohne authentifizierten User — siehe Security-Review).
2. `StaleImportJobCleaner`: Grenze = `clock.instant() − categorizationTimeout − reserve`
   (300 + 300 s). Jobs mit Status `RUNNING` und `createdAt` davor werden über das vorhandene
   `ImportJob.fail(Instant)` auf `FAILED` gesetzt und per `saveAll` geschrieben — die
   Zustandslogik bleibt damit an einer Stelle statt als zweites UPDATE danebenzustehen.
   Rückgabe: Anzahl.
3. Logzeile (AC3): `WARN` mit Anzahl **und** Job-IDs, wenn > 0; bei 0 keine Zeile. Keine Beträge,
   Buchungstexte oder E-Mail-Adressen.
4. Aufhänger: `@EventListener(ApplicationReadyEvent.class)`, synchron, aber in `try/catch` — ein
   DB-Schluckauf darf den Start nie verhindern. Dazu `@Scheduled` mit `fixedDelayString` und
   `initialDelayString` = Intervall, damit der Start-Lauf nicht doppelt feuert.
5. `SchedulingConfig` mit `@EnableScheduling` anlegen.
6. `ImportJobRunner`-Catch erweitern.
7. Properties, Javadoc und Kommentare nachziehen.

## Test-Strategie

**Unit — `StaleImportJobCleanerTest`**

- alter Job (Grenze − 1 s) → `FAILED`, `finishedAt` gesetzt (AC1)
- junger Job (Grenze + 1 s) → unangetastet, `saveAll` bekommt ihn nicht (AC2)
- Grenzfall exakt auf der Schwelle — legt das `<`/`<=`-Verhalten fest, statt es zufällig zu lassen
- Logzeile mit korrekter Anzahl (AC3); bei 0 bereinigten Jobs **keine** Zeile
- `DONE`/`FAILED`-Jobs beliebigen Alters bleiben unberührt

**Integration — `StaleImportJobCleanerIntegrationTest`** (`@SpringBootTest`, eigene Datenbank auf
dem gemeinsamen Testcontainer)

- ein alter und ein junger `RUNNING`-Job in PostgreSQL; nach `cleanUpStaleJobs()` steht genau
  einer auf `FAILED`. Das ist zugleich der einzige Ort, an dem ein Tippfehler in der abgeleiteten
  Query auffliegt — ein Mock bestätigt nur den erfundenen Methodennamen.
- **die 409-Sperre löst sich:** nach der Bereinigung liefert
  `existsByUserIdAndPdfSha256AndStatus(…, RUNNING)` `false`, derselbe Auszug ist wieder
  importierbar.

**`ImportJobRunnerTest`** — neuer Fall: der Lauf wirft einen `Error` → Job ist `FAILED` **und**
der `Error` kommt beim Aufrufer an.

## Acceptance Criteria

- [ ] Beim Start werden Jobs mit Status `RUNNING`, deren `created_at` älter ist als
      `budgetbuddy.import.categorization-timeout-seconds` plus Reserve, auf `FAILED` gesetzt
- [ ] Ein Job, der jünger als diese Grenze ist, wird **nicht** angefasst
- [ ] Die Bereinigung schreibt eine Logzeile mit der Anzahl bereinigter Jobs
- [ ] Ein automatisierter Test deckt beide Fälle ab — alter Job wird bereinigt, junger bleibt

## Security-Review (Vorschau)

Berührt werden Zeile 1 (Repository-Zugriff auf Nutzerdaten), 4 (Properties) und 7 (Logging) der
Auslösematrix. **Zeile 1 ist der ernste Punkt:** `findByStatusAndCreatedAtBefore` hat bewusst
*keine* User-Einschränkung und ist damit die erste Query am `ImportJobRepository`, die über
Mandantengrenzen geht. Vertretbar ist sie, weil sie kein Request-Pfad ist und nichts
zurückgibt, was ein Nutzer je zu sehen bekommt; der PR-Body belegt, dass kein Controller sie
erreicht, und die Javadoc hält den Grund an der Methode fest.
