# [FE-FC-07] Fixkosten-Seite zu «Ausgaben» umbenennen (Route, Nav, Titel) und kombiniertes Total anzeigen

- **Issue:** [#355](https://github.com/dfme/budget-buddy/issues/355)
- **Task-ID:** `FE-FC-07`
- **Branch:** `feature/FE-FC-07-ausgaben-seite`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-22

## Ausgangslage

Seit FE-FC-05 (#338) zeigt `/fixkosten` die Fixkosten-Tabelle **und** den Abschnitt «Erkannte
Abos». Route, Nav-Label und `<h1>` heissen weiterhin «Fixkosten», und der Kommentar in
`app.routes.ts` begründet das ausdrücklich («keinen Mehrwert», Bookmarks). Dieses Issue kehrt
den Entscheid um: die Seite heisst «Ausgaben», beide alten Pfade leiten um, und eine neue
Kennzahl summiert Fixkosten und erkannte Abos.

Vor der Umsetzung entstand ein Mockup (Desktop + Mobile, echte Tokens der App):
https://claude.ai/artifact/AiQeDbbxDuG4p56Y6FPwxm. Zwei Punkte daraus gingen über die ACs des
Issues hinaus und wurden beim Planen entschieden (siehe Entscheide); ein dritter — Icons und
schmale Tabelle auf Mobile — wurde als [#356 (FE-FC-08)](https://github.com/dfme/budget-buddy/issues/356)
ausgelagert und ist «Blocked by» #355.

## Entscheide

| Punkt | Entscheid |
| ----- | --------- |
| Total-Platz | Eigene Card «Monatliche fixe Ausgaben» direkt unter dem Seitenkopf, über der Einkommens-Warnung — die Kennzahl der Seite. Betrag in `$fs-xl` (32px), bewusst nicht `$fs-hero`: das bleibt dem Safe-to-Spend vorbehalten. Darunter die Aufschlüsselung «CHF x Fixkosten + CHF y erkannte Abos», damit sichtbar ist, dass es eine einfache Addition ist. |
| Total-Quelle | `summary().summeMonatlich` + Σ `amount` über `RecurringExpenseService.detected()` (nur `DETECTED`; Verneinte sind dort schon ausgefiltert). Addition in Rappen (`Math.round(x * 100)`), damit `27.92 + 17.9` nicht `45.8199…` ergibt. **Ohne** die Dedupe-/Toleranz-Logik des `FixedCostDebitMatcher` — das Total kann deshalb von der Safe-to-Spend-Minderung abweichen, wenn ein Abo einer Fixkosten-Position entspricht. Im Javadoc benannt. |
| Total bei Ladefehler | Ausblenden, sobald Fixkosten *oder* Abos nicht geladen sind. Eine Zahl, die still einen Summanden unterschlägt, ist schlimmer als keine. |
| Lade-/Fehlerzustand der Abos | `FixedCostList` liest ihn per `viewChild(RecurringExpenseList)` von der eingebetteten Komponente (`loading()`, `errorMessage()` sind public). Der Service bleibt unverändert; die Abhängigkeit «Seite kennt ihren Abschnitt» bleibt lokal. |
| Zwischenüberschrift | Neues `<h2>` «Erfasste Fixkosten» über der Tabelle — Spiegel zu «Erkannte Abos» (*erfasst* von der Nutzerin, *erkannt* vom System). Ohne sie wäre die Tabelle nach dem Rename der einzige Abschnitt ohne Überschrift. Der bisherige `aria-label="Fixkosten-Tabelle"` wird zu `aria-labelledby` auf diese Überschrift. |
| «+ Neue Position» | Zieht vom Seitenkopf in die Zeile der Zwischenüberschrift. Der Button legt eine Fixkosten-Position an, kein Abo — neben dem h1 «Ausgaben» suggerierte er sonst mehr. Vor FE-FC-05 *war* der Seitenkopf der einzige sinnvolle Platz; FE-FC-05 hat die Seitenstruktur nicht angefasst. |
| Redirects | `fixkosten` und `abos` beide `pathMatch: 'full'` → `ausgaben`. Ohne Guards: das Ziel bringt seine eigenen mit. Der alte Kommentar in `app.routes.ts` wird **ersetzt**, nicht ergänzt. |
| Teaser-CTA | «Zu Fixkosten & Abos →» wird «Zu Ausgaben →» — ein Link, der nach dem Klick eine anders benannte Seite zeigt, ist ein Naming-Rest. Ausserhalb der ACs, im PR-Body deklariert. |
| Scope-Delta aus der breiten Suche | `SpaRoutingTest.java` und `spa-routing.spec.ts` führen `/fixkosten` als «repräsentative Route, deckungsgleich mit `app.routes.ts`»; `auth.fixture.ts` zählt die guard-geschützten Routen im Kommentar auf. Alle drei auf `/ausgaben`, im PR-Body deklariert. |
| Nicht in diesem Issue | Icons an Bearbeiten/Löschen/Kein Abo, dreispaltige Mobile-Tabelle → #356. |

## Betroffene Dateien

### Frontend — ändern

- `frontend/src/app/app.routes.ts` — Route `ausgaben` mit Guards + `loadComponent`; `fixkosten` und `abos` als Redirects; neuer Kommentar
- `frontend/src/app/core/layout/shell.ts` — Nav-Eintrag `{ path: '/ausgaben', label: 'Ausgaben', icon: '▦' }`
- `frontend/src/app/onboarding/fixed-cost-list.ts` — `RecurringExpenseService` injizieren, `viewChild(RecurringExpenseList)`, `computed` `monthlyTotal`; Klassen-Javadoc
- `frontend/src/app/onboarding/fixed-cost-list.html` — `<h1>Ausgaben</h1>` allein im Seitenkopf; Total-Card; `<h2>` «Erfasste Fixkosten» + Button in einer Zeile; `aria-labelledby`
- `frontend/src/app/onboarding/fixed-cost-list.scss` — Total-Card, `.section-header`
- `frontend/src/app/dashboard/dashboard.html` — `routerLink="/ausgaben"`, CTA «Zu Ausgaben →», Kommentar
- `frontend/src/app/dashboard/dashboard.ts` — Javadoc
- `frontend/src/app/notifications/notification-bell.ts` — `navigate(['/ausgaben'])`, Javadoc
- `frontend/src/app/recurring/recurring-expense-list.ts` — Javadoc
- `frontend/src/app/recurring/recurring-expense-list.scss` — Kommentar

### Frontend — Tests

- **neu** `frontend/src/app/app.routes.spec.ts` — Redirects am echten Router (Muster: `onboarding.guard.spec.ts`, «am echten Router»): `/fixkosten` → `/ausgaben`, `/abos` → `/ausgaben`, `/abos/x` **nicht** umgeleitet (Beleg für `pathMatch: 'full'`); Guard-Zuordnung von `ausgaben`
- `frontend/src/app/onboarding/fixed-cost-list.spec.ts` — h1/h2; Total = 1227.92 + 17.90; `DISMISSED` zählt nicht; Total ausgeblendet bei Abo-Fehler und bei Fixkosten-Fehler
- `frontend/src/app/core/layout/shell.spec.ts`, `notifications/notification-bell.spec.ts`, `dashboard/dashboard.spec.ts`, `recurring/recurring-expense-list.spec.ts` — Pfade/Labels nachziehen

### E2E

- `e2e/tests/recurring-expenses.spec.ts` — `goto('/ausgaben')`; Redirect-Test für **beide** alten Pfade; neuer Test: Fixkosten-Position 1200 per API + Abo-Import 15.90 → Total `CHF 1’215.90`, nach «Kein Abo» `CHF 1’200.00`
- `e2e/tests/fixed-cost-wizard.spec.ts`, `e2e/tests/spa-routing.spec.ts`, `e2e/fixtures/auth.fixture.ts`

### Backend

- `backend/src/test/java/com/budgetbuddy/config/SpaRoutingTest.java` — `/fixkosten` → `/ausgaben`

### Doku

- `docs/requirements/US-08-wiederkehrende-ausgaben.md` — Hinweisblock
- `docs/CONVENTIONS.md` — Frontend-Feature-Liste (`onboarding/`: Seite `/ausgaben`)
- `docs/plans/FE-FC-07-ausgaben-seite.md`, `docs/plans/README.md`

## Implementierungsschritte

1. Plan ablegen, Index-Zeile, Branch
2. `app.routes.ts`: Route + zwei Redirects + neuer Kommentar
3. Nav-Label, h1, Dashboard-Teaser, Notification-Bell-Ziel, Javadocs
4. Total: `FixedCostList` erweitern (Service, viewChild, computed), Template (Total-Card, h2, Button-Umzug), SCSS
5. Unit-Specs nachziehen, neue Tests (`app.routes.spec.ts`, `fixed-cost-list.spec.ts`)
6. E2E + `SpaRoutingTest` + `auth.fixture` nachziehen, neuer E2E-Total-Test
7. Doku (US-08, CONVENTIONS)
8. `ng test`, `ng build`, `ng lint`, `SpaRoutingTest`, E2E `recurring-expenses` + `spa-routing` + `fixed-cost-wizard`
9. Security-Review (Matrix: Zeile 4 Secrets; kein Endpoint, kein Claude-Call, keine Nutzerdaten-Query berührt) + lokaler Review → Bestätigung → PR

## Test-Strategie

| Ebene | Was |
| ----- | --- |
| Unit (Vitest/TestBed) | Redirects am echten Router inkl. `pathMatch: 'full'`-Gegenprobe; Total-Berechnung inkl. `DISMISSED`-Ausschluss und beide Fehlerfälle; Nav/Bell/Teaser-Ziele |
| Backend (JUnit) | `SpaRoutingTest` mit `/ausgaben` |
| E2E (Playwright) | Beide Redirects per Hard-Load (trifft Server-Catch-all *und* Angular-Redirect); Total mit echter Fixkosten-Position + importiertem Abo, vor und nach «Kein Abo» |

## Acceptance Criteria (aus dem Issue)

- [ ] Neue Route `/ausgaben` lädt `FixedCostList`; `/fixkosten` **und** `/abos` redirecten (`pathMatch: 'full'`) auf `/ausgaben`
- [ ] Nav-Eintrag in `shell.ts` zeigt «Ausgaben» statt «Fixkosten»
- [ ] `<h1>` auf der Seite lautet «Ausgaben»
- [ ] Dashboard-Teaser und Notification-Bell (`RECURRING_EXPENSE_DETECTED`) zeigen auf `/ausgaben`
- [ ] Neues Summen-Element: `summeMonatlich` + Summe der angezeigten «Erkannte Abos»-Beträge, einfache Addition ohne `FixedCostDebitMatcher`
- [ ] Verneinte Abos («Kein Abo») fliessen **nicht** ins Total ein
- [ ] Component-/E2E-Test deckt Redirect und Total-Anzeige ab
- [ ] Doku aktualisiert (US-08-Hinweisblock, `docs/CONVENTIONS.md`, `app.routes.ts`-Kommentar)
