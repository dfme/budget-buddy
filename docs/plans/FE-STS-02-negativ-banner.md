# [FE-STS-02] Negativ-Banner

- **Issue:** [#34](https://github.com/dfme/budget-buddy/issues/34)
- **Task-ID:** `FE-STS-02`
- **Branch:** `feature/FE-STS-02-negativ-banner`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-16

## Scope

Nur das rote Warn-Banner, wenn der Safe-to-Spend-Betrag negativ ist. Das Basis-Widget
(Betrag + Wochen-Label) steht bereits aus [#33](https://github.com/dfme/budget-buddy/issues/33)
(FE-STS-01); der No-Income-Hinweis mit Einkommens-Vorschlag und CTA ist
[#35](https://github.com/dfme/budget-buddy/issues/35) (FE-STS-03) und bleibt hier unangetastet.

## Entscheide

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Datenfeld | `negative`, **nicht** `isNegative` | Die AC im Issue nennt `isNegative`; das ist der Stand vor der Contract-Anpassung an [#23](https://github.com/dfme/budget-buddy/issues/23). Das Wire-Format heisst `negative` (`backend/src/main/java/com/budgetbuddy/budget/dto/SafeToSpendResponse.java`), und `BudgetControllerIntegrationTest.java:202` hält per Test fest, dass `isNegative` in der Antwort **nicht** vorkommt. Das Frontend-Modell spiegelt das bereits (`safe-to-spend.model.ts:15`). Die AC-Formulierung ist überholt, nicht die Implementierung. |
| Banner-Text | «Achtung: Dein Budget für diese Woche ist überzogen» | Wortlaut aus `docs/requirements/US-06-safe-to-spend.md:19`. Die Kurzform «Budget überzogen» im Issue-Text stammt aus der Task-Tabelle (`docs/prompts/04_01_prompt_lab1_l1_jason.md:133`) und ist eine Verkürzung der Story, kein eigener Entscheid. US-06 ist die Anforderung von Rang. |
| Position | über der Safe-to-Spend-Card, unterhalb der `h1` | US-06 verlangt «am oberen Rand des Dashboards». |
| Komponente | bestehendes `<app-notice variant="error">` | Erfüllt AC 2 bereits vollständig: `background: $c-negative-soft`, `color: $c-negative`, `font-weight: 600` (`frontend/src/app/shared/notice/notice.scss:18-22`), in beiden Themes definiert (`frontend/src/styles/styles.scss:83-84` hell, `:127-128` dunkel), plus `role="alert"` (`frontend/src/app/shared/notice/notice.ts`). Ein eigenes rotes Element würde die Token-Basis duplizieren und bei einem Theme-Wechsel auseinanderlaufen. |
| Rendering-Zweig | innerhalb `@else if (data(); as d)` | So erscheint das Banner nicht neben dem Lade- oder dem Fehlerzustand — im Fehlerfall steht dort bereits ein `app-notice variant="error"`, zwei rote Banner übereinander wären irreführend. |

## Betroffene Files

| File | Änderung |
| ---- | -------- |
| `frontend/src/app/dashboard/dashboard.html` | Banner-Block `@if (d.negative)` vor `<app-card>` |
| `frontend/src/app/dashboard/dashboard.scss` | `.negative-banner` — Breite und Zentrierung bündig zur Card |
| `frontend/src/app/dashboard/dashboard.ts` | Klassenkommentar: FE-STS-02 ist umgesetzt, nicht mehr «eigenes Issue» |
| `frontend/src/app/dashboard/dashboard.spec.ts` | Fixture `NEGATIVE` + drei Tests |
| `docs/plans/FE-STS-02-negativ-banner.md` | neu (diese Datei) |
| `docs/plans/README.md` | eine Zeile im Index |

Keine Backend-Änderung: `negative` wird von `GET /budget/safe-to-spend` bereits geliefert
(BE-STS-03). Damit auch kein neuer Endpoint und kein OpenAPI-Eintrag.

## Implementierungsschritte

1. Banner in `dashboard.html` ergänzen — `@if (d.negative)` mit `<app-notice variant="error"
   class="negative-banner">`, direkt vor der Card.
2. `.negative-banner` in `dashboard.scss` — gleiche `max-width: 28rem` und Zentrierung wie
   `.safe-to-spend-card`, Abstand über `$sp-*`-Tokens.
3. Klassenkommentar in `dashboard.ts` (Zeilen 14-17) nachziehen: die Doku beschreibt den
   Ist-Zustand, nicht den geplanten.
4. Tests schreiben, `npm test` und `npm run build` im `frontend/` laufen lassen.

## Test-Strategie

Unit-Tests im Angular TestBed (Vitest), je Acceptance Criterion einer:

| AC | Test |
| -- | ---- |
| Banner erscheint wenn negativ | Fixture `NEGATIVE` (`amount: -120`, `negative: true`) → `.negative-banner` vorhanden, Text enthält «ist überzogen», Element trägt `notice--error` und `role="alert"` |
| Hintergrund rot, Text klar lesbar | Nachweis über die Klasse `notice--error` am `app-notice`. Die konkreten Farbwerte liegen in `notice.scss` und den Theme-Tokens und sind dort durch `notice.spec.ts` abgedeckt; ein Unit-Test auf berechnete Farben würde die CSS-Engine testen, nicht diesen Code. |
| Nicht sichtbar wenn nicht negativ | Bestehende Fixture `NORMAL` (`negative: false`) → `.negative-banner` ist `null`. Zusätzlich für `NO_INCOME` (`amount: null`), damit der Platzhalter-Pfad kein Banner mitzieht. |

Kein E2E-Test: US-06 hat bisher keine Playwright-Spec, und die Definition of Done verlangt den
Happy Path «Playwright **oder** JUnit». Der Dashboard-Zustand ist im TestBed vollständig und ohne
Datenbank-Setup steuerbar; ein E2E-Test müsste dafür erst einen User mit überzogenem Budget
aufbauen — das gehört zu einer eigenen US-06-E2E-Spec, nicht in dieses 1-Punkt-Issue.

## Acceptance Criteria (aus dem Issue)

- [ ] Banner erscheint wenn `isNegative=true` — umgesetzt als `negative=true`, siehe Entscheid oben
- [ ] Hintergrund ist rot, Text klar lesbar
- [ ] Banner ist nicht sichtbar wenn `isNegative=false`
