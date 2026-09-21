# [BE-PDF-15] Notification bei Abschluss eines Import-Jobs — auch wenn die Import-Seite verlassen wurde

- **Issue:** [#337](https://github.com/dfme/budget-buddy/issues/337)
- **Task-ID:** `BE-PDF-15`
- **Branch:** `feature/BE-PDF-15-import-completion-notification`
- **Story:** US-04 — PDF-Upload
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-20

## Ausgangslage

Der Import-Fortschritt ist nur sichtbar, solange die `PdfUpload`-Komponente lebt
(`takeUntilDestroyed`, `frontend/src/app/transactions/pdf-upload.ts:307-309`). Verlässt der Nutzer
die Import-Seite, läuft `ImportJobRunner` im Backend unbeeinflusst weiter
(`backend/src/main/java/com/budgetbuddy/transaction/ImportJobRunner.java`), ohne dass der Nutzer
je erfährt, ob der Import geklappt hat.

Das generische Notification-System (`NotificationPort`/`NotificationService`,
`BE-NOTIF-01`) existiert bereits und wird bisher nur von `RecurringExpenseService.detect`
genutzt (Typ `RECURRING_EXPENSE_DETECTED`). Die Glocke rendert jede Notification generisch über
ihren `message`-Text — ein neuer `type` braucht **kein** Frontend-Änderung, solange kein eigenes
Klickverhalten vorgesehen ist.

## Entscheide (aus Rückfrage vor der Umsetzung)

- **Drei eigene Notification-Typen** statt einem gemeinsamen: `IMPORT_COMPLETED`,
  `IMPORT_DEGRADED`, `IMPORT_FAILED` — konsistent mit dem bestehenden Muster (ein Typ pro
  fachlichem Ereignis) und offen für ein späteres, typbasiertes Klickverhalten.
- **Kein eigenes Klickverhalten.** Klick markiert generisch als gelesen, wie jeder Typ ohne
  Sonderfall in `notification-bell.ts`. Deckt die Kernanforderung des Issues ab; ein Sprungziel
  wäre reiner Komfort und nicht Teil dieses Issues.
- **Abo-Erkennung bleibt unverändert** — sie erzeugt weiterhin ihre eigene, separate Notification
  nach `finishSuccessfully` (zwei fachlich unabhängige Ereignisse, wie bisher).

## Betroffene Dateien

- `backend/src/main/java/com/budgetbuddy/transaction/ImportJobRunner.java` — `NotificationPort`
  als neue Dependency, drei Typ-Konstanten, Notification-Erzeugung an den beiden Endzuständen.
- `backend/src/test/java/com/budgetbuddy/transaction/ImportJobRunnerTest.java` — Mock ergänzen,
  neue Assertions/Tests für Erfolg, Degraded und Fehlschlag.
- `backend/src/test/java/com/budgetbuddy/transaction/ImportJobRunnerTimingTest.java` —
  Konstruktor-Aufruf um den neuen Mock ergänzen (keine Verhaltensänderung, reiner Kompilier-Fix).

Keine neuen Dateien: `NotificationPort`, `NotificationService`, Tabelle `notifications` (V10)
existieren bereits und werden nur um einen vierten Producer erweitert.

## Implementierungsschritte

1. `ImportJobRunner`: Konstruktor um `NotificationPort notificationPort` erweitern (Feld +
   Zuweisung), analog zu `RecurringExpenseService`.
2. Typ-Konstanten definieren (freier String, kein Enum — `Notification`-Javadoc): `IMPORT_COMPLETED`,
   `IMPORT_DEGRADED`, `IMPORT_FAILED`.
3. Erfolgspfad in `categorizeAndPersist`: nach `job.finishSuccessfully(degraded, end);
   importJobRepository.save(job);` eine private Methode `notifyFinished(job, entities.size(),
   degraded)` aufrufen:
   - Typ `IMPORT_COMPLETED` bzw. `IMPORT_DEGRADED` je nach `degraded`.
   - Nachricht mit Transaktionsanzahl; bei `degraded` zusätzlich der Hinweis auf manuelle
     Korrektur, textlich angelehnt an `DEGRADED_HINT` (`pdf-upload.ts:40-42`).
   - `referenceId = job.getId()`.
4. Fehlerpfad in `markFailed`: nach `importJobRepository.save(job)` eine private Methode
   `notifyFailed(job)` aufrufen (Typ `IMPORT_FAILED`, Text angelehnt an `JOB_FAILED_MESSAGE`,
   `pdf-upload.ts:45`).
5. Beide Notification-Aufrufe mit `try { … } catch (RuntimeException e) { log.warn(...) }`
   umschliessen — exakt das Muster von `detectRecurringExpenses`: ein Fehler beim Erzeugen der
   Benachrichtigung darf weder einen erfolgreichen Import auf `FAILED` kippen noch (im
   `Error`-Pfad) die bestehende `addSuppressed`-Logik um den ursprünglichen `Error` sprengen.
6. Kein Frontend-Change — die Notification landet generisch in der Glocke.

## Test-Strategie

Unit-Tests in `ImportJobRunnerTest`:

- Erfolgsfall: `notificationPort.create(USER_ID, IMPORT_COMPLETED, job.getId(), message mit
  Transaktionsanzahl)` wird aufgerufen.
- Degraded-Fall (bestehendes Watchdog-Szenario erweitern): Assertion auf `IMPORT_DEGRADED` mit
  Korrektur-Hinweis im Text.
- Fehlerfall (bestehendes `unexpectedFailure_...`-Szenario erweitern): Assertion auf
  `IMPORT_FAILED`.
- Neuer Test: eine fehlschlagende `notificationPort.create` im Erfolgspfad lässt den Job trotzdem
  `DONE` bleiben (Gegenstück zu `failingRecurringExpenseDetection_leavesTheImportSuccessful`).
- Neuer Test: eine fehlschlagende `notificationPort.create` im Fehlerpfad lässt `markFailed`
  trotzdem den Job auf `FAILED` setzen, ohne den ursprünglichen Fehler zu verschlucken.
- `ImportJobRunnerTimingTest`: nur Konstruktor-Update, keine neuen Assertions (misst Laufzeit,
  nicht Notifications).

## Acceptance Criteria (aus dem Issue)

- [ ] Schliesst ein Import-Job erfolgreich ab, erhält der User über den bestehenden
      `NotificationPort` eine Benachrichtigung mit der Anzahl importierter Transaktionen — auch
      wenn er die Import-Seite bereits verlassen hat.
- [ ] War der Abschluss `degraded`, weist die Nachricht zusätzlich darauf hin, dass ein Teil der
      Kategorien manuell zu korrigieren ist.
- [ ] Schlägt der Job fehl, erhält der User ebenfalls eine Benachrichtigung.
- [ ] Die Benachrichtigung entsteht unabhängig davon, ob der Nutzer noch auf der Import-Seite ist.
- [ ] Ein automatisierter Test deckt mindestens den Erfolgsfall und den Fehlerfall ab.
