# [FE-CAT-08] Kategorie-Übersicht: Keine-Daten-Hinweis

- **Issue:** [#249](https://github.com/dfme/budget-buddy/issues/249)
- **Task-ID:** `FE-CAT-08`
- **Branch:** `feature/FE-CAT-08-keine-daten-hinweis`
- **Story:** US-12 — Monatswechsel
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-05

## Ausgangslage

Kleine AC-Lücke in der bestehenden Monatswechsel-Funktion der Kategorie-Übersicht (FE-CAT-04,
#144): Der Hinweis für Monate ohne Daten fehlt noch. `isEmpty()` (`category-overview.ts:150-154`)
zeigt bislang nur den generischen Text "Keine Ausgaben in diesem Monat." — ohne Bezug zum
gewählten Monat und ohne Weg zurück zum PDF-Import.

## Entscheid

`isEmpty()` (Kategorien leer) und "Monat nicht in `availableMonths()`" sind backend-seitig
deckungsgleich: beide beruhen auf `findDistinctExpenseMonths` bzw. der Summary-Query, beide
filtern auf `is_income = false` und gruppieren nach `YearMonth`
(`TransactionListService.java:119-125`, `TransactionSummaryService.java:20`). Ein Monat mit
Ausgaben hat deshalb immer mindestens eine Kategorie in der Summary — die Kombination "in
`availableMonths()`, aber `isEmpty()`" tritt praktisch nie ein. Der bestehende `isEmpty()`-Zweig
wird daher direkt durch den neuen Hinweistext ersetzt, ohne zusätzliches Signal (kein
`hasDataForMonth`) — ein zweiter, unerreichbarer Zweig wäre ungetesteter Code ohne Nutzen.

## Betroffene Dateien

- `frontend/src/app/transactions/category-overview.ts` — `RouterLink` zu den Component-Imports
  hinzufügen
- `frontend/src/app/transactions/category-overview.html` — `isEmpty()`-Zweig bekommt neuen
  Hinweistext mit Link
- `frontend/src/app/transactions/category-overview.spec.ts` — bestehenden Test anpassen + neuer
  `describe('Keine-Daten-Hinweis (FE-CAT-08)', ...)`-Block

## Implementierungsschritte

1. In `category-overview.ts`: `RouterLink` importieren und in die `imports`-Liste der Komponente
   aufnehmen.
2. In `category-overview.html`, den `isEmpty()`-Zweig ersetzen durch:
   ```html
   } @else if (isEmpty()) {
     <p class="status empty">
       Keine Daten für {{ monthLabel() }} — <a routerLink="/import">PDF hochladen?</a>
     </p>
   }
   ```
   `monthLabel()` liefert bereits das Format "Juli 2026" (de-CH). Der Link zeigt auf `/import`
   (Route der `PdfUpload`-Komponente, `app.routes.ts:26-30`), analog zum bestehenden Muster in
   `fixed-cost-list.html:27`.

## Test-Strategie

Vitest/Angular TestBed, analog zu den bestehenden Konventionen in `category-overview.spec.ts`:

- Bestehenden Test "communicates the empty state when the month has no expenses" um Assertions
  auf den neuen Text und Link erweitern.
- Neuer `describe`-Block `'Keine-Daten-Hinweis (FE-CAT-08)'` mit Tests für:
  - AC1: Hinweistext enthält "Keine Daten für [korrektes Monatslabel]" beim Anzeigen eines
    Monats ohne Daten.
  - AC2: Der Hinweis enthält einen `<a>` mit `routerLink="/import"`.

## Acceptance Criteria

- [ ] Wählt der Nutzer in der Kategorie-Übersicht einen Monat, der nicht in `availableMonths()`
      enthalten ist, erscheint der Hinweis "Keine Daten für [Monat Jahr] — PDF hochladen?"
- [ ] Der Hinweis verlinkt zum PDF-Upload
