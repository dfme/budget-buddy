# [FE-NOTIF-05] Klick auf Import-Benachrichtigung führt zur Import-Seite mit der Übersicht des Imports

- **Issue:** [#348](https://github.com/dfme/budget-buddy/issues/348)
- **Task-ID:** `FE-NOTIF-05`
- **Branch:** `feature/FE-NOTIF-05-import-notification-link`
- **Story:** US-04 — Kontoauszug als PDF hochladen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-21

## Ausgangslage

Seit BE-PDF-15 (#337, gemergt als #340) meldet die Glocke den Abschluss eines Import-Jobs mit
den Typen `IMPORT_COMPLETED`, `IMPORT_DEGRADED`, `IMPORT_FAILED` und der Job-ID als
`referenceId` (`ImportJobRunner.java:69-75`, `notifyFinished`/`notifyFailed`). Die Glocke
interpretiert bisher nur `RECURRING_EXPENSE_DETECTED` (`notification-bell.ts:114-116`); ein
Klick auf eine Import-Benachrichtigung markiert sie lediglich als gelesen.

Die Import-Seite kann die Übersicht bereits zeigen: `trackJob` pollt
`GET /api/import/{jobId}/status` und lädt bei `DONE` die Buchungen über
`GET /api/import/{jobId}/transactions` nach (`pdf-upload.ts:335-393`). Beide Endpoints sind über
`findByIdAndUserId` mandantengeschützt und antworten für fremde oder unbekannte Jobs mit 404
(`PdfImportController.java:109-129`, `131-161`). Es fehlt nur der Einstieg von aussen: Die Job-ID
lebt im Component-State, `/import` startet leer. Kein Backend-Change.

Für Query-Parameter gibt es ein etabliertes Muster: `?month=` in `CategoryOverview` und
`Dashboard` (`queryParamMap`-Subscription → `syncFromUrl`, Schreiben per
`router.navigate([], {relativeTo, queryParams, queryParamsHandling: 'merge'})`,
`category-overview.ts:262-345`).

## Befunde aus der Analyse, die über die ACs hinausgehen

- ~~**Leere Liste nach Force-Replace** («Zu untersuchen» #1) ist real: `ImportJobRunner.java:268`
  löscht per `deleteByUserIdAndPdfSha256`, der alte Job bleibt `DONE`. Es ist der **einzige** Weg,
  auf dem Buchungen verschwinden — es gibt keinen Delete-Endpoint für Transaktionen
  (`grep @DeleteMapping` trifft nur `UserController` und `FixedCostController`). Der Hinweis
  kann deshalb präzise sein.~~
  **Korrektur nach Review (#349, danielwagner990):** Die Prämisse aus dem Issue-Text war falsch,
  und ich habe sie nur am Lösch-Pfad geprüft, nicht am Lese-Pfad.
  `PdfImportService.listTransactions` löst die Buchungen über `userId + pdfSha256` auf, nicht
  über die Job-ID; das Javadoc von `findByUserIdAndPdfSha256OrderByBuchungsdatumDescIdDesc`
  sagt es explizit: «liefern dann beide Job-IDs den Bestand des jüngsten Imports». Nach einem
  Force-Reimport zeigt der Deep-Link auf die alte Benachrichtigung also die **neuen** Zeilen,
  nicht nichts. Der einzige Weg, auf dem Buchungen verschwinden, ist die Kontolöschung — und die
  nimmt den Job mit. Die Wache `transactions.length === 0` bleibt als Defensiv-Zweig für die
  Invariante; ihr Satz behauptet keine Ursache mehr (`LIST_EMPTY_MESSAGE`).
- **Nullfall-Invariante:** Ein Job mit `total = 0` wird als `DONE` persistiert
  (`PdfImportService.java:126-133`). Ein Resume über `?job=` liefe bisher in
  `loadImportedTransactions`, schriebe `[]` ins Signal und das Template renderte eine leere
  `<ul>` — Bruch der dokumentierten Invariante «eine leere Liste ist es nie»
  (`pdf-upload.ts:181-183`). Der `DONE`-Zweig lädt deshalb nur bei `total > 0` nach.
- Breite Suche nach veraltenden Aussagen: nur `notification.model.ts:4-5` («der einzige `type`,
  den das Frontend bisher interpretiert») — liegt in der ohnehin zu ändernden Datei.

## Entscheide

1. **Playwright-Test hier, nicht im E2E-Task.** Präzedenz `pdf-import.spec.ts:8-13`: FE-PDF-04
   hat den Happy Path im Feature-PR erweitert. Ein Fall: `importFixture` (INFRA-45) →
   `/dashboard` → Glocke → Klick «Import abgeschlossen» → URL `/import?job=N` → 5 Zeilen mit
   Dropdown.
2. **Leere Liste bei `total > 0`:** ein Satz als `variant="info"` unter der Erfolgsmeldung. Die
   Liste selbst bleibt `null`, die Invariante hält. *Ursprünglich* als Force-Replace-Fall mit
   dem Satz «… durch einen erneuten Import desselben Kontoauszugs ersetzt» geplant — nach dem
   Review-Befund oben (der Fall ist so nicht erreichbar) neutral gefasst: «Zu diesem Import sind
   keine Buchungen mehr vorhanden.»
3. **Ungültiges `?job=`** (`abc`, `0`, `-1`, `1.5`): ignorieren **und** per `replaceUrl` aus der
   Adresse entfernen — wie `CategoryOverview.syncFromUrl` bei kaputtem `?month=`
   (`category-overview.ts:326-335`).
4. **404 des Status-Polls:** «Dieser Import ist nicht mehr abrufbar.» als Fehler-Notice,
   `uploading` auf `false`, Dropzone frei. Gilt für jeden 404 des Polls, nicht nur den
   Resume-Pfad — die Aussage stimmt in beiden.
5. **Poll-Wechsel:** Wer bei laufendem Poll einen anderen Job öffnet (Glocke während eines
   Imports, Browser-Zurück), bricht den alten Poll ab — `Subscription` halten, vor `trackJob`
   unsubscriben. Sonst schrieben zwei Polls in dieselben Signals.
6. **URL-Echo:** Nach dem Upload schreibt die Komponente `?job=<neu>`; die eigene
   `queryParamMap`-Subscription sieht das und überspringt es (`trackedJobId`-Wache, analog zur
   Gleichheits-Wache in `CategoryOverview`). `?job` wird bei einer client-seitig abgelehnten
   Datei **nicht** gelöscht — ein Reload zeigt dann den letzten erfolgreichen Import.
   **Ergänzt nach Review (#349):** Eine parameterlose Adresse (Sidebar-Link, Browser-Zurück)
   löst die Wache (`trackedJobId = null`), sonst schluckte sie den nächsten Klick auf dieselbe
   Benachrichtigung, obwohl die URL sich dann echt ändert. Die Seite selbst bleibt dabei stehen.
7. **`reloadNotifications()` im `DONE`-Zweig bleibt unbedingt.** Auf dem Resume-Pfad ist der
   Call bei einem bereits fertigen Job redundant (die Glocke lud bei `NavigationEnd`), bei einem
   per F5 wiederaufgenommenen `RUNNING`-Job aber nötig. Ein GET ist billiger als die
   Fallunterscheidung.
8. **`referenceId: null` auf einem Import-Typ** (im Backend nicht vorgesehen): Navigation nach
   `/import` ohne Parameter — die Seite ist trotzdem das sinnvolle Ziel der Meldung.
9. **Bekannte Grenze:** Ein Klick auf die Benachrichtigung desselben Jobs, der bereits in der URL
   steht, ist für den Router eine identische URL und löst nichts aus — auch wenn die Seite
   inzwischen etwas anderes zeigt (z. B. nach einer abgelehnten Datei). Unwahrscheinlich, nicht
   behandelt. Seit der Ergänzung in Entscheid 6 ist das wirklich nur noch der Fall der
   identischen URL.

## Betroffene Dateien

Ändern:

- `frontend/src/app/notifications/notification.model.ts` — Konstanten `IMPORT_COMPLETED`,
  `IMPORT_DEGRADED`, `IMPORT_FAILED`; Doku nachziehen
- `frontend/src/app/notifications/notification-bell.ts` — `select()`: bei den drei Import-Typen
  `close()` + `navigate(['/import'], {queryParams: {job: referenceId}})`, Gelesen-Call parallel
- `frontend/src/app/transactions/pdf-upload.ts` — `ActivatedRoute`/`Router`, `queryParamMap` →
  `syncFromUrl` → `openJob(jobId)` über `trackJob`; URL nach Upload schreiben; 404-Meldung;
  `total > 0`-Guard; Poll-Subscription; Ersetzt-Hinweis
- `frontend/src/app/transactions/pdf-upload.html` — Notice für die ersetzte Liste
- `frontend/src/app/notifications/notification-bell.spec.ts`
- `frontend/src/app/transactions/pdf-upload.spec.ts` — `provideRouter` + `provideLocationMocks`
  im `beforeEach` (wie `category-overview.spec.ts:156-157`), neuer `describe`
- `e2e/tests/pdf-import.spec.ts` — Playwright-Fall
- `e2e/tests/recurring-expenses.spec.ts` — lokaler `bell()`-Helfer nach `e2e/support/`
- `docs/plans/README.md` — Indexzeile

Neu:

- `docs/plans/FE-NOTIF-05-import-notification-link.md` (diese Datei)
- `e2e/support/notifications.ts` — `bell(page)`

## Implementierungsschritte

1. Konstanten + Doku in `notification.model.ts`.
2. `NotificationBell.select()` erweitern; Bell-Tests (drei Typen, `referenceId: null`, gelesene
   Import-Benachrichtigung navigiert ohne Read-Call).
3. `PdfUpload`: Router/Route injizieren, `syncFromUrl`, `openJob`, `trackedJobId`,
   `pollSubscription`, 404-Meldung, `total > 0`-Guard, URL-Schreiben nach Upload,
   Ersetzt-Hinweis + Template.
4. `pdf-upload.spec.ts`: Router-Provider, `describe('deep link via ?job')`.
5. E2E: `bell()`-Helfer extrahieren, Fall in `pdf-import.spec.ts`.
6. `ng build`, `npm test`, Lint; E2E lokal, sofern Postgres/Backend-Jar verfügbar.

## Test-Strategie

Vitest/TestBed:

- Bell: `IMPORT_COMPLETED`/`IMPORT_DEGRADED`/`IMPORT_FAILED` → Dropdown zu,
  `router.url === '/import?job=42'`, `POST …/read` parallel; `referenceId: null` → `/import`;
  gelesene Import-Benachrichtigung → navigiert, kein Read-Call.
- PdfUpload via `?job=7`: `DONE` → Erfolgsmeldung + Liste + Dropdown, kein Upload-Request;
  `DONE` degraded → Hinweis; `FAILED` → bestehende Fehlermeldung, Dropzone frei, kein
  Transactions-Request; 404 → «nicht mehr abrufbar», Dropzone frei; ungültig (`abc`, `0`, `-1`)
  → kein Status-Request, URL bereinigt; Upload → URL trägt `job=7` und löst **keinen** zweiten
  Poll aus; `DONE` mit `total: 0` → kein Transactions-Request; leere Liste bei `total > 0` →
  Ersetzt-Hinweis, keine `<ul>`; Wechsel auf zweiten Job bei laufendem Poll → alter Poll stumm.

Playwright: ein Happy Path (Entscheid 1).

## Acceptance Criteria (aus dem Issue)

- [ ] Ein Klick auf eine Benachrichtigung vom Typ `IMPORT_COMPLETED` oder `IMPORT_DEGRADED`
      schliesst das Dropdown und führt nach `/import?job=<referenceId>`; die Import-Seite zeigt
      dort die Erfolgsmeldung (Anzahl, bei `IMPORT_DEGRADED` mit dem bestehenden Hinweis auf die
      manuelle Korrektur) und die Übersicht der Buchungen dieses Imports — inklusive
      Kategorie-Dropdown, wie nach einem frischen Upload (FE-PDF-04)
- [ ] Ein Klick auf `IMPORT_FAILED` führt ebenfalls nach `/import?job=<referenceId>`; die Seite
      zeigt die bestehende Fehlermeldung des Job-Fehlschlags, die Dropzone bleibt für einen neuen
      Versuch nutzbar
- [ ] Antwortet der Status-Endpoint für den übergebenen Job mit 404 (unbekannt oder fremder
      User), zeigt die Seite eine verständliche Meldung («Dieser Import ist nicht mehr abrufbar»
      o. ä.) statt eines generischen Fehlers; die Dropzone bleibt nutzbar. Die Mandantentrennung
      bleibt Sache des Backends — das Frontend filtert nichts
- [ ] Ein ungültiger Wert in `?job=` (keine positive Ganzzahl) wird ignoriert; die Seite startet
      wie ohne Parameter
- [ ] Nach einem neuen Upload auf der Seite trägt die URL den neuen Job (`?job=<neu>`), damit ein
      Reload dieselbe Übersicht zeigt, die gerade sichtbar ist
- [ ] Die Benachrichtigung wird beim Klick weiterhin als gelesen markiert — parallel zur
      Navigation, wie beim Abo-Sprung (FE-REC-01)
- [ ] Tests (Vitest/TestBed): Glocke navigiert bei den drei Typen mit Query-Parameter;
      `PdfUpload` zeigt via `?job` die Übersicht (`DONE`), die Fehlermeldung (`FAILED`) und die
      404-Meldung
