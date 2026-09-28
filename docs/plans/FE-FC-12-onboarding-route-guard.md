# [FE-FC-12] /onboarding sollte nach abgeschlossenem Onboarding nicht mehr erreichbar sein

- **Issue:** [#375](https://github.com/dfme/budget-buddy/issues/375)
- **Task-ID:** `FE-FC-12`
- **Branch:** `fix/FE-FC-12-onboarding-route-guard`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-28

## Entscheide

- **Gegen-Guard statt `onboardingGuard` an `/onboarding`.** Der bestehende `onboardingGuard`
  leitet nicht onboardete User *auf* `/onboarding` um — an der Route selbst wäre das eine
  Endlosschleife. Neu ist `onboardingPendingGuard` in `onboarding.guard.ts`, die Umkehrung:
  - eingeloggt, `onboardingCompleted === true` → `UrlTree` auf `/budget`
  - eingeloggt, Onboarding offen → `true`
  - anonym → `true`; die Entscheidung trifft der `authGuard` im selben `canActivate`-Array
    (dasselbe Muster wie beim `onboardingGuard`, weil Angular die Guards nebenläufig ausführt)
- **Ziel `/budget`**, nicht `/dashboard`: dort liegt seit FE-FC-03 (#26) und FE-FC-10 (#372) der
  Bearbeitungsweg für Fixkosten, den der Wizard bisher doppelte.
- Der Profil-Request läuft über `AuthService.ensureCurrentUser()` und wird mit dem parallel
  laufenden `authGuard` geteilt — kein zweites `GET /api/users/me` pro Navigation.

## Scope-Erweiterung (mit dem User abgestimmt)

Die breite Suche nach `/onboarding` fand ausserhalb der ACs `e2e/tests/fixed-cost-wizard.spec.ts`
(E2E-FC-01): beide Tests nutzen `authenticatedPage` (Onboarding per API abgeschlossen) und
springen per `goto('/onboarding')` in den Wizard — genau der Weg, den dieses Issue sperrt. Ohne
Anpassung wäre die CI rot. Entscheid: **mitbeheben** und im PR-Body deklarieren. Ebenso das
Beispiel in `e2e/README.md`, das dasselbe Muster zeigt.

## Betroffene Dateien

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/app/core/guards/onboarding.guard.ts` | `onboardingPendingGuard` neu; veralteter Javadoc-Absatz ersetzt |
| `frontend/src/app/app.routes.ts` | `/onboarding` → `canActivate: [authGuard, onboardingPendingGuard]`, Kommentar |
| `frontend/src/app/core/guards/onboarding.guard.spec.ts` | Unit- und Router-Tests für den neuen Guard, Zuordnungs-Assertion |
| `e2e/tests/fixed-cost-wizard.spec.ts` | `freshUserPage`; vor `/budget` Onboarding per API abschliessen |
| `e2e/README.md` | Beispiel korrigiert |

## Implementierungsschritte

1. `onboardingPendingGuard` in `onboarding.guard.ts` ergänzen, Javadoc beider Guards nachziehen.
2. Route `/onboarding` in `app.routes.ts` mit `[authGuard, onboardingPendingGuard]` schützen.
3. `onboarding.guard.spec.ts` um Unit-, Router- und Zuordnungstests erweitern.
4. `fixed-cost-wizard.spec.ts` auf `freshUserPage` umstellen; die Listen-Gegenprobe unter
   `/budget` schliesst das Onboarding vorher per `POST /api/users/me/onboarding-complete` ab.
5. `e2e/README.md` anpassen.

## Test-Strategie

- **Unit (Vitest):** onboardet → `UrlTree('/budget')`; nicht onboardet → `true`; anonym (401) → `true`.
- **Router (Vitest, echte `app.routes`):** onboardet → landet auf `/budget`; nicht onboardet →
  bleibt auf `/onboarding` (Regression); anonym → `/login`.
- **Zuordnung:** `/onboarding` trägt `[authGuard, onboardingPendingGuard]`.
- **E2E:** `fixed-cost-wizard.spec.ts` läuft mit dem nicht onboardeten Einstieg weiter grün.

## Acceptance Criteria (aus dem Issue)

- [ ] `onboardingGuard` (oder eine gleichwertige Guard-Logik) hängt an `/onboarding` und leitet
      einen eingeloggten User mit `onboardingCompleted === true` auf `/budget` um
- [ ] Ein noch nicht onboardeter User kann `/onboarding` weiterhin normal durchlaufen
      (keine Regression der bestehenden Zwangsumleitung dorthin)
- [ ] Der veraltete Javadoc-Absatz in `onboarding.guard.ts` («Die Route bleibt … per Direkt-Link
      erreichbar …») ist entfernt bzw. korrigiert
- [ ] Test: eingeloggter User mit abgeschlossenem Onboarding wird bei Navigation auf `/onboarding`
      auf `/budget` umgeleitet (`onboarding.guard.spec.ts`)
