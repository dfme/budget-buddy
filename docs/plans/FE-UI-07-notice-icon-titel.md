# [FE-UI-07] Notice-Komponente: Icon und optionaler Titel

- **Issue:** [#181](https://github.com/dfme/budget-buddy/issues/181)
- **Task-ID:** `FE-UI-07`
- **Branch:** `feature/FE-UI-07-notice-icon-titel`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-21

## Entscheide

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Icons je Variante | `warning: !`, `info: i`, `error: ✕` | `!` ist aus der Design-Baseline belegt (`design/variant-a/index.html:226`, `transactions.html:275`); für `info` und `error` gibt es dort kein Vorbild. Monoline-Zeichen wie der Rest der App (`⏻`, `↑`, `◎`, `≡`, `⚙` in `shell.html`, `pdf-upload.html`) — `⚠`/`ℹ` wären auf macOS/iOS und Android häufig als farbige Emoji gerendert und würden aus der Farbgebung der Variante ausbrechen. |
| Name des Titel-Inputs | `title` | Gleiche Benennung wie `card.ts:19`. Kein zweites Vokabular für dieselbe Sache in derselben Komponenten-Familie. |
| Scope-Erweiterung | die 4 handgeschriebenen `<p class="status error">` werden mitmigriert | Vom User im Rahmen der Plan-Rückfragen bestätigt. Es ist dieselbe Doppelspurigkeit, die das Issue beschreibt. `.status empty` und `.status` («Lädt …») bleiben — das ist Zustandstext, kein Banner. |
| Titel-Element | `<span>` + `font-weight: 600`, **kein** `<h*>` | Die Komponente sitzt in einer Live-Region (`role="status"`/`"alert"`). Ein Heading darin verzerrt die Dokument-Gliederung. Entspricht dem heutigen Rendering von `.no-income__headline` (`dashboard.scss:83`). |

## Aufbau der Komponente

`<ng-content />` lässt sich nicht in beide Zweige eines `@if` legen — Angular projiziert einmal.
Deshalb **immer** ein Body-Wrapper, der Titel und Inhalt stapelt; der Titel ist das bedingte
Element darin:

```html
<span class="notice__icon" aria-hidden="true">{{ icon() }}</span>
<span class="notice__body">
  @if (title(); as heading) { <span class="notice__title">{{ heading }}</span> }
  <ng-content />
</span>
```

Damit ist die Flex-Zeile von `:host` (`notice.scss:4-5`) fest mit Icon + Body belegt. Die Falle
aus dem Issue — mehrere Blöcke im Notice landen nebeneinander statt untereinander — existiert
danach nicht mehr, weil der Aufrufort nicht mehr direkt in die Flex-Zeile schreibt.

Alle 18 Nicht-Dashboard-Aufruforte projizieren reinen Text (nur Interpolationen); für die
rendert ein Column-Flex-Body pixelgleich. Das ist der Nachweis für AC 4.

## Betroffene Files

### Neu

- `frontend/src/app/shared/notice/notice.html` — Template wandert aus dem Inline-`template`

### Geändert — Komponente

- `notice.ts` — `title`-Input, `icon`-Computed aus `variant`, `templateUrl`
- `notice.scss` — `.notice__icon` (`flex: none`), `.notice__body` (Column-Flex, `min-width: 0`), `.notice__title`
- `notice.spec.ts` — neue Fälle (siehe Test-Strategie)

### Geändert — Migration Dashboard (AC 5)

- `dashboard.html` — handgeschriebenes `<span aria-hidden>!</span>` und `.no-income__text`/`__headline`/`__hint` raus, `title="Kein Einkommen erfasst"` rein; der Kommentar zur Flex-Falle wird gekürzt, weil die Falle weg ist
- `dashboard.scss` — `.no-income__text`, `.no-income__headline` entfallen
- `dashboard.spec.ts` — Zeilen 155–181 und 207 auf die neue Struktur

### Geändert — Scope-Erweiterung, 4 Fehlerbanner

- `category-overview.html:18, 90, 135`, `category-overview.scss` (`.status.error` entfällt), `category-overview.spec.ts:235, 591, 882`
- `fixed-cost-list.html:10`, `fixed-cost-list.scss` (`.status.error` entfällt), ggf. `fixed-cost-list.spec.ts`

### Geändert — Aufruforte, die durch das Icon brechen

- `login.spec.ts:98`, `register.spec.ts:111` — prüfen `textContent.trim()` mit `toBe(...)`; das Icon
  steht ab jetzt mit drin. Ziel wird `.notice__body`, nicht ein aufgeweichtes `toContain`.

### Geändert — Styleguide (AC 6)

- `styleguide.html:74-79` — je Variante ein Beispiel mit und ohne Titel

### Nicht angefasst

Die übrigen 15 Aufruforte. Sie projizieren Text, erben das Icon und bleiben sonst unverändert.

## Implementierungsschritte

1. `notice.ts` / `notice.html` / `notice.scss` umbauen (Icon-Map, `title`, Body-Wrapper)
2. `notice.spec.ts` erweitern
3. Dashboard migrieren, Spec nachziehen
4. Die 4 `.status error` auf `app-notice variant="error"` migrieren, SCSS-Regeln entfernen, Specs nachziehen
5. `login.spec.ts` / `register.spec.ts` nachziehen
6. Styleguide erweitern
7. `npm test`, `npm run build`, Prettier/Lint

## Test-Strategie

- **Unit (Vitest + Angular TestBed)** — `notice.spec.ts` deckt AC 7 vollständig ab: Icon je
  Variante, `aria-hidden` am Icon, `role` je Variante unverändert, Titel **oberhalb** des Inhalts
  (Reihenfolge über `compareDocumentPosition`, nicht über Text-Enthaltensein), ohne Titel kein
  `.notice__title`
- **Bestehende Component-Specs** — Regressionsnetz für AC 4/5: dashboard, login, register,
  category-overview, fixed-cost-list, pdf-upload
- **E2E** — `e2e/tests/pdf-import.spec.ts:63,105,113` selektiert
  `app-notice.notice--info[role="status"]` bzw. `.notice--error[role="alert"]`. Host-Klassen und
  `role` bleiben unverändert, die Selektoren gelten weiter. Kein neuer E2E-Test: die Änderung ist
  rein visuell/strukturell innerhalb einer Shared-Komponente.
- **Backend** — nicht berührt, `mvn` läuft nicht.

## Acceptance Criteria (aus dem Issue)

- [ ] `app-notice` rendert das Icon selbst, abgeleitet aus `variant` — der Aufrufort schreibt keines mehr
- [ ] Das Icon ist für Screenreader unsichtbar (`aria-hidden`), die Variante bleibt allein über `role` (`alert` bei `error`, `status` sonst) hörbar
- [ ] Optionaler Titel als Input; ist er gesetzt, steht er **über** dem Inhalt, nicht daneben — die Flex-Zeile der Komponente stapelt Titel und Inhalt intern
- [ ] Ohne Titel bleibt das Rendering unverändert, damit die bestehenden Aufruforte nicht umgebaut werden müssen
- [ ] Alle 19 Aufruforte geprüft und, wo nötig, migriert; das handgeschriebene Icon im No-Income-Hinweis (`dashboard.html`, FE-STS-03) ist entfernt
- [ ] Styleguide (`/styleguide`) zeigt die Varianten mit und ohne Titel
- [ ] Tests decken ab: Icon je Variante, Titel oberhalb des Inhalts, `role` unverändert
