# Plan: FE-PDF-01 — PDF-Upload Component

- **Issue:** [#27](https://github.com/dfme/budget-buddy/issues/27) `[FE-PDF-01] PDF-Upload Component` (Label `us-04`)
- **Task-ID:** FE-PDF-01
- **Branch:** `feature/FE-PDF-01-pdf-upload-component`
- **Base:** `feature/FE-UI-03-shared-basiskomponenten` (#108) — stacked PR, Vorgabe: umsetzen, als wären #107 (FE-UI-02 Tokens) und #108 (FE-UI-03 Shared-Komponenten) bereits gemergt. Nach Merge der Kette retargetet GitHub den PR automatisch auf `main`.

## Entscheidungen

- **Scope-Abgrenzung zu FE-PDF-02 (#28):** FE-PDF-01 liefert die Upload-Komponente (Drag-and-Drop, File-Picker, client-seitige Validierung, Spinner während `POST /import/pdf`). Die differenzierte Anzeige der Server-Antworten (Erfolgs-Count, 400 Passwort/Format, 408 Timeout, 409 Duplikat) ist FE-PDF-02. Die Komponente hält nach Request-Abschluss einen bewusst generischen Erfolg/Fehler-Status im State, damit FE-PDF-02 darauf aufsetzen kann.
- **Ablage im Feature-Folder `transactions/`** gemäss CLAUDE.md (US-04 Upload gehört zu `transactions/`).
- **Shared-Komponenten aus FE-UI-03** (`Card`, `Button`, `Notice`) und Tokens aus `_tokens.scss` verwenden — kein eigenes Styling-Fundament.
- **Eine Datei pro Upload:** Backend nimmt einen einzelnen `file`-Part; Drop mehrerer Dateien wird mit Hinweis abgelehnt.
- **Proxy:** nur `/import` ergänzen; das bereits fehlende `/transactions` ist ein separater Fix (nicht Teil dieses PRs).

## Betroffene Files

**Neu:**

| File | Zweck |
|------|-------|
| `frontend/src/app/transactions/pdf-import.service.ts` (+ `.spec.ts`) | Zustandsloser Service: `POST /import/pdf` mit `FormData` (Muster: `TransactionSummaryService`) |
| `frontend/src/app/transactions/pdf-upload.ts` / `.html` / `.scss` (+ `.spec.ts`) | Standalone-Komponente (OnPush, Signals): Dropzone + File-Picker + Validierung + Spinner |
| `docs/plans/FE-PDF-01-pdf-upload-component.md` | Dieser Plan |

**Geändert:**

| File | Änderung |
|------|----------|
| `frontend/src/app/app.routes.ts` | Route `/import` (lazy, `authGuard`) |
| `frontend/src/app/app.html` | Nav-Link „Import" |
| `frontend/proxy.conf.json` | `/import` → `http://localhost:8080` |

## Implementierungsschritte

1. Branch von `origin/feature/FE-UI-03-shared-basiskomponenten` erstellen
2. `PdfImportService` mit `importPdf(file: File): Observable<ImportResponse>` (`{ count: number }`)
3. `PdfUpload`-Komponente:
   - Dropzone mit `dragover`/`dragleave`/`drop`-Handlern, visuelles Feedback über Token-Farben; Klick + Tastatur öffnet den File-Picker (`<input type="file" accept="application/pdf">`, a11y: fokussierbar, aria-Label)
   - Client-Validierung vor dem Upload: kein PDF → „Nur PDF-Dateien werden unterstützt"; > 10 MB → „Maximale Dateigrösse: 10 MB" (Wortlaut US-04); mehrere Dateien → Hinweis, nur eine Datei. Fehler als `app-notice`
   - Während des Uploads: `uploading`-Signal, CSS-Spinner (Token-basiert), Dropzone deaktiviert, `aria-busy`
4. Route + Nav-Link + Proxy-Eintrag
5. Prettier, `ng build`, `ng test`

## Test-Strategie (Vitest)

- **Service-Spec:** via `provideHttpClientTesting` — korrekte URL (`/import/pdf`), Methode POST, `FormData`-Feld `file`
- **Komponenten-Spec:**
  - Nicht-PDF wird abgelehnt (Fehlermeldung, kein HTTP-Call)
  - Datei > 10 MB wird abgelehnt (Fehlermeldung, kein HTTP-Call)
  - Happy Path: gültige PDF via Drop → POST ausgelöst, Spinner an → nach Antwort aus
  - Fehlerpfad: HTTP-Fehler → Spinner aus, generischer Fehlerstatus

## Acceptance Criteria (Issue #27)

- [ ] Drag-and-Drop akzeptiert PDF-Dateien
- [ ] Dateien > 10 MB werden client-seitig abgelehnt mit Fehlermeldung
- [ ] Spinner wird während POST /import/pdf angezeigt
- [ ] Nur .pdf-Dateien werden akzeptiert
