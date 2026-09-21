# [FE-FC-05] Fixkosten- und Abo-Ansicht zusammenführen

- **Issue:** [#338](https://github.com/dfme/budget-buddy/issues/338)
- **Task-ID:** `FE-FC-05`
- **Branch:** `feature/FE-FC-05-fixkosten-abos-zusammenfuehren`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard) / US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** — (Karte ohne Sprint im Board; laufend ist Sprint 7)
- **Bestätigt am:** 2026-09-20

## Ausgangslage

`/fixkosten` (`FixedCostList`, manuell erfasste Positionen) und `/abos` (`RecurringExpenseList`,
automatisch erkannte wiederkehrende Ausgaben) sind zwei Seiten für verwandte Inhalte. `/abos`
hat keinen Nav-Eintrag; der Einstieg läuft über die Teaser-Card auf dem Dashboard
(`dashboard.html:244`) und den Klick auf eine `RECURRING_EXPENSE_DETECTED`-Benachrichtigung
(`notification-bell.ts:116`).

Im Safe-to-Spend (`SafeToSpendService.calculateOpenMonth`) zählen erkannte Abos heute nur als
variable Ausgabe des Monats, in dem ihre Belastung liegt — vor der Abbuchung ist das Budget um
den Abo-Betrag zu hoch, nach der Abbuchung stimmt es. Fixkosten-Positionen dagegen mindern den
Betrag von Monatsbeginn an über die Fixkosten-Seite, und der `FixedCostDebitMatcher` streicht
ihre Abbuchung aus dem Ausgaben-Summanden (ADR-13).

## Entscheide

| Punkt | Entscheid |
| ----- | --------- |
| Überlappung Abo ↔ Fixkosten-Position | **Betragsgleich = bereits erfasst.** Multiset-Differenz der erkannten Abo-Beträge gegen `FixedCost.betrag`; nur der Rest zählt zusätzlich. Dieselbe Betragslogik wie ADR-13 — der Empfänger steht als Kriterium nicht zur Verfügung, und der häufigste Fall (Handy, Krankenkasse, Streaming manuell *und* erkannt) wäre sonst doppelt abgezogen. |
| Formel | `verfügbar = Einkommen − Fixkosten-Monatssumme − Σ(nicht abgedeckte Abos) − variable Ausgaben`; der Matcher streicht je Fixkosten-Position **und** je nicht abgedecktem Abo höchstens eine betragsgleiche Belastung. |
| Modulkante | Neuer Lese-Port im liefernden Modul: `recurring.RecurringExpenseAmountPort.detectedAmounts(userId)` — nur `DETECTED`, nur Beträge. `budget` greift nicht auf `RecurringExpenseRepository` zu. |
| `/abos` | Redirect auf `/fixkosten` — Bookmarks und alte Links bleiben gültig; ohne eigene Route liefe `**` aufs Dashboard. |
| Frontend-Struktur | `RecurringExpenseList` bleibt in `recurring/` und wird als Kind-Komponente in `FixedCostList` eingebettet (`h1 Abos` → `h2 Erkannte Abos`). Kein Template-Merge: eigener State, eigene Tests bleiben. |
| Nav | «Fixkosten» bleibt der einzige Eintrag. Dashboard-Teaser verlinkt auf `/fixkosten`, CTA «Zu Fixkosten & Abos →». |
| Scope-Delta | Frontend-Feature-Liste in `docs/CONVENTIONS.md` auf den Ist-Stand bringen (`recurring/`, `notifications/`, `styleguide/` fehlten) — im PR-Body deklariert. |

## Betroffene Dateien

### Backend — neu

- `backend/src/main/java/com/budgetbuddy/recurring/RecurringExpenseAmountPort.java`

### Backend — ändern

- `recurring/RecurringExpenseRepository.java` — `findByUserIdAndStatus`
- `recurring/RecurringExpenseService.java` — implementiert den Port
- `recurring/package-info.java`, `budget/package-info.java` — neue Kante
- `budget/FixedCostDebitMatcher.java` — `uncoveredRecurringExpenses(abos, fixkosten)` (Multiset-Differenz) und `variableExpenses(belastungen, fixkosten, abos)`
- `budget/SafeToSpendService.java` — Port injizieren, Formel erweitern, Javadoc

### Backend — Tests

- `FixedCostDebitMatcherTest` — Nested-Klasse für Abos: nicht abgedeckt / abgedeckt / Multiset / eine Belastung pro Abo
- `SafeToSpendServiceTest` — neuer Mock; Abo ohne Belastung zählt einmal, Abo mit Belastung genau einmal, betragsgleich zu Fixkosten nicht doppelt; `never()`-Nachweise in noIncome / CLOSED / future
- `SafeToSpendServiceIntegrationTest` — echte `recurring_expenses`-Zeilen: DETECTED zählt, DISMISSED nicht, fremder User nicht
- `RecurringExpenseServiceTest` — Port liefert nur DETECTED-Beträge

### Frontend — ändern

- `app.routes.ts` — `abos` → `redirectTo: 'fixkosten'`
- `recurring/recurring-expense-list.{ts,html,scss}` — Abschnitt statt Seite, `h2 Erkannte Abos`, Hinweis auf den Safe-to-Spend
- `onboarding/fixed-cost-list.{ts,html}` — `<app-recurring-expense-list>` unter der Tabelle
- `dashboard/dashboard.html`, `dashboard/dashboard.ts` — Link und Javadoc
- `notifications/notification-bell.ts` — `navigate(['/fixkosten'])`
- Specs: `recurring-expense-list.spec.ts`, `fixed-cost-list.spec.ts`, `dashboard.spec.ts`, `notification-bell.spec.ts`

### E2E

- `e2e/tests/recurring-expenses.spec.ts` — `goto('/fixkosten')`, Heading «Erkannte Abos», URL-Assertion; neuer Fall «`/abos` leitet auf `/fixkosten` um»

### Doku

- `docs/requirements/US-06-safe-to-spend.md` — neues AC für erkannte Abos
- `docs/requirements/US-08-wiederkehrende-ausgaben.md` — Abo-Übersicht ist seit FE-FC-05 ein Abschnitt auf `/fixkosten`
- `docs/adr/ADR-13-fixkosten-transaktions-zuordnung.md` — Nachtrag FE-FC-05
- `docs/CONVENTIONS.md` — Frontend-Feature-Liste

## Implementierungsschritte

1. Port, Repository-Query und Service-Implementierung im `recurring`-Modul, mit Unit-Test.
2. `FixedCostDebitMatcher` erweitern (Multiset-Differenz, 3-Arg-`variableExpenses`), Tests.
3. `SafeToSpendService` verdrahten, Javadoc und Formel nachziehen, Unit- und Integrationstests.
4. `RecurringExpenseList` zum Abschnitt umbauen, in `FixedCostList` einbetten, Specs.
5. Route-Redirect, Teaser-Link, Glocken-Navigation, Specs.
6. E2E anpassen (+ Redirect-Fall), lokal gegen das prod-JAR laufen lassen.
7. Doku (US-06, US-08, ADR-13-Nachtrag, CONVENTIONS).

## Test-Strategie

- **Unit (JUnit):** Matcher-Regel isoliert; Service-Formel mit gemockten Ports inkl. `never()`-Nachweisen.
- **Integration (Spring Boot + Testcontainers):** DETECTED/DISMISSED über echte Zeilen, Mandantentrennungs-Gegenprobe mit fremdem User.
- **Unit (Vitest/TestBed):** eingebetteter Abschnitt, Redirect-Ziele in Teaser und Glocke, bestehende Abo-Tests unverändert grün.
- **E2E (Playwright):** die vier bestehenden Abo-Szenarien auf `/fixkosten`, plus Redirect `/abos → /fixkosten`.

Nicht Teil dieses PRs (per Issue): Datenmodell-Merge, Promotion Abo → Fixkosten-Position,
`exceedsIncome`-Warnung unter Einbezug der Abos.

## Acceptance Criteria (aus dem Issue)

- [ ] `/fixkosten` zeigt zusätzlich zur bestehenden Fixkosten-Tabelle einen Abschnitt "Erkannte Abos"
      mit den heutigen Inhalten der `/abos`-Liste (Empfänger, "seit <Monat>", Betrag, "Neu"-Badge)
- [ ] "Kein Abo"-Aktion (Dismiss) bleibt unverändert: idempotent, ohne Bestätigungsdialog, ohne Undo
- [ ] Der bestehende Abschnitt für dismissed Abos (read-only, FE-NOTIF-03) ist auf der neuen Seite
      ebenfalls vorhanden
- [ ] Route `/abos` entfällt oder leitet auf `/fixkosten` um; Dashboard-Teaser-Card und
      Notification-Bell-Link zu Abos verlinken neu auf `/fixkosten`
- [ ] `/fixkosten` bleibt der einzige Nav-Eintrag für diesen Bereich (kein neuer Nav-Eintrag für Abos)
- [ ] Safe-to-Spend-Berechnung berücksichtigt erkannte, nicht dismissed `RecurringExpense`-Einträge
      analog zu `FixedCostDebitMatcher` (ein Abzug pro Position aus den variablen Ausgaben, keine
      Doppelzählung)
- [ ] Bestehende Fixkosten-CRUD-Funktionalität (Edit/Delete, Onboarding-Erstellung) ist unverändert
      funktionsfähig

## Review-Runde PR #345 (2026-09-21)

Zwei blockierende Befunde (danielwagner990), beide mit derselben Wurzel — `recurring_expenses.amount`
ist eine nie aktualisierte Momentaufnahme, die dieser Task zum finanziellen Eingabewert macht:

1. **Abweichende Abbuchung zählte doppelt.** Die Erkennung erlaubt ±2 %, der Matcher strich
   rappengenau. Fix: `RecurringExpenseAmountPort.withinTolerance` als eine Regel für Erkennung
   und Matcher; `FixedCostDebitMatcher.match` liefert beide Summanden aus einem Durchgang und
   zählt auf der Fixkosten-Seite den gestrichenen Betrag. Die Deduplizierung gegen Positionen
   nutzt dieselbe Toleranz (statt rappengenau wie geplant).
2. **Eine beendete Abo-Zeile minderte den Safe-to-Spend dauerhaft.** Fix: `detectedAmounts(userId,
   month)` liefert nur Abos mit einer Abbuchung in `[month − 2, month]`, über die gefensterte
   `ExpenseHistoryPort.expenseHistory(userId, from, to)`. Die Wurzel (Neubewertung beim Import)
   ist [#350](https://github.com/dfme/budget-buddy/issues/350) (BE-REC-04).

Nicht blockierend, im PR behoben: Intro-Text präzisiert, `pathMatch: 'full'` am Redirect.
Folge-Issues: [#351](https://github.com/dfme/budget-buddy/issues/351) (FE-FC-06,
Anchor-Scrolling), [#352](https://github.com/dfme/budget-buddy/issues/352) (FE-UI-10,
Heading-Level an `app-card`).
