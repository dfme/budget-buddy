# [FE-CAT-02] Pie-Chart Ausgaben nach Kategorie

- **Issue:** [#31](https://github.com/dfme/budget-buddy/issues/31)
- **Task-ID:** `FE-CAT-02`
- **Branch:** `feature/FE-CAT-02-kategorie-donut-chart`
- **Story:** US-05 — Transaktionen kategorisieren (Auto + manuell)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-09

## Ziel

Die Ausgabenverteilung des gewählten Monats als Chart über der Kategorie-Tabelle —
Farben identisch zu den Badges der Tabelle, mit lesbarer Legende, aktualisiert beim
Monatswechsel.

## Ausgangslage

Der Chart-Baustein existiert bereits aus FE-UI-05 (#102):
`frontend/src/app/shared/chart/donut-chart.ts` bringt Token-Farben (`--cat-<slug>`),
HTML-Legende, Theme-Reaktivität über `ChartTheme` und eigene `CHART_PROVIDERS` mit.
Er war bisher nur im Styleguide eingebunden. FE-CAT-01 (#30) liefert mit
`CategoryOverview` bereits Monatsnavigation, Lade-, Fehler- und Leerzustand sowie die
`CategorySummary`-Daten aus BE-CAT-05.

Diese Aufgabe ist damit **Verdrahtung, kein neuer Chart-Code**.

## Acceptance Criteria (aus Issue)

- [ ] Pie-Chart rendert fehlerfrei mit realen Daten
- [ ] Farben sind den Kategorien konsistent zugeordnet
- [ ] Legende ist lesbar
- [ ] Chart aktualisiert sich bei Monatswechsel

## Entscheide

- **Donut statt echtem Pie.** Das Issue sagt «Pie-Chart», die Design-Baseline Variante A
  (ADR-11, umgesetzt in FE-UI-05) liefert einen Donut. Die Aussage ist dieselbe —
  Ausgabenverteilung nach Kategorie —, und der bestehende Baustein bringt Farben,
  Legende, a11y-Beschreibung und Theme-Wechsel bereits getestet mit. Eine zweite
  Chart-Komponente (oder ein `cutout`-Input an der bereits abgenommenen Shared-Komponente)
  würde dieselbe Logik für dieselbe Aussage duplizieren. Vom User am 2026-08-09 bestätigt.
- **Nur `/categories`.** Das Dashboard ist bis US-06 ein Platzhalter; eine zweite
  Einbindung dort wäre Scope, den das Issue nicht verlangt.
- **Slug-Zuordnung über die bestehende `SLUG_BY_LABEL`-Map.** Dieselbe Map, die schon die
  Tabellen-Badges speist — dadurch ziehen Chart-Segment, Legendenpunkt und Badge derselben
  Zeile garantiert dieselbe `--cat-<slug>`-Farbe (AC 2). Eine zweite Zuordnungsquelle wäre
  genau die Stelle, an der die Konsistenz später auseinanderläuft.
- **Unbekanntes Label → Label als Slug.** `DonutSlice.slug` ist `string`, `categorySlug()`
  liefert `string | undefined`. Fallback ist das Label selbst: es trifft kein
  `--cat-*`-Token, die Komponente rendert das Segment darum neutral grau (`lineStrong`)
  statt bunt — ein unbekanntes Backend-Label fällt so auf, statt eine fremde
  Kategorie-Farbe zu belegen. Als `track`-Wert der Legende bleibt es eindeutig, weil das
  Backend pro Kategorie genau eine Zeile liefert.
- **Chart nur im geladenen Nicht-Leer-Zustand.** Er lebt im bestehenden
  `@else if (summary(); as data)`-Zweig; Lade-, Fehler- und Leerzustand aus FE-CAT-01
  bleiben unverändert.

## Betroffene Files

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/app/transactions/category-overview.ts` | `slices()`-Computed, `DonutChart` in `imports` |
| `frontend/src/app/transactions/category-overview.html` | `<app-donut-chart>` in eigener `app-card` über der Tabelle |
| `frontend/src/app/transactions/category-overview.scss` | Abstand der Chart-Karte (nur `$sp-*`-Tokens) |
| `frontend/src/app/transactions/category-overview.spec.ts` | Canvas-Stub + Tests zu AC 1–4 |
| `docs/plans/FE-CAT-02-kategorie-donut-chart.md` (neu) | dieser Plan |
| `docs/plans/README.md` | Indexzeile |

## Implementierungsschritte

1. `slices()` in `CategoryOverview`: `summary().categories` → `DonutSlice[]`
   (`label` = API-Label, `value` = `amount`, `slug` = `SLUG_BY_LABEL.get(label) ?? label`).
   Leeres Array, solange nichts geladen ist.
2. `DonutChart` importieren und im Template über der Tabelle in einer `app-card` rendern.
3. SCSS: Abstand zwischen Chart-Karte und Tabellen-Karte.
4. Tests ergänzen (siehe unten).
5. `npm test` und `npm run build` — der Build prüft zugleich das Bundle-Budget, weil
   Chart.js neu in den Lazy-Chunk von `/categories` wandert.

AC 4 fällt strukturell an: `slices()` leitet sich von `summary()` ab, das der
Monatswechsel bereits neu setzt. Kein eigener Update-Pfad — aber ein Test darauf.

## Test-Strategie

Vitest + TestBed, wie FE-CAT-01. Kein E2E: `e2e/tests/` deckt bisher Auth und Routing ab,
und FE-CAT-01 ist ebenfalls über Component-Tests abgesichert.

- `installCanvasStub()` / `restoreCanvasStub()` aus `frontend/src/testing/canvas.ts` in
  `beforeEach`/`afterEach` — ohne 2D-Kontext und `ResizeObserver` kommt Chart.js in jsdom
  nicht hoch.
- **AC 1:** nach `flush(SUMMARY)` existiert `app-donut-chart canvas` mit `role="img"` und
  nicht-leerem `aria-label`.
- **AC 2:** `slices()` liefert `wohnen` / `lebensmittel` zu den API-Labels; ein
  unbekanntes Label fällt auf sich selbst zurück.
- **AC 3:** zwei `.legend__item` mit Kategorienamen und Betrag im Schweizer Format.
- **AC 4:** `previousMonth()` → Antwort mit anderen Kategorien → `slices()` und die
  gerenderte Legende zeigen die neuen Werte.
- Gegenprobe Leerzustand: kein `app-donut-chart` im DOM.

## Nachtrag: Scope-Erweiterung Dev-Proxy (2026-08-09)

Bei der manuellen Verifikation zeigte die Kategorie-Übersicht in jedem Monat
«Die Kategorie-Übersicht konnte nicht geladen werden». Ursache war nicht der Chart,
sondern eine Lücke in `frontend/proxy.conf.json`: der Proxy kannte `/auth`, `/users` und
`/import`, aber nicht `/transactions`. `ng serve` beantwortete `/transactions/summary`
darum mit dem SPA-Fallback `index.html` (200, `text/html`), der `HttpClient` scheiterte am
JSON-Parsen und die Komponente lief in ihren Fehlerzweig.

Belegt durch: `curl localhost:8080/transactions/summary?month=2025-03` → 401 (Backend
korrekt), `curl localhost:4200/...` → 200 `text/html` mit `<!doctype html>`.

Damit war FE-CAT-01 seit BE-CAT-05 in der lokalen Entwicklung defekt. Produktion und E2E
blieben unauffällig, weil dort SPA und API aus demselben JAR im selben Origin laufen —
es gibt gar keinen Proxy.

Der Fix (`/transactions` im Proxy, Prefix-Liste im Spec vereinheitlicht) gehört nach
CLAUDE.md eigentlich in ein eigenes INFRA-Ticket. Der User hat am 2026-08-09 entschieden,
ihn in diesem PR mitzunehmen; er ist dort als Scope-Erweiterung deklariert.
