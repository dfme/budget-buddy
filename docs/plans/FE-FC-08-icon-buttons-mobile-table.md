# [FE-FC-08] Aktions-Buttons mit Icons (Bearbeiten, Löschen, Kein Abo): Icon + Text ab 900px, nur Icon und drei Spalten auf Mobile

- **Issue:** [#356](https://github.com/dfme/budget-buddy/issues/356)
- **Task-ID:** `FE-FC-08`
- **Branch:** `feature/FE-FC-08-icon-buttons-mobile-table`
- **Story:** US-03 — Fixkosten-Wizard
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-24

## Entscheide

1. **`appButton` bekommt einen Icon-Slot und einen Input `iconOnlyMobile`.** Template:
   `<ng-content select="svg" /><span class="btn__label"><ng-content /></span>`. Grund: unter
   emulierter Kapselung erreicht `button.scss` projizierten Inhalt nicht, `::ng-deep` nutzt das
   Repo nirgends — der Label-Span muss im Button-eigenen Template liegen. `iconOnlyMobile`
   versteckt das Label unter `$bp-desktop` visuell (Mixin `visually-hidden`) und macht den Button
   44×44px. Nebenwirkung für alle `appButton`: der Text steckt künftig in einem `<span>` —
   optisch gleich (`inline-flex` behandelt Text ohnehin als Flex-Item), `textContent` gleich.
2. **Zugänglicher Name mit Position in beiden Varianten** (`aria-label="Bearbeiten: Miete"`),
   dasselbe Muster wie das bestehende `Kein Abo: {payeeKey}`. Ein breakpointabhängiger Name ginge
   nicht ohne JS; der Name beginnt mit dem sichtbaren Label (WCAG 2.5.3 Label in Name).
3. **Schmale Tabelle über CSS, ein Markup.** Betrag- und Intervall-Zellen `display: none` unter
   900px, Unterzeile als `<span>` in der Bezeichnungs-Zelle nur unter 900px sichtbar. Das
   `colspan="3"` der Total-Zeile wird in `<th>Total</th>` plus zwei leere, mitverschwindende Zellen
   aufgelöst — sonst liefe es auf Mobile gegen drei statt fünf Spalten. Spaltenkopf bleibt
   «Monatsbetrag» (AC-Wortlaut; das Mockup zeigt «Monatlich», ein zweiter Kopftext hiesse zwei
   Markups). Die Bezeichnung bleibt `<td>`, damit die E2E-Zellindizes stehen.
4. **Wartezustand «Kein Abo»:** `disabled` + `aria-busy`; `:host([aria-busy='true'])` im Button
   pulsiert, bei `prefers-reduced-motion` nicht.
5. **Styleguide** (`/styleguide`, dev-only) zeigt die neuen Button-Fähigkeiten: Ghost mit Icon,
   Icon only unter 900px, Busy.

## Betroffene Dateien

- `frontend/src/styles/_tokens.scss` — `@mixin visually-hidden`
- `frontend/src/styles.scss` — `.visually-hidden` nutzt das Mixin
- `frontend/src/app/shared/button/button.{ts,scss,spec.ts}`
- `frontend/src/app/onboarding/fixed-cost-list.{html,scss,ts,spec.ts}`
- `frontend/src/app/recurring/recurring-expense-list.{html,scss,spec.ts}`
- `frontend/src/app/styleguide/styleguide.{html,spec.ts}`
- neu: `e2e/tests/fixed-cost-list-mobile.spec.ts`

## Implementierungsschritte

1. Mixin `visually-hidden`, Button umbauen + `button.spec.ts`
2. Fixkosten-Tabelle: Icons, `aria-label`, Spaltenklassen, Unterzeile, Total-Zeile,
   «Aktionen»-Kopf nur für Screenreader unter 900px, `min-width: 32rem` nur ab Desktop,
   `overflow-wrap: anywhere` für die Bezeichnung, `.table-scroll`-Kommentar neu
3. «Kein Abo»: Icon, `iconOnlyMobile`, `[attr.aria-busy]`
4. Styleguide-Beispiele
5. Component-Tests, E2E-Test
6. `ng test`, `ng build`, Lint/Prettier, E2E lokal

## Test-Strategie

- **Vitest (jsdom, kein Layout):** Unterzeile (`CHF 335.00 · jährlich` bzw. `monatlich`),
  `aria-label` mit Position, je ein `svg[aria-hidden=true]` pro Button, jeder Button einmal pro
  Zeile, `aria-busy` während des Dismiss, Button-Spec für Icon-Slot/Label-Span, Styleguide-Beispiele.
- **Playwright (Layout nötig):** bei 390px `scrollWidth === clientWidth` am `.table-scroll`,
  Betrag/Intervall ausgeblendet, Buttons ≥ 44×44 und per Rolle mit Positionsnamen auffindbar.
  Bestehende E2E laufen auf 1280px weiter.

## Acceptance Criteria (aus dem Issue)

- «Bearbeiten», «Löschen» und «Kein Abo» tragen in beiden Varianten ein Icon (Inline-SVG,
  `aria-hidden="true"`); ab 900px Textlabel sichtbar daneben, darunter nur das Icon
- «Kein Abo» behält unter 900px den Namen `Kein Abo: {payeeKey}`; Wartezustand ohne sichtbares
  Label wahrnehmbar (`disabled` + `aria-busy`); E2E-Selektor unverändert
- Wechsel Icon+Text ↔ Icon über CSS an `$bp-desktop`, jeder Button genau einmal im Template
- Unter 900px ≥ 44×44px und Name mit Position; Aktionen selbst unverändert
- Unter 900px drei Spalten, kein horizontales Scrollen bei 390px (`scrollWidth === clientWidth`)
- Unterzeile `{Betrag} · {Intervall}` bzw. nur `{Intervall}` bei monatlich
- Ab 900px bis auf die Icons unverändert
- Semantische `<table>` in beiden Varianten, bestehende Selektoren funktionieren
- Component-Test für die schmale Variante, E2E/Component-Test für kein Overflow bei 390px
- Kommentar zu `.table-scroll` aktualisiert: Scrollen nur noch Sicherheitsnetz
