# [E2E-STS-01] Playwright: Safe-to-Spend (Happy Path + Fehlerpfad)

- **Issue:** [#125](https://github.com/dfme/budget-buddy/issues/125)
- **Task-ID:** `E2E-STS-01`
- **Branch:** `feature/E2E-STS-01-safe-to-spend`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-30

## Ziel

Die in CLAUDE.md („Testing: Frameworks") vorgeschriebene E2E-Abdeckung für die Must-Have-Story
US-06: je ein Happy Path und ein Fehlerpfad **pro Story**, nicht pro Issue. Der Test gehört
deshalb in einen eigenen Task und nicht rückwirkend in eines der Feature-Issues (FE-STS-01…03,
BE-STS-01…04).

Aufgesetzt wird auf der Harness aus [INFRA-14](INFRA-14-playwright-e2e-setup.md) (#91): Config,
CI-Job und `authenticatedPage`-Fixture stehen, das Gerüst wird nicht dupliziert.

## Entscheide

### 1. Der Erwartungswert wird nicht verdrahtet, sondern quergeprüft

Der zentrale Entscheid dieses Tasks. `SafeToSpendService.java:162` teilt den verfügbaren Betrag
durch `weeksLeft` — die aufgerundete Zahl der im laufenden Monat verbleibenden Wochen, also 1 bis
5 je nach Kalendertag. Ein fest verdrahteter CHF-Betrag wäre an vier von fünf Wochen rot, ohne
dass sich am Code etwas geändert hätte.

Die naheliegende Gegenmassnahme — den Erwartungswert im Test aus `weeksLeft` selbst ausrechnen —
ist ausdrücklich **verworfen**: das wäre eine zweite Kopie der Produktionsformel, die genau dann
mitwandert, wenn die erste falsch wird. Ein E2E-Test, der die getestete Rechnung nachbaut, kann
sie nicht mehr widerlegen.

Stattdessen zwei getrennte Assertions:

| Was | Wie | Was es belegt |
| --- | --- | --- |
| **Format** | Regex auf den Text von `app-amount` | die de-CH-Formatierung aus AC 1 |
| **Wert** | Vergleich mit `amount` aus `GET /api/budget/safe-to-spend` über `context.request` | die UI rendert die Zahl des Backends |

Die Formel selbst liegt bei `SafeToSpendServiceTest` — dort gehört sie hin, mit fixer `Clock`.

### 2. Testdaten so gewählt, dass die Regex den Apostroph erzwingt

Eine Regex, die nur `/^CHF\s[\d'.]+$/` verlangt, würde auch `CHF 960.00` durchlassen und über die
Tausendertrennung nichts aussagen. Damit die Gruppierung in **jeder** Kalenderwoche im Ergebnis
vorkommt, muss der Betrag für jedes mögliche `weeksLeft` vierstellig bleiben:

| Einkommen | Fixkosten | verfügbar | `weeksLeft` = 1 … 5 |
| --------- | --------- | --------- | ------------------- |
| 12'000.00 | Miete 1'200.00 `monatlich` | 10'800.00 | 10'800.00 · 5'400.00 · 3'600.00 · 2'700.00 · 2'160.00 |

Keine Transaktionen im Konto → `variableExpenses = 0` (`FixedCostDebitMatcher.variableExpenses`),
der Abzug ist damit allein die Fixkostensumme.

### 3. ASCII-Apostroph, nicht U+2019 — anders als im Wizard-Test

`fixed-cost-wizard.spec.ts:37` erwartet ein Right Single Quotation Mark (`’`), weil die
Fixkostenliste über Angulars `CurrencyPipe` unter `de-CH` formatiert.

Das Dashboard geht einen anderen Weg: `amount.ts:48` ruft `formatSwissAmount`
(`frontend/src/app/shared/format.ts:12`), und die Funktion setzt den Apostroph selbst — als
**ASCII `'`**. Die beiden Zeichen sind im Quelltext kaum zu unterscheiden; deshalb steht die
Abweichung als Kommentar direkt an der Regex, sonst zieht der nächste Leser sie „zur
Konsistenz" auf `’` nach und der Test reisst.

### 4. Der Fehlerpfad braucht kein Setup — nur die Abwesenheit von Setup

Ein frisch registriertes Konto hat kein `monthlyIncome` (`User`-Konstruktor). `SafeToSpendService`
antwortet dann mit `amount = null, noIncome = true` (`SafeToSpendService.java:146`). Der
Fehlerpfad ist damit exakt die Fixture ohne Zutat.

Wichtig für die Determinismus-Frage: `incomeSuggestion` bleibt `null`, weil die Heuristik ein
wiederkehrendes Gutschriftsmuster in den Transaktionen sucht und das Konto keine hat. Der
`Übernehmen`-Button (`dashboard.html:58-69`) erscheint deshalb nie — der Test kann seine
Abwesenheit belegen, statt um ihn herum zu assertieren.

## Betroffene Files

**Neu**

- `e2e/tests/safe-to-spend.spec.ts`

**Geändert**

- `e2e/README.md` — eine Zeile in der Test-Tabelle (`:84-88`)

**Bewusst nicht angefasst**

- `e2e/playwright.config.ts` — `testDir: './tests'` (`:19`) nimmt die neue Datei von selbst auf
- `.github/workflows/build.yml` — der Job `E2E (Playwright)` ruft `npm test` ohne Testliste
  (`:134-136`)

Das ist keine Auslassung, sondern der Inhalt von AC 4 und AC 5: die Harness aus #91 ist so
gebaut, dass ein neuer Test kein Metadatum braucht.

## Implementierungsschritte

1. `test.describe('Safe-to-Spend')` in `e2e/tests/safe-to-spend.spec.ts`, Einstieg über
   `authenticatedPage` **und** `authenticatedContext` aus `fixtures/auth.fixture.ts` (AC 3 — kein
   Durchklicken durchs Login-Formular)
2. **Happy Path**
   - `PUT /api/users/me/income` mit `{ betrag: 12000 }` über `context.request`
   - `POST /api/fixed-costs` mit `{ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' }`
   - `page.goto('/dashboard')`
   - `app-amount.safe-to-spend__amount` ist sichtbar
   - sein Text matcht `/^CHF\s\d{1,3}'\d{3}\.\d{2}$/`
   - kein führendes `+` (`hidePositiveSign`, `dashboard.html:22`)
   - der gerenderte Betrag entspricht `amount` aus `GET /api/budget/safe-to-spend`
   - `.safe-to-spend__amount--placeholder` hat Count 0
3. **Fehlerpfad**
   - frisches Konto, kein Einkommen, direkt `page.goto('/dashboard')`
   - `app-notice.no-income__notice` sichtbar, `.notice__title` = `Kein Einkommen erfasst`
   - Platzhalter `CHF —` sichtbar, `app-amount` hat Count 0
   - Kartentext enthält weder `NaN` noch `CHF 0.00` — beide Formulierungen wörtlich aus AC 2
   - kein `Übernehmen`-Button (siehe Entscheid 4)
4. Zeile in `e2e/README.md` ergänzen

## Test-Strategie

Der Task ist der Test; es gibt keine zusätzliche Unit-Ebene. Verifikation vor dem PR:

- `./mvnw -Pprod package` im `backend/` — das JAR, gegen das die Suite läuft
  (`playwright.config.ts:66`)
- `npm test` in `e2e/` — die **ganze** Suite, nicht nur die neue Datei: ein Worker und eine
  geteilte Datenbank (`playwright.config.ts:29-34`) heissen, dass ein neuer Test die
  Nachbartests beeinflussen kann
- `npm run typecheck` in `e2e/`

## Acceptance Criteria (aus #125)

- [ ] Happy Path: eingeloggt mit Einkommen und Fixkosten → Dashboard zeigt den
      Safe-to-Spend-Betrag in CHF (de-CH-Formatierung)
- [ ] Fehlerpfad: kein Einkommen erfasst → No-Income-State statt einer irreführenden Zahl
      (kein `NaN`, kein `CHF 0.00` als Platzhalter)
- [ ] Der Test nutzt die `authenticatedPage`-Fixture aus #91 als Vorbedingung — kein
      Durchklicken durchs Login-Formular
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npm test` in `e2e/`
- [ ] Der Test läuft im bestehenden CI-Job `E2E (Playwright)` mit — kein neuer Job nötig
