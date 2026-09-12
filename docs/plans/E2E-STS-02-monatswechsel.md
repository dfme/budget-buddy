# [E2E-STS-02] Playwright: Monatswechsel

- **Issue:** [#251](https://github.com/dfme/budget-buddy/issues/251)
- **Task-ID:** `E2E-STS-02`
- **Branch:** `feature/E2E-STS-02-monatswechsel`
- **Story:** US-12 — Zwischen Monaten wechseln
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-12

## Entscheide

### Kein Produktionscode

FE-STS-04 (`dashboard.ts` / `dashboard.html`) und BE-STS-06 (`status: 'CLOSED'`) sind gemerged,
der Flow existiert vollständig. Dieser Task fügt ausschliesslich den E2E-Test hinzu.

### Vorbedingung «Daten in einem vergangenen Monat» über die bestehende PDF-Fixture

`e2e/fixtures/pdf/kontoauszug-synthetisch.pdf` trägt fünf Buchungen aus **Juni 2025** — ein
zwangsläufig vergangener Monat, und damit genau der Fall, den US-12 AC2 mit «Abgeschlossen»
beantwortet. Importiert wird über `POST /api/import/pdf`, nicht durch die Upload-UI: der Import
ist Vorbedingung dieses Tests, nicht sein Gegenstand — ein Bug im Upload-UI liesse sonst auch
diese beiden Fälle rot werden, ohne Hinweis auf die eigentliche Ursache. Dieselbe Begründung, mit
der die Auth-Fixture über die API registriert statt durchs Login-Formular.

Auf `GET /api/import/{jobId}/status` wird bis `DONE` gepollt. Das Warten ist nicht kosmetisch:
`Transaction.category` bleibt bis zum Abschluss des Kategorisierungs-Jobs `null`
(`backend/src/main/java/com/budgetbuddy/transaction/Transaction.java:67`), die Kategorie-Übersicht
wäre sonst leer und Schritt 7 des Happy Path scheiterte an einer Vorbedingung statt an seiner
Aussage.

### Import-Helper lokal im Spec, nicht in `support/`

Er steht damit als zweite Kopie neben `e2e/tests/categorization.spec.ts:98`. `support/` ist
gemeinsamer Harness-Code, an dem alle sieben Specs hängen; für einen PR, der einen Test
*hinzufügt*, wäre eine Änderung dort der falsche Blast Radius. `categorization.spec.ts` begründet
dieselbe Abwägung an seinem eigenen Cleanup bereits so.

### Beide Bedienwege der `app-month-nav` kommen vor

Der Happy Path springt über das **Direktsprung-Dropdown** (FE-CAT-04) nach Juni 2025 — 15 Monate
zurück, über den Stepper wären das 15 Klicks. Der Fehlerpfad nimmt den **‹-Pfeil** in den
Vormonat. So ist jeder der beiden Wege einmal abgedeckt, ohne einen dritten Testfall.

### Kein neuer CI-Job

`E2E (Playwright)` (`.github/workflows/build.yml:93`) ruft `npm test` in `e2e/`, und
`testDir: './tests'` (`playwright.config.ts`) nimmt jede neue Datei dort automatisch mit. Der
Nachweis für AC 5 ist der grüne Lauf, nicht eine Workflow-Änderung.

### Bekannte Restexposition

`currentMonth()` wird im Browser ausgewertet, das erwartete Label im Testprozess. Ein Lauf, der
exakt über Mitternacht des Monatsletzten geht, könnte auseinanderlaufen. Dieselbe Exposition hat
`safe-to-spend.spec.ts` bereits (`weeksLeft` hängt am Kalendertag); eine Sonderbehandlung dafür
wäre mehr Mechanik als der Fall wert.

## Betroffene Dateien

| Datei | Art |
| ----- | --- |
| `e2e/tests/month-switch.spec.ts` | neu — der Test |
| `e2e/README.md` | ändern — Zeile in der `Aufbau`-Tabelle, Ergänzung im `Scope`-Abschnitt |
| `docs/plans/E2E-STS-02-monatswechsel.md` | neu — dieser Plan |
| `docs/plans/README.md` | ändern — Index-Zeile |

## Implementierungsschritte

1. `e2e/tests/month-switch.spec.ts` anlegen: `test.describe('Monatswechsel')`, Einstieg über
   `authenticatedPage` / `authenticatedContext` aus `../fixtures/auth.fixture`.
2. Lokalen Helper `importFixtureStatement(request)` schreiben — Upload plus Polling bis `DONE`.
3. Happy Path implementieren (siehe unten).
4. Fehlerpfad implementieren (siehe unten).
5. `e2e/README.md` nachziehen: Tabellenzeile und Scope-Abschnitt.
6. `npm test` in `e2e/` gegen das frisch gebaute prod-JAR laufen lassen.

### Happy Path — «Wechsel in einen Monat mit Daten zeigt Abgeschlossen, Dashboard und Kategorien synchron»

1. Fixture importieren, Job abwarten.
2. `/dashboard` öffnen: Label der `app-month-nav` ist der laufende Monat, `›` ist gesperrt
   (`isCurrentMonth`), die URL trägt **keinen** `month`-Parameter.
3. Im Dropdown `2025-06` wählen: URL wird `?month=2025-06`, Label `Juni 2025`.
4. `app-notice.closed-banner` sichtbar mit Titel `Abgeschlossen`; `app-card.safe-to-spend-card`
   und `.status.empty` haben Count 0 — für einen abgeschlossenen Monat wird bewusst keine Zahl
   gerendert (`dashboard.html`, `Dashboard.closed`).
5. Übersicht synchron: Card-Titel `Drei Monate bis Juni 2025`, die hervorgehobene Zeile
   (`tr.totals__row--selected`) ist Juni 2025.
6. Querprobe gegen das Backend statt Nachbau der Regel im Test:
   `GET /api/budget/safe-to-spend?month=2025-06` → `status: 'CLOSED'`, `amount: null`,
   `weeksLeft: 0`.
7. Kategorien synchron: `/categories?month=2025-06` — Label `Juni 2025`, mindestens eine
   Tabellenzeile sichtbar, kein Leerzustand.

### Fehlerpfad — «Monat ohne Daten zeigt den Keine-Daten-Hinweis mit Upload-CTA»

1. Dieselbe Fixture importieren — das Konto ist also *nicht* leer. Damit belegt der Test, dass
   der Hinweis am gewählten Monat hängt und nicht bloss an einem leeren Konto.
2. `/dashboard`, `‹` klicken → Vormonat des laufenden Monats.
3. `.status.empty` sichtbar mit exaktem Text `Keine Daten für <Vormonat> — PDF hochladen?`; der
   Link führt auf `/import`, und ein Klick landet dort auch.
4. Gegenprobe: weder `closed-banner` noch `safe-to-spend-card` — der Keine-Daten-Hinweis
   verdrängt «Abgeschlossen» (dokumentiert bei `Dashboard.closed`).
5. Querprobe `GET /api/transactions/monthly-totals?month=<Vormonat>` → Zeile dieses Monats mit
   `income`, `expenses` und `difference` alle `null`.

## Test-Strategie

Ausschliesslich E2E (Playwright) — der Test *ist* der Task. Ein Happy Path und ein Fehlerpfad,
die in CLAUDE.md vorgeschriebene Menge pro Story. Verifiziert wird lokal mit `npm test` in `e2e/`
gegen das frisch gebaute prod-JAR, in CI über den bestehenden Job `E2E (Playwright)`.

## Acceptance Criteria (aus dem Issue)

- [ ] Happy Path: Dashboard öffnet mit aktuellem Monat, Wechsel zu einem Vormonat zeigt
      "Abgeschlossen" auf Dashboard, Kategorien und Safe-to-Spend synchron
- [ ] Fehlerpfad: Wechsel zu einem Monat ohne importierte Daten zeigt den "Keine Daten"-Hinweis
      mit Upload-CTA
- [ ] Der Test nutzt die `authenticatedPage`-Fixture aus #91 als Vorbedingung
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npm test` in `e2e/`
- [ ] Der Test läuft im bestehenden CI-Job `E2E (Playwright)` mit — kein neuer Job nötig
