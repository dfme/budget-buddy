# [FE-FC-09] Einkommen aus Einstellungen auf die Ausgaben-Seite verschieben, Seite zu «Budget» umbenennen

- **Issue:** [#360](https://github.com/dfme/budget-buddy/issues/360)
- **Task-ID:** `FE-FC-09`
- **Branch:** `feature/FE-FC-09-budget-seite`
- **Story:** US-14 — Passwort, Einkommen und Erscheinungsbild ändern; US-06 — Verweis im No-Income-Hinweis
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-23

## Entscheid

Der Abschnitt «Einkommen» (FE-SET-03, #179) zieht von den Einstellungen auf die bisherige
Ausgaben-Seite um. Diese Seite wird dabei zu **«Budget»** umbenannt (Route `/budget`, Nav-Label,
`<h1>`). Muster: FE-FC-07 (#355, `/fixkosten` → `/ausgaben`) — alle drei alten Pfade
(`/ausgaben`, `/fixkosten`, `/abos`) bleiben als Redirect erhalten.

**Wortlaut-Entscheide:**

- Neuer Hinweistext, wo bisher „…in den Einstellungen" stand: **„Bitte erfasse dein
  Monatseinkommen auf der Budget-Seite"**.
- Dashboard-Teaser-CTA „Zu Ausgaben →" → „Zu Budget →" (folgt aus der Umbenennung, wie
  FE-FC-07 „Zu Fixkosten" → „Zu Ausgaben" änderte).
- Card «Einkommen» steht **oberhalb** der bestehenden Abschnitte, direkt unter `<h1>Budget</h1>`
  und vor der Total-Card.
- US-14: Titel/Story lassen den Standortbezug für Einkommen fallen; Passwort/Erscheinungsbild
  bleiben „in den Einstellungen", Einkommen wird „auf der Budget-Seite".

**Scope-Delta aus der breiten Suche (User-bestätigt: mitbeheben):** `SpaRoutingTest.java`,
`e2e/tests/spa-routing.spec.ts` und `e2e/fixtures/auth.fixture.ts` nennen `/ausgaben` nur als
repräsentativen Beispiel-Pfad (kein funktionaler Link) — bleiben ohne Fix technisch grün, werden
trotzdem mitgezogen, analog zum Scope-Delta, das FE-FC-07 bereits für dieselben drei Dateien
dokumentiert hat.

## Betroffene Dateien

**Frontend-Code**

- `frontend/src/app/app.routes.ts` — Route `ausgaben`→`budget`; Redirects `ausgaben`, `fixkosten`,
  `abos` → `budget` (`pathMatch: 'full'`)
- `frontend/src/app/core/layout/shell.ts` — Nav-Item `/budget` / «Budget»
- `frontend/src/app/onboarding/fixed-cost-list.html` / `.ts` / `.scss` — `<h1>Budget</h1>`;
  Einkommens-Card + Formularlogik (verschoben, `MIN_BETRAG_CHF`/`maxTwoDecimals` aus
  `fixed-cost.validators.ts` wiederverwendet statt dupliziert)
- `frontend/src/app/settings/settings.html` / `.ts` / `.scss` — Card «Einkommen» inkl. Code/Imports
  entfernt
- `frontend/src/app/dashboard/dashboard.html` — Hinweistext, Teaser-Link + CTA + Kommentar
- `frontend/src/app/notifications/notification-bell.ts` — Navigationsziel + Javadoc
- `frontend/src/app/styleguide/styleguide.html` — Hinweistext

**Frontend-Tests**

- `frontend/src/app/app.routes.spec.ts` — neu für `/budget` + drei Redirects
- `frontend/src/app/core/layout/shell.spec.ts`, `frontend/src/app/notifications/notification-bell.spec.ts`
- `frontend/src/app/dashboard/dashboard.spec.ts` — Hinweistext-Assertions
- `frontend/src/app/settings/settings.spec.ts` — Einkommens-Tests entfernt, „vier"→„drei
  Abschnitte", Settings↔Dashboard-Integrationstest entfernt (zieht um)
- `frontend/src/app/onboarding/fixed-cost-list.spec.ts` — Einkommens-Formular-Tests
  (übernommen/angepasst) + Integrationstest „Route /budget" (Einkommensänderung wirkt ohne Reload
  aufs Dashboard) + Titel-Assertions

**E2E**

- `e2e/tests/fixed-cost-wizard.spec.ts`, `e2e/tests/recurring-expenses.spec.ts` — `/ausgaben`→`/budget`,
  Überschriften, dritter alter Pfad im Redirect-Loop
- `e2e/tests/spa-routing.spec.ts`, `e2e/fixtures/auth.fixture.ts` — Scope-Delta

**Backend**

- `backend/src/test/java/com/budgetbuddy/config/SpaRoutingTest.java` — Scope-Delta

**Doku**

- `docs/requirements/US-06-safe-to-spend.md` — Hinweistext-Zitat + „in den Einstellungen"
- `docs/requirements/US-14-einstellungen.md` — Titel/Story/ACs für den neuen Einkommens-Standort
- `docs/requirements/US-08-wiederkehrende-ausgaben.md` — `/ausgaben` → `/budget`
- `docs/CONVENTIONS.md` — Frontend-Feature-Liste (`onboarding/`, `settings/`)

## Implementierungsschritte

1. Route + Redirects in `app.routes.ts`, Nav-Label in `shell.ts`
2. Einkommens-Card samt Logik von `settings.*` nach `onboarding/fixed-cost-list.*` verschieben
3. `settings.*` bereinigen (Card, Code, Imports, Klassendoc)
4. Alle Verweise auf den alten Ort/Pfad aktualisieren (Dashboard, Styleguide, Notification-Bell)
5. Frontend-Unit-Tests anpassen/verschieben (Settings, FixedCostList, Shell, Notification-Bell,
   Dashboard, app.routes)
6. E2E-Tests anpassen (Ausgaben-Seite → Budget-Seite)
7. Scope-Delta: `SpaRoutingTest.java`, `spa-routing.spec.ts`, `auth.fixture.ts`
8. Doku aktualisieren (US-06, US-14, US-08, CONVENTIONS.md)

## Test-Strategie

- Vitest: Component-Tests für das verschobene Einkommens-Formular auf `FixedCostList` (Vorbelegung,
  optional-Hinweis, Vorschlag übernehmen, Validierungsfehler, Speichern/Fehlerpfade) — angepasst aus
  den bisherigen `settings.spec.ts`-Tests
- Vitest: Router-Level-Tests für `/budget` und die drei Redirects (`app.routes.spec.ts`), Nav-Label
  (`shell.spec.ts`), Navigationsziel der Glocke (`notification-bell.spec.ts`)
- Vitest: Integrationstest „Einkommensänderung wirkt ohne Reload aufs Dashboard" — verschoben von
  `settings.spec.ts` nach `fixed-cost-list.spec.ts`, jetzt über `/budget`
- Playwright: bestehende Ausgaben-Seite-Szenarien auf `/budget` umgestellt, Redirect-Test um den
  dritten alten Pfad (`/ausgaben`) ergänzt

## Acceptance Criteria (aus dem Issue)

- [ ] Neue Route `/budget` lädt `FixedCostList`; `/ausgaben`, `/fixkosten`, `/abos` redirecten
      (`pathMatch: 'full'`) auf `/budget`
- [ ] Nav-Eintrag in der App-Shell zeigt Label «Budget» statt «Ausgaben»
- [ ] `<h1>` auf der Seite lautet «Budget»
- [ ] Card «Einkommen» erscheint auf der Budget-Seite, Verhalten identisch zum bisherigen Abschnitt
- [ ] Card «Einkommen» ist aus `settings.html`/`settings.ts` entfernt; Einstellungen zeigen nur noch
      «Passwort», «Erscheinungsbild» und «Konto löschen»
- [ ] Alle Stellen, die auf den bisherigen Ort verweisen, sind aktualisiert (Dashboard, Styleguide,
      zugehörige Specs)
- [ ] Alle internen Links/Navigationsaufrufe, die bisher `/ausgaben` ansteuern, zeigen neu auf
      `/budget`
- [ ] Component-Test deckt Einkommens-Formular auf der neuen Seite sowie den Redirect ab
- [ ] Doku aktualisiert (US-06, US-14, CONVENTIONS.md, `app.routes.ts`-Kommentar)

## Nachtrag: Einkommen auch im Onboarding-Wizard (User-Anfrage nach Plan-Bestätigung)

Nach der ursprünglichen Umsetzung kam die Anfrage, das Einkommen auch im Onboarding-Wizard
erfassbar zu machen — Lara soll es nicht erst nach dem Onboarding auf der Budget-Seite nachtragen
müssen. Damit hätte die Einkommens-Card einen dritten Kopie-Ort gebraucht (nach `settings.ts` und
`fixed-cost-list.ts`); stattdessen wurde sie in eine eigene Komponente extrahiert, analog
{@link RecurringExpenseList}:

- **Neu** `frontend/src/app/income/income-card.ts` / `.html` / `.scss` / `.spec.ts` — Formular,
  Signale und Methoden unverändert aus dem ursprünglichen `fixed-cost-list.ts` übernommen, inkl.
  der Zwischenüberschrift «Einkommen» (ausserhalb der Card, analog «Erfasste Fixkosten» — zwei
  Layout-Korrekturen aus dem User-Review der ursprünglichen Umsetzung: die Überschrift stand
  zunächst als `app-card`-Titel, und der Card fehlte der Abstand nach unten, weil der Container
  keinen eigenen `gap` hat).
- `frontend/src/app/onboarding/fixed-cost-list.ts`/`.html`/`.scss` — Einkommens-Code entfernt,
  bindet stattdessen `<app-income-card />` ein.
- `frontend/src/app/onboarding/fixed-cost-wizard.ts`/`.html` — bindet `<app-income-card />`
  zusätzlich über dem Fixkosten-Formular ein; unabhängig vom Abschluss-Button (Onboarding lässt
  sich weiterhin ohne erfasstes Einkommen abschliessen).
- Tests entsprechend verschoben: `income-card.spec.ts` (neu, deckt das Formular selbst ab),
  `fixed-cost-list.spec.ts` und `fixed-cost-wizard.spec.ts` prüfen nur noch die Einbettung.
- Doku: `docs/CONVENTIONS.md` (neuer Ordner `income/`), `docs/requirements/US-14-einstellungen.md`
  (neuer Pfad, zweiter Einbettungsort), `docs/requirements/US-03-fixkosten-wizard.md` (Hinweis,
  keine neue AC — das Feld bleibt optional und ändert AC1 nicht).

## Nachtrag 2: Seitenstruktur des Onboarding-Wizards (weiteres User-Feedback)

Der Wizard behielt nach dem ersten Nachtrag seinen alten Titel «Fixkosten erfassen», obwohl die
Einkommens-Card jetzt darüber stand — ein Titel, der nur den zweiten Abschnitt benennt. Zusätzlich
bezog sich die Beschriftung des Abschluss-Buttons («Keine Fixkosten — weiter zum Dashboard»)
ausschliesslich auf Fixkosten und war irreführend, sobald nur ein Einkommen gespeichert wurde.
Behoben:

- `frontend/src/app/onboarding/fixed-cost-wizard.html` — `<h1>` von «Fixkosten erfassen» zu
  «Budget» (deckungsgleich mit der Budget-Seite); Lead-Text nennt jetzt beide Angaben und weist
  darauf hin, dass sich beides später unter «Budget» nachtragen lässt; neue Zwischenüberschrift
  `<h2>Fixkosten</h2>` vor der Fixkosten-Card, analog dem `<h2>Einkommen</h2>` aus `IncomeCard`.
- `frontend/src/app/onboarding/fixed-cost-wizard.ts` — neues `hasEnteredData` (computed aus
  `hasSaved` **oder** `incomeSection()?.incomeSaved()`, per `viewChild(IncomeCard)` gelesen)
  ersetzt `hasSaved` als Grundlage der Button-Beschriftung; «Keine Fixkosten» → «Später erfassen».
- Tests: `fixed-cost-wizard.spec.ts` (Beschriftungs-Tests umbenannt/ergänzt, u. a. ein Fall, der
  nur das Einkommen speichert und die «Fertig»-Beschriftung erwartet), `onboarding-completion.spec.ts`
  und `auth.spec.ts`/`fixed-cost-wizard.spec.ts` (e2e) für die neue Überschrift «Budget» und die
  neue Button-Beschriftung.
- Doku: `docs/requirements/US-03-fixkosten-wizard.md` (Hinweis präzisiert).

## Nachtrag 3: Submit-Button-Verhalten der beiden Onboarding-Formulare angeglichen

Auffällig im direkten Vergleich auf derselben Seite: der Einkommen-Button war standardmässig
deaktiviert (bis das Feld gültig ist), der Fixkosten-Button liess sich immer klicken — Fehler
zeigten sich erst nach dem Klick (`markAllAsTouched()`). Auf Rückfrage entschieden: Fixkosten wird
an Einkommen angeglichen (Mehrheitsmuster in der App — Passwort-Formular und
Konto-löschen-Dialog deaktivieren ebenfalls proaktiv).

- `frontend/src/app/onboarding/fixed-cost-wizard.html` — Submit-Button neu
  `[disabled]="form.invalid || submitting()"` statt nur `submitting()`. Die interne Guard-Klausel
  in `submit()` (`if (form.invalid) { markAllAsTouched(); return; }`) bleibt als Absicherung gegen
  ein Absenden per Enter-Taste bestehen — ein disabled-Button verhindert das nicht.
- Tests: neue Unit-Tests für den Sperr-/Freigabe-Zustand des Buttons
  (`fixed-cost-wizard.spec.ts`). Der E2E-Fehlerpfad-Test (`fixed-cost-wizard.spec.ts`, e2e) liess
  sich nicht mehr per Klick auf ein leeres Formular auslösen — umgebaut auf Fokuswechsel (blur)
  pro Feld, mit zusätzlicher Zusicherung, dass der Button dabei durchgehend deaktiviert bleibt.

## Nachtrag 4: Überschriften-Hierarchie der Budget-Seite

Auf der Budget-Seite standen «Erfasste Fixkosten» und «Erkannte Abos» als h2 direkt unter der h1
«Budget» — auf gleicher Ebene wie «Einkommen», obwohl beide inhaltlich zusammengehören (Fixkosten
und Abos sind beides «Ausgaben», im Gegensatz zum Einkommen). Ergänzt:

- `frontend/src/app/onboarding/fixed-cost-list.html`/`.scss` — neue gruppierende
  Zwischenüberschrift `<h2>Ausgaben</h2>` vor «Erfasste Fixkosten»; «Erfasste Fixkosten» selbst
  von h2 zu h3.
- `frontend/src/app/recurring/recurring-expense-list.html`/`.scss` — «Erkannte Abos» von h2 zu h3
  (dieselbe Komponente, nur auf der Budget-Seite eingebettet, siehe `fixed-cost-list.spec.ts`).
- Tests: `fixed-cost-list.spec.ts`, `recurring-expense-list.spec.ts` (Heading-Level-Assertions
  angepasst, neue Zusicherung für «Ausgaben»), `recurring-expenses.spec.ts` (e2e, Heading-Level
  in den Redirect- und Happy-Path-Tests).
- Der Onboarding-Wizard (`fixed-cost-wizard.html`) ist bewusst unverändert: dort gibt es keine
  «Ausgaben»-Gruppierung, «Einkommen» und «Fixkosten» stehen dort als zwei parallele h2-Abschnitte
  ohne gemeinsames Dach.
