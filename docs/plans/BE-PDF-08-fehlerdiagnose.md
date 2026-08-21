# [BE-PDF-08] Import eines Kontoauszugs schlägt zuverlässig fehl

- **Issue:** [#173](https://github.com/dfme/budget-buddy/issues/173)
- **Task-ID:** `BE-PDF-08`
- **Branch:** `fix/BE-PDF-08-fehlerdiagnose`
- **Story:** — (kein `us-*`-Label; Bug im Bereich Backend/PDF-Import)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-19

## Ausgangslage

Das Issue ist laut Kommentar des Repo-Owners explizit als **Untersuchungsbudget** gescoped (2 SP,
nicht für den Fix): Phasendauer-Logs aus #157 auswerten, synthetisches Test-PDF bauen und messen,
Circuit-Breaker-Verhalten prüfen, Timeout-Grenzen entlang der Kette abklopfen. Falls sich der
Timeout-Verdacht bestätigt und `@Async` + `ImportJob` + Status-Polling nötig wird, gehört das in
eine eigene Folge-Story (~8 SP) — nicht in dieses Ticket. Ist der Fix klein, darf er direkt hier
mitgehen.

## Root-Cause-Analyse

**Timeout-These stark entkräftet.** Der Melder bestätigt: der Fehler tritt **sofort** auf (<1s).
Das ist strukturell unvereinbar mit der Timeout-Kette, die real erst nach ~30–50s greifen würde:

- `PdfImportService.java:106` — kooperatives 30s-Budget (`budgetbuddy.import.timeout-seconds`),
  nur zwischen Transaktionen geprüft, nie präventiv während eines laufenden Claude-Calls.
- `AnthropicConfig.java:31,41` — 10s SDK-Timeout × 1 Retry = bis zu ~20s pro Claude-Call.
- `PdfImportService.java:33-37` (eigene Javadoc) dokumentiert den realen Worst-Case bereits selbst
  als **~50s**, nicht 30s — ein akzeptierter, bekannter Gap.
- `ClaudeCategorizationService.java:91,98-110,213-224` (BE-CAT-02 Circuit Breaker) — öffnet erst
  *nach* 3 gescheiterten Calls; die ersten 3 zahlen jeweils den vollen Timeout, bevor er greift.

Bei einer sofortigen Ablehnung (<1s) sind diese Pfade nicht erreicht — der Fehler passiert *vor*
jedem Claude-Call, direkt beim PDF-Parsen.

**Wahrscheinlichste Ursache: einer der drei synchronen, sofortigen Ablehnungspfade**, alle
deterministisch bei jedem Retry derselben Datei (passt zu "schlägt zuverlässig/jedes Mal fehl"):

1. `UnsupportedStatementFormatException` (`SwissBankStatementParser.java:193`) — Text vorhanden,
   aber kein bekanntes Layout erkannt (nur Viseca/PostFinance/UBS/generischer
   Raiffeisen-Fallback werden unterstützt).
2. `MissingTextLayerException` (`SwissBankStatementParser.java:173`) — gescanntes PDF ohne
   extrahierbaren Text.
3. `PasswordProtectedPdfException` — verschlüsseltes PDF.

**Ausgeschlossen: `MaxUploadSizeExceededException` → 413** (Datei über dem 10-MB-Limit). Der
Melder hat die deployte App im Browser benutzt — auf diesem Weg ist der Fall nicht erzeugbar, weil
das Frontend die Dateigrösse **vor** dem Upload prüft und gar keinen Request absetzt:

- `pdf-upload.ts:133` — `if (file.size > MAX_PDF_BYTES)` setzt die lokale Meldung "Maximale
  Dateigrösse: 10 MB" und kehrt zurück; `upload()` wird nie erreicht. `selectFile` ist der einzige
  Pfad dorthin (`confirmDuplicateImport` reicht die bereits geprüfte Datei weiter).
- Belegt durch den bestehenden Test `pdf-upload.spec.ts:131` — "rejects a file larger than 10 MB
  without calling the backend".
- Die Grenzen sind byte-identisch: `MAX_PDF_BYTES = 10 * 1024 * 1024` = 10485760, und
  `spring.servlet.multipart.max-file-size=10MB` parst Spring ebenfalls als 10485760. Beide
  vergleichen strikt `>`, es bleibt also nicht einmal ein Off-by-one-Fenster.
  `max-request-size=11MB` greift bei einer ≤ 10-MiB-Datei plus Multipart-Overhead ebenfalls nicht.

Ohne das Original-PDF (aus Datenschutzgründen nicht verfügbar) oder die exakte
Fehlermeldung/den Status, den der Melder client-seitig sah, lässt sich zwischen diesen dreien
nicht abschliessend unterscheiden. Das bleibt eine offene Frage — siehe Kommentar auf #173.

**Konkreter, unabhängig vom exakten Fall relevanter Bug gefunden:**
`PdfImportExceptionHandler.handlePdfParse` (Zeile 34-39) fängt die Basisklasse
`PdfParseException` und mappt **beide** Subtypen — `UnsupportedStatementFormatException` **und**
`MissingTextLayerException` — auf denselben `Reason.UNSUPPORTED_FORMAT`. Das widerspricht
`MissingTextLayerException`s eigener Javadoc (Zeile 7-9), die explizit eine andere, hilfreichere
Nutzermeldung vorsieht ("bitte aus dem E-Banking herunterladen statt scannen"). Zusätzlich hat
der 413-Fall (Datei zu gross) im Frontend **keine eigene Nutzermeldung** — er fällt auf den
generischen Fallback-Text zurück (`pdf-upload.ts:207`).

**Empfehlung:** Die im Sprint-5-Vorschlag befürchtete `@Async`+`ImportJob`-Folge-Story ist nach
dieser Evidenz voraussichtlich **nicht nötig** — dokumentiert als Befund im Issue-Kommentar,
nicht als eigenes Ticket angelegt (spart ~8 SP unnötige Planung).

## Entscheidung

Dieser Fix bleibt bewusst klein und diagnosebezogen — keine Architekturänderung:

1. Backend: `MissingTextLayerException` bekommt einen eigenen `Reason.MISSING_TEXT_LAYER` statt
   im generischen `UNSUPPORTED_FORMAT` unterzugehen.
2. Frontend: neue Nutzermeldung für `MISSING_TEXT_LAYER`, plus eine dedizierte Meldung für 413
   (bisher generischer Fallback).
3. Empirischer Timing-Test (aus dem Issue explizit gewünscht): belegt die im Javadoc behauptete
   Sequenzialität der Kategorisierungs-Loop und den kooperativen (nicht präventiven)
   Deadline-Check messbar, statt nur in der Doku behauptet.
4. Root-Cause-Befund als Kommentar auf #173, **Issue bleibt offen** (kein `Closes #173` im PR) —
   die exakte Ursache unter den drei Kandidaten ist nicht zu 100% bestätigt, das ist eine
   Entscheidung des Melders/Teams, kein Automatismus.

## Betroffene Files

**Geändert:**
- `backend/src/main/java/com/budgetbuddy/transaction/dto/ImportErrorResponse.java`
- `backend/src/main/java/com/budgetbuddy/transaction/PdfImportExceptionHandler.java`
- `backend/src/test/java/com/budgetbuddy/transaction/PdfImportControllerIntegrationTest.java`
- `frontend/src/app/transactions/import-error.model.ts`
- `frontend/src/app/transactions/pdf-upload.ts`
- `frontend/src/app/transactions/pdf-upload.spec.ts`

**Neu:**
- `backend/src/test/java/com/budgetbuddy/transaction/PdfImportServiceTimingTest.java`

## Implementierungsschritte

1. `ImportErrorResponse.Reason` um `MISSING_TEXT_LAYER` erweitern (Javadoc analog zu den
   bestehenden zwei Werten).
2. `PdfImportExceptionHandler`: neuen `@ExceptionHandler(MissingTextLayerException.class)` vor/
   neben dem bestehenden `PdfParseException`-Handler ergänzen (Spring wählt automatisch den
   spezifischeren Typ, Reihenfolge der Methoden ist irrelevant). Javadoc der Klasse entsprechend
   nachziehen (aktuell beschreibt sie noch das alte, gemeinsame Mapping).
3. Backend-Test: `PdfImportControllerIntegrationTest` — neuer Testfall
   `pdfWithoutTextLayerReturns400WithMissingTextLayerReason` mit einer leeren PDF-Seite (Muster:
   `pdfWithLines(List.of())` aus `SwissBankStatementParserTest`), assertet
   `$.reason == "MISSING_TEXT_LAYER"`.
4. `import-error.model.ts`: `reason`-Union um `'MISSING_TEXT_LAYER'` erweitern.
5. `pdf-upload.ts` `importErrorMessage` (Zeile 192-208): Ternary zu if/else-Kette umbauen —
   `PASSWORD_PROTECTED` → bestehende Meldung, `MISSING_TEXT_LAYER` → neue Meldung ("Das PDF
   enthält keinen Text (vermutlich ein Scan). Bitte lade den Original-Kontoauszug aus dem
   E-Banking herunter, statt ihn zu scannen."), sonst (inkl. `UNSUPPORTED_FORMAT` und fehlender
   Reason) → bestehende generische Format-Meldung. Zusätzlich: dedizierte 413-Meldung ("Das PDF
   ist zu gross (max. 10 MB). Bitte lade eine kleinere Datei hoch.") statt Fallback auf den
   generischen Text.
6. `pdf-upload.spec.ts`: neuer Testfall für `MISSING_TEXT_LAYER` (Muster: bestehender
   `UNSUPPORTED_FORMAT`-Testfall, Zeile 163-174) und neuer Testfall für 413.
7. Neue Testklasse `PdfImportServiceTimingTest` (echter `Clock.systemUTC()`, kein Mock — im
   Unterschied zu `PdfImportServiceTest`, das deterministische Clock-Mocks für die
   Deadline-Logik nutzt):
   - Test A: N=5 unbekannte Transaktionen, gemockter `CategorizationPort` mit künstlicher
     Verzögerung (`Thread.sleep`, 100ms) pro Call, grosszügiger Timeout (30s). Misst reale
     Wall-Clock-Dauer des `importPdf`-Aufrufs, assertet `Duration >= N × 100ms` — belegt
     empirisch die sequenzielle, nicht gebündelte Kategorisierungs-Loop.
   - Test B: N=6 unbekannte Transaktionen, je 400ms Verzögerung, `timeoutSeconds=1`. Assertet:
     `PdfImportTimeoutException` wird geworfen, **und** `categorizationPort` wurde weniger als
     N-mal aufgerufen (`verify(..., atMost(N - 1))`) — belegt den kooperativen, nicht
     präventiven Deadline-Check ohne eine exakte Call-Anzahl zu fixieren (vermeidet Flakiness
     durch Thread-Scheduling-Jitter).
8. Kommentar auf #173 mit der Root-Cause-Einordnung aus diesem Dokument, inkl. Bitte um die
   exakte Fehlermeldung/den Status für die endgültige Bestätigung.

## Akzeptanzkriterien (aus dem Issue abgeleitet, da keine formalen ACs vorhanden)

Das Issue selbst listet keine Akzeptanzkriterien-Sektion, sondern offene Untersuchungsfragen.
Für dieses Ticket gelten die vier "Zu untersuchen"-Punkte als abgearbeitet, wenn:

- [ ] Die Phasendauer-Instrumentierung aus #157 ist ausgewertet/eingeordnet (siehe Root-Cause-
      Analyse oben — Auswertung der echten Render-Logs war ohne Zugriff nicht möglich, daher
      codebasierte Einordnung plus offene Frage an den Melder).
- [ ] Ein synthetischer Timing-Test existiert und belegt die Sequenzialität/den kooperativen
      Deadline-Check messbar (`PdfImportServiceTimingTest`).
- [ ] Der Circuit-Breaker-Mechanismus (BE-CAT-02) ist eingeordnet (siehe oben: schützt nicht vor
      den ersten 3 Fehlern).
- [ ] Timeout-Grenzen entlang der Kette sind dokumentiert (App 30s/~50s worst case; Render/
      Browser ohne konfigurierbares Limit im Repo).
- [ ] Ergebnis (belegte Ursache oder eingegrenzte Kandidaten) ist als Kommentar auf #173
      festgehalten.
