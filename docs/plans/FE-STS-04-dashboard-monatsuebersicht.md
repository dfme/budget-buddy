# [FE-STS-04] Dashboard: Monatsnavigation und Drei-Monats-Übersicht

- **Issue:** [#250](https://github.com/dfme/budget-buddy/issues/250)
- **Task-ID:** `FE-STS-04`
- **Branch:** `feature/FE-STS-04-dashboard-monatsuebersicht`
- **Story:** US-12 — Zwischen Monaten wechseln
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-09

## Ausgangslage

Das Dashboard kennt keinen Monatswechsel — Safe-to-Spend zeigt immer den laufenden Monat. Der
Grund liegt nicht im Backend: `GET /api/budget/safe-to-spend` nimmt seit BE-STS-06 einen
`month`-Parameter (`BudgetController.java:85-96`), nur setzt ihn `SafeToSpendService` im Frontend
nicht (`safe-to-spend.service.ts:20-22`).

Dieser Task gibt dem Dashboard eine **Monatsnavigation** über die bestehende `MonthNav` und
darunter eine **Übersicht der letzten drei Monate** mit Einnahmen, Ausgaben und Differenz.

### Vorgeschichte: PR #280

Ein erster Anlauf lief als PR [#280](https://github.com/dfme/budget-buddy/pull/280). Er setzte die
Monatsnavigation um, wurde von @dfme approved und dann geschlossen, weil das Issue neu formuliert
wurde — die Drei-Monats-Übersicht kam hinzu. Der Branch ist gelöscht, die Commits sind aber unter
`refs/pull/280/head` erreichbar und dienen hier als **Ausgangspunkt**: die dort gefundenen
Race-Conditions und die elf reviewten Testfälle sollen nicht zweimal erarbeitet werden.

Was aus #280 **nicht** übernommen wird, ist die Verdoppelung der Monatslogik — siehe Entscheid 1.

### Abhängigkeit BE-STS-07 (erledigt)

Die Übersicht braucht je Monat Einnahmen, Ausgaben und Differenz. Das lieferte kein Endpoint;
BE-STS-07 (#288) hat ihn nachgezogen und ist mit PR #294 (`7ccd14e`) auf `main`:

`GET /api/transactions/monthly-totals?month=YYYY-MM&months=3` → nacktes Array, neuester Monat
zuerst, je Zeile `month`, `income`, `expenses`, `difference`. Alle drei Beträge sind `null`, wenn
der Monat keine einzige Buchung trägt; ein Monat mit nur Gutschriften trägt `expenses: 0.00`.

## Entscheide

### 1. Die Monatslogik wandert nach `shared/month.ts` — für beide Aufrufer

`MONTH_PATTERN` und die vier Helfer (`currentMonth`, `shiftMonth`, `toMonthString`, `formatMonth`)
liegen heute als `private static` in `CategoryOverview` (`category-overview.ts:58` und
`:771-793`). Das Dashboard braucht genau dieselbe Logik.

PR #280 hatte sie kopiert — fünf identische Definitionen in `dashboard.ts`. Zwei Definitionen von
«welche Monate es gibt» sind genau das, was auseinanderläuft: eine Seite akzeptierte dann einen
Monat, den die andere ablehnt, und der Fehler zeigte sich erst im Deep-Link zwischen den beiden
Seiten.

Deshalb ein gemeinsames Modul mit reinen Funktionen und eigenem Spec, und `CategoryOverview` wird
mit umgezogen. Das ist eine **Scope-Erweiterung** über #250 hinaus und wird im PR-Body deklariert;
sie ist der Grund, warum die Datei überhaupt angefasst wird.

`isValidMonth()` kommt neu dazu: die Regel «Format stimmt **und** nicht in der Zukunft» steht
heute als zusammengesetzter Ausdruck in `syncFromUrl` (`category-overview.ts:329`) und wird im
Dashboard wortgleich gebraucht.

### 2. `MonthNav` wird wiederverwendet, nicht nachgebaut

Die Komponente ist rein präsentational und trägt Stepper **und** Direktsprung-Dropdown
(`month-nav.ts:35-62`); das Dropdown erscheint nur, wenn `months` Einträge hat. Sie hält keinen
Monatszustand — genau richtig für einen zweiten Aufrufer. Eine zweite Monatskomponente wäre ein
AC-Verstoss; heute referenzieren sie nur `category-overview`, der Styleguide und deren Specs.

### 3. Query-Param-Synchronisation nach dem Muster der Kategorie-Übersicht

Monat-Signal + `queryParamMap`-Subscription mit `takeUntilDestroyed` + `router.navigate` mit
`queryParamsHandling: 'merge'` (`category-overview.ts:269-350`). Drei Details, die dort aus
Erfahrung stehen und hier mitkommen:

- **`replaceUrl: true`** beim Zurechtrücken eines unbrauchbaren Parameters, damit die kaputte
  Adresse nicht im Verlauf liegenbleibt und «Zurück» sie wieder aufruft.
- **Die Gleichheits-Wache** (`initialLoadDone && month === this.month()`), ohne die jeder Wechsel
  zwei Requests auslöst.
- **Stiller Rückfall** auf den laufenden Monat bei kaputtem Format *oder* Zukunftsmonat. Das
  Backend lehnt Zukunftsmonate am Safe-to-Spend mit 400 ab; der Client darf dort nicht hinlaufen.

### 4. Zwei Requests, zwei getrennte Fehlerzustände

Safe-to-Spend und Übersicht laden unabhängig. Fällt die Übersicht aus, bleibt der
Safe-to-Spend-Block darüber geladen und bedienbar und die Übersicht trägt ihre eigene Meldung —
sie reisst nicht die ganze Seite in den Fehlerzustand (ausdrücklicher AC).

Beide behalten die `pendingRequest`-Stornierung aus #280: zu Beginn eines Ladevorgangs wird die
laufende Subscription abgebrochen, damit eine späte Antwort nicht in eine Anzeige schreibt, zu der
sie nicht mehr gehört.

### 5. Die Differenz wird nie im Frontend gerechnet

Sie kommt als Feld vom Backend und wird so gerendert. Zwei JSON-`number` zu subtrahieren ist genau
die Gleitkomma-Rechnung, die ADR-9 für Geldbeträge ausschliesst.

### 6. `–` statt `0.00`, Vorzeichen *und* Farbe

Ein Monat ohne erfasste Daten zeigt `–` in seinen Zellen; eine Null behauptete erfasste
Nullbeträge. Deshalb geht `null` auch nie an `app-amount` (dessen `value` ist
`input.required<number>`), sondern wird vorher im Template abgefangen.

Für die Differenz wird `hidePositiveSign` **nicht** gesetzt: ein Überschuss zeigt `+`, ein
Defizit `−`, beides zusätzlich zur Farbe und über `aria-label` auch für Screenreader
(`amount.ts:38-54`). Der Safe-to-Spend-Betrag oben behält `hidePositiveSign`, weil ein positiver
Kontostand dort der Normalfall ist und keine Veränderung.

### 7. Mobile: die Übersicht scrollt in der Card

Muster von FE-CAT-06 (`category-overview.scss:29`): ein `overflow-x: auto`-Container um die
Tabelle, statt die Spalten unleserlich zu quetschen oder die Seite seitlich rauslaufen zu lassen.

### 8. Der BE-PDF-10-Hinweis gilt dem gewählten Monat

`loadUncertainCount()` reicht den Monat durch, und der Hinweistext nennt den Monat statt «dieses
Monats» (`dashboard.ts:127-135`). Für einen vergangenen Monat wird der Zähler weiterhin geladen —
der Hinweis gilt der Zahl, die gerade auf dem Schirm steht.

## Betroffene Dateien

### Neu

| Datei | Inhalt |
| ----- | ------ |
| `frontend/src/app/shared/month.ts` | `MONTH_PATTERN`, `currentMonth`, `shiftMonth`, `toMonthString`, `formatMonth`, `isValidMonth` |
| `frontend/src/app/shared/month.spec.ts` | Spec dazu |
| `frontend/src/app/transactions/monthly-totals.model.ts` | Spiegel des Backend-Records |
| `frontend/src/app/transactions/monthly-totals.service.ts` | Zugriff auf den Endpoint |
| `frontend/src/app/transactions/monthly-totals.service.spec.ts` | Spec dazu |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/app/transactions/category-overview.ts` | Statics und `MONTH_PATTERN` raus, Import aus `shared/month` (Scope-Erweiterung, Entscheid 1) |
| `frontend/src/app/dashboard/dashboard.ts` | Monatszustand, Übersichtszustand, `MonthNav`-Handler |
| `frontend/src/app/dashboard/dashboard.html` | Monatsnavigation, gewählter Monat im Safe-to-Spend-Block, Übersicht |
| `frontend/src/app/dashboard/dashboard.scss` | Übersicht inkl. Mobile-Scroll |
| `frontend/src/app/dashboard/safe-to-spend.service.ts` | optionaler `month`-Parameter |
| `frontend/src/app/dashboard/safe-to-spend.service.spec.ts` | Parameter-Fälle |
| `frontend/src/app/dashboard/dashboard.spec.ts` | Monatswechsel, Zustände, Übersicht |
| `frontend/src/app/settings/settings.spec.ts` | zusätzliche Dashboard-Requests in den Erwartungen |

Weitere Specs, die das Dashboard mounten, werden über den Suite-Lauf gefunden, nicht geraten.

## Implementierungsschritte

1. `shared/month.ts` + Spec anlegen.
2. `category-overview.ts` auf das Modul umziehen, Suite grün halten.
3. `safe-to-spend.service.ts` um den optionalen `month` erweitern, Spec ergänzen.
4. `monthly-totals.model.ts` + `.service.ts` + Spec anlegen.
5. `dashboard/` aus `refs/pull/280/head` als Basis übernehmen und auf `shared/month` umbauen.
6. Übersicht in `dashboard.ts`/`.html`/`.scss` ergänzen.
7. `dashboard.spec.ts` erweitern; `settings.spec.ts` und alles, was der Lauf zeigt, nachziehen.
8. `npx ng test --watch=false`, `npx tsc --noEmit`, `npx ng build` grün.

## Test-Strategie

Vitest/Angular TestBed. Die Definition of Done verlangt mindestens Monatswechsel,
`CLOSED`-Zustand, Query-Param-Rücksprung und die Übersicht inkl. `–`-Fall und negativer
Differenz — dazu:

**`shared/month.spec.ts`** — `shiftMonth` über beide Jahresgrenzen, `MONTH_PATTERN` lehnt
`2026-13`/`2026-0`/`""`/`2026-1` ab, `formatMonth` liefert das `de-CH`-Label, `isValidMonth`
lehnt Zukunftsmonate ab.

**`monthly-totals.service.spec.ts`** — URL und beide Query-Parameter, `null`-Felder kommen
unverändert durch.

**`safe-to-spend.service.spec.ts`** — ohne Monat kein Query-Parameter (bestehender Test),
mit Monat `?month=`.

**`dashboard.spec.ts`** — Erstaufbau mit dem laufenden Monat · Stepper und Dropdown laden den
gewählten Monat · Vor-Pfeil am laufenden Monat gesperrt · kaputter und künftiger `month`-Param
fallen still zurück und die URL wird zurechtgerückt · `CLOSED` zeigt «Abgeschlossen» statt eines
Betrags · Monat ohne Daten zeigt den `/import`-Hinweis · Übersicht rendert drei Zeilen endend beim
gewählten Monat · `–` für einen `null`-Monat · negative Differenz mit Vorzeichen · Ausfall der
Übersicht lässt Safe-to-Spend stehen · gewählter Monat hervorgehoben · späte Antwort eines
vorherigen Monats wird verworfen.

**Nicht Teil dieses Tasks:** Playwright. Der Monatswechsel-E2E ist #251 (E2E-STS-02). Der
bestehende `e2e/tests/safe-to-spend.spec.ts` darf nicht brechen; er läuft in CI.

## Acceptance Criteria aus dem Issue

### Monatsnavigation

- [ ] Dashboard öffnet standardmässig mit dem laufenden Monat
- [ ] `MonthNav` wiederverwendet — Stepper plus Direktsprung-Dropdown aus
      `GET /api/transactions/months`, keine zweite Monatskomponente
- [ ] Monat über Query-Param `month` synchronisiert, Deep-Link und Reload behalten ihn
- [ ] Vor-Pfeil am laufenden Monat gesperrt; kaputter oder künftiger `month` fällt still zurück
- [ ] Safe-to-Spend-Block zeigt den **gewählten** Monat; `SafeToSpendService` bekommt den Parameter
- [ ] `status = 'CLOSED'` zeigt «Abgeschlossen» statt eines Betrags
- [ ] Monat ohne Daten: `Keine Daten für [Monat Jahr] — PDF hochladen?`, wortgleich zu FE-CAT-08
- [ ] BE-PDF-10-Hinweis gilt dem gewählten Monat und nennt ihn im Text

### Drei-Monats-Übersicht

- [ ] Drei Monatszeilen mit Einnahmen, Ausgaben und Differenz
- [ ] Differenz kommt vom Backend, nicht aus dem Frontend
- [ ] Negativer Wert an Vorzeichen **und** Farbe erkennbar
- [ ] Die drei Monate enden beim gewählten Monat und wandern beim Blättern mit
- [ ] Gewählter Monat optisch hervorgehoben
- [ ] Beträge über `app-amount` bzw. `formatSwissAmount`, kein `toFixed`
- [ ] Monat ohne Daten zeigt `–`, nicht `0.00`
- [ ] Ausfall der Übersicht lässt den Safe-to-Spend-Block geladen und bedienbar
- [ ] Auf Mobile lesbar, läuft nicht über die Card hinaus
