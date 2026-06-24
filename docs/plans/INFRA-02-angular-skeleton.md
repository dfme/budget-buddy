# [INFRA-02] Angular Skeleton anlegen

**Issue:** #2
**Task-ID:** INFRA-02
**Branch:** `feature/INFRA-02-angular-skeleton`
**Area:** Frontend · Sprint 1 · Wave 0 · Story Points 2

## Beschreibung (aus Issue)

Angular 21 CLI-Projekt mit ng2-charts, HttpClient (`withCredentials: true`), OnPush als
globale Change-Detection-Strategie und Routing-Grundstruktur erstellen.

## Entscheidungen

- **Test runner:** Vitest (Angular-21-Default + CLAUDE.md-Vorgabe) — kein Zusatz-Setup nötig.
- **Change detection:** Zoneless (`provideZonelessChangeDetection()`), Signals-ready.
- **Stylesheet:** SCSS.
- **Kein SSR** — SPA wird ins Spring-Boot-JAR gebündelt (C2-Diagramm).
- **`withCredentials: true`:** Über funktionalen HTTP-Interceptor. Angular `provideHttpClient`
  hat keine globale `withCredentials`-Option — ein Interceptor ist der einzige Weg, das auf
  jeden Request anzuwenden. CLAUDE.md "kein manueller HttpInterceptor nötig" bezieht sich auf
  den nicht benötigten Bearer-Token-Interceptor (Cookies werden automatisch gesendet); ein
  Credentials-Interceptor wird für das AC dennoch benötigt.

## Betroffene / neue Files

Scaffolded via `ng new` nach `frontend/` (App-Name `budgetbuddy`), danach angepasst:

- `frontend/angular.json` — `@schematics/angular:component` Defaults → `changeDetection: OnPush`, `style: scss`
- `frontend/src/app/app.config.ts` — `provideZonelessChangeDetection()`, `provideHttpClient(withInterceptors([credentialsInterceptor]))`, `provideRouter(routes)`
- `frontend/src/app/app.ts` — Root-Komponente, `ChangeDetectionStrategy.OnPush`
- `frontend/src/app/app.routes.ts` — Platzhalter-Routes
- `frontend/src/app/core/interceptors/credentials.interceptor.ts` — funktionaler Interceptor (`withCredentials: true`)
- `frontend/src/app/core/interceptors/credentials.interceptor.spec.ts` — Vitest-Test
- `frontend/src/app/dashboard/dashboard.ts` (+ Template) — Platzhalter-Komponente (OnPush)
- `frontend/src/app/auth/login.ts` (+ Template) — Platzhalter-Komponente (OnPush)
- `package.json` — `chart.js` + `ng2-charts`

## Implementierungsschritte

1. Scaffold: `ng new budgetbuddy --directory=frontend --routing --style=scss --zoneless --ssr=false --skip-git`.
2. Charts installieren: `npm install chart.js ng2-charts`.
3. OnPush + scss als Schematics-Defaults in `angular.json`; Root-`App` auf OnPush.
4. `credentialsInterceptor` erstellen, `provideHttpClient(withInterceptors([...]))` in `app.config.ts`.
5. `provideZonelessChangeDetection()` in `app.config.ts` sicherstellen.
6. Platzhalter-Komponenten (`dashboard`, `auth/login`) + Routes in `app.routes.ts` (Default + Wildcard → dashboard).
7. Vitest-Spec für Interceptor; generierte `app.spec.ts` behalten.
8. `ng build` und `ng test` (Vitest) ausführen, beide grün.

## Test-Strategie

- **Unit (Vitest):** Interceptor-Spec — jeder Request hat `withCredentials === true` (AC); App-Component-Spec als Render-Happy-Path.
- **Build:** `ng build` läuft fehlerfrei (AC #1).
- Kein E2E/Playwright hier — Skeleton-only; E2E mit den Must-Have-Stories.

## Acceptance Criteria

- [ ] `ng build` läuft fehlerfrei durch
- [ ] HttpClient ist mit `withCredentials: true` konfiguriert
- [ ] OnPush ist global als Default-ChangeDetection gesetzt
- [ ] Routing-Modul mit Platzhalter-Routes vorhanden

## Definition of Done

- [ ] Code reviewed (mind. 1 Approval im PR)
- [ ] `ng build` läuft fehlerfrei durch
- [ ] Happy Path durch automatisierten Test abgedeckt (Vitest)
- [ ] Alle Acceptance Criteria erfüllt
