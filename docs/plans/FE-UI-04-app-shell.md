# [FE-UI-04] App-Shell: Navigation, Topbar/Sidebar, Konto/Logout (Variante A)

- **Issue:** [#101](https://github.com/dfme/budget-buddy/issues/101)
- **Task-ID:** FE-UI-04
- **Hängt an:** #99 (FE-UI-02, Token-Fundament) — gemerged; nutzt FE-UI-03 nur weich
- **Branch:** `feature/FE-UI-04-app-shell` (abgezweigt von `main` @ `4cca8f4`, enthält
  bereits die `/import`-Route aus FE-PDF-01)
- **Vorlage:** `design/variant-a/index.html` + `design/variant-a/styles.scss`
  (Abschnitt «3. Layout / App-Shell»)

## Entscheide (Rückfragen vor der Umsetzung)

| Punkt        | Entscheid                                                                                                                                | Begründung                                                                                                                                  |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Nav-Ziele    | 3 Items: Übersicht → `/dashboard`, Transaktionen → `/categories`, Import → `/import`. «Einstellungen» folgt mit US-14.                   | Für Einstellungen gibt es keinen Screen. Ein Link dorthin liefe in die Wildcard-Route und würde fälschlich «Übersicht» aktiv markieren.     |
| Konto-Block  | Initialen aus dem E-Mail-Local-Part (`lara.meier@…` → «LM», `lara@…` → «LA»), darunter die volle E-Mail.                                 | Das `User`-Model hat kein `name`-Feld. Ein Backend-Change (Entity + DTO + Flyway + Registrierung) sprengt den Frontend-Scope dieses Issues. |
| Mobile-Konto | Avatar-Button in der Topbar öffnet ein Popover mit E-Mail + «Abmelden». Escape / Klick daneben schliesst, Fokus kehrt zum Button zurück. | Die Tab-Bar gehört den Hauptzielen; im Prototyp ist der Avatar ohne Verhalten. Ein Popover entspricht der Erwartung an einen Avatar-Button. |

## Architektur

Die Shell wird eine eigene Komponente unter `core/layout/`, nicht Logik im Root:

```
app.html   →  <app-shell><router-outlet /></app-shell>
```

Ein **einziger** Router-Outlet. Die Shell blendet Topbar/Nav intern aus, solange
`isAuthenticated()` false ist — Login/Register bleiben vollflächig. Die Alternative, den
Outlet per `@if` zu duplizieren, würde die Route-Komponente beim Login-Wechsel unnötig
zerstören und neu aufbauen.

## Betroffene / neue Files

**Neu**

- `frontend/src/app/core/layout/shell.ts` — Nav-Items, Initialen, Popover-State, Logout (OnPush)
- `frontend/src/app/core/layout/shell.html` — Topbar / Nav / Konto-Block / `<main>` mit `<ng-content>`
- `frontend/src/app/core/layout/shell.scss` — `.app`/`.topbar`/`.nav`/`.nav__account` aus dem Prototyp, auf `tokens` portiert
- `frontend/src/app/core/layout/shell.spec.ts`
- `docs/plans/FE-UI-04-app-shell.md`

**Geändert**

- `frontend/src/app/app.ts` / `app.html` — Header, Nav und `logout()` wandern in die Shell
- `frontend/src/app/app.scss` — provisorische Shell-Styles raus, bleibt nur `:host { display: block }`
- `frontend/src/app/app.spec.ts` — auf das schlanke Root reduziert; Logout-/Nav-Tests ziehen nach `shell.spec.ts` um
- `frontend/src/index.html` — `lang="en"` → `lang="de-CH"`
- `frontend/src/styles.scss` — Kommentar «App-Shell (.topbar/.nav → FE-UI-04)» auf die Komponente umbiegen

## Implementierungsschritte

1. Branch von `main`.
2. `shell.scss` aus dem Prototyp portieren: `.app` (Spalte → ab 900px Reihe, `max-width: 1180px`),
   `.topbar` (sticky oben, ab 900px `display: none`), `.nav` (sticky unten via `order: 1` →
   ab 900px Sidebar mit `order: 0`), `.nav__account` (nur Desktop, `margin-top: auto`).
3. `shell.ts`: `navItems` als typisiertes Array, `initials`/`email` als `computed` aus
   `auth.currentUser`, `accountMenuOpen` als Signal, `logout()` von `App` übernehmen
   (inkl. Fehlerpfad → `resetState()` + Redirect).
4. `shell.html`: Wortmarke als Inline-SVG, Nav-Links mit `routerLinkActive` +
   `ariaCurrentWhenActive="page"`, `<nav aria-label="Hauptnavigation">`, Avatar-Button mit
   `aria-haspopup="menu"` / `aria-expanded` / `aria-controls`.
5. Popover-A11y: Escape schliesst und gibt den Fokus zurück, Klick ausserhalb schliesst
   (Host-Listener mit Target-Prüfung gegen den eigenen `ElementRef`), Logout schliesst mit.
6. `App` auf `<app-shell><router-outlet /></app-shell>` reduzieren.
7. `npm run lint`, `npm test`, `ng build` grün.

## Test-Strategie

Vitest + TestBed (`shell.spec.ts`) — im Repo gibt es noch kein `e2e/`-Verzeichnis; die
bisherigen Frontend-Issues sind ebenfalls über Vitest abgedeckt.

- **Happy Path:** eingeloggt → Topbar, Nav mit den 3 Zielen und Konto-Block sind da
- Ausgeloggt → keine Topbar, keine Nav (Login-Screen vollflächig)
- Nav-Links zeigen auf `/dashboard`, `/categories`, `/import`
- Aktive Route trägt `aria-current="page"` (echte Navigation via `provideRouter`)
- Initialen: `lara.meier@…` → `LM`, `lara@…` → `LA`
- Popover: initial zu (`aria-expanded="false"`), öffnet auf Klick, schliesst auf Escape,
  schliesst auf Klick daneben
- Logout: `POST /auth/logout` → State leer → Redirect `/login`; Fehlerpfad ebenso

`app.spec.ts` behält nur noch «rendert die Shell mit Outlet».

## Acceptance Criteria (Issue #101)

- [ ] Navigation als Mobile-Tab-Bar unten und Desktop-Sidebar links (ein Markup, per
      Breakpoint umgeschaltet), aktives Ziel markiert
- [ ] Topbar (Mobile) mit Wortmarke; auf Desktop durch die Sidebar ersetzt
- [ ] Konto-Block (Avatar/Initialen, Name) + Abmelden-Aktion; auf Mobile über die Topbar
      erreichbar, auf Desktop am Fuss der Sidebar
- [ ] Vollständig tastaturbedienbar, sichtbarer Fokus, korrekte ARIA (`nav`, `aria-current`)
- [ ] OnPush; nutzt die Shared-Komponenten und Tokens
- [ ] Kein horizontaler Scroll bei 375px; Desktop-Verhalten ab 900px
