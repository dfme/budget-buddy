# [BE-PDF-03] POST /import/pdf Endpoint

- **Issue:** #18
- **Task-ID:** BE-PDF-03
- **Depends on:** #17 (BE-PDF-02, gemergt)
- **Branch:** `feature/BE-PDF-03-pdf-upload-endpoint`

## Entscheide

- **Oversize-Status:** 413 Payload Too Large (RFC-korrekt; trennt Grössenfehler von
  Format-/Passwortfehlern 400). Vom Team bestätigt.
- **Response-Body 200:** `{ "count": <n> }` — nur die Anzahl, kein Hash nach aussen
  (Issue spezifiziert `{count}`).
- **409/408 ohne eigenen Handler:** `DuplicatePdfImportException` und
  `PdfImportTimeoutException` tragen bereits `@ResponseStatus` → automatisches Mapping.

## Kontext

`PdfImportService` (BE-PDF-02) ist fertig und wirft alle nötigen Exceptions. Es fehlen nur
der Multipart-Endpoint, das 400-Mapping (Parse/Passwort), das 413-Mapping (Oversize) und die
serverseitige 10-MB-Durchsetzung.

## Betroffene / neue Files

Neu:
- `transaction/PdfImportController.java` — `POST /import/pdf`, `@RequestParam("file") MultipartFile`,
  `@AuthenticationPrincipal Long userId`, OpenAPI mit 200/400/401/408/409/413.
- `transaction/dto/ImportResponse.java` — Record `{ int count }`.
- `transaction/PdfImportExceptionHandler.java` — `@RestControllerAdvice(assignableTypes = PdfImportController.class)`:
  `PdfParseException` (+ Subtypen) → 400, `PasswordProtectedPdfException` → 400,
  `MaxUploadSizeExceededException` → 413.
- `transaction/PdfImportControllerIntegrationTest.java`
- `transaction/PdfImportOversizeIntegrationTest.java`

Geändert:
- `application.properties` — `max-file-size=10MB`, `max-request-size=11MB` (Puffer für
  Multipart-Overhead, damit eine Datei an der 10-MB-Grenze nicht fälschlich mit 413 abgelehnt
  wird), `resolve-lazily=true` (Handler beim Werfen der MaxUploadSizeExceededException bereits
  aufgelöst → 413-Mapping statt 500).

## Implementierungsschritte

1. `ImportResponse`-DTO.
2. `PdfImportController` mit voller OpenAPI-Annotation.
3. `PdfImportExceptionHandler` (400/413, kein Body — Info-Leak-Schutz).
4. Multipart-Limits in `application.properties`.
5. Tests.

## Test-Strategie (JUnit, @SpringBootTest + MockMvc)

- 200 Happy Path — UBS-Fixture (28 Tx), Port gemockt → `count: 28`.
- 409 — dasselbe PDF zweimal → Conflict.
- 400 Format — Nicht-PDF-Bytes → Bad Request.
- 400 Passwort — AES-verschlüsseltes PDF (PDFBox-generiert) → Bad Request.
- 408 Timeout — `budgetbuddy.import.timeout-seconds=0` → Request Timeout.
- 401 — ohne JWT-Cookie → Unauthorized.
- 413 Oversize — eigener Context mit 1KB-Limit, Fixture > 1KB → Payload Too Large.

## Acceptance Criteria (aus Issue)

- 200: erfolgreicher Import mit Anzahl Transaktionen
- 409: Duplikat-PDF wird erkannt und gemeldet
- 400: passwortgeschütztes oder ungültiges PDF
- 408: Timeout nach 30s
- Endpoint in Swagger UI mit allen Response-Codes dokumentiert
- File-Size-Limit 10 MB wird serverseitig durchgesetzt
