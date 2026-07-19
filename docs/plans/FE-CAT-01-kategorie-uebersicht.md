# FE-CAT-01 — Kategorie-Übersicht

- **Issue:** [#30](https://github.com/dfme/budget-buddy/issues/30)
- **Task-ID:** FE-CAT-01
- **Branch:** `feature/FE-CAT-01-kategorie-uebersicht`
- **Depends on:** #20 (BE-CAT-05, `GET /transactions/summary?month=YYYY-MM`) — closed/merged

## Ziel

Liste mit CHF-Summe, Anzahl und Prozentanteil pro Kategorie für den ausgewählten
Monat, mit Monat-Navigation und sauberem Leerzustand.

## Acceptance Criteria (aus Issue)

- [ ] Alle Kategorien mit Summe, Anzahl und Prozent werden angezeigt
- [ ] Monat-Selector ermöglicht Navigation zwischen Monaten
- [ ] Leerer Zustand wird sauber kommuniziert

## Entscheide

- Neuer Feature-Ordner `frontend/src/app/transactions/` (Domänen-Struktur laut CLAUDE.md).
- Service = dünner HTTP-Wrapper (analog zu `AuthService`); die Komponente hält den
  UI-State als Signals (`loading` / `error` / `summary`), OnPush + Signals wie `Login`.
- Monat-Selector: Prev/Next-Stepper (`‹ Juli 2026 ›`), Default = aktueller Monat.
  Jeder Schritt lädt neu.
- Beträge via Angular `CurrencyPipe` (`CHF`); Prozente auf 2 Nachkommastellen.
- Neue Route `/categories` (auth-guarded) + Nav-Link im Header-Shell.

## Betroffene Files

### Neu
- `frontend/src/app/transactions/category-summary.model.ts` — TS-Interfaces (Spiegel der Backend-DTOs)
- `frontend/src/app/transactions/transaction-summary.service.ts` — `getSummary(month)`
- `frontend/src/app/transactions/category-overview.ts` / `.html` / `.scss` — Komponente
- `frontend/src/app/transactions/category-overview.spec.ts` — Komponenten-Tests
- `frontend/src/app/transactions/transaction-summary.service.spec.ts` — Service-Test

### Geändert
- `frontend/src/app/app.routes.ts` — lazy `categories`-Route (auth-guarded)
- `frontend/src/app/app.html` + `app.ts` — Nav-Link (nur eingeloggt sichtbar)

## Implementierungsschritte

1. Model-Interfaces (`CategorySummaryItem`, `CategorySummary`).
2. Service mit dem einzelnen `GET /transactions/summary`-Call.
3. Komponente: aktueller-Monat-Signal, Prev/Next-Handler, Laden füllt
   `loading`/`error`/`summary`-Signals.
4. Template: Monat-Selector-Header, Kategorie-Liste (Kategorie · CHF · Anzahl ·
   Prozent), Total-Zeile, Leerzustand, Fehlerzustand.
5. Route + Nav-Link.

## Test-Strategie (Vitest / TestBed, `HttpTestingController`)

- Service: `GET /transactions/summary?month=YYYY-MM`.
- Komponente Happy Path: rendert alle Kategorien mit Betrag/Anzahl/Prozent.
- Komponente Leerzustand: leere `categories` → Leerzustand-Meldung.
- Komponente Monat-Navigation: Prev/Next ändert Monat und löst neuen Request aus.
