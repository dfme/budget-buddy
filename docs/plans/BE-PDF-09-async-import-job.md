# [BE-PDF-09] PDF-Import läuft in Produktion ins 30s-Zeitbudget und verwirft den gesamten Import

- **Issue:** [#192](https://github.com/dfme/budget-buddy/issues/192)
- **Task-ID:** `BE-PDF-09`
- **Branch:** `fix/BE-PDF-09-async-import-job`
- **Story:** US-04 — Kontoauszug als PDF hochladen
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-22

## Ausgangslage

Der synchrone PDF-Import läuft in Produktion reproduzierbar ins 30-Sekunden-Zeitbudget und
verwirft dabei den **gesamten** Import — der einzige Schreibzugriff ist der abschliessende
Commit. Zwei Abbrüche innerhalb einer Stunde beim selben Auszug mit 108 Transaktionen.

Die Root-Cause-Analyse in den Issue-Kommentaren ist eindeutig: der Engpass sind ~41
**sequentielle** Claude-Calls à ~1.14 s, nicht die Dauer eines einzelnen. Der CPU-gebundene
Parse-Anteil beträgt ~1.95 s von 30. Der Flow verhält sich wie entworfen; der Entwurf trägt
einen 108-Zeilen-Auszug in 30 s nicht.

Beleg im Code: die Kategorisierungsschleife in `PdfImportService.java:144-167` ruft
`categorizationPort.categorize()` genau einmal pro Transaktion auf. `PdfImportServiceTimingTest`
weist diese Sequenzialität bereits empirisch nach.

## Entscheide

Der Plan geht über die im Issue vorgeschlagene Sofortmassnahme hinaus und geht den
strukturellen Weg, den CLAUDE.md unter „Backend: Import Flow" bereits als Upgrade-Pfad
vorzeichnet. Auslöser war der Wunsch nach einer **Fortschrittsanzeige im UI**: Fortschritt kann
nur der Server melden, also kann der Upload kein blockierender Request mehr sein. Damit
verschwindet das Zeitbudget als Fehlerbild vollständig, statt nur gemildert zu werden.

| Entscheid | Wahl | Begründung |
| --- | --- | --- |
| Kategorisierung | Option A: ~20 Transaktionen pro Claude-Call via Structured Output | Der Fixkostenanteil pro Request dominiert (~1.1 s bei ~5 generierten Tokens). Prompt kürzen bringt nichts, ein schnelleres Modell als `claude-haiku-4-5` gibt es nicht. |
| Import-Flow | Parse synchron, Kategorisierung `@Async` + `ImportJob` + Status-Polling | Parse dauert ~2 s, Kategorisierung ~28 s. Bleibt der Parse im Request, behalten 400/`reason`, 409 und 413 ihre HTTP-Semantik — Frontend-Fehlermapping, Duplikat-Dialog und e2e-Fehlerpfad bleiben unangetastet, und der Fortschrittsbalken kennt seinen Nenner schon beim ersten Poll. |
| Parse-Zeitbudget | `budgetbuddy.import.timeout-seconds` bleibt 30 | Gilt neu nur noch für den Parse. PDFBox kennt kein eigenes Timeout; 408 bleibt für diesen Pfad korrekt und die Frontend-Meldung dazu gültig. |
| Job-Watchdog | neu `budgetbuddy.import.categorization-timeout-seconds` = 300 | Auf nichts wartet mehr ein Request. Der Watchdog verhindert nur, dass ein hängender Claude-Call einen Worker-Thread unbegrenzt bindet. |
| Watchdog-Fall | Restliche Transaktionen ohne Claude-Call als `Sonstiges`, alles persistiert, Job `DONE` + `degraded` | AC2: der Nutzer verliert nie den Import. `Sonstiges` ist die dokumentierte Fallback-Kategorie; manuelle Korrekturen erweitern nach ADR-6 die Lookup-Tabelle. |

Verworfen: **Timeout-Anhebung als Sofortmassnahme**. Sie verlagert das Churn-Problem nur in eine
längere Wartezeit — 50 s Blindwarten sind für Lara nicht besser als 30 s Blindwarten mit
anschliessendem Datenverlust (Risiko #1 in CLAUDE.md).

Verworfen: **Option B (parallele Einzel-Calls)**. Kleinerer Eingriff, aber die Semantik von
„Fehler in Folge" im Circuit Breaker (BE-CAT-02) ist unter echter Nebenläufigkeit nicht mehr
sauber definiert, dazu Anthropic-Rate-Limits.

Verworfen: **Option C (Message-Batches-API)**. Bewusst asynchron mit Durchlaufzeit bis 24 h —
für Latenzsenkung ungeeignet.

Verworfen: **Prompt Caching**. Die minimale cachefähige Prefix-Länge liegt bei ~1024 Tokens, der
Vorspann aus System-Prompt und Kategorienliste bei ~100. Der Cache spränge still nie an.

## Betroffene Files

### Backend — neu

- `db/migration/V05__create_import_jobs_table.sql`
- `transaction/ImportJob.java`, `transaction/ImportJobRepository.java`, `transaction/ImportJobStatus.java`
- `transaction/dto/ImportStartedResponse.java`, `transaction/dto/ImportJobStatusResponse.java`
- `config/AsyncConfig.java` — `@EnableAsync` plus begrenzter `ThreadPoolTaskExecutor`

### Backend — geändert

- `PdfImportController.java` — POST liefert `202 {jobId, total}`; neu `GET /api/import/{jobId}/status`
- `PdfImportService.java` — Aufteilung in `startImport` (synchron) und `runCategorization` (`@Async`)
- `CategorizationPort.java` — `categorizeAll(List<String>)` mit sequenziellem Default
- `HybridCategorizationService.java` — Lookup für alle, ein Bündel-Call für den Rest
- `ClaudeCategorizationService.java` — Structured Output, Chunking, Circuit Breaker pro Bündel
- `application.properties` — Batchgrösse und Watchdog

### Frontend — geändert

- `transactions/pdf-import.service.ts` — Start plus `pollJob()`
- `transactions/pdf-upload.ts` / `.html` — Fortschritts-Signals, `<app-meter>` (FE-UI-03)
- `transactions/import-response.model.ts` — Job-Modelle

### Docs

- `docs/adr/ADR-13-asynchroner-pdf-import.md` und `docs/adr/README.md`
- `CLAUDE.md` — „Backend: Import Flow", ADR-Tabelle
- `docs/requirements/US-04-pdf-upload.md` — Timeout-AC umformuliert

## Implementierungsschritte

1. Migration V05 `import_jobs` plus Entity und Repository
2. `CategorizationPort.categorizeAll` als Default-Methode; `HybridCategorizationService`
   überschreibt sie mit Lookup-first
3. `ClaudeCategorizationService.categorizeAll`: Structured Output mit
   `record TxCategory(int index, Category category)` — die Kategorienliste wird als `enum` ins
   Schema abgeleitet, eine halluzinierte Kategorie ist damit ausgeschlossen; fehlender Index
   fällt auf `Sonstiges`
4. `AsyncConfig` mit begrenztem Pool
5. `PdfImportService` aufteilen: `startImport` (Hash → Duplikat → Parse → Job → Async-Start),
   `runCategorization` (Bündelschleife, `processed` nach jedem Bündel fortschreiben, Watchdog →
   Degradation, Persistierung, Job auf `DONE`)
6. Controller: 202 plus Status-Endpoint mit `userId`-Prüfung (404 bei fremdem Job)
7. Frontend: Service-Polling, Fortschritts-Signals, `<app-meter>` im Template
8. ADR-13 und Doku-Nachzug

## Test-Strategie

| Ebene | Test |
| --- | --- |
| Unit BE | `ClaudeCategorizationServiceTest`: Index-Mapping, fehlender Index → `Sonstiges`, Breaker pro Bündel, Schema enthält alle 13 Kategorien |
| Unit BE | `HybridCategorizationServiceTest`: Lookup-Treffer lösen keinen Call aus, Reihenfolge bleibt erhalten |
| Unit BE | `PdfImportServiceTest`: **AC4** — mehr Transaktionen als in einem Zeitbudget verarbeitbar → alle persistiert, Rest `Sonstiges`, Job `DONE` + `degraded`; `processed` wächst pro Bündel |
| Unit BE | `PdfImportServiceTimingTest` umgeschrieben: belegt neu, dass die Dauer nicht mehr mit der Anzahl skaliert |
| Integration BE | Status-Endpoint: eigener Job 200, fremder Job 404 (Mandantentrennung); POST liefert 202 plus jobId |
| Integration BE | `PdfImportTimeoutIntegrationTest` bleibt unverändert gültig (Parse-Budget) |
| Unit FE | `pdf-upload.spec.ts`: Polling-Verlauf, Balken zeigt `processed`/`total`, Terminalzustände, Fehlermapping unverändert |
| E2E | `pdf-import.spec.ts`: Happy Path zusätzlich mit sichtbarem Fortschrittsbalken; Fehlerpfad unverändert |

## Acceptance Criteria (aus #192)

- [ ] Ein Auszug mit ~110 Transaktionen wird vollständig importiert, ohne ins Zeitbudget zu
      laufen → Batching (~41 Calls auf ~3) und Wegfall des wartenden Requests
- [ ] Ein Import, der das Zeitbudget dennoch überschreitet, verwirft nicht mehr die gesamte
      Arbeit → Watchdog-Degradation, alles persistiert
- [ ] Die gewählte Lösung ist als Entscheid dokumentiert → ADR-13
- [ ] Ein automatisierter Test deckt „mehr Transaktionen als in einem Zeitbudget verarbeitbar"
      ab → `PdfImportServiceTest`
