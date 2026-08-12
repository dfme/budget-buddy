# [FE-PDF-03] Duplikat-Dialog

- **Issue:** [#29](https://github.com/dfme/budget-buddy/issues/29)
- **Task-ID:** `FE-PDF-03`
- **Branch:** `feature/FE-PDF-03-duplikat-dialog`
- **Story:** US-04 — Kontoauszug als PDF hochladen
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-10

## Entscheide

### 1. Scope-Erweiterung: das Force-Flag entsteht in diesem PR

AC 3 verlangt, dass «Trotzdem importieren» einen Request mit Force-Flag sendet — im Backend
existiert dieses Flag nicht. `PdfImportController.importPdf` nimmt ausschliesslich `file`
entgegen, `PdfImportService.importPdf` wirft die `DuplicatePdfImportException` unbedingt. Ohne
Backend-Änderung liefe «Trotzdem importieren» erneut in 409; der Dialog wäre eine Attrappe.

Ein Folge-Issue für das Backend hätte #29 unerfüllbar zurückgelassen, deshalb kommt der
Endpoint-Teil hier mit — deklariert im PR-Body. Das Issue trägt Area *Frontend*; die Erweiterung
ist bewusst und nicht stillschweigend.

### 2. Force bedeutet Ersetzen, nicht Anhängen

Vor dem Force-Import werden die Transaktionen dieses Users mit demselben `pdf_sha256` gelöscht.
Anhängen wäre einfacher, produzierte aber genau die Dubletten, die US-04 mit «ohne explizite
Bestätigung werden keine Dubletten gespeichert» im Blick hat — nach der Bestätigung soll der
Auszug korrekt dastehen, nicht doppelt.

Manuelle Kategorie-Korrekturen gehen dabei **nicht** verloren: `TransactionCategoryService`
lernt jede Korrektur als Lookup-Pattern in `category_lookup`
(`TransactionCategoryService.java:57`), und der Re-Import kategorisiert über genau diese Tabelle
(ADR-6, Schritt 3). Die Korrektur überlebt das Löschen der Zeile.

Delete und `saveAll` laufen atomar über ein `TransactionTemplate`. Der dokumentierte Entscheid
«kein `@Transactional` um den ganzen Flow» (`PdfImportService.java:37-45` — sonst hinge die
JDBC-Connection über allen Claude-Calls) bleibt gültig: umschlossen wird nur der Schreibblock
nach der Kategorisierung, nicht der Import. Ohne diese Klammer könnte ein Fehler zwischen Delete
und Insert die alten Zeilen ersatzlos entfernen — das wäre echter Datenverlust und nicht mit dem
bereits akzeptierten TOCTOU-Race vergleichbar, der höchstens eine Dublette erzeugt.

### 3. Modal auf `@angular/cdk/a11y`, nicht auf nativem `<dialog>`

Nativ wäre attraktiv (Fokus-Falle, Escape und Top-Layer gratis), ist hier aber untestbar: jsdom
28 implementiert `HTMLDialogElement` nicht — `el.showModal is not a function` (geprüft gegen
`frontend/node_modules/jsdom`). Ein Modal, dessen Öffnen im Test nicht ausgeführt werden kann,
liesse sich nicht gegen AC 1–3 absichern.

CDK ist ausserdem der dokumentierte Entscheid aus FE-UI-02 (#99) für genau diesen Fall: eigener
Variante-A-Look über Tokens, a11y-harte Primitive (Focus-Trap, Overlay) aus dem CDK. Das Paket
ist seit FE-UI-02 installiert und wird hier erstmals genutzt.

## Betroffene / neue Files

### Backend

| Aktion | Datei | Inhalt |
| --- | --- | --- |
| ändern | `backend/src/main/java/com/budgetbuddy/transaction/TransactionRepository.java` | `deleteByUserIdAndPdfSha256(Long, String)` — auf den User eingeschränkt |
| ändern | `backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java` | `importPdf(long, byte[], boolean force)`; force überspringt den Duplikatcheck und ersetzt die Vorgänger-Zeilen atomar |
| ändern | `backend/src/main/java/com/budgetbuddy/transaction/PdfImportController.java` | `@RequestParam(name = "force", defaultValue = "false")` + OpenAPI-`@Parameter`, 409-Beschreibung ergänzt |
| ändern | `backend/src/test/java/com/budgetbuddy/transaction/PdfImportServiceTest.java` | Force-Pfad |
| ändern | `backend/src/test/java/com/budgetbuddy/transaction/PdfImportControllerIntegrationTest.java` | 409 vs. `force=true` |

### Frontend

| Aktion | Datei | Inhalt |
| --- | --- | --- |
| neu | `frontend/src/app/shared/modal/modal.ts` / `.html` / `.scss` / `.spec.ts` | wiederverwendbare Modal-Komponente (AC 4) |
| ändern | `frontend/src/app/transactions/pdf-import.service.ts` | `importPdf(file, force = false)` → `?force=true` nur im Force-Fall |
| ändern | `frontend/src/app/transactions/pdf-upload.ts` | 409-Zweig statt generischem Fallback: Datei merken, Dialog öffnen; Confirm → Force-Upload; Cancel → Dialog zu + Duplikat-Notice ohne Retry-Hinweis |
| ändern | `frontend/src/app/transactions/pdf-upload.html` | `app-modal` einbinden |
| ändern | `frontend/src/app/transactions/pdf-upload.spec.ts` | AC 1–3 |
| ändern | `frontend/src/app/styleguide/styleguide.html` / `.ts` | Modal in den Komponenten-Katalog aufnehmen |

## Modal-API

```html
<app-modal
  title="Kontoauszug bereits importiert"
  confirmLabel="Trotzdem importieren"
  cancelLabel="Abbrechen"
  (confirm)="…"
  (cancel)="…"
>Text</app-modal>
```

- `role="dialog"`, `aria-modal="true"`, `aria-labelledby` auf den Titel
- `cdkTrapFocus` mit `cdkTrapFocusAutoCapture`: Fokus wandert beim Öffnen in den Dialog und beim
  Schliessen zurück auf das auslösende Element
- Escape und Backdrop-Klick emittieren `cancel`
- Sichtbarkeit steuert der Parent per `@if` — die Komponente hält keinen eigenen Offen-State
  (dasselbe Muster wie `Notice`: der State liegt in der Feature-Komponente als Signal)

## Implementierungsschritte

1. Backend: Repository-Methode, Service-Signatur + Force-Pfad, Controller-Param + OpenAPI.
2. Backend-Tests: `PdfImportServiceTest` (force überspringt Check, löscht, speichert; Nicht-Force
   unverändert), `PdfImportControllerIntegrationTest` (`force=true` auf Duplikat → 200; ohne Flag
   weiterhin 409; Zeilenzahl nach dem Ersetzen unverändert).
3. Frontend: `shared/modal/` bauen, SCSS auf den Variante-A-Tokens.
4. `PdfImportService.importPdf(file, force)`.
5. `PdfUpload`: `duplicateFile`-Signal, 409-Zweig, Confirm-/Cancel-Handler, 409-Meldung
   «Dieser Kontoauszug wurde bereits importiert.» — ohne Retry-Hinweis (Issue-Kommentar aus dem
   Review zu PR #118: der Retry-Rat ist für ein Duplikat aktiv falsch).
6. Styleguide-Eintrag.
7. `mvn package`, `ng build`, `ng test`, `mvn test`.

## Test-Strategie

- **Component (Vitest):** `modal.spec.ts` — Content-Projection, `role`/`aria-modal`/`aria-labelledby`,
  `confirm`- und `cancel`-Outputs, Escape, Backdrop-Klick, Fokus im Dialog.
- **Component (Vitest):** `pdf-upload.spec.ts` — 409 öffnet den Dialog und zeigt keine
  Fehlermeldung (AC 1); Abbrechen schliesst ohne zweiten Request (AC 2); «Trotzdem importieren»
  sendet POST mit `force=true` und meldet Erfolg (AC 3).
- **Unit (Vitest):** `pdf-import.service.spec.ts` — Query-Param nur im Force-Fall.
- **Unit (JUnit/Mockito):** `PdfImportServiceTest` — Force-Pfad ohne `existsBy…`, mit
  `deleteByUserIdAndPdfSha256(userId, hash)` und `saveAll`; Nicht-Force-Pfad unverändert.
- **Integration (Testcontainers):** `PdfImportControllerIntegrationTest` — Duplikat mit
  `force=true` → 200, danach dieselbe Zeilenzahl wie nach dem Erstimport.

**Bewusst nicht Teil dieses PRs:** ein Playwright-E2E für den Upload — das ist
[#122 (E2E-PDF-01)](https://github.com/dfme/budget-buddy/issues/122) und offen.

## Acceptance Criteria (aus dem Issue)

- [ ] Dialog erscheint bei 409-Response
- [ ] 'Abbrechen' schliesst Dialog ohne Import
- [ ] 'Trotzdem importieren' sendet erneuten Request mit Force-Flag
- [ ] Dialog ist als wiederverwendbare Modal-Component implementiert
