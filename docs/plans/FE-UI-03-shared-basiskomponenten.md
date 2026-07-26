# [FE-UI-03] Shared-Basiskomponenten (Variante A)

- **Issue:** [#100](https://github.com/dfme/budget-buddy/issues/100)
- **Task-ID:** FE-UI-03
- **Hängt an:** #99 (FE-UI-02, Token-Fundament) — PR #107 offen
- **Branch:** `feature/FE-UI-03-shared-basiskomponenten` (abgezweigt von
  `feature/FE-UI-02-design-token-fundament`, **nicht** von `main`)
- **PR-Base:** `feature/FE-UI-02-design-token-fundament` (stacked PR; GitHub retargetet nach
  Merge von #107 automatisch auf `main`)

## Komponenten (standalone, OnPush, `frontend/src/app/shared/`)

| Komponente | Selector | Kern-API / a11y |
|---|---|---|
| Button | `button[appButton], a[appButton]` | `variant: primary\|ghost`, `block`; bleibt natives `<button>`/`<a>` |
| Card | `app-card` | `title`/`meta` + `<ng-content>` |
| Badge | `app-badge` | `category` (Slug), `label`; Farbpunkt + Text |
| Amount | `app-amount` | `value: number`; CHF `1'234.56`, `tabular-nums`, Vorzeichen + Farbe |
| Segment | `app-segment` | `options`, `value` (two-way); `aria-pressed` |
| MonthNav | `app-month-nav` | `label`, `disablePrev/Next`; Outputs `prev/next`; aria-labels |
| Notice | `app-notice` | `variant: warning\|info` + Content; `role="status"` |
| Chip | `app-chip` | `selected` + Content; `aria-pressed` |
| Meter | `app-meter` | `value` (0–100), `variant negative`, Legende; `role="progressbar"` |

## Kategorie-Farben aus einer Quelle (AC)

- Slug→Farbe: `$categories`-Map in `_tokens.scss` (→ `--cat-*`, global einmal definiert). Die
  Badge-SCSS generiert die Punkt-Farbklassen per `@each` daraus — keine Duplizierung.
- Slug/Label-Liste der 13 Kategorien (Showcase/Tests): neue `shared/category.ts`
  (`CATEGORIES`), kommentiert als Spiegel von `Category.java`.

## Showcase (dev-only)

- Route `/styleguide` (Kitchen-Sink), **nicht** in der Nav, `devOnly`-Guard
  (`isDevMode()` → Prod-Redirect auf `/dashboard`).

## Betroffene / neue Files

- Neu: `shared/{button,card,badge,amount,segment,month-nav,notice,chip,meter}/` je
  `*.ts`/`*.html`/`*.scss`/`*.spec.ts`; `shared/category.ts`;
  `styleguide/styleguide.{ts,html,scss}`; `core/guards/dev-only.guard.ts` (+ spec).
- Ändern: `app.routes.ts` (Route `/styleguide`), `docs/plans/FE-UI-03-…md`.

## Test-Strategie

- Unit (Vitest + TestBed): je Komponente ein Spec (Render, Varianten-Klassen, Output-Emits,
  ARIA, Amount-Formatierung). Guard-Spec für `devOnly`.
- `ng build` + `ng test` grün, Prettier sauber.
- Manueller Nachweis: `/styleguide` im Smartphone-Viewport (Tokens/Fokus/kein Overflow).

## Acceptance Criteria (Issue #100)

- [ ] Standalone-Komponenten in `shared/`, alle `OnPush`
- [ ] Button, Card, Badge, Amount, Segment, Month-Nav, Notice, Chip, Meter umgesetzt
- [ ] Kategorie-Farben aus einer Quelle (Token-basiert, 13 Kategorien)
- [ ] Positiv/negativ nie nur farbcodiert (Vorzeichen + Text)
- [ ] Sichtbarer Fokus + sinnvolle ARIA/Tastatur je Komponente
- [ ] CHF-Format `1'234.56`, Datum `dd.MM.yyyy`
