# [FE-REC-01] Abo-Übersicht-Screen

- **Issue:** [#255](https://github.com/dfme/budget-buddy/issues/255)
- **Task-ID:** `FE-REC-01`
- **Branch:** `feature/FE-REC-01-abo-uebersicht`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-15

## Kontext

Fünfter Task der US-08-Kette aus [us-08-09-12-breakdown.md](us-08-09-12-breakdown.md), nach
`BE-REC-02` (#254, REST-Endpoints) und `FE-NOTIF-01` (#247, Glocke). Dieser Task baut den
Frontend-Screen für die Abo-Übersicht und den Einstieg vom Dashboard aus. Die Erkennung selbst
und die Endpoints existieren bereits; Playwright-Abdeckung ist `E2E-REC-01`.

## Entscheide

Mit dem User bestätigt:

- **Route `/abos`**, deutsch wie `fixkosten`/`einstellungen`, hinter `authGuard` +
  `onboardingGuard`. **Kein Nav-Eintrag** — der Einstieg läuft laut AC über die Teaser-Card auf
  dem Dashboard. Die mobile Tab-Bar hat vier Ziele; ein fünftes wäre eng.
- **Teaser-Card immer sichtbar.** Bei 0 Einträgen steht «Keine Abos erkannt», sonst
  «N Abo(s) erkannt»; die Card verlinkt in jedem Fall nach `/abos`. Ein Ladefehler bleibt still
  — sekundäre Funktion, dieselbe Abwägung wie bei `Dashboard.loadUncertainCount`.
- **«Gruppierte Liste» = eine Zeile pro erkannter Gruppe** (Empfänger + Betrag), so wie
  `GET /api/recurring-expenses` sie liefert. Die Einzeltransaktionen einer Gruppe sind nicht Teil
  der DTO und werden nicht angezeigt. Sortierung kommt vom Backend (alphabetisch nach `payeeKey`).
- **«Kein Abo» entfernt den Eintrag lokal aus dem State**, kein Reload: die Antwort ist
  `status=DISMISSED`, und `GET` liefert nur `DETECTED` — ein Reload wäre reine Wiederholung.
  **Kein Bestätigungs-Modal:** anders als beim Fixkosten-Löschen geht kein Datensatz verloren
  (der Eintrag bleibt als `DISMISSED` erhalten), und das Backend ist idempotent.
- **Scope-Erweiterung (breite Suche, Schritt 2):** `notification.model.ts` hielt fest, dass kein
  Frontend-Consumer `type`/`referenceId` interpretiert, mit Verweis auf dieses Issue. Ein Klick
  auf eine `RECURRING_EXPENSE_DETECTED`-Benachrichtigung in der Glocke navigiert neu nach
  `/abos` (zusätzlich zum bestehenden `markAsRead`); der Kommentar wird aktualisiert. Wird im
  PR-Body deklariert.

## Betroffene / neue Files

### Neu — `frontend/src/app/recurring/`

- `recurring-expense.model.ts` — `RecurringExpenseResponse`, spiegelt das Backend-DTO
- `recurring-expense.service.ts` — Signal-State analog `NotificationService`: `expenses`,
  `count`; `load()`, `dismiss(id)`, `clear()`
- `recurring-expense.service.spec.ts`
- `recurring-expense-list.ts` / `.html` / `.scss` — der Screen
- `recurring-expense-list.spec.ts`

### Geändert

- `frontend/src/app/app.routes.ts` — Route `abos`
- `frontend/src/app/dashboard/dashboard.ts` / `.html` / `.scss` — Teaser-Card
- `frontend/src/app/dashboard/dashboard.spec.ts` — Flush des neuen Requests, neue Fälle
- `frontend/src/app/notifications/notification-bell.ts` / `.spec.ts` — Navigation nach `/abos`
- `frontend/src/app/notifications/notification.model.ts` — Kommentar
- `frontend/src/app/core/layout/shell.ts` — `logout()` leert den neuen Service
- `docs/plans/README.md` — neue Zeile

## Implementierungsschritte

1. Model + Service + Service-Spec
2. Listen-Component + Spec, Route
3. Dashboard-Teaser + Spec-Anpassungen
4. Glocke → Navigation + Spec, Kommentar im Model, Logout-Clear
5. `npm test`, `npm run lint`, `npm run build`
6. Security-Review + lokaler Review, PR

## Test-Strategie

Vitest/Angular TestBed mit `HttpTestingController`, im Stil von `notification.service.spec.ts`
und `dashboard.spec.ts`:

- Service: `load` schreibt State, `dismiss` sendet `POST /api/recurring-expenses/{id}/dismiss`
  und entfernt den Eintrag, `clear` leert
- Liste: Happy Path, «Neu»-Label nur bei `isNew`, «Kein Abo» entfernt die Zeile, Leer-, Fehler-
  und Dismiss-Fehlerzustand
- Dashboard: Teaser-Text bei N und bei 0, Link auf `/abos`
- Glocke: Klick auf Abo-Benachrichtigung navigiert nach `/abos`

Kein E2E in diesem Task — das ist `E2E-REC-01`.

## Acceptance Criteria (aus dem Issue)

- [ ] Neue Route zeigt gruppierte Liste wiederkehrender Ausgaben
- [ ] Neu erkannte Einträge tragen ein "Neu"-Label
- [ ] "Kein Abo"-Button pro Eintrag ruft `POST /api/recurring-expenses/{id}/dismiss` auf und
      entfernt den Eintrag aus der Liste
- [ ] Teaser-Card auf dem Dashboard verlinkt in die Abo-Übersicht (z. B. "3 Abos erkannt")
