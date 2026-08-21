# [FE-FC-04] Fixkosten-Tabelle läuft auf schmalen Viewports über die Card hinaus

- **Issue:** [#172](https://github.com/dfme/budget-buddy/issues/172)
- **Task-ID:** `FE-FC-04`
- **Branch:** `fix/FE-FC-04-tabelle-overflow`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-19

## Ursache

`table { width: 100%; }` in [fixed-cost-list.scss](../../frontend/src/app/onboarding/fixed-cost-list.scss)
hat keinen umgebenden Scroll-Container. Die Aktions-Zelle (`td.actions`) legt die beiden Buttons
„Bearbeiten"/„Löschen" per `display: flex` nebeneinander; zusammen mit den übrigen vier Spalten
übersteigt die minimale Inhaltsbreite der Tabelle auf schmalen Viewports die Breite der Card, und
nichts fängt das per Umbruch oder Scroll ab — die Tabelle läuft sichtbar über den Kartenrand hinaus.

## Betroffene Dateien

- `frontend/src/app/onboarding/fixed-cost-list.html` — Tabelle in einen Scroll-Container wrappen
- `frontend/src/app/onboarding/fixed-cost-list.scss` — `.table-scroll { overflow-x: auto }`,
  `min-width` auf der Tabelle, `white-space: nowrap` auf der Aktions-Zelle
- `frontend/src/app/onboarding/fixed-cost-list.spec.ts` — Test für den Scroll-Wrapper

## Implementierungsschritte

1. Im Template die `<table>` (im `@else`-Zweig neben dem Empty-State) in
   `<div class="table-scroll">…</div>` wrappen.
2. In SCSS `.table-scroll { overflow-x: auto; }` ergänzen, der Tabelle ein `min-width: 32rem;`
   geben (verhindert unleserliches Zusammenquetschen der Spalten — stattdessen scrollt die Card),
   und `td.actions { white-space: nowrap; }`, damit die Button-Beschriftungen nicht mitten im Wort
   umbrechen.
3. Manuelle Verifikation im Dev-Server bei schmalem Viewport (~360px): Tabelle scrollt horizontal
   innerhalb der Card statt über den Rand hinauszulaufen.

## Test-Strategie

- Unit (Vitest/TestBed): Assertion, dass `.table-scroll table` existiert — bestätigt die
  strukturelle Fixierung und verhindert eine Regression, die den Wrapper wieder entfernt.
- Manuell: Verifikation bei schmalem Viewport im Dev-Server. Reines CSS-Layout-Fix ohne
  Backend-/E2E-Auswirkung, JSDOM hat keine reale Layout-Engine für einen automatisierten
  Overflow-Test.

## Acceptance Criteria (aus Issue #172)

- Tabelle bleibt innerhalb der Card — entweder durch responsives Umbrechen der Spalten oder durch
  horizontales Scrollen innerhalb der Card, ohne dass Inhalt über den Kartenrand hinausläuft.
