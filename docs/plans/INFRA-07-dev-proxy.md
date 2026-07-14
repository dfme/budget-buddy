# INFRA-07 — Angular Dev-Proxy für Backend-Calls

**Issue:** #60 · **Task-ID:** INFRA-07 · **Branch:** `feature/INFRA-07-dev-proxy`
**Sprint:** Sprint 2 · **Area:** DevOps · **Story Points:** 1

## Ziel

Im lokalen Dev-Betrieb (`ng serve` auf `localhost:4200`) sollen relative API-Calls der SPA
(`/auth/*`, `/users/me`) an das Spring-Boot-Backend auf `localhost:8080` weitergeleitet werden —
ohne CORS-Konfiguration im Backend und ohne Base-URL-Logik im Frontend. In Produktion läuft die
SPA same-origin im JAR, daher wird dort kein Proxy benötigt.

## Entscheide

- **Proxied Prefixe: nur `/auth` und `/users`.** Das sind die einzigen real existierenden
  Backend-Controller-Prefixe (AuthController `/auth`, UserController `/users/me`). Weitere
  geplante Domänen (transactions, reports, savings, settings) sind zugleich wahrscheinliche
  Frontend-Routen; sie pauschal an `:8080` zu proxyen würde die SPA-Navigation im Dev-Betrieb
  brechen. `/auth` und `/users` kollidieren mit keiner SPA-Route (`/dashboard`, `/login`,
  `/register`). Neue Prefixe werden ergänzt, sobald der jeweilige Controller entsteht.
- **Test: Vitest-Config-Validierung statt Playwright-E2E.** Es existiert keine
  Playwright-Infrastruktur; ein echter End-to-End-Proxytest bräuchte beide laufenden Server.
  Für einen reinen Config-Task unverhältnismässig. DoD erlaubt „Playwright oder JUnit" — hier
  das FE-Äquivalent (Vitest).

## Betroffene / neue Files

| Aktion | Datei |
|--------|-------|
| neu | `frontend/proxy.conf.json` |
| ändern | `frontend/angular.json` (`serve` → `options.proxyConfig`) |
| ändern | `frontend/README.md` (Abschnitt „Lokaler Dev-Betrieb") |
| neu | `frontend/src/proxy.conf.spec.ts` (Config-Validierungstest) |

## Implementierungsschritte

1. `frontend/proxy.conf.json` anlegen: `/auth` und `/users` → `http://localhost:8080`
   (`secure: false`, `changeOrigin: true`).
2. In `angular.json` unter `serve` ein `options`-Objekt mit `"proxyConfig": "proxy.conf.json"`
   ergänzen — greift automatisch bei `ng serve`.
3. `frontend/README.md`: Abschnitt, wie man Backend (`:8080`) + `ng serve` (`:4200` mit Proxy)
   parallel startet.
4. Vitest-Config-Test `frontend/src/proxy.conf.spec.ts`: importiert `proxy.conf.json` +
   `angular.json`, prüft Mapping auf `http://localhost:8080` und die `proxyConfig`-Verdrahtung.

## Test-Strategie

- Vitest-Config-Validierungstest (Happy Path der Verdrahtung).
- `ng build` läuft fehlerfrei durch (vor PR verifiziert).

## Acceptance Criteria (aus Issue #60)

- [ ] `frontend/proxy.conf.json` leitet API-Pfade (`/auth`, `/users`) an `http://localhost:8080` weiter
- [ ] Proxy in `angular.json` unter `serve` (`proxyConfig`) verdrahtet → `ng serve` lädt ihn automatisch
- [ ] Relativer API-Call der SPA erreicht im Dev-Betrieb das Backend auf `:8080` inkl. `Set-Cookie`
- [ ] Hinweis in der Frontend-README, wie der lokale Dev-Betrieb gestartet wird
