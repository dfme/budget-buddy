# [FE-STS-05] Drei-Monats-Übersicht: horizontaler Scrollbalken trotz Platz links/rechts

- **Issue:** [#361](https://github.com/dfme/budget-buddy/issues/361)
- **Task-ID:** `FE-STS-05`
- **Branch:** `fix/FE-STS-05-totals-table-scrollbar`
- **Story:** — (kein us-*-Label, Bug in FE-STS-04)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-23

## Scope-Erweiterung (Team-Entscheidung, nachträglich im selben Branch/PR)

Bei der Ursachenanalyse fiel eine breitere Inkonsistenz auf: Die Dashboard-Elemente (Banner,
Safe-to-Spend-Karte, Monatswechsel, Drei-Monats-Karte, Abo-Teaser) waren auf `max-width: 28rem`
begrenzt, während `/ausgaben` (`fixed-cost-list.scss`), `/einstellungen` (`settings.scss`) und die
Kategorie-Übersicht (`category-overview.scss`) unabhängig davon `max-width: 40rem` verwenden. Das
Ergebnis: die Dashboard-Spalte stand sichtbar schmaler und weiter rechts zentriert als auf den
anderen Seiten — und hatte als Folge zu wenig Innenraum für die Drei-Monats-Tabelle, die deshalb
auf jeder Viewport-Breite horizontal scrollte.

Nutzer hat entschieden: **beide Punkte zusammen auf diesem Branch/PR beheben**, Zielbreite
**40rem** (bereits der Standard an vier anderen Stellen). Der ursprünglich geplante Fix (Zellen-
Padding in `.totals` von `$sp-2` auf `$sp-2 $sp-1` verkleinern) entfällt damit — die 40rem geben
der Tabelle genug Innenraum, ohne dass die Zellenpolsterung angefasst werden muss.

## Befund (per Repro-Messung und Live-Check nachgewiesen)

Isolierte Repro-Seite aus den echten SCSS-Tokens kompiliert (`frontend/src/styles/_tokens.scss`),
Markup/Klassen 1:1 aus `dashboard.html`/`dashboard.scss` übernommen, mit Playwright (Chromium,
headless) über mehrere Viewport-Breiten vermessen (`scrollWidth` vs. `clientWidth` von
`.table-scroll`). Zusätzlich live gegen den lokalen Dev-Stack (Backend + Postgres, echter Testuser
mit 3 Monaten Transaktionsdaten) verifiziert.

**Ursache der Breiten-Inkonsistenz:** `dashboard.scss` hat keine eigene Wrapper-Klasse für die
ganze Seite — stattdessen setzt jedes einzelne Element selbst `max-width`
([dashboard.scss:12-26](../../frontend/src/app/dashboard/dashboard.scss#L12-L26),
[:122-125](../../frontend/src/app/dashboard/dashboard.scss#L122-L125),
[:185-189](../../frontend/src/app/dashboard/dashboard.scss#L185-L189)). Der Wert 28rem stammt aus
[FE-STS-01](https://github.com/dfme/budget-buddy/issues/33) (Sprint 4), als das Dashboard nur das
einzelne Safe-to-Spend-Widget trug, und wurde nie an die 40rem angeglichen, auf die `settings.scss`
([FE-SET-01, #177](https://github.com/dfme/budget-buddy/issues/177)), `fixed-cost-list.scss`
([FE-FC-03, #26](https://github.com/dfme/budget-buddy/issues/26)) und `category-overview.scss`
unabhängig davon gelandet sind.

**Ursache des Scrollbalkens:** Bei 28rem Kartenbreite betrug der Karteninnenraum (Card-Padding
`$sp-5` = 24px je Seite, [card.scss:8](../../frontend/src/app/shared/card/card.scss#L8)) nur
398px. Die Tabelle selbst braucht mit `padding: $sp-2` rundum
([dashboard.scss](../../frontend/src/app/dashboard/dashboard.scss)) eine natürliche Breite von
414px — getrieben vor allem durch die nicht umbrechende Monatsspalte (`white-space: nowrap`, z. B.
„September 2026") und die einwortigen Header „Einnahmen"/„Ausgaben"/„Differenz". Die 16px
Differenz reichten, damit `.table-scroll { overflow-x: auto; }` auf **jeder** Viewport-Breite
einen Scrollbalken zeigte.

Gemessen (Repro, vor dem Fix):

| Viewport | Karteninnenraum (28rem-Karte) | Tabellenbreite (Ist) | Scrollbar? |
| -------- | ----------------------------- | --------------------- | ---------- |
| 1440 / 900 / 600px | 398px | 414px | ja (Bug) |
| 430 / 375px | schrumpft mit | 414px | ja (gewollt) |

Gemessen (Repro, mit 40rem-Karte, sonst unverändert):

| Viewport | Karteninnenraum (40rem-Karte) | Tabellenbreite (Ist) | Scrollbar? |
| -------- | ----------------------------- | --------------------- | ---------- |
| 1440 / 900 / 600px | 590 / 590 / 486px | 590 / 590 / 486px | nein |
| 430 / 375px | schrumpft mit | 414px | ja (gewollt) |

Live-Check gegen den echten Dev-Stack (Testuser mit Transaktionen September/August/Juli 2026,
Beträge z. B. `5'200.00` / `3'845.30` / `+1'354.70`):

- 1280px Viewport: `/dashboard`, `/ausgaben`, `/einstellungen` jeweils exakt 640px (40rem) breit,
  kein Scrollbalken in der Drei-Monats-Tabelle, alle vier Spalten sichtbar.
- 375px Viewport: `.table-scroll` hat weiterhin `scrollWidth (414) > clientWidth (293)` — der
  Scroll-Fallback für schmale Screens bleibt aktiv, wie im Kommentar
  [dashboard.html:164-166](../../frontend/src/app/dashboard/dashboard.html#L164-L166) beschrieben.

## Fix

`frontend/src/app/dashboard/dashboard.scss`: alle vier `max-width: 28rem` auf `max-width: 40rem`
geändert (`.negative-banner`/`.uncertain-banner`/`.closed-banner`/`.safe-to-spend-card`,
`.month-selector`, `.totals-card`, `.recurring-teaser`), plus erklärender Kommentar zur
Vereinheitlichung. Keine Änderung an `.table-scroll`, `.totals`-Padding oder `-min-width` — die
40rem geben genug Innenraum, ohne dass daran etwas angepasst werden muss.

`fixed-cost-wizard.scss` (28rem) bleibt bewusst unangetastet: das ist ein fokussierter
Einzelschritt-Wizard, kein Seiten-Layout wie Dashboard/Ausgaben/Einstellungen, und war nicht Teil
der gemeldeten Beobachtung.

## Betroffene Dateien

- `frontend/src/app/dashboard/dashboard.scss` — vier `max-width`-Werte 28rem → 40rem, ein
  erklärender Kommentar (einzige Code-Änderung)

## Test-Strategie

Kein Unit-/Component-Test möglich oder sinnvoll: Vitest/Angular TestBed prüft Komponentenlogik,
nicht gerenderte Pixel-Breiten, und das Projekt hat kein Visual-Regression-Tooling (siehe
`docs/CONVENTIONS.md`, Abschnitt „Testing: Frameworks"). Verifikation erfolgt über:

1. Repro-Messung (reale SCSS-Tokens, Playwright, mehrere Viewport-Breiten) — siehe oben.
2. Live-Check gegen den lokalen Dev-Stack (Backend + Postgres, echter Testuser mit
   Transaktionsdaten) — siehe oben.
3. Bestehende Suite: `ng test --include='**/dashboard.spec.ts'` — 59/59 grün, unverändert (reine
   CSS-Änderung, keine Komponentenlogik betroffen).

## Acceptance Criteria (aus Issue #361, plus Scope-Erweiterung)

- Auf Viewports, auf denen für die Card mehr Breite verfügbar wäre, erscheint kein horizontaler
  Scrollbalken mehr. — Erfüllt: Live-Check bei 1280px zeigt keinen Overflow.
- Auf wirklich schmalen Viewports (Mobile) darf der Scroll-Fallback weiterhin greifen. — Erfüllt:
  Live-Check bei 375px zeigt weiterhin `scrollWidth > clientWidth`.
- (Scope-Erweiterung) `/dashboard`, `/ausgaben` und `/einstellungen` haben dieselbe Spaltenbreite.
  — Erfüllt: alle drei live bei 640px (40rem) gemessen.
