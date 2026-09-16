# [FE-UI-09] Favicon austauschen

- **Issue:** [#316](https://github.com/dfme/budget-buddy/issues/316)
- **Task-ID:** `FE-UI-09`
- **Branch:** `feature/FE-UI-09-favicon`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-16

## Ausgangslage

Das bestehende `frontend/public/favicon.ico` ist das generische Angular-CLI-Platzhalter-Icon
(eingebunden in `frontend/src/index.html:8`) und soll durch ein BudgetBuddy-eigenes Favicon
ersetzt werden. Im Issue selbst und im Repo lag zunächst keine neue Datei vor; auf Rückfrage
wurden zwei Varianten unter `frontend/public/` abgelegt: `favicon-light.ico` und
`favicon-dark.ico` (je 16×16, PNG-in-ICO).

## Entscheid: Theme-Kopplung statt einzelner Datei

Das AC verlangt nur eine einzelne `favicon.ico`. Da zwei Varianten vorliegen und die App bereits
ein eigenes, expliziertes Light/Dark/System-Theme führt (`frontend/src/app/core/theme/theme.ts`,
FE-SET-04, US-14), wurde entschieden, den Favicon an das **aufgelöste App-Theme** zu koppeln
(nicht an die reine `prefers-color-scheme`-Media-Query) — analog zu `ChartTheme`, das denselben
`data-theme`-Zustand für Chart.js-Farben beobachtet. Eine reine CSS-Media-Query-Lösung würde bei
manueller Theme-Wahl (Nutzer wählt „Dunkel" trotz hellem OS) vom sichtbaren Theme abweichen.

## Betroffene Dateien

- `frontend/public/favicon-light.ico` / `favicon-dark.ico` — neu, bereits abgelegt
- `frontend/public/favicon.ico` — alter Platzhalter, wird entfernt (AC3)
- `frontend/src/index.html` — `<link rel="icon">` auf `favicon-light.ico` als statischen Default;
  das bestehende Pre-Paint-Script (verhindert den Theme-Flash) wird um dieselbe Logik für den
  Favicon-`href` erweitert
- `frontend/src/app/core/theme/theme.ts` — neue Konstanten `FAVICON_LIGHT_HREF`,
  `FAVICON_DARK_HREF`; der bestehende `effect()` setzt zusätzlich den `href` des
  Favicon-`<link>` passend zu `resolved()`
- `frontend/src/app/core/theme/theme.spec.ts` — Tests für den Favicon-Wechsel bei manueller und
  System-Theme-Änderung
- `frontend/src/app/core/theme/theme-boot.spec.ts` — Test, dass das Inline-Script in
  `index.html` dieselbe Favicon-Logik wie `theme.ts` enthält (Drift-Schutz, gleiches Muster wie
  die bestehenden Tests dort)

## Implementierungsschritte

1. `favicon.ico` entfernen, `favicon-light.ico`/`favicon-dark.ico` stagen
2. `index.html`: Link-Tag + Inline-Script anpassen
3. `theme.ts`: Konstanten + Favicon-Swap im `effect()`
4. `theme.spec.ts` und `theme-boot.spec.ts`: Tests ergänzen
5. Manuell mit `ng serve` prüfen (AC2): Tab-Icon wechselt mit Theme-Einstellung

## Test-Strategie

- Unit-Tests (Vitest) für den Effekt in `theme.ts` (Favicon-`href` folgt `resolved()`)
- Unit-Test für den Boot-Script-Abgleich in `theme-boot.spec.ts`
- Manuelle Prüfung im Browser-Tab für AC2

## Acceptance Criteria (aus dem Issue)

- [ ] Neue Favicon-Datei liegt unter `frontend/public/favicon.ico` (bzw. Referenz in
      `frontend/src/index.html` angepasst, falls Format/Pfad abweicht) — hier: zwei Dateien,
      Referenz in `index.html` entsprechend angepasst
- [ ] Favicon ist im Browser-Tab sichtbar (lokal per `ng serve` geprüft)
- [ ] Alte Platzhalter-Datei entfernt, falls durch ein anderes Format ersetzt
