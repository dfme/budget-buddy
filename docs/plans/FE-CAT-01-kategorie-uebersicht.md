# FE-CAT-01 — Kategorie-Übersicht

- **Issue:** [#30](https://github.com/dfme/budget-buddy/issues/30)
- **Task-ID:** FE-CAT-01
- **Branch:** `feature/FE-CAT-01-kategorie-uebersicht`
- **Depends on:** #20 (BE-CAT-05, `GET /transactions/summary?month=YYYY-MM`) — closed/merged

## Ziel

Liste mit CHF-Summe, Anzahl und Prozentanteil pro Kategorie für den ausgewählten
Monat, mit Monat-Navigation und sauberem Leerzustand.

## Acceptance Criteria (aus Issue)

- [x] Alle Kategorien mit Summe, Anzahl und Prozent werden angezeigt
- [x] Monat-Selector ermöglicht Navigation zwischen Monaten
- [x] Leerer Zustand wird sauber kommuniziert

## Entscheide

- Neuer Feature-Ordner `frontend/src/app/transactions/` (Domänen-Struktur laut CLAUDE.md).
- Service = dünner HTTP-Wrapper (analog zu `AuthService`); die Komponente hält den
  UI-State als Signals (`loading` / `error` / `summary`), OnPush + Signals wie `Login`.
- Monat-Selector: Prev/Next-Stepper (`‹ Juli 2026 ›`), Default = aktueller Monat.
  Jeder Schritt lädt neu; "›" ist ab dem aktuellen Monat deaktiviert (kein Vorblättern
  in beliebig viele leere zukünftige Monate — Review-Feedback aus PR #90).
- Beträge via Angular `CurrencyPipe` (`CHF`); Prozente via `DecimalPipe`
  (`number: '1.2-2'`) auf 2 Nachkommastellen. Beide Pipes werden gezielt importiert
  (`imports: [CurrencyPipe, DecimalPipe]`), nicht der gesamte `CommonModule`.
- **Wire-Format:** `amount`, `totalAmount`, `percentage` im Model sind `number`, nicht
  `string` — das Backend serialisiert `BigDecimal` ohne String-Serializer, Jackson
  liefert JSON-Zahlen. Ursprünglich als `string` modelliert, in PR #90 korrigiert.
- **Race Condition bei schneller Monat-Navigation:** `load()` cancelt einen noch
  laufenden Request (`Subscription.unsubscribe()`), bevor ein neuer startet — sonst
  könnte die Antwort eines älteren, langsameren Requests die eines neueren überschreiben.
- Neue Route `/categories` (auth-guarded) + Nav-Link im Header-Shell, mit
  `routerLinkActive` zur visuellen Markierung des aktiven Menüpunkts.

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
- Komponente Happy Path: rendert alle Kategorien mit Betrag/Anzahl/Prozent — Assertion
  prüft gezielt einzelne `<td>`-Zellen statt des zusammengeklebten Zeilentexts.
- Komponente Leerzustand: leere `categories` → Leerzustand-Meldung.
- Komponente Monat-Navigation: Prev/Next ändert Monat und löst neuen Request aus.
- Komponente Navigationslimit: "›" ist im aktuellen Monat deaktiviert, nach `previousMonth()` wieder aktiv.
- Komponente Race Condition: zweiter Monatswechsel vor Antwort des ersten Requests →
  der erste Request wird gecancelt (`TestRequest.cancelled`), nur die Antwort des
  zuletzt angeforderten Monats landet in `summary()`.

## Review-Feedback (PR #90)

Beide Reviewer (`danielwagner990`, `dfme`) forderten vor dem Merge zwei Fixes an —
Details siehe Entscheide oben:

1. **Model/Wire-Format-Mismatch** (`amount`/`totalAmount`/`percentage`: `string` → `number`).
2. **Race Condition** bei schneller Monat-Navigation.

Zusätzlich mit umgesetzt (Nice-to-haves, nicht blockierend): `CurrencyPipe`/`DecimalPipe`
statt `CommonModule`, `routerLinkActive`, Navigationslimit auf den aktuellen Monat,
`<th scope="row">` in der Total-Zeile, Test für den Kategorien-Nav-Link in `app.spec.ts`.
