# [FE-STS-06] «Monatliche fixe Ausgaben»-Karte von der Budget-Seite ins Dashboard verschieben

- **Issue:** [#366](https://github.com/dfme/budget-buddy/issues/366)
- **Task-ID:** `FE-STS-06`
- **Branch:** `feature/FE-STS-06-fixkosten-total-dashboard`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-24

## Entscheide

- **Position: zuunterst auf dem Dashboard**, an der Stelle des bisherigen Abo-Teasers. Das Total
  ist keine Monatsgrösse — zwischen Safe-to-Spend und Drei-Monats-Übersicht, die beide dem
  Monats-Stepper folgen, läse es sich wie eine Zahl des gewählten Monats. Zudem ist die Card ein
  Link, der von der Seite wegführt. Sie steht ausserhalb der Lade-/Fehler-Kette des
  Safe-to-Spend und erscheint deshalb auch bei einem abgeschlossenen Monat.
- **Logik direkt im `Dashboard`**, keine eigene Komponente — gleiches Muster wie die übrigen
  Blöcke (je eigener Lade-/Fehlerzustand).
- **Abos: eigener Lade-/Fehlerzustand auf dem Dashboard.** Ohne eingebettete
  `RecurringExpenseList` entfällt der `viewChild`-Umweg; der bisher stille Abo-Fehler speist neu
  `totalUnavailableReason`.
- **`FixedCostList` verliert die Card samt Total-Logik** (`monthlyTotal`,
  `totalUnavailableReason`, `MonthlyTotal`, `toRappen`, `viewChild`, `RecurringExpenseService`).
- **Abo-Teaser entfällt ersatzlos**, ohne Anzahl in der neuen Card (Entscheid des Users).
- **#351** wird nur im PR-Body erwähnt, keine Relationship (Entscheid des Users).
- **Scope-Erweiterung:** E2E-Tests des Totals, Route-Integrationstest und veraltete
  «Teaser-Card»-Kommentare werden mitgezogen und im PR-Body deklariert.

## Betroffene Dateien

- `frontend/src/app/dashboard/dashboard.{ts,html,scss,spec.ts}`
- `frontend/src/app/onboarding/fixed-cost-list.{ts,html,scss,spec.ts}`
- `frontend/src/app/recurring/recurring-expense.service.ts`, `recurring-expense.service.spec.ts`,
  `recurring-expense-list.ts` (Kommentare)
- `frontend/src/app/core/layout/shell.ts`, `shell.spec.ts` (Kommentare)
- `e2e/tests/recurring-expenses.spec.ts`

## Implementierungsschritte

1. `Dashboard`: `FixedCostService` injizieren, `loadFixedCosts()` einmal beim Aufbau;
   `recurringLoading`/`recurringError`; `monthlyTotal` und `totalUnavailableReason` übernehmen;
   `recurringTeaserText` entfernen.
2. Template: Teaser durch `<a class="monthly-total" routerLink="/budget">` mit `app-card`
   ersetzen, sonst `.monthly-total__unavailable`.
3. Styles `.recurring-teaser*` → `.monthly-total*`.
4. `FixedCostList`: Card, Total-Logik und Styles entfernen, Javadoc nachziehen.
5. Kommentare «Teaser-Card» in Service, Liste, Shell nachziehen.
6. Tests und E2E anpassen.

## Test-Strategie

- Unit (Vitest): `dashboard.spec.ts` — Summe, Rappen-Addition, verneinte Abos, Link, drei
  Fehlertexte, kein Hinweis während des Ladens, einmaliges Laden unabhängig vom Monat, Card bei
  geschlossenem Monat. `fixed-cost-list.spec.ts` — Card nicht mehr auf `/budget`, Route-Test
  beantwortet `/api/fixed-costs` des Dashboards.
- E2E (Playwright): die beiden Total-Tests laufen über das Dashboard.
- `ng build`.

## Acceptance Criteria (aus dem Issue)

- [ ] Die Card «Monatliche fixe Ausgaben» erscheint auf dem Dashboard (Betrag + Aufschlüsselung
      "X Fixkosten + Y erkannte Abos", wie bisher auf `/budget`)
- [ ] Die ganze Card ist ein Link auf `/budget`
- [ ] Der bisherige Abo-Teaser ist entfernt
- [ ] Die Card ist von `/budget` entfernt; die restlichen Abschnitte bleiben unverändert
- [ ] Fehlt einer der beiden Summanden, erscheint ein erklärender Text statt der Card
- [ ] Position der Card auf dem Dashboard ist im PR begründet
- [ ] Bestehende Tests sind an die neue Aufteilung angepasst, inkl. Entfernen der Teaser-Tests
