# [FE-STS-03] No-Income State und Einkommens-Vorschlag

- **Issue:** [#35](https://github.com/dfme/budget-buddy/issues/35)
- **Task-ID:** `FE-STS-03`
- **Branch:** `feature/FE-STS-03-no-income-state-and-suggestion`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-19

## Ausgangslage

Das Backend ist fertig: `SafeToSpendResponse` liefert `noIncome` und `incomeSuggestion`
(`backend/src/main/java/com/budgetbuddy/budget/dto/SafeToSpendResponse.java:49`), die Heuristik
dahinter steht im `IncomeSuggestionService` (BE-STS-02), und `PUT /users/me/income` existiert
inklusive `@NotNull @Positive`-Validierung
(`backend/src/main/java/com/budgetbuddy/auth/UserController.java:53`).

Im Frontend war der `noIncome`-Fall bisher nur ein Strich-Platzhalter statt eines Betrags
(`frontend/src/app/dashboard/dashboard.html:22-28`, FE-STS-01); `incomeSuggestion` wurde nirgends
gelesen.

## Deltas aus dem Abgleich gegen US-06

Der Breit-Abgleich gegen die Story (statt nur gegen die drei Issue-ACs) hat drei Punkte ergeben.
Alle drei wurden dem Team vorgelegt und entschieden:

1. **Banner-Text.** Der Issue-AC sagt „Kein Einkommen erfasst", US-06 verlangt wörtlich „Bitte
   erfasse dein Monatseinkommen in den Einstellungen". *Entscheid: beides* — die erste Zeile als
   Überschrift, die zweite als Erläuterung. So ist keine der beiden Quellen gegen die andere
   ausgespielt.
2. **Sackgasse „in den Einstellungen".** Es gibt keine Settings-Route (`app.routes.ts`), und
   `grep -rn "monthlyIncome\|/income" frontend/src` zeigt nur lesende Stellen: das Einkommen ist
   im Frontend nirgends manuell erfassbar. Der Übernehmen-Button aus diesem Task ist der erste
   schreibende Zugriff überhaupt — und er existiert nur, wenn die Heuristik einen Vorschlag
   gefunden hat. *Entscheid: US-14 vollständig als Issues erfassen* — [#176](https://github.com/dfme/budget-buddy/issues/176)
   (BE-AUTH-09), [#177](https://github.com/dfme/budget-buddy/issues/177) (FE-SET-01),
   [#178](https://github.com/dfme/budget-buddy/issues/178) (FE-SET-02),
   [#179](https://github.com/dfme/budget-buddy/issues/179) (FE-SET-03, schliesst genau diese
   Lücke), [#180](https://github.com/dfme/budget-buddy/issues/180) (FE-SET-04). Alle P2 - Medium,
   mit Story Points, ohne Milestone und ohne Sprint.
3. **„Letzte Woche des Monats".** Eigener US-06-AC, der in `frontend/src`, `backend/src` und
   `docs/plans` nirgends vorkam und von keinem FE-STS-Issue abgedeckt war (33 und 34 geschlossen,
   35 erwähnt ihn nicht). *Entscheid: in diesem PR mitnehmen* — es sind wenige Zeilen in genau der
   Datei, die für #35 ohnehin angefasst wird.

## Entscheide

1. **Das `PUT` liegt im `AuthService`, nicht im `SafeToSpendService`.** `/users/me` gehört dorthin
   (`completeOnboarding` ist das bestehende Muster), und `monthlyIncome` ist Teil des `User`-State.
   Läge der Aufruf im Dashboard-Service, bliebe der Auth-State nach dem Übernehmen auf dem alten
   Wert stehen — derselbe Fehler, den `completeOnboarding` mit seinem `tap` bereits vermeidet.
2. **Der Zustand steht in der Card, nicht als Banner darüber — und folgt der Design-Baseline.**
   Zuerst als Banner oberhalb der Card gebaut (analog FE-STS-02), nach der visuellen Prüfung
   korrigiert: `app-notice` ist `display: flex` ohne `flex-direction`
   (`frontend/src/app/shared/notice/notice.scss:4-5`, übernommen aus
   `design/variant-a/styles.scss:799-806`), und die Row-Richtung ist dort für das Paar
   **Icon + Text** gedacht. Alle 13 bestehenden Verwendungen im Repo übergeben genau einen
   Textlauf; mehrere Blöcke darin landeten als Flex-Items nebeneinander statt untereinander.

   Der Aufbau folgt jetzt dem Entwurf in `design/variant-a/index.html:214-231`
   (`hero hero--muted`), der genau diesen Screen bereits zeigt: Titel und Erläuterung als
   Fliesstext in der Card, der Vorschlag als `app-notice` mit Icon und Text, der Block-Button
   darunter **ausserhalb** des Notice.

   Die Variante bleibt `warning`, nicht `error`: kein erfasstes Einkommen ist kein Fehler,
   sondern ein offener Schritt. `warning` meldet sich als `role="status"` (höflich); das
   assertive `role="alert"` trägt nur die Fehlermeldung eines fehlgeschlagenen Submits.

   **Nebeneffekt, der den Ausschlag gab:** in der Banner-Variante lag der Button innerhalb der
   Live-Region, sodass der Screenreader bei jeder Änderung des Hinweises die Button-Beschriftung
   mitvorlas. Ausserhalb des Notice entfällt das.
3. **Nach erfolgreichem Übernehmen wird `GET /budget/safe-to-spend` neu geladen.** Der erscheinende
   Betrag ist die Bestätigung; eine reine Erfolgsmeldung liesse den Nutzer mit dem Platzhalter
   zurück.
4. **Der Vorschlagstext folgt US-06 wörtlich:** „Regelmässige Gutschrift von 3'800.00 CHF erkannt —
   als Monatseinkommen übernehmen?" Der Betrag steht *vor* der Währung, deshalb `formatSwissAmount()`
   statt der `app-amount`-Komponente, die „CHF" voranstellt.
5. **Der Letzte-Woche-Hinweis ist eine eigene Zeile** unter dem bestehenden „noch 1 Woche im Monat"
   und erscheint bei `weeksLeft === 1` auch im No-Income-Fall: das Wochen-Label steht dort ebenfalls,
   und die Aussage ist rein kalendarisch. Er ersetzt das Wochen-Label nicht — der bestehende
   FE-STS-01-Test bleibt damit gültig.

## Betroffene Files

| File | Änderung |
| ---- | -------- |
| `frontend/src/app/auth/auth.service.ts` | neu: `updateIncome(betrag)` → `PUT /users/me/income`, Antwort in den `currentUserState` |
| `frontend/src/app/auth/auth.service.spec.ts` | Test für `updateIncome` |
| `frontend/src/app/dashboard/dashboard.ts` | Signals `saving`/`saveErrorMessage`, Computeds `suggestionText`/`lastWeek`, Methode `applySuggestion()` |
| `frontend/src/app/dashboard/dashboard.html` | No-Income-Zustand in der Card, Vorschlag + Übernehmen-Button, Letzte-Woche-Hinweis |
| `frontend/src/app/dashboard/dashboard.scss` | No-Income-Block in der Card, Letzte-Woche-Hinweis |
| `frontend/src/app/dashboard/dashboard.spec.ts` | Tests für Banner, Vorschlag, Übernehmen, Fehlerfall, Letzte Woche |
| `CLAUDE.md` | neue Zeile `FE-SET-XX` in der Task-ID-Präfix-Tabelle |

## Implementierungsschritte

1. Fünf US-14-Issues anlegen, Board-Felder setzen, Blocked-by verdrahten (Delta 2)
2. `AuthService.updateIncome()` samt Test
3. `Dashboard`: State-Signals, `applySuggestion()`, Computeds
4. Template und SCSS: Banner, Vorschlagssatz, Button, Letzte-Woche-Hinweis
5. `CLAUDE.md`-Präfixzeile für `FE-SET`
6. Tests ergänzen, `npm test` und `npm run build` grün
7. Security-Review (Matrix), lokaler Review, PR

## Test-Strategie

Vitest + Angular TestBed; kein Playwright — die DoD lässt Vitest ausdrücklich zu, und die
E2E-Abdeckung von US-06 ist als eigener Task `E2E-STS-01` erfasst.

`dashboard.spec.ts`:

- No-Income-Zustand erscheint bei `noIncome=true`, trägt beide Textzeilen und steht in der Card
- kein No-Income-Zustand bei `noIncome=false`
- Vorschlagssatz mit dem formatierten Betrag, wenn `incomeSuggestion` gesetzt ist
- kein Vorschlagssatz und kein Button, wenn `incomeSuggestion` `null` ist
- Übernehmen sendet `PUT /users/me/income` mit `{ betrag: 3800 }` **und** lädt Safe-to-Spend neu
- Fehler beim `PUT` → Fehlermeldung als `error`-Notice, Zustand bleibt stehen
- Button ist deaktiviert, solange der Request läuft
- Letzte-Woche-Hinweis bei `weeksLeft === 1`, nicht bei `> 1`

`auth.service.spec.ts`:

- `updateIncome` PUTet den Betrag und schreibt die Antwort in `currentUser`

## Acceptance Criteria (aus dem Issue)

- [ ] Hinweis „Kein Einkommen erfasst" erscheint, wenn `noIncome=true` — als Überschrift des
      No-Income-Blocks in der Safe-to-Spend-Card, nicht als eigenes Banner darüber (siehe
      Entscheid 2)
- [ ] Einkommens-Vorschlag wird mit Betrag angezeigt, wenn vorhanden
- [ ] „Übernehmen"-Button setzt das Einkommen via `PUT /users/me/income`

Zusätzlich mitgenommen (Delta 3, US-06): Hinweis „Letzte Woche des Monats" bei `weeksLeft === 1`.
