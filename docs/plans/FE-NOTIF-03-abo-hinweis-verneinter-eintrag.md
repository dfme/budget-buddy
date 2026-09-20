# [FE-NOTIF-03] Verneinte Abos bleiben auf /abos sichtbar — Abschnitt «Kein Abo»

- **Issue:** [#333](https://github.com/dfme/budget-buddy/issues/333)
- **Task-ID:** `FE-NOTIF-03`
- **Branch:** `fix/FE-NOTIF-03-abo-hinweis-verneinter-eintrag`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-20

## Ausgangslage

Klickt eine Person in der Glocke auf eine `RECURRING_EXPENSE_DETECTED`-Benachrichtigung, deren
Abo-Eintrag inzwischen per «Kein Abo» verneint wurde, landet sie auf `/abos` — und der Eintrag
war dort nicht zu sehen, weil `GET /api/recurring-expenses` bis dahin hart auf `DETECTED` filterte
(`RecurringExpenseService.java:250`).

## Verworfene Variante: «gelesene Benachrichtigung navigiert nicht mehr»

Der Issue-Text schlägt als einfachsten Fix vor, den Klick auf eine *gelesene* Benachrichtigung
dieses Typs nicht mehr navigieren zu lassen. Das kann AC2 nicht erfüllen, denn `read` ist kein
Stellvertreter für `DISMISSED`:

- `notification-bell.ts:112-126` — `select()` navigiert **und** markiert dieselbe
  Benachrichtigung als gelesen. Nach dem ersten, völlig normalen Klick ist sie gelesen, auch
  wenn das Abo unverändert `DETECTED` ist.
- `NotificationRepository:31` (`findByUserIdOrderByUnreadFirstThenNewest`) liefert gelesene
  Einträge weiter mit aus, `notification-bell.html:45` macht aus `read` nur eine CSS-Klasse.

Der zweite Klick auf ein intaktes Abo liefe damit ins Nichts — genau das, was AC2 verbietet.

## Weitere verworfene Varianten

**Dismiss entfernt die Benachrichtigung (Backend).** Sauber, aber dreht den Kern von BE-REC-03
(#324, PR #328) teilweise zurück und nimmt die Meldung aus der Historie.

**Hinweis-Notice auf `/abos` (nur FE, erste Fassung dieses PR).** Die Glocke gab die
`referenceId` als `?ref=` mit, die Übersicht meldete «Dieser Eintrag ist als Kein Abo markiert».
Kleinster Eingriff, aber AC1 nur sinngemäss erfüllt: der Klick landete weiterhin auf einer Seite
ohne den Eintrag. Im Review verworfen, siehe Entscheid.

## Entscheid

**Variante 3 aus dem Issue: `/abos` zeigt verneinte Einträge in einem eigenen Abschnitt
«Kein Abo».** Der Klick aus der Glocke hat damit immer ein echtes Ziel — alle drei ACs sind
wörtlich erfüllt.

Produktentscheid im Review von PR #335 (dfme, 2026-09-20). Die erste Fassung des PR hatte
stattdessen einen Hinweis-Notice auf `/abos` gesetzt (die Glocke gab die `referenceId` als
`?ref=` mit, die Übersicht meldete den fehlenden Eintrag). Das erfüllte AC1 nur sinngemäss und
wurde deshalb verworfen — zugunsten der einzigen Variante, bei der der Eintrag wirklich da ist.

**Was der Entscheid an US-08 ändert:** AC3 («wird sie aus der Abo-Übersicht entfernt») heisst
seither «aus der Liste der Abos entfernt, auf der Seite unter Kein Abo weiterhin sichtbar». Der
Wortlaut in `docs/requirements/US-08-wiederkehrende-ausgaben.md` ist entsprechend präzisiert.

### API-Form

`GET /api/recurring-expenses` liefert beide Status in einer Liste; das DTO trägt `status` bereits.
Die Übersicht braucht damit einen Request statt zwei, der Client trennt nach `status`. Einzige
weitere Konsumentin ist die Dashboard-Teaser-Card (`count()`), die nur `DETECTED` zählt.
Verworfen: `?status=` — zwei Requests für `/abos` oder ein Sonderwert `ALL` für nichts.

### Kein `?ref`

Der Eintrag ist auf der Seite; ein Highlight des gemeinten Eintrags wäre Scope-Creep. Die
Glocke navigiert unverändert nach `/abos`.

## Betroffene Dateien

| Datei | Änderung |
| --- | --- |
| `backend/…/RecurringExpenseRepository.java` | `findByUserIdAndStatusOrderByPayeeKeyAsc` → `findByUserIdOrderByPayeeKeyAsc` |
| `backend/…/RecurringExpenseService.java` | `list` liefert beide Status; Javadoc zu `list` und `dismiss` |
| `backend/…/RecurringExpenseController.java` | OpenAPI-Beschreibung von `GET` |
| `backend/…/RecurringExpenseServiceTest.java`, `…ControllerIntegrationTest.java` | Mocks/Assertions auf beide Status |
| `frontend/…/recurring-expense.model.ts` | Doku zu `status` |
| `frontend/…/recurring-expense.service.ts` | `detected`/`dismissed` als `computed`, `count` aus `detected`, `dismiss()` ersetzt statt entfernt |
| `frontend/…/recurring-expense-list.{ts,html,scss}` | zweite Card «Kein Abo» (`li.dismissed-expense`), nur wenn nicht leer; ohne «Neu», ohne Button |
| `frontend/…/recurring-expense-list.spec.ts`, `…service.spec.ts` | siehe Test-Strategie |
| `e2e/tests/recurring-expenses.spec.ts` | Alt-Pfad: zwei zusätzliche Assertions auf den Abschnitt |
| `docs/requirements/US-08-wiederkehrende-ausgaben.md` | AC3 präzisiert |

## Test-Strategie

Backend JUnit, Angular TestBed/Vitest und der bestehende Playwright-Alt-Pfad, additiv erweitert —
die Abo-Card oben bleibt unverändert (`li.expense → 0`, `p.status.empty` bleiben wahr), der neue
Abschnitt hat eigene Klassen.

Backend

- `list` liefert `DETECTED` und `DISMISSED` mit ihrem Status (Unit + IT)
- Nach `dismiss` bleibt der Eintrag in `GET`, mit `status=DISMISSED`, `isNew=false` (IT)
- Mandantentrennung unverändert (IT)

`recurring-expense.service.spec.ts`

- Trennung nach Status, `count` zählt nur `DETECTED`
- `dismiss` ersetzt den Eintrag an Ort und Stelle

`recurring-expense-list.spec.ts`

- Verneinter Eintrag im Abschnitt «Kein Abo», ohne «Neu», ohne Button; Abo-Liste enthält ihn
  nicht → AC1 + US-08 AC3
- Kein Abschnitt ohne Verneinte
- Leerzustand und Abschnitt zugleich, wenn nur Verneinte da sind
- «Kein Abo» verschiebt die Zeile in den Abschnitt

`e2e/tests/recurring-expenses.spec.ts` (Alt-Pfad)

- Nach dem Dismiss steht der Empfänger unter «Kein Abo», ohne Button; nach dem Reload weiterhin

## Acceptance Criteria (aus dem Issue)

- [x] Ein Klick auf eine `RECURRING_EXPENSE_DETECTED`-Benachrichtigung, deren Eintrag `DISMISSED`
      ist, führt nicht auf eine Seite, auf der der Eintrag fehlt — er steht unter «Kein Abo»
- [x] Ein Klick auf eine Benachrichtigung, deren Eintrag noch `DETECTED` ist, führt weiterhin
      nach `/abos` (US-08 AC2 bleibt erfüllt) — die Glocke ist unverändert
- [x] Test deckt den gewählten Weg ab (JUnit, Vitest/TestBed und Playwright)
