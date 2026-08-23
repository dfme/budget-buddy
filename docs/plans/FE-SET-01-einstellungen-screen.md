# [FE-SET-01] Einstellungen-Screen: Route und Navigation

- **Issue:** [#177](https://github.com/dfme/budget-buddy/issues/177)
- **Task-ID:** `FE-SET-01`
- **Branch:** `feature/FE-SET-01-einstellungen-screen`
- **Story:** US-14 — Passwort, Einkommen und Erscheinungsbild ändern
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-23

## Entscheid: Routen-Pfad

`/einstellungen` — deutschsprachige Nutzer-Route wie `/fixkosten`, kollidiert mit keinem
Backend-Pfad unter `/api/**`.

## Betroffene Files

- Neu: `frontend/src/app/settings/settings.ts`
- Neu: `frontend/src/app/settings/settings.html`
- Neu: `frontend/src/app/settings/settings.scss`
- Neu: `frontend/src/app/settings/settings.spec.ts`
- Geändert: `frontend/src/app/app.routes.ts` — Route hinter `authGuard` + `onboardingGuard`
- Geändert: `frontend/src/app/core/layout/shell.ts` — Nav-Eintrag „Einstellungen", veralteter
  Kommentar zur bewussten Auslassung entfernt
- Geändert: `frontend/src/app/core/layout/shell.spec.ts` — Nav-Item-Erwartungen erweitert

## Implementierungsschritte

1. `Settings`-Standalone-Component (`OnPush`, keine HTTP-Calls — reines Gerüst): Überschrift
   „Einstellungen" plus drei `<app-card title="…">`-Abschnitte („Passwort", „Einkommen",
   „Erscheinungsbild"), inhaltlich leer — derselbe `Card`-Baustein wie in `fixed-cost-list`.
2. Route in `app.routes.ts`: `{ path: 'einstellungen', canActivate: [authGuard, onboardingGuard],
   loadComponent: () => import('./settings/settings').then((m) => m.Settings) }`, neben den
   übrigen geschützten Routen.
3. `shell.ts`: `{ path: '/einstellungen', label: 'Einstellungen', icon: '⚙' }` zu `navItems`
   hinzufügen, den Kommentar zur bewussten Auslassung streichen.
4. `shell.spec.ts`: Route-Stub und die Nav-Item-Assertion um den fünften Eintrag erweitern.

## Test-Strategie

- `settings.spec.ts`:
  - Rendert Überschrift „Einstellungen" und drei Cards mit den Titeln „Passwort", „Einkommen",
    „Erscheinungsbild"
  - Real-Router-Test (`provideRouter(routes)`): anonymer `navigateByUrl('/einstellungen')` →
    `router.url === '/login'`
  - Real-Router-Test: eingeloggter, onboardeter User erreicht `/einstellungen`
  - Wiring-Assertion: `canActivate` für Pfad `'einstellungen'` entspricht
    `[authGuard, onboardingGuard]`
- `shell.spec.ts`: Nav-Eintrag vorhanden, verlinkt auf `/einstellungen`, Label „⚙ Einstellungen"

Bewusst **nicht** angefasst: die bestehende `it.each(['dashboard', 'categories', 'import'])`-Liste
in `onboarding.guard.spec.ts` (lässt bereits `fixkosten` aus — vorbestehende Lücke, nicht Teil
dieses Diffs). Der Wiring-Nachweis für `einstellungen` lebt stattdessen lokal in `settings.spec.ts`.

## Acceptance Criteria (aus Issue)

- [ ] Route für den Einstellungs-Screen in `app.routes.ts`, hinter `authGuard` und
      `onboardingGuard` wie die übrigen geschützten Routen
- [ ] Navigationseintrag „Einstellungen" in der Shell, aktiv markiert wenn die Route offen ist
- [ ] Der Screen rendert die Überschrift und drei leere Abschnitte („Passwort", „Einkommen",
      „Erscheinungsbild") als Cards der Design-Variante A
- [ ] Anonymer Aufruf der Route landet auf `/login`
- [ ] Test deckt ab: Route ist erreichbar, Navigationseintrag vorhanden
