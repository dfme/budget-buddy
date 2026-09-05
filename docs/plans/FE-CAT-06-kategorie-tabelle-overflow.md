# [FE-CAT-06] Overflow-Verhalten der Kategorie-Tabelle auf schmalen Viewports prüfen

- **Issue:** [#186](https://github.com/dfme/budget-buddy/issues/186)
- **Task-ID:** `FE-CAT-06`
- **Branch:** `fix/FE-CAT-06-kategorie-tabelle-overflow`
- **Story:** US-05 — Transaktionen kategorisieren
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-05

## Messung (AC1)

`frontend/src/app/transactions/category-overview.html` rendert dieselbe ungeschützte Struktur
wie die Fixkosten-Tabelle vor #185 (`table { width: 100% }` ohne Scroll-Container). Gemessen mit
Playwright/Chromium headless gegen eine Nachbildung der aktuellen Auszeichnung (exakte Werte aus
`category-overview.html`/`.scss`, `shared/card/card.scss`, `shared/badge/badge.scss` und den
Tokens aus `styles/_tokens.scss`/`styles.scss`), mit fünf realistischen Zeilen (Badge-Label bis
12 Zeichen — "Versicherung"/"Lebensmittel", CHF-Beträge im de-CH-Format, Anzahl, Anteil):

- Card-Innenbreite (Content-Box) bei 360px Viewport: **326px**
- Tabellenbreite (benötigt, `scrollWidth`): **391px**
- **Überlauf: ja** — ca. 65px über den Kartenrand hinaus; das gesamte Dokument überläuft den
  Viewport (`body.scrollWidth` 432px vs. 360px)

Damit greifen die conditional ACs 2 und 3 aus dem Issue (Fix + Test), nicht AC4 (kein Fix nötig).
Das Messergebnis ist zusätzlich als Kommentar in #186 dokumentiert.

## Betroffene Dateien

- `frontend/src/app/transactions/category-overview.html` — Tabelle in einen Scroll-Wrapper
  wrappen
- `frontend/src/app/transactions/category-overview.scss` — `.table-scroll { overflow-x: auto }`
  plus `min-width` auf der Tabelle
- `frontend/src/app/transactions/category-overview.spec.ts` — neuer Test auf die wirksame
  CSS-Deklaration

## Implementierungsschritte

1. Tabelle in `<div class="table-scroll" role="region" aria-label="Kategorie-Tabelle"
   tabindex="0">` wrappen — identisches Muster zu `fixed-cost-list.html` (#185): `role="region"`
   + `aria-label` machen den scrollbaren Bereich für Screenreader auffindbar, `tabindex="0"`
   macht ihn per Tastatur scrollbar.
2. `.table-scroll { overflow-x: auto; }` in `category-overview.scss` ergänzen.
3. `min-width: 28rem` auf der Tabelle ergänzen (Richtwert oberhalb der gemessenen 391px/24.4rem,
   mit Reserve für längere Inhalte als in der Messung) — verhindert unleserliches Zusammenquetschen
   der vier Spalten, analog zu den 32rem der Fixkosten-Tabelle (dort fünf Spalten inkl.
   Aktions-Zelle mit zwei Buttons, hier vier ohne Aktionen).
4. Unit-Test ergänzen, der `.table-scroll` findet, prüft, dass die Tabelle darin steckt, und
   `getComputedStyle(wrapper).overflowX` auf `'auto'` assertiert — exakt das Muster aus
   `fixed-cost-list.spec.ts:115-122`.

## Test-Strategie

- Unit-Test (Vitest/Angular TestBed) in `category-overview.spec.ts`, analog zum bestehenden Test
  in `fixed-cost-list.spec.ts`.
- Gesamte Frontend-Suite laufen lassen, keine Regression.
- Manuell gegen den Dev-Server bei 360px Viewportbreite verifizieren.

## Acceptance Criteria (aus dem Issue)

- [x] Verhalten der Kategorie-Tabelle bei 360px Viewportbreite gemessen und als Kommentar im
      Issue dokumentiert (Tabellenbreite vs. Cardbreite, analog zur Messung in #185)
- [x] **Falls Überlauf:** Tabelle bleibt innerhalb der Card — per horizontalem Scroll oder
      responsivem Spaltenumbruch, ohne Inhalt über den Kartenrand hinaus
- [x] **Falls Überlauf:** Unit-Test assertiert die wirksame CSS-Deklaration
      (`getComputedStyle(...).overflowX`), nicht nur die Existenz eines Wrapper-Elements
- [ ] **Falls kein Überlauf:** Issue mit dem Messergebnis geschlossen, kein Fix — entfällt,
      Überlauf wurde gemessen
