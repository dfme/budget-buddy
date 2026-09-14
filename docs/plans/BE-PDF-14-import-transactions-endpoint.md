# [BE-PDF-14] Endpoint: Transaktionen eines Import-Jobs auflisten

- **Issue:** [#291](https://github.com/dfme/budget-buddy/issues/291)
- **Task-ID:** `BE-PDF-14`
- **Branch:** `feature/BE-PDF-14-import-transactions-endpoint`
- **Story:** US-04 — PDF-Upload (Anzeige nach Import) und US-05 — Transaktionen kategorisieren
- **Sprint:** — (auf dem Board kein Sprint gesetzt)
- **Bestätigt am:** 2026-09-12

## Entscheide

### 1. Zuordnung Job → Transaktionen über `userId` + `pdfSha256`

Wie im Issue vorgezeichnet: `ImportJob` und `Transaction` tragen den Hash beide bereits (V05/V02),
es braucht keine neue Spalte und keine Migration.

**Bekannte Grenze, im Javadoc festgehalten.** Nach einem Force-Reimport tragen *beide* Jobs
denselben Hash. Weil der Force-Lauf die alten Zeilen löscht
(`ImportJobRunner` → `TransactionRepository.deleteByUserIdAndPdfSha256`), liefern beide Job-IDs
die Buchungen des **jüngsten** Imports. Das ist in sich konsistent — die alten Zeilen existieren
nicht mehr —, aber es ist keine Historie: Wer die Job-ID des ersten Imports abfragt, bekommt den
Stand nach dem zweiten. Eine echte Job-Zuordnung bräuchte eine `import_job_id`-Spalte auf
`transactions`; das ist bewusst nicht Teil dieses Tasks.

### 2. Der 409 kommt aus einem `@ExceptionHandler`, nicht aus `@ResponseStatus`

Zwei nachgemessene Gründe, keine Stilfrage:

- **`server.error.include-message` ist nirgends gesetzt**, und Spring Boots Default ist `never`.
  Ein `@ResponseStatus(HttpStatus.CONFLICT, reason = "…")` liefert damit einen Body **ohne** die
  Meldung. Der vom AC verlangte «Statushinweis» käme beim Client nie an.
- **MockMvc führt den ERROR-Dispatch nicht aus.** `@ResponseStatus` antwortet über
  `sendError()` und damit über `/error`; `PdfImportControllerIntegrationTest` hält als Grenze
  ausdrücklich fest, dass 408/409 dort grün waren und real als 401 ankamen
  (`PdfImportErrorDispatchIntegrationTest` deckt jene Seite separat ab).

Ein `@ExceptionHandler` im bestehenden `PdfImportExceptionHandler` umgeht beides und folgt dem
Muster, das die drei 400er dort bereits benutzen: echter DTO-Body, unter MockMvc prüfbar.

Body: `ImportNotCompleteResponse(ImportJobStatus status)` → `{"status":"RUNNING"}` bzw.
`{"status":"FAILED"}`. `FAILED` ist eingeschlossen, weil das AC `status != DONE` sagt und ein
gescheiterter Job nachweislich nichts persistiert hat (`ImportJobRunnerTest`:
`verify(repository, never()).saveAll(any())`) — eine leere Liste wäre dort die irreführendere
Antwort.

### 3. Die Regel «`category == null` → `Sonstiges`» bekommt eine einzige Quelle

AC 4 verlangt ausdrücklich dasselbe Kategorie-Format wie `GET /api/transactions`. Diese Auflösung
steckt heute in `TransactionListService.labelOf` (private), während `TransactionResponse.from` den
Rohwert durchreicht. Sie wandert als `TransactionResponse.fromResolvingCategory(tx)` ins DTO;
`TransactionListService.toResponse` zeigt darauf. Verhalten unverändert — aber AC 4 hat danach
einen Beleg statt einer Kopie, die auseinanderlaufen kann.

`TransactionResponse.from` bleibt daneben bestehen: Auf den Schreibpfaden
(`TransactionCategoryService`, `TransactionDirectionService`) ist `category` nie `null`, und eine
stille Auflösung dort verschöbe die Bedeutung des Rückgabewerts.

### 4. Keine Paginierung

`GET /api/transactions` paginiert wegen US-13. Hier fordert kein AC es, und der Import-Screen
zeigt bewusst genau das, was gerade importiert wurde — eine Seitengrenze mitten im frisch
Importierten wäre für den Nutzer eine andere Frage als «zeig mir Monat X». Die Menge ist durch ein
einzelnes PDF begrenzt (10-MB-Limit in `application.properties`; die grösste Fixture im Repo trägt
240 Buchungen). Als bewusste Grenze im Javadoc festgehalten.

## Betroffene Dateien

| Datei | Art |
| ----- | --- |
| `transaction/PdfImportController.java` | ändern — `GET /{jobId}/transactions` mit OpenAPI-Annotation |
| `transaction/PdfImportService.java` | ändern — `listTransactions(userId, jobId)` |
| `transaction/TransactionRepository.java` | ändern — `findByUserIdAndPdfSha256OrderByBuchungsdatumDescIdDesc` |
| `transaction/ImportJobNotCompleteException.java` | neu |
| `transaction/dto/ImportNotCompleteResponse.java` | neu |
| `transaction/PdfImportExceptionHandler.java` | ändern — Mapping auf 409 |
| `transaction/dto/TransactionResponse.java` | ändern — `fromResolvingCategory` |
| `transaction/TransactionListService.java` | ändern — delegiert an `fromResolvingCategory` |
| `PdfImportControllerIntegrationTest`, `PdfImportServiceTest`, `PdfImportOpenApiTest` | ändern |
| `docs/plans/BE-PDF-14-import-transactions-endpoint.md`, `docs/plans/README.md` | neu / ändern |

Kein Frontend — FE-PDF-04 ist ein eigener Task.

## Implementierungsschritte

1. Repository-Query `findByUserIdAndPdfSha256OrderByBuchungsdatumDescIdDesc` — dieselbe Sortierung
   wie die Monatsliste (Datum absteigend, ID als stabiler Zweitschlüssel).
2. `ImportJobNotCompleteException(ImportJobStatus)` und `ImportNotCompleteResponse`.
3. `PdfImportService.listTransactions`: Job über `findByIdAndUserId` → sonst
   `ImportJobNotFoundException`; `status != DONE` → `ImportJobNotCompleteException`; Transaktionen
   laden und über die aufgelöste Kategorie mappen. `@Transactional(readOnly = true)`.
4. `TransactionResponse.fromResolvingCategory` einziehen, `TransactionListService` umstellen.
5. Controller-Methode mit `@Operation`/`@ApiResponses` (200/401/404/409), Handler-Methode für 409.
6. Tests, `./mvnw verify` grün.

## Test-Strategie

- **`PdfImportServiceTest`** (Unit): unbekannter Job → `ImportJobNotFoundException`; `RUNNING` und
  `FAILED` → `ImportJobNotCompleteException` mit dem jeweiligen Status; `DONE` → Liste;
  `category = null` kommt als `Sonstiges` heraus.
- **`PdfImportControllerIntegrationTest`** (MockMvc + Postgres): Happy Path nach echtem Upload
  (200, Anzahl und Felder); fremder `jobId` → 404 samt Gegenprobe, dass der zweite User die
  Buchungen des ersten nicht sieht; unbekannter `jobId` → 404; laufender Job → 409 mit
  `status: RUNNING`; ohne JWT → 401; DONE-Job ohne Buchungen → 200 mit leerer Liste.
- **`PdfImportOpenApiTest`**: `paths['/api/import/{jobId}/transactions'].get` existiert, mit
  `summary`, `jobId`-Parameter und den Responses 404 und 409 (AC 5).

## Acceptance Criteria (aus dem Issue)

- [ ] Neuer Endpoint `GET /api/import/{jobId}/transactions` liefert die Transaktionen des Imports
      (Datum, Buchungstext, Betrag, Kategorie) für den eingeloggten User
- [ ] Zugriff ist auf den eigenen User beschränkt — ein `jobId` eines fremden Users oder ein
      unbekannter `jobId` liefert 404, analog zu `GET /api/import/{jobId}/status`
- [ ] Solange der Job noch läuft (`status != DONE`), liefert der Endpoint kein unvollständiges
      Ergebnis unkommentiert aus — Verhalten ist dokumentiert
- [ ] Response nutzt dasselbe Kategorie-Feld/Format wie `GET /api/transactions`
- [ ] Endpoint ist in Swagger UI sichtbar (OpenAPI-Annotation vorhanden)
