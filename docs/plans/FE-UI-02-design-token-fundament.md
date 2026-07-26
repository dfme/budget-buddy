# [FE-UI-02] Design-Token-Fundament (Variante A, theme-fähig)

- **Issue:** [#99](https://github.com/dfme/budget-buddy/issues/99)
- **Task-ID:** FE-UI-02
- **Branch:** `feature/FE-UI-02-design-token-fundament`
- **Basiert auf:** Design-Entscheid FE-UI-01 (#80), ADR-11 — Variante A «Klarheit»

## Entscheid Komponenten-Unterbau (AC-Punkt 4)

**Custom SCSS + `@angular/cdk`.**

Der eigene Variante-A-Look bleibt zu 100 % über die Design-Tokens erhalten; `@angular/cdk`
liefert ausschliesslich die a11y-harten Primitive (Overlay/Dialog, Focus-Trap, LiveAnnouncer,
`cdk-table`) für den späteren Korrektur-Dialog / das Bottom-Sheet (US-05). Das entspricht der
„dritten Option" aus `design/README.md`: eigener minimaler Look, aber die barrierefreie Mechanik
nicht von Hand bauen. Kostenprofil: ein einmalig installiertes, vorerst kaum genutztes Paket
gegen wiederkehrenden, unterschätzten Aufwand für einen von Hand korrekt gebauten Dialog.
Paket wird installiert; Begründung wird als Issue-Kommentar dokumentiert.

## Architektur-Grundsatz

- **`frontend/src/styles/_tokens.scss`** (neuer Partial, **emittiert kein CSS**): SCSS-Aliase
  `$c-* → var(--c-*)`, `$categories`-Map, Spacing-/Typo-/Radius-SCSS-Variablen, `$bp-desktop`,
  Mixins `desktop` + `tabular`. Per `@use 'tokens'` von Komponenten nutzbar.
- **`frontend/src/styles.scss`** (global, **einmal geladen**): die eigentlichen
  Custom-Property-Werte in `:root` / `[data-theme="light"]` / `[data-theme="dark"]`
  (theme-fähig), Reset + Basis-Styles.
- **Warum getrennt:** Custom-Property-Definitionen (`:root { … }`) dürfen nur **einmal global**
  stehen. Lägen sie im Partial, würde jede Komponente, die `@use`t, sie duplizieren. Der Partial
  enthält daher ausschliesslich nebeneffekt-freie Deklarationen (Variablen, Maps, Mixins).
- **Keine** Komponentenklassen (`.card`, `.badge`, `.btn`) und **keine** App-Shell hier — das ist
  laut Roadmap FE-UI-03 (Basiskomponenten) bzw. FE-UI-04 (Shell). Scope: Tokens + Reset + Basis.

## Betroffene / neue Files

| Aktion | Datei | Inhalt |
|---|---|---|
| neu | `frontend/src/styles/_tokens.scss` | SCSS-Var-Aliase, `$categories`, Spacing/Typo/Radius, Mixins `desktop`/`tabular` |
| ändern | `frontend/src/styles.scss` | `:root`/`[data-theme]`-Blöcke (light+dark, `--c-*` + `--cat-*` für alle 13 Kategorien), `--shadow-card`, Reset (box-sizing, `system-ui`, sichtbarer `:focus-visible`), Body-Basis |
| ändern | `frontend/angular.json` | `stylePreprocessorOptions.includePaths: ["src/styles"]` (Build **und** Test) |
| ändern | `frontend/package.json` / lockfile | `@angular/cdk@^21.2.0` |
| ändern (Nachweis) | `frontend/src/app/transactions/category-overview.scss` | Hardcodierte Hex → Tokens; `tabular`-Mixin |
| ändern (Nachweis) | `frontend/src/app/app.scss` | Shell-Header auf Spacing-/Farb-Tokens |

## Implementierungsschritte

1. `@angular/cdk@^21.2.0` installieren.
2. `src/styles/_tokens.scss` anlegen (kein CSS-Output).
3. `src/styles.scss`: Theme-Custom-Properties (light default via `:root`, dark via
   `[data-theme="dark"]`), Reset + Basis; `@use 'tokens' as *`.
4. `angular.json`: `includePaths` für Build- und Test-Target ergänzen.
5. Nachweis: `category-overview.scss` (+ leicht `app.scss`) auf Tokens migrieren.
6. Verifizieren: `ng build` und `ng test`.

## Test-Strategie

- **Happy Path (automatisiert):** bestehender `category-overview.spec.ts` bleibt grün nach der
  Token-Migration → beweist, dass der Partial via `includePaths` auch im Test-Builder auflöst und
  die Tokens die Darstellung nicht brechen.
- **`ng build` + `ng test`** fehlerfrei (DoD). Ein fehlender Token/Pfad lässt den SCSS-Compile
  hart scheitern — die eigentliche Absicherung des Fundaments.
- **Bewusst kein** brittler CSS-Runtime-Unittest (Custom Properties aus dem globalen Stylesheet
  sind in jsdom nicht zuverlässig auslesbar) — analog zum als „n/a" behandelten Swagger-DoD-Punkt.

## OnPush/Signals-Kompatibilität (AC-Punkt 5)

Global nur `:root`-Tokens, Reset auf Element-Selektoren und `body`. Tokens sind Custom Properties
(kaskadieren sauber durch Emulated Encapsulation). Keine Änderung an Change-Detection/Signals.

## Acceptance Criteria (aus Issue #99)

- [ ] Farb-Tokens (`--c-*`, `--cat-*` für 13 Kategorien) als CSS Custom Properties in
      `frontend/src/styles.scss`, umschaltbar über `data-theme`
- [ ] Spacing-, Typo- und Radius-Tokens übernommen (SCSS-Variablen bzw. Custom Properties)
- [ ] Globaler Reset + Basis-Styles (Box-Sizing, sichtbarer Fokus, `system-ui`)
- [ ] Entscheid Custom SCSS pur vs. + `@angular/cdk` getroffen und im Issue dokumentiert
      (CDK gewählt → Paket installiert, Begründung)
- [ ] OnPush + Signals bleiben kompatibel
- [ ] Kurzer Nachweis, dass die Tokens greifen (Anwendung auf bestehendem Platzhalter-Screen)
