# [FE-UI-06] Bestehende Screens auf Variante-A-Fundament migrieren

- **Issue:** [#104](https://github.com/dfme/budget-buddy/issues/104)
- **Task-ID:** FE-UI-06
- **Branch:** `feature/FE-UI-06-variante-a-migration`
- **User Stories:** US-01 (Login/Register), US-05 / US-13 (Kategorie-Übersicht)

## Entscheide

- **Input/Field:** FE-UI-03 hat keine Field-/Input-Komponente geliefert, das AC nennt aber
  eine. Entscheid (Rückfrage mit User): **neue Basiskomponente** `app-field` + `input[appInput]`
  bauen — House-Style: Attribut-Komponente auf nativem Element (analog `button[appButton]`),
  a11y bleibt erhalten, kein `::ng-deep`.
- **Kein neuer Design-Token:** Es wird ausschliesslich das bestehende FE-UI-02-Token-Fundament
  genutzt (`$c-line-strong`, `$c-negative`, `$r-md`, `$sp-*`, …).
- **Card in Auth + Kategorie-Übersicht:** Formular bzw. Tabelle in `app-card` einbetten (A-Look,
  Surface/Elevation). `<h1>` bleibt als Seitentitel erhalten (Card ohne `title`, damit keine
  h1→h2-Degradierung).
- **Scope-Grenze verifiziert:** Einzige hartcodierte Farben im Frontend sind die `#b00020` in
  auth login/register (= dieser Scope). `dashboard/` (Platzhalter, 0 Farben → #33 FE-STS-01) und
  `app.html`/`app.scss` (0 Farben → #101 FE-UI-04) bleiben bewusst aussen vor.

## Neue Files

| File | Zweck |
|---|---|
| `frontend/src/app/shared/field/field.ts` / `.html` / `.scss` / `.spec.ts` | `app-field` — Label + projizierter Input + optionale Fehlermeldung (`role="alert"`, `$c-negative`) |
| `frontend/src/app/shared/input/input.ts` / `.scss` / `.spec.ts` | `input[appInput]` — natives `<input>` im A-Look; roter Rahmen bei `.ng-invalid.ng-touched` |

## Geänderte Files

| File | Änderung |
|---|---|
| `auth/login.html` + `login.ts` + `login.scss` | Form in `app-card`; Felder auf `app-field`+`appInput`; Form-Fehler auf `app-notice`; Submit `button appButton block`; `emailError()`/`passwordError()`-Helfer; SCSS auf Tokens, **kein `#b00020`** |
| `auth/register.html` + `register.ts` + `register.scss` | Analog Login (inkl. minlength-Meldung) |
| `transactions/category-overview.html` + `.ts` + `.scss` | Month-Selector → `app-month-nav`; Kategorie-Zelle → `app-badge` (Label→Slug-Lookup via `CATEGORIES`); Tabelle in `app-card`; `.month-selector`/`.nav`/`.month-label`-SCSS entfällt |
| `styleguide/styleguide.html` + `.ts` | Neue «Field»-Sektion (Normal + Fehlerzustand) + Imports (lebender FE-UI-03-Nachweis) |

## Implementierungsschritte

1. `app-field` + `input[appInput]` bauen (Tokens, a11y: `for`↔`id`, `role="alert"`).
2. Login migrieren (Card, Field, Input, Notice, Button; SCSS entschlacken).
3. Register migrieren (analog, minlength-Meldung).
4. Category-Overview: MonthNav + Badge + Card einsetzen, Label→Slug-Helfer.
5. Styleguide um Field-Sektion ergänzen.
6. Tests + Build grün ziehen.

## Test-Strategie

- **Neu:** `field.spec.ts` (Label/`for`, Content-Projektion, Fehler an/aus), `input.spec.ts`
  (bleibt natives `<input>`).
- **Unverändert grün:** `login.spec.ts`, `register.spec.ts`, `category-overview.spec.ts` —
  Selektoren (`table`/`tbody tr`/`td`, `.status.empty|error`, `button[aria-label="Nächster Monat"]`)
  bleiben gültig, keine Anpassung nötig.
- **Nachweis-Grep:** kein Hex/rgb in migrierten Komponenten-SCSS.
- **Build:** `npm test` (Vitest) + `ng build` fehlerfrei.

## Acceptance Criteria (aus Issue #104)

- [ ] `auth/login` + `auth/register` auf Tokens und Basiskomponenten (Button, Input/Field, Card, Notice) umgestellt
- [ ] `transactions/category-overview` auf Tokens und Komponenten umgestellt (Tabelle, Badges, Month-Nav im A-Look)
- [ ] **Kein** hartcodierter Hex-/rgb-Farbwert mehr in den migrierten Komponenten-SCSS (grep-Nachweis)
- [ ] Funktionalität und bestehende Tests unverändert grün
- [ ] Visuell konsistent mit Variante A, mobile-first (375px nutzbar)
