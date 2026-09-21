# [BE-PDF-16] Notification auch für Import-Jobs, die der Stale-Cleaner auf FAILED setzt

- **Issue:** [#341](https://github.com/dfme/budget-buddy/issues/341)
- **Task-ID:** `BE-PDF-16`
- **Branch:** `feature/BE-PDF-16-stale-cleaner-notification`
- **Story:** US-04 — PDF-Upload
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-21

## Ausgangslage

BE-PDF-15 (#337, PR #340) benachrichtigt den User über den Ausgang eines Import-Jobs, sobald
`ImportJobRunner` den Endstatus schreibt. Ein Job kann aber auch ausserhalb des Runners auf
`FAILED` gehen: der `StaleImportJobCleaner` (DB-10, #270) räumt verwaiste `RUNNING`-Jobs beim
Start, periodisch und beim nächsten Upload ab — ohne Benachrichtigung. Aus Sicht des Nutzers ist
das derselbe Fall, den #337 lösen wollte, nur mit anderer Ursache.

## Befund aus der Analyse: der vierte FAILED-Pfad

`grep -rn '\.fail(' backend/src/main/java` findet vier Übergänge nach `FAILED`, die ACs nennen
drei davon:

| Pfad | Notification heute | Nutzer auf der Seite? |
| ---- | ------------------ | --------------------- |
| `ImportJobRunner.markFailed` | ja (BE-PDF-15) | egal |
| `StaleImportJobCleaner.cleanUpStaleJobs` | nein | nein — Scope dieses Issues |
| `StaleImportJobCleaner.cleanUpIfStale` | nein | nein — Scope dieses Issues |
| `PdfImportService:144` (`TaskRejectedException`) | nein | **ja** |

Der vierte Pfad bleibt **bewusst stehen** (Entscheid des Teams am 2026-09-21). Er ist ein anderer
Fall: Der Job scheitert synchron im Upload-Request, der Nutzer steht in diesem Moment auf der
Import-Seite und das Polling zeigt ihm den Fehlschlag ohnehin sofort. Eine Glocken-Meldung
dafür wäre eine Doppelmeldung für einen Fehler, den er gerade auf dem Bildschirm hat. Die
Begründung steht in der Javadoc des neuen `ImportFailureNotifier`, damit der nächste Leser des
Pfads nicht denselben Befund nochmals erhebt.

## Entscheid: wo Typ und Text liegen (AC1)

Neue Komponente `ImportFailureNotifier` im `transaction`-Modul. Sie hält den Typ
`IMPORT_FAILED`, den Anzeigetext und das `catch (RuntimeException)`; `ImportJobRunner` und
`StaleImportJobCleaner` rufen sie beide.

Verworfen: die Konstante in `ImportJobRunner` lassen und der Cleaner referenziert sie. Das
erfüllt AC1 nur halb — der *Text* und das Fehlerverhalten wären dupliziert, und der Cleaner
hinge am Runner, obwohl er nichts von ihm braucht.

`NOTIFICATION_TYPE_COMPLETED` und `NOTIFICATION_TYPE_DEGRADED` bleiben im Runner: die haben
genau einen Erzeuger und gewönnen durch einen Umzug nichts.

## Betroffene Dateien

| Datei | Änderung |
| ----- | -------- |
| `transaction/ImportFailureNotifier.java` | neu — Typ-Konstante, Text, `notifyFailed(ImportJob)`, swallow + log |
| `transaction/ImportJobRunner.java` | `NOTIFICATION_TYPE_FAILED` + `notifyFailed` raus, Notifier rein, `markFailed` delegiert |
| `transaction/StaleImportJobCleaner.java` | Notifier injizieren, nach `saveAll`/`save` je Job benachrichtigen; Javadoc |
| `transaction/package-info.java` | Kante beschreibt jetzt zwei Erzeuger von `IMPORT_FAILED` |
| `transaction/ImportFailureNotifierTest.java` | neu |
| `transaction/StaleImportJobCleanerTest.java` | Konstruktor + neue Tests |
| `transaction/StaleImportJobCleanerIntegrationTest.java` | echte Notification-Zeile prüfen |
| `transaction/ImportJobRunnerTest.java`, `transaction/PdfImportServiceIntegrationTest.java` | Konstanten-Referenz nachziehen |

## Implementierungsschritte

1. `ImportFailureNotifier` anlegen (`@Component`): ruft
   `create(job.getUserId(), IMPORT_FAILED, job.getId(), "Der Import ist fehlgeschlagen — bitte versuche es erneut.")`,
   umschlossen von `try/catch (RuntimeException)` mit `log.warn` — wortgleich zum heutigen
   `ImportJobRunner.notifyFailed`, damit der Text beim Umzug unverändert bleibt.
2. `ImportJobRunner`: Konstante und private Methode entfernen, `ImportFailureNotifier` in den
   Konstruktor, `markFailed` delegiert. `notificationPort` bleibt für COMPLETED/DEGRADED.
3. `StaleImportJobCleaner`: Notifier in den Konstruktor. In `cleanUpStaleJobs` **nach** `saveAll`
   `stale.forEach(notifier::notifyFailed)` — die Reihenfolge ist die Zusicherung aus AC2/AC3: der
   `FAILED`-Write ist geschrieben, bevor irgendetwas benachrichtigt wird, und ein Fehler bei einem
   Job stoppt die Schleife für die übrigen nicht. In `cleanUpIfStale` analog nach `save`.
4. AC3 braucht keinen neuen Code: `onApplicationReady` hat das `try/catch (RuntimeException)`
   bereits, der Notifier schluckt zusätzlich pro Job. Belegt wird das durch einen Test, nicht
   durch eine Behauptung.
5. `package-info.java` nachziehen; die beiden Testreferenzen auf die umgezogene Konstante.

## Test-Strategie

**`ImportFailureNotifierTest`** (Unit, Mockito)

- erzeugt genau eine Notification mit Typ `IMPORT_FAILED`, `referenceId` = Job-ID, User des Jobs
- `RuntimeException` aus dem Port verlässt `notifyFailed` nicht (AC2)

**`StaleImportJobCleanerTest`** (Unit)

- `cleanUpStaleJobs`: zwei verwaiste Jobs zweier User → je genau eine Notification für den
  richtigen User (AC4); `never()` für einen nicht-verwaisten Job
- Reihenfolge `saveAll` vor Notification via `InOrder`
- Notifier wirft → Job steht trotzdem `FAILED` und `saveAll` ist gelaufen (AC2)
- Notifier wirft im Startpfad → `onApplicationReady` propagiert nicht (AC3)
- `cleanUpIfStale`: verwaister Job → eine Notification; laufender bzw. fertiger Job → keine

**`StaleImportJobCleanerIntegrationTest`** (Postgres + Flyway)

- verwaister Job eines echten Users → in `notifications` genau eine Zeile mit
  `type='IMPORT_FAILED'`, `reference_id` = Job-ID, `user_id` = dieser User. Das ist der Nachweis,
  den der Unit-Test nicht führen kann: dort ist `job.getId()` mangels Persistenz `null`.

## Acceptance Criteria (aus #341)

- [ ] Setzt der `StaleImportJobCleaner` einen Job auf `FAILED` (beide Pfade: `cleanUpStaleJobs`
      und `cleanUpIfStale`), erhält der betroffene User über den `NotificationPort` dieselbe
      Fehlschlags-Benachrichtigung wie bei `ImportJobRunner.markFailed` (Typ `IMPORT_FAILED`,
      `referenceId` = Job-ID) — Text und Typ an einer Stelle gehalten, nicht dupliziert
- [ ] Ein Fehler beim Erzeugen der Benachrichtigung verhindert den `FAILED`-Write nicht
      (`RuntimeException` fangen, loggen, weiter)
- [ ] Der Cleaner beim Start (`onApplicationReady`) darf durch den Notification-Aufruf nicht am
      Hochfahren gehindert werden
- [ ] JUnit-Test: verwaister Job → genau eine `IMPORT_FAILED`-Notification für den richtigen
      User; Notification-Fehler → Job trotzdem `FAILED`
