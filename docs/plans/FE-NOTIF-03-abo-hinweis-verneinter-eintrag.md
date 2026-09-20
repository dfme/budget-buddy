# [FE-NOTIF-03] Klick auf Benachrichtigung eines verneinten Abos führt nach /abos ins Leere

- **Issue:** [#333](https://github.com/dfme/budget-buddy/issues/333)
- **Task-ID:** `FE-NOTIF-03`
- **Branch:** `fix/FE-NOTIF-03-abo-hinweis-verneinter-eintrag`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-20

## Ausgangslage

Klickt eine Person in der Glocke auf eine `RECURRING_EXPENSE_DETECTED`-Benachrichtigung, deren
Abo-Eintrag inzwischen per «Kein Abo» verneint wurde, landet sie auf `/abos` — und der Eintrag
ist dort nicht mehr zu sehen, weil `GET /api/recurring-expenses` hart auf `DETECTED` filtert
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

**`/abos` zeigt verneinte Einträge in eigenem Abschnitt (FE+BE).** Die einzige Variante, bei der
der Klick ein echtes Ziel hat — aber sie kollidiert mit dem Wortlaut von US-08 AC3 («wird sie aus
der Abo-Übersicht entfernt») und bricht zwei Assertions des gerade erst mit #329 grün gewordenen
E2E-Alt-Pfads (`e2e/tests/recurring-expenses.spec.ts:140-153`: `li.expense → 0` und
`p.status.empty` sichtbar).

## Entscheid

Die Glocke navigiert für `RECURRING_EXPENSE_DETECTED` weiterhin nach `/abos`, hängt aber die
`referenceId` als Query-Parameter `ref` an. Die Abo-Übersicht vergleicht diese ID nach dem Laden
mit ihren Zeilen; fehlt sie, erscheint ein `info`-Notice über der Liste. Kein Backend, kein
Contract, kein E2E-Eingriff.

Der Parameter statt des Gelesen-Status, weil die Zeilen-ID die einzige Information ist, die beide
Seiten teilen: `Notification.referenceId` zeigt auf `recurring_expenses.id` (`NotificationPort:21`,
`RecurringExpenseService.java:286`).

## Betroffene Dateien

| Datei | Änderung |
| --- | --- |
| `frontend/src/app/notifications/notification-bell.ts` | `select()` navigiert mit `queryParams: { ref: … }`, wenn `referenceId` gesetzt ist |
| `frontend/src/app/notifications/notification-bell.spec.ts` | Test: Ziel ist `/abos?ref=42`; Test: ohne `referenceId` weiterhin `/abos` |
| `frontend/src/app/recurring/recurring-expense-list.ts` | `ActivatedRoute` injizieren, `referencedId` als Signal, `referencedEntryMissing` als `computed` |
| `frontend/src/app/recurring/recurring-expense-list.html` | `app-notice variant="info"` über der Card |
| `frontend/src/app/recurring/recurring-expense-list.scss` | Abstand für den neuen Notice (analog `.dismiss-error`) |
| `frontend/src/app/recurring/recurring-expense-list.spec.ts` | vier Tests, siehe Test-Strategie |

## Implementierungsschritte

1. `notification-bell.ts`: In `select()` beim Typ `RECURRING_EXPENSE_DETECTED` navigieren mit
   `{ queryParams: notification.referenceId === null ? {} : { ref: notification.referenceId } }`.
   Javadoc um den Grund ergänzen; die bestehende Begründung zur sofortigen Navigation bleibt.
2. `recurring-expense-list.ts`: `ActivatedRoute` injizieren und `referencedId` über
   `toSignal(this.route.queryParamMap.pipe(map(...)), { initialValue: null })` ableiten, mit
   `Number.parseInt` plus `Number.isInteger`-Wache. Reaktiv statt einmalig im Konstruktor, weil
   die Glocke auch von `/abos` aus geklickt werden kann — dann wechselt nur der Query-Parameter
   und Angular baut die Komponente nicht neu.
3. `recurring-expense-list.ts`: `referencedEntryMissing` als `computed` aus `!loading()`,
   `errorMessage() === null`, `referencedId() !== null` und `!rows().some(...)`. Die ersten
   beiden Wachen sind der Kern: während des Ladens ist `rows()` leer, und ein Ladefehler bedeutet
   «unbekannt», nicht «verneint».
4. `recurring-expense-list.html`: Im `@else`-Zweig, vor `dismissErrorMessage`, den Notice
   einsetzen: «Dieser Eintrag ist als «Kein Abo» markiert und erscheint deshalb nicht in der
   Liste.» Zustandsbeschreibend statt ereignisbezogen — dismisst der Nutzer den referenzierten
   Eintrag selbst auf dieser Seite, bleibt der Satz korrekt.
5. `.scss`: Regel für `.dismissed-hint` analog `.dismiss-error`.
6. Tests schreiben, `npm test` und `ng build` laufen lassen.

## Test-Strategie

Angular TestBed / Vitest, keine neuen E2E-Tests — der Weg ist reine Frontend-Logik, und der
bestehende E2E-Alt-Pfad bleibt unberührt.

`notification-bell.spec.ts`

- Klick auf `RECURRING_UNREAD` (`referenceId: 42`) → `router.url` ist `/abos?ref=42`
- Klick auf eine `RECURRING_EXPENSE_DETECTED` ohne `referenceId` → `/abos` ohne Parameter

`recurring-expense-list.spec.ts` (`ActivatedRoute`-Stub mit `queryParamMap`)

- `?ref=99`, Liste enthält 1 und 2 → Notice sichtbar → AC1
- `?ref=1`, Liste enthält 1 → kein Notice → AC2
- ohne Parameter → kein Notice
- `?ref=99` und der `GET` schlägt fehl → kein Notice, nur die Fehlermeldung

## Acceptance Criteria (aus dem Issue)

- [ ] Ein Klick auf eine `RECURRING_EXPENSE_DETECTED`-Benachrichtigung, deren Eintrag `DISMISSED`
      ist, führt nicht auf eine Seite, auf der der Eintrag fehlt
- [ ] Ein Klick auf eine Benachrichtigung, deren Eintrag noch `DETECTED` ist, führt weiterhin
      nach `/abos` (US-08 AC2 bleibt erfüllt)
- [ ] Test deckt den gewählten Weg ab (Vitest/TestBed oder Playwright, je nach Lösung)

**Offen deklariert:** AC1 verlangt wörtlich, dass der Klick *nicht* auf einer Seite ohne den
Eintrag landet. Dieser Weg landet dort, erklärt es aber. Nur die dritte Variante hätte den
Buchstaben erfüllt — sie ist aus den oben genannten Gründen verworfen. Gehört so in den PR-Body.
