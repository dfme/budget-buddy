# [FE-PDF-04] Importierte Transaktionen mit Kategorie direkt im Import-Screen anzeigen und korrigieren

- **Issue:** [#292](https://github.com/dfme/budget-buddy/issues/292)
- **Task-ID:** `FE-PDF-04`
- **Branch:** `feature/FE-PDF-04-import-transaction-list`
- **Story:** US-04 — Kontoauszug als PDF hochladen (Korrekturmechanik aus US-05)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-14

## Entscheide

- **Backend ist fertig.** `GET /api/import/{jobId}/transactions` kam mit BE-PDF-14 (`57f044e`) und
  liefert `TransactionResponse` im selben Format wie `GET /api/transactions` — inklusive der
  Auflösung `category == null → "Sonstiges"` in `TransactionResponse.fromResolvingCategory`.
  **Kein Backend-Code in diesem PR.**
- **Wiederverwendung:** `TransactionService.updateCategory`, `CATEGORIES` und das optimistische
  Muster aus `category-overview.ts:414-447` werden wiederverwendet; das Markup entsteht lokal in
  `pdf-upload.html`. `category-overview.*` bleibt unangetastet. Bewusst *keine* gemeinsame
  `CategorySelect`-Komponente: sie hätte `category-overview.html/.scss` samt Tests mit angefasst
  und den Diff in FE-CAT-03-Gebiet ausgeweitet, ohne dass der AC das verlangt. Die Gleichheit der
  13 Kategorien garantiert ohnehin die gemeinsame Konstante `shared/category.ts`, nicht das Markup.
- **`buchungsdetails` als zweite Zeile** — über den AC-Wortlaut (vier Felder) hinaus. Grund: Bei
  PostFinance trägt `buchungstext` nur die Zahlungsart («LASTSCHRIFT», «TWINT»), die Gegenpartei
  steht ausschliesslich in `buchungsdetails`. Ohne sie sähen mehrere Zeilen identisch aus und der
  Nutzer könnte gar nicht entscheiden, welche Kategorie stimmt — genau der Zweck dieses Screens.
  Dieselbe Begründung wie in `category-overview.html:191-200`.
- **`directionUncertain` wird hier nicht angezeigt.** Die Korrektur der Buchungsrichtung
  (BE-PDF-10) lebt in der Prüfliste der Kategorie-Übersicht; ein zweiter Ort für dieselbe
  Entscheidung wäre ein zweiter Ort zum Auseinanderlaufen — dieselbe Begründung, die schon in
  `category-overview.html:205-210` steht.

## Betroffene Dateien

| Datei | Art |
| --- | --- |
| `frontend/src/app/transactions/pdf-import.service.ts` | ändern — `importTransactions(jobId)` |
| `frontend/src/app/transactions/pdf-upload.ts` | ändern — Liste laden, Kategorie korrigieren |
| `frontend/src/app/transactions/pdf-upload.html` | ändern — Liste + Dropdown |
| `frontend/src/app/transactions/pdf-upload.scss` | ändern — Zeilen-Layout |
| `frontend/src/app/transactions/pdf-upload.spec.ts` | ändern — `completeImport`-Helper + neue Tests |
| `e2e/tests/pdf-import.spec.ts` | ändern — Happy Path ohne `/categories`-Umweg |
| `docs/plans/FE-PDF-04-import-transaction-list.md` | **neu** |
| `docs/plans/README.md` | ändern — Indexzeile |

Keine neue Model-Datei: `Transaction` aus `transaction.model.ts` passt unverändert.

## Implementierungsschritte

1. **`PdfImportService.importTransactions(jobId): Observable<Transaction[]>`** —
   `GET /api/import/${jobId}/transactions`. Kein Polling, kein Retry: der Aufruf erfolgt erst nach
   `DONE`, ein 409 ist damit ausgeschlossen.
2. **`PdfUpload`: drei neue Signals** — `importedTransactions` (`Transaction[] | null`),
   `listErrorMessage`, `saveErrorMessage`; dazu `categories = CATEGORIES` fürs Template.
3. **Laden.** Im `DONE`-Zweig von `trackJob` nach `finish(...)` die Liste nachladen, aber nur bei
   `total > 0`. Der Nullfall verlässt `upload()` schon vorher über `finish({count: 0})` und fragt
   gar nicht erst nach (AC6).
4. **Zurücksetzen** in `upload()` — nicht in `selectFile()`: so erwischt es auch den «Trotzdem
   importieren»-Pfad, der über `confirmDuplicateImport()` direkt in `upload()` springt (AC7).
5. **`changeCategory(tx, category)`** — 1:1 das Muster von `category-overview.ts:421`: neuer Wert
   sofort ins Signal, dann `updateCategory`; bei Fehler alter Wert zurück und Meldung. **Ohne** das
   `refreshAfterCorrection()` der Übersicht — hier gibt es keine Summen, Anteile oder einen Donut,
   die nachziehen müssten.
6. **Template** — `<ul class="imported">` unter der Erfolgsmeldung, pro Zeile Datum
   (`date: 'dd.MM.yyyy'`), Buchungstext plus optionale Detailzeile, Betrag (`currency: 'CHF'`),
   `<select appInput>` mit den 13 Optionen. Vorauswahl über `[selected]` am `<option>`, nicht
   `[value]` am `<select>` — der Kniff aus `category-overview.html:238-243`, ohne den die Bindung
   ins Leere liefe und der Rollback unsichtbar bliebe. Neu zu importieren: `CurrencyPipe`,
   `DatePipe`, `Input`.
7. **Degradierter Fall** — braucht keine Zeile Code: der Hinweis steckt in `successMessage()` und
   damit in der Erfolgsmeldung, die über der Liste stehen bleibt (AC5).
8. **Styles** — `.imported*` in `pdf-upload.scss`, an `.transaction*` aus
   `category-overview.scss:92-146` angelehnt. `.upload` hat `max-width: 40rem`; die Zeile bekommt
   deshalb ein eigenes Grid, keine Kopie der vierspaltigen Tabellenzeile.

## Test-Strategie

**Vitest** (`pdf-upload.spec.ts`) — der `completeImport`-Helper muss zusätzlich den
Transaktions-Request beantworten, sonst schlägt `httpMock.verify()` in allen bestehenden Tests
fehl; er bekommt dafür einen optionalen Listen-Parameter, der per Default leer ist.

1. Erfolgreicher Import rendert eine Zeile pro Transaktion mit Datum, Text, Betrag, Kategorie
2. Buchung mit `buchungsdetails` zeigt die zweite Zeile, ohne Details entfällt sie
3. Nullfall rendert keine Liste — nur die bestehende Meldung (AC6)
4. Degradierter Import zeigt Hinweis **und** Liste (AC5)
5. Dropdown trägt 13 Optionen, `"Sonstiges"` ist bei einer nicht zugeordneten Buchung
   vorausgewählt (AC4)
6. Kategorie-Änderung schickt `PUT /api/transactions/{id}/category` und hält den neuen Wert
7. Gescheiterter PUT rollt den alten Wert zurück und meldet den Fehler (AC3)
8. Gescheiterter Listen-Request meldet den Fehler, ohne die Erfolgsmeldung zu verdrängen
9. Ein neuer Upload räumt die Liste des vorigen Imports weg (AC7)

**Playwright** (`e2e/tests/pdf-import.spec.ts`, Happy Path) — statt
`page.goto('/categories?month=2025-06')` direkt auf dem Import-Screen prüfen: fünf Zeilen sichtbar,
die erste trägt ein Kategorie-Dropdown. Damit fällt die Konstante `FIXTURE_MONTH` weg und der
Kommentar bei Zeile 72–86 wird ersetzt — der Persistenznachweis läuft jetzt über einen zweiten
Endpoint (`GET /api/import/{jobId}/transactions`) statt über eine zweite Seite, der Beweiswert
bleibt derselbe (AC8). Der Fehlerpfad bleibt unverändert.

## Acceptance Criteria

- [ ] Nach erfolgreichem Import (Anzahl > 0) zeigt der Import-Screen zusätzlich zur bestehenden
      Erfolgsmeldung eine Liste der importierten Transaktionen: Datum, Buchungstext, Betrag,
      Kategorie
- [ ] Jede Zeile hat ein Kategorie-Dropdown mit denselben 13 Kategorien wie in FE-CAT-03, über das
      sich die Kategorie direkt hier ändern lässt
- [ ] Eine Kategorie-Änderung wird über `PUT /api/transactions/{id}/category` gespeichert —
      optimistisch mit Rollback bei Fehler, wie in der Kategorie-Übersicht
- [ ] Transaktionen ohne automatische Zuordnung erscheinen mit "Sonstiges" vorausgewählt
- [ ] Der degradierte Fall (BE-PDF-09, `degraded=true`) bleibt sichtbar — der bestehende
      Hinweistext bleibt erhalten, zusätzlich zur Liste
- [ ] Der Nullfall (0 Transaktionen erkannt) zeigt weiterhin nur die bestehende Meldung, keine
      leere Liste
- [ ] Die Liste bezieht sich ausschliesslich auf den soeben abgeschlossenen Import — kein
      Vermischen mit älteren Buchungen
- [ ] `e2e/tests/pdf-import.spec.ts` (Happy Path) prüft die importierten Buchungen direkt auf dem
      Import-Screen statt über den Umweg `/categories?month=...`
