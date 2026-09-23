# [FE-STS-05] Drei-Monats-Übersicht: horizontaler Scrollbalken trotz Platz links/rechts

- **Issue:** [#361](https://github.com/dfme/budget-buddy/issues/361)
- **Task-ID:** `FE-STS-05`
- **Branch:** `fix/FE-STS-05-totals-table-scrollbar`
- **Story:** — (kein us-*-Label, Bug in FE-STS-04)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-23

## Befund (per Repro-Messung nachgewiesen)

Isolierte Repro-Seite aus den echten SCSS-Tokens kompiliert (`frontend/src/styles/_tokens.scss`),
Markup/Klassen 1:1 aus `dashboard.html`/`dashboard.scss` übernommen, mit Playwright (Chromium,
headless) über mehrere Viewport-Breiten vermessen (`scrollWidth` vs. `clientWidth` von
`.table-scroll`).

Bei voller Kartenbreite (`.totals-card { max-width: 28rem; }`, Card-Padding `$sp-5` = 24px je
Seite aus `frontend/src/app/shared/card/card.scss:8`) beträgt der Innenraum der Karte **398px**.
Die Tabelle selbst braucht mit der bisherigen Zellenpolsterung (`padding: $sp-2` rundum,
[dashboard.scss:133](../../frontend/src/app/dashboard/dashboard.scss#L133)) eine natürliche
Breite von **414px** — getrieben vor allem durch die nicht umbrechende Monatsspalte
(`white-space: nowrap`, z. B. „September 2026") und die einwortigen Header „Einnahmen"/
„Ausgaben"/„Differenz", die als unbrechbare Tokens ihre volle Wortbreite je Spalte erzwingen.

Die 16px Differenz reichen, damit `.table-scroll { overflow-x: auto; }`
([dashboard.scss:122-124](../../frontend/src/app/dashboard/dashboard.scss#L122-L124)) auf **jeder**
Viewport-Breite einen Scrollbalken zeigt — auch bei 1440px, wo rund um die (bewusst auf 28rem
gedeckelte) Karte reichlich Platz ist. `.totals-card` teilt diese 28rem-Spaltenbreite absichtlich
mit dem Safe-to-Spend-Widget und den Bannern darüber
([dashboard.scss:109-117](../../frontend/src/app/dashboard/dashboard.scss#L109-L117)) und wächst
deshalb nicht mit, selbst wenn der Viewport das zuliesse.

Gemessen:

| Viewport | Karteninnenraum | Tabellenbreite (Ist) | Scrollbar? |
| -------- | --------------- | --------------------- | ---------- |
| 1440 / 1280 / 1024 / 900 / 600px | 398px | 414px | ja (Bug) |
| 430 / 375px | schrumpft mit | 414px | ja (gewollt) |

Die beiden anderen Tabellen mit demselben `.table-scroll`-Muster (Kategorie-Übersicht,
[category-overview.scss:29-35](../../frontend/src/app/transactions/category-overview.scss#L29-L35),
40rem-Karte vs. 28rem `min-width`; Fixkosten-Liste,
[fixed-cost-list.scss:68-74](../../frontend/src/app/onboarding/fixed-cost-list.scss#L68-L74),
40rem-Karte vs. 32rem `min-width`) haben deutlich mehr Reserve und sind nicht betroffen — der Bug
bleibt auf das Dashboard beschränkt, kein Scope-Delta zu Issue #361.

## Fix

`.totals th, .totals td` Padding von `$sp-2` (8px rundum) auf `$sp-2 $sp-1` (8px vertikal /
4px horizontal) ändern — spart 32px, mehr als die 16px Lücke.

Erneut gemessen mit derselben Repro-Methode: Tabellenbreite sinkt auf exakt 398px → kein
Scrollbalken mehr ab ca. 432px Viewport-Breite. Der bewusste Schmal-Viewport-Fallback (375–430px,
siehe Kommentar [dashboard.html:164-166](../../frontend/src/app/dashboard/dashboard.html#L164-L166))
greift weiterhin unverändert (Tabellenbreite dort 382px, Karte schrumpft darunter).

## Betroffene Dateien

- `frontend/src/app/dashboard/dashboard.scss` — `.totals th, .totals td` Padding anpassen (einzige
  Code-Änderung)

Keine Änderung an `.totals-card`, `.table-scroll` oder `.totals` `min-width` nötig — die
16px-Lücke wird allein durch die Zellenpolsterung geschlossen, ohne die geteilte 28rem-Spaltenbreite
mit Safe-to-Spend-Card/Bannern anzufassen.

## Implementierungsschritte

1. `frontend/src/app/dashboard/dashboard.scss`: Padding-Regel für `.totals th, .totals td` von
   `padding: $sp-2;` auf `padding: $sp-2 $sp-1;` ändern.
2. Verifikation über die Repro-Messung (Playwright gegen eine aus den echten Tokens kompilierte
   Repro-Seite) wiederholen und Ergebnis im PR dokumentieren.

## Test-Strategie

Kein Unit-/Component-Test möglich oder sinnvoll: Vitest/Angular TestBed prüft Komponentenlogik,
nicht gerenderte Pixel-Breiten, und das Projekt hat kein Visual-Regression-Tooling (siehe
`docs/CONVENTIONS.md`, Abschnitt „Testing: Frameworks"). Verifikation erfolgt über die oben
dokumentierte Repro-Messung (reale SCSS-Tokens, Playwright, mehrere Viewport-Breiten) statt über
einen automatisierten Test.

## Acceptance Criteria (aus Issue #361)

- Auf Viewports, auf denen für die Card mehr Breite verfügbar wäre, erscheint kein horizontaler
  Scrollbalken mehr. — Erfüllt: ab ca. 432px Viewport-Breite kein Overflow mehr (Messung oben).
- Auf wirklich schmalen Viewports (Mobile) darf der Scroll-Fallback weiterhin greifen. — Erfüllt:
  bei 375/430px bleibt die Tabelle breiter als der Karteninnenraum, Scroll bleibt aktiv.
