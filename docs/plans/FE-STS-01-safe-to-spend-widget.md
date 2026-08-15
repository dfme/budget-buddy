# [FE-STS-01] Safe-to-Spend Dashboard-Widget

- **Issue:** [#33](https://github.com/dfme/budget-buddy/issues/33)
- **Task-ID:** `FE-STS-01`
- **Branch:** `feature/FE-STS-01-safe-to-spend-widget`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-15

## Scope

Nur das Basis-Widget: grosser, zentraler Betrag + Wochen-Label, geladen von
`GET /budget/safe-to-spend`. Das Negativ-Banner (#34, FE-STS-02) und der
No-Income-Hinweis mit Einkommens-Vorschlag + CTA (#35, FE-STS-03) sind eigene,
von #33 abhängige Folge-Issues und nicht Teil dieses Plans. Der `amount: null`-Fall
(kein Einkommen erfasst) wird hier trotzdem sauber behandelt — ein `—`-Platzhalter
statt `NaN`/`CHF 0.00` — damit die Basis nicht neu angefasst werden muss, sobald #35
das Banner ergänzt.

## Backend-Vertrag (bereits vorhanden, BE-STS-03/#23)

`GET /budget/safe-to-spend`, authentifiziert via JWT-Cookie, liefert:

```json
{ "amount": 500.00, "weeksLeft": 3, "negative": false, "noIncome": false, "incomeSuggestion": null }
```

`amount` ist eine JSON-Zahl (nicht String), `null` genau dann, wenn `noIncome=true`.
`weeksLeft` ist die Anzahl verbleibender Wochen im Monat (inkl. heute, aufgerundet,
mindestens 1) — Basis für das Wochen-Label.

## Betroffene Files

- Neu: `frontend/src/app/dashboard/safe-to-spend.model.ts`
- Neu: `frontend/src/app/dashboard/safe-to-spend.service.ts`
- Neu: `frontend/src/app/dashboard/safe-to-spend.service.spec.ts`
- Geändert: `frontend/src/app/dashboard/dashboard.ts` (Platzhalter → echtes Widget)
- Geändert: `frontend/src/app/dashboard/dashboard.html`
- Geändert: `frontend/src/app/dashboard/dashboard.scss`
- Neu: `frontend/src/app/dashboard/dashboard.spec.ts`

## Implementierungsschritte

1. `SafeToSpendResponse`-Interface, 1:1 Spiegel des Backend-DTOs (Konvention wie
   `CategorySummary`/`CategorySummaryItem`).
2. `SafeToSpendService` (zustandslos, `providedIn: 'root'`), wrappt
   `GET /budget/safe-to-spend` ohne Query-Parameter, analog `TransactionSummaryService`.
3. `Dashboard`-Component: Signals `data`/`loading`/`errorMessage`, `constructor()`
   ruft `load()` (kein `ngOnInit`), `computed weekLabel` mit Singular/Plural
   ("noch 1 Woche im Monat" vs. "noch N Wochen im Monat").
4. Template: `app-card` als Container, `@if/@else if`-Kette für
   loading → error → Daten (Konvention aus `category-overview.html`). Im
   Daten-Zweig: `app-amount [value] [showCurrency]="true"` wenn `amount !== null`,
   sonst `—`-Platzhalter; Wochen-Label darunter.
5. SCSS: bestehendes Token `$fs-hero` (44px/600) für die Betragsgrösse, zentriert.
6. Wiederverwendung von `Card`, `Amount`, `Notice` aus `shared/` — keine neuen
   UI-Primitiven.
7. Kein Routing-Aufwand: `/dashboard` ist bereits mit `authGuard` + `onboardingGuard`
   verdrahtet und die Startroute.

## Test-Strategie

Vitest-Unit-Tests (Playwright-E2E für US-06 ist bewusst #125, ein eigener
Branch/PR — hier nicht dupliziert):

- `safe-to-spend.service.spec.ts`: `GET /budget/safe-to-spend` ohne Parameter,
  Response-Passthrough (`HttpTestingController`).
- `dashboard.spec.ts`: Ladezustand; Happy Path (Betrag + korrektes
  Singular/Plural-Wochen-Label); `noIncome`/`amount === null` rendert `—` ohne
  `NaN`/`CHF 0.00`; HTTP-Fehler zeigt `app-notice`.

## Acceptance Criteria (aus Issue #33)

- [ ] Betrag ist gross und zentral positioniert
- [ ] Wochen-Label wird korrekt berechnet und angezeigt
- [ ] Widget lädt Daten von `GET /budget/safe-to-spend`
