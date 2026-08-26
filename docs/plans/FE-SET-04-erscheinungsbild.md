# [FE-SET-04] Erscheinungsbild: Hell, Dunkel, System

- **Issue:** [#180](https://github.com/dfme/budget-buddy/issues/180)
- **Task-ID:** `FE-SET-04`
- **Branch:** `feature/FE-SET-04-erscheinungsbild`
- **Story:** US-14 — Passwort, Einkommen und Erscheinungsbild ändern
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-25

## Entscheide

**`data-theme` bleibt der einzige Schalter.** `frontend/src/styles.scss` kennt Dunkel
ausschliesslich über `:root[data-theme='dark']`; einen `prefers-color-scheme`-Block gibt es
nicht. „System" wird deshalb in JavaScript aufgelöst und ebenfalls als
`data-theme="light|dark"` geschrieben — nicht als zweiter Weg in CSS. Das hält die
Token-Architektur aus FE-UI-02 unverändert und hat einen Nebeneffekt, der AC2 gratis erfüllt:
der `MutationObserver` in `shared/chart/chart-theme.ts` beobachtet genau dieses Attribut und
feuert damit auch beim Wechsel des Betriebssystem-Themes. Die Chart-Komponenten brauchen keine
Änderung.

**Der Storage-Wert ist dreiwertig.** Gespeichert wird `light`, `dark` oder `system` unter dem
Key `bb-theme`; ein fehlender oder unbekannter Wert gilt als `system`. Lesen und Schreiben
laufen in `try/catch` — `localStorage` kann in manchen Kontexten blockiert sein, der Prototyp
`design/variant-a/theme.js` behandelt das bereits so.

**Kein `matchMedia` in der Testumgebung.** Eine Probe im Vitest-Lauf ergab
`window.matchMedia is not a function`. Der Service muss die Abwesenheit tragen, sonst bricht
`app.spec.ts`, sobald `App` ihn injiziert. Specs, die den OS-Zustand steuern wollen, bekommen
einen Stub in `src/testing/` — analog zu `testing/tokens.ts`, das dasselbe für die
Custom Properties tut.

**Der Styleguide-Toggle wird umgehängt.** `styleguide.ts` schreibt `data-theme` heute direkt.
Neben dem Service wären das zwei Schreiber auf einem Attribut: bei Präferenz `system` würde ein
Wechsel im Betriebssystem den Toggle stillschweigend zurückdrehen. Dieser Defekt entsteht erst
durch diesen Task und wird deshalb hier behoben, nicht ticketiert.

**Guard-Test für das Pre-Paint-Script.** AC4 verlangt das Theme vor dem ersten Bildaufbau; das
geht nur mit einem Inline-Script im `<head>` von `index.html`. Damit steht der Storage-Key an
zwei Orten. Ein Spec importiert `index.html` als Text und prüft die Übereinstimmung; dafür
bekommen die Build-Optionen in `angular.json` `"loader": { ".html": "text" }`. Ohne diesen Test
wäre die Regression stumm — der Flash käme zurück, kein Test schlüge an.

## Betroffene Files

### Neu

| Datei | Zweck |
| ----- | ----- |
| `frontend/src/app/core/theme/theme.ts` | Service `Theme`, Typ `ThemePreference`, Konstanten `THEME_STORAGE_KEY` / `THEME_ATTRIBUTE` |
| `frontend/src/app/core/theme/theme.spec.ts` | Umschalten, Persistenz, Default, OS-Wechsel, blockierter Storage |
| `frontend/src/app/core/theme/theme-boot.spec.ts` | Guard: Inline-Script in `index.html` deckt sich mit dem Service |
| `frontend/src/testing/prefers-color-scheme.ts` | steuerbarer `matchMedia`-Stub für Specs |
| `frontend/src/raw-html.d.ts` | `declare module '*.html'` für den Text-Import |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/index.html` | Inline-Script im `<head>`, vor dem Stylesheet |
| `frontend/angular.json` | `"loader": { ".html": "text" }` in den Build-Optionen |
| `frontend/src/app/settings/settings.{ts,html,scss,spec.ts}` | Abschnitt „Erscheinungsbild" |
| `frontend/src/app/app.ts` | `Theme` injizieren, damit der OS-Listener auf jeder Route lebt |
| `frontend/src/app/styleguide/styleguide.{ts,html}` | Toggle über den Service |
| `frontend/src/styles.scss` | Kommentar 22–27: „noch nicht umgesetzt" stimmt nicht mehr |
| `design/README.md` | Zeile 314: „bisher nur über den Dev-Toggle" |
| `docs/requirements/US-14-einstellungen.md` | Zeile 33: dieselbe Aussage |

Die letzten drei Zeilen sind eine bewusste Scope-Erweiterung über die ACs hinaus, im PR-Body
deklariert. Es sind Ein-Satz-Aussagen, die genau durch diesen Task falsch werden.

## Implementierungsschritte

1. **Service.** `preference` als Signal aus `localStorage`; `systemDark` als Signal aus
   `matchMedia('(prefers-color-scheme: dark)')` plus `change`-Listener, über `DestroyRef`
   wieder abgemeldet; `resolved` als `computed()` auf `light`/`dark`; ein `effect()` schreibt
   `data-theme` auf `<html>`. `select(pref)` setzt das Signal und schreibt den Storage. Ohne
   `matchMedia` gilt hell und es wird kein Listener registriert.
2. **Inline-Script** in `index.html`: liest `bb-theme`, setzt bei `dark`/`light` direkt, sonst
   das Ergebnis von `matchMedia`. ES5, ohne Abhängigkeiten, in `try/catch`.
3. **Settings-UI.** `<app-segment>` mit Hell/Dunkel/System im Card „Erscheinungsbild",
   zweiweg an die Präferenz gebunden, dazu ein Hilfstext, dass die Wahl nur in diesem Browser
   gilt (AC5).
4. **Styleguide** auf `theme.select()` umstellen, Button-Zustand aus `theme.resolved()`.
5. **Doku-Stellen** nachziehen, jede Aussage mit `file:line` belegt.

## Test-Strategie

Vitest mit Angular TestBed; kein Backend berührt, also weder JUnit noch Playwright.

| Test | AC |
| ---- | -- |
| Default ohne Storage-Eintrag ist `system`, `data-theme` folgt `matchMedia` | AC3 |
| Stub meldet OS-Wechsel → `data-theme` kippt ohne Neuaufbau des Service | AC2, AC3 |
| `select('dark')` setzt `data-theme="dark"` **und** schreibt `bb-theme` | AC1, AC6 |
| Vorbelegter Storage + neu erzeugter Service → Wahl überlebt den Reload | AC4, AC6 |
| Explizite Wahl schlägt den OS-Wechsel; `select('system')` gibt die Führung zurück | AC1, AC3 |
| Blockierter Storage (Zugriff wirft) → Service lädt, Default `system` | Robustheit |
| Settings rendert drei Optionen, Klick wählt aus und markiert aktiv | AC1 |
| `index.html` trägt das Script im `<head>`, mit demselben Key und denselben Attributwerten | AC4 |
| Chart-Neuaufbau: `shared/chart/chart-theme.spec.ts` deckt das bereits ab — als Nachweis geführt, nicht dupliziert | AC2 |

Dazu `ng build` (verifiziert die Loader-Option) und `mvn package` als DoD-Nachweis.

## Acceptance Criteria (aus dem Issue)

- [ ] Auswahl zwischen „Hell", „Dunkel" und „System"; die App stellt sofort und ohne Reload um
- [ ] Diagramme (Chart.js) bauen ihre Farben beim Wechsel neu auf und behalten keine Farben
      des vorherigen Themes
- [ ] Ohne je getroffene Wahl ist „System" aktiv und folgt einem späteren Wechsel im
      Betriebssystem automatisch
- [ ] Eine explizite Wahl überlebt den Reload und wird **vor dem ersten Bildaufbau** angewendet —
      das falsche Theme darf nicht kurz aufblitzen
- [ ] Die Wahl liegt nur im Browser: in einem anderen Browser oder auf einem anderen Gerät
      gilt wieder „System"
- [ ] Tests decken ab: Umschalten setzt `data-theme`, Persistenz über `localStorage`,
      Default „System"
