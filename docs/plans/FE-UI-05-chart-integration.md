# [FE-UI-05] Chart-Integration: ng2-charts + Donut/Bar-Basiskomponenten (Variante A)

- **Issue:** [#102](https://github.com/dfme/budget-buddy/issues/102)
- **Task-ID:** `FE-UI-05`
- **Branch:** `feature/FE-UI-05-chart-integration`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag (zusätzlich US-10 — Monatsvergleich)
- **Sprint:** Sprint 3
- **Bestätigt am:** 2026-08-01

## Ausgangslage

`chart.js` und `ng2-charts` sind laut Tech-Stack gesetzt, aber nicht installiert. Die
Chart-Konfiguration der Design-Variante A liegt als lauffähiger Prototyp in
`design/variant-a/charts.js` vor und ist portierbar; die Farben kommen aus dem
Token-Fundament aus FE-UI-02 (`--c-*`, `--cat-*`).

Dieses Issue liefert nur die Bausteine. Die Einbindung in Dashboard (US-06) und
Monatsvergleich (US-10) erfolgt in den jeweiligen Feature-Issues.

## Entscheide

| Frage             | Entscheid                                                                                                       | Begründung                                                                                                                                                                                                                                                 |
| ----------------- | --------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Lib-Versionen     | `chart.js@^4` + `ng2-charts@^10`                                                                                | `ng2-charts@8` (Tech-Stack-Stand) ist gegen Angular 19 gebaut; v10 ist die Linie mit Peer `@angular/core >=21.0.0`. Das Frontend läuft auf Angular 21.2.                                                                                                   |
| Doku-Delta        | `CLAUDE.md:138` auf `4.x / 10.x` korrigieren                                                                    | Vom User als Scope-Erweiterung freigegeben; die Zeile ist die einzige Stelle im Repo mit einer Chart-Versionsangabe (ADR-2 und `design/README.md` nennen die Libs ohne Version). `docs/prompts/` und `docs/plans/` sind Historie und bleiben unangetastet. |
| Theme-Reaktivität | Root-Service `ChartTheme`: Token-Farben als Signal, aktualisiert per `MutationObserver` auf `<html data-theme>` | Ein Canvas kennt keine CSS-Variablen. Der Prototyp löst das über ein `themechange`-Event (`design/variant-a/charts.js:19-22`); in Angular ist Signal + `computed()` das Äquivalent ohne globales Event.                                                    |
| Chart.js-Defaults | Kein globales `Chart.defaults`-Mutieren                                                                         | Der Prototyp tut das (`charts.js:81-84`). Global mutierter State ist weder theme- noch testsicher; Font und Farben gehen pro Chart in die `options`.                                                                                                       |
| Legende           | Eigene HTML-Legende statt Chart.js-Legende                                                                      | Übernommen aus dem Prototyp (`design/variant-a/index.html:115-116`): bricht auf 375 px zuverlässig um und zeigt die Beträge direkt mit.                                                                                                                    |
| Nachweis          | Charts-Sektion im dev-only `/styleguide` mit den Demo-Daten aus `charts.js`                                     | Gleiches Muster wie FE-UI-03; sichtbarer Beleg inklusive Theme-Toggle.                                                                                                                                                                                     |

## Betroffene Files

### Neu

- `frontend/src/app/shared/chart/chart-theme.ts` — Root-Service: Token-Farben als Signal, `MutationObserver` auf `data-theme`
- `frontend/src/app/shared/chart/chart-theme.spec.ts`
- `frontend/src/app/shared/chart/chart-options.ts` — reine Helfer: Tooltip-Optionen, CHF-Tooltip-Callback (nutzt `formatSwissAmount` aus `shared/format.ts`)
- `frontend/src/app/shared/chart/chart-options.spec.ts`
- `frontend/src/app/shared/chart/donut-chart.ts|.html|.scss|.spec.ts`
- `frontend/src/app/shared/chart/bar-chart.ts|.html|.scss|.spec.ts`
- `frontend/src/testing/canvas.ts` — 2D-Context- und `ResizeObserver`-Stub für jsdom (Chart.js wirft ohne Context)

### Geändert

- `frontend/package.json`, `frontend/package-lock.json` — `chart.js`, `ng2-charts`
- ~~`frontend/src/app/app.config.ts` — `provideCharts(withDefaultRegisterables())`~~ → stattdessen `frontend/src/app/shared/chart/chart-providers.ts` (siehe Implementation Notes)
- `frontend/src/app/styleguide/styleguide.ts|.html` — Donut-/Bar-Sektion
- `CLAUDE.md` — Chart-Versionen im Tech-Stack
- `docs/plans/README.md` — Index-Zeile

## Komponenten-API

```ts
// donut-chart.ts
readonly data = input.required<readonly DonutSlice[]>();   // { slug, label, value }
readonly showLegend = input(true);
readonly ariaLabel = input<string>();   // Default: aus data generiert
```

Farben aus `--cat-<slug>`, Trennlinie in `--c-surface`, `cutout: '72%'`.

```ts
// bar-chart.ts
readonly data = input.required<readonly BarPoint[]>();     // { label, value }
readonly highlightIndex = input<number>();                 // Default: letzter Eintrag
readonly ariaLabel = input<string>();
```

Hervorhebung `--c-accent`, Historie `--c-line-strong`, Grid `--c-line`, `borderRadius: 4`,
`barPercentage: 0.62` — übernommen aus `charts.js:130-160`.

Beide Komponenten: `OnPush`, `role="img"` + `aria-label` am Canvas, `.chart`-Wrapper mit
fester Höhe (220 px, Desktop 260 px) — Chart.js wächst bei `responsive: true` sonst endlos.

## Implementierungsschritte

1. `npm install chart.js ng2-charts` im `frontend/`, Versionen prüfen
2. ~~`provideCharts(withDefaultRegisterables())` in `app.config.ts`~~ → komponentenlokal (siehe Implementation Notes)
3. `ChartTheme`-Service + Spec (Signal-Update bei `data-theme`-Wechsel, Observer-Cleanup via `DestroyRef`)
4. `chart-options.ts` (reine Funktionen, ohne DOM)
5. Donut-Komponente + SCSS (Legende portiert aus `design/variant-a/styles.scss:831-865`)
6. Bar-Komponente + SCSS
7. jsdom-Canvas-Stub, Component-Specs
8. Styleguide-Sektion mit den Demo-Daten aus `charts.js`
9. `CLAUDE.md` korrigieren, Plan + Index committen
10. `ng test`, `ng build` inklusive Bundle-Budget-Kontrolle

## Test-Strategie

| Ebene               | Inhalt                                                                                                                                                                                                |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Unit (Vitest)       | `chart-options.spec` — CHF-Format im Tooltip-Callback, Tooltip-Farben aus übergebenen Tokens                                                                                                          |
| Unit (Vitest)       | `chart-theme.spec` — Signal liefert Token-Werte, aktualisiert bei `data-theme`-Wechsel, Observer wird bei Destroy getrennt                                                                            |
| Component (TestBed) | `donut-chart.spec` — `role="img"` + generiertes `aria-label` mit allen Kategorien/Beträgen, Legenden-Items = Datenlänge, `data-cat`-Slug am Punkt, Dataset-Farben aus `--cat-*`, `ariaLabel`-Override |
| Component (TestBed) | `bar-chart.spec` — Labels, hervorgehobener Balken in Akzentfarbe / übrige `--c-line-strong`, `highlightIndex`-Override, `aria-label`                                                                  |

Kein Playwright: `/styleguide` ist im Prod-Build über den `devOnlyGuard` gesperrt, und E2E
läuft laut Konvention gegen das Prod-JAR — ein E2E-Test darauf wäre nicht ausführbar. Die
DoD-Zeile „Happy Path automatisiert" wird über die Vitest-Component-Specs erfüllt, wie schon
bei FE-UI-03 (`bb11ce9`).

**Offenes Risiko bei Planung:** Ob Chart.js unter jsdom mit einem selbstgebauten
Context-Stub durchläuft, zeigt sich erst beim Implementieren. Trägt der Stub nicht, wird die
Chart-Konfiguration über die `computed()`-Signale der Komponenten getestet statt über die
gerenderte Chart-Instanz; das wird im Review ausgewiesen. Die DOM-Zusagen (aria, Legende)
bleiben davon unberührt.

## Implementation Notes (Abweichungen vom Plan)

Nachgetragen nach dem Review von [PR #130](https://github.com/dfme/budget-buddy/pull/130),
damit die Abweichungen nicht nur im PR-Text stehen.

1. **`provideCharts` komponentenlokal statt in `app.config.ts`** (`shared/chart/chart-providers.ts`).
   App-weit registriert wuchs das Initial-Bundle auf 488.64 kB von 500 kB Warnbudget, weil
   Chart.js in jeden App-Start wanderte — auch für Nutzer, die nie ein Chart sehen.
   Komponentenlokal bleibt es bei 280.04 kB, dem Stand von `main`; Chart.js liegt in den
   Lazy-Chunks der Routes, die Charts zeigen.
2. **Der jsdom-Canvas-Stub trägt** — das bei der Planung offene Risiko ist damit erledigt:
   die Specs erzeugen echte Chart-Instanzen. Der Stub nutzt bewusst eine feste
   Methodenliste und keinen Catch-all-Proxy: ein Proxy beantwortet auch `length`, worauf
   Chart.js' `getCanvas()` den Kontext für array-artig hält und ein Chart ohne Canvas baut.
3. **Kein `font` auf Chart-Ebene im Donut** (Review-Befund): ohne Skalen, ohne
   Chart.js-Legende und mit explizit gesetzter Tooltip-Schrift hätte die Option keinen
   Konsumenten. Im Bar-Chart steht die Schrift dort, wo sie wirkt — an den Ticks.

## Acceptance Criteria (aus Issue #102)

- [ ] `chart.js` und `ng2-charts` als Dependencies installiert (Versionen gemäss Tech-Stack)
- [ ] Donut-Komponente (Ausgaben nach Kategorie) im A-Look, Legende separat/HTML, 13-Kategorien-Palette aus den Tokens
- [ ] Bar-Komponente (Monatsverlauf) im A-Look, laufender Monat hervorgehoben
- [ ] Farben aus den CSS-Custom-Property-Tokens (kein Hardcoding), theme-fähig
- [ ] OnPush; Daten über `input()`/Signals, damit direkt aus den Feature-Services speisbar
- [ ] Zugängliche Fallback-Beschreibung (`aria-label`/`role="img"`) je Chart
