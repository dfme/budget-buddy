# [FE-NOTIF-02] Glocke: Dropdown auf Desktop ausserhalb des Viewports, Icon passt nicht zum Design

- **Issue:** [#308](https://github.com/dfme/budget-buddy/issues/308)
- **Task-ID:** `FE-NOTIF-02`
- **Branch:** `fix/FE-NOTIF-02-bell-dropdown-icon`
- **Story:** US-08 — Wiederkehrende Ausgaben (Fundament In-App-Benachrichtigungen)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-16

## Entscheide

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Öffnungsrichtung Desktop | `bottom: 100%` + `left: 0` in einem `@include desktop`-Block | Die Glocke sitzt auf Desktop im Konto-Block am Fuss der Sidebar (`shell.scss:268` `margin-top: auto` in einer `100dvh` hohen Sidebar, `shell.scss:220`). Nach unten und nach links ist dort kein Platz. Mobile bleibt unberührt, weil die Basis-Regeln (`top: 100%`, `right: 0`) stehen bleiben und nur die Desktop-Query sie überschreibt. |
| Breite bleibt `18rem` | ja | Ab ca. `1.25rem` linkem Offset endet die Liste bei ~`19.25rem`; die Sidebar ist `15rem` breit, der Viewport ab `$bp-desktop` = `900px` ≙ `56.25rem`. Die Liste ragt bewusst über die Sidebar in den Content — das ist Absicht bei einem Popover und kein Überlauf. |
| Glocken-Icon | Inline-SVG, `stroke="currentColor"` | `🔔` hat `Emoji_Presentation=Yes`; der Textpräsentations-Selektor U+FE0E (`🔔︎`) wird von vielen Fonts ignoriert und das Zeichen rendert farbig. Damit läuft es nicht in `$c-ink-2`/`$c-ink` mit, ignoriert Hover und Dark-Theme. Ein SVG mit `currentColor` löst genau das. |
| `⚙` und `⏻` in `shell.html` | U+FE0E anhängen, **kein** SVG | Scope-Erweiterung, im Plan-Gate freigegeben. Beide haben `Emoji_Presentation=No`, rendern also bereits als Textglyphe — VS15 pinnt das nur gegen Plattformen fest, die sie trotzdem emojifizieren. Ein SVG wäre hier der falsche Fix: `⚙` steht in der Sidebar direkt neben `◎ ≡ ↑ ▦` (`shell.ts:103-106`), ein einzelnes SVG dazwischen bräche die Icon-Sprache. Dieselbe Abwägung hat `FE-UI-07` schon getroffen (monoline Zeichen statt `⚠`/`ℹ`, weil die als Emoji rendern). |
| `☀︎`/`☾` im Styleguide | unangetastet | `styleguide.html` ist eine Demo-Seite, kein Shell-Chrome. Dort hängt kein Nutzerpfad dran. |

## Betroffene Dateien

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/app/notifications/notification-bell.html` | `🔔︎` → Inline-SVG |
| `frontend/src/app/notifications/notification-bell.scss` | `@include desktop`-Block für `.bell-list`; SVG-Sizing für `.bell__icon` |
| `frontend/src/app/core/layout/shell.html` | `⚙` (Z. 46, 96) und `⏻` (Z. 50, 100) mit U+FE0E |
| `frontend/src/app/notifications/notification-bell.spec.ts` | Test: SVG statt Emoji |

## Implementierungsschritte

1. `@include desktop`-Block in `.bell-list`: `inset: auto auto 100% 0` plus `margin-bottom: $sp-1`.
2. Glocken-Icon als Inline-SVG in `notification-bell.html` — `viewBox="0 0 24 24"`, `fill="none"`,
   `stroke="currentColor"`, `stroke-width="2"`, runde Enden. Machart wie die Wortmarke
   (`shell.html:116-128`), nur schlanker, weil das Icon bei `1.125rem` statt `1.75rem` sitzt.
3. `.bell__icon` in `notification-bell.scss`: feste Masse plus
   `svg { display: block; width: 100%; height: 100% }` — das Muster von `.topbar__mark`
   (`shell.scss:51-62`).
4. `⚙`/`⏻` in `shell.html` um U+FE0E ergänzen.
5. Tests ergänzen.

## Test-Strategie

- **Neu** (Vitest/TestBed): `.bell__icon svg` existiert, trägt `stroke="currentColor"`, und der
  Icon-Container enthält keinen Emoji-Text mehr. Deckt AC 3 und den DoD-Happy-Path ab.
- **Bestand:** die 13 Tests in `notification-bell.spec.ts` bleiben unverändert grün (AC 4).
- **AC 1 und 2** sind CSS-Regeln und werden mit `file:line` belegt, wie das Issue es selbst
  verlangt. jsdom wertet Media Queries für Komponenten-Styles nicht aus; ein Vitest-Test dafür
  prüfte nichts und wird deshalb bewusst nicht geschrieben.
- `npm run build` (`ng build`) und `npm test` im Frontend.

## Acceptance Criteria (aus dem Issue)

- [ ] Auf Desktop (≥ 900px) öffnet das Dropdown nach oben und nach rechts, vollständig innerhalb
      des Viewports — Regel unter `@include desktop` in `notification-bell.scss`, belegt mit
      `file:line`
- [ ] Auf Mobile bleibt das Verhalten unverändert (öffnet nach unten, rechtsbündig unter der
      Topbar)
- [ ] Das Glocken-Icon ist ein Inline-SVG mit `stroke="currentColor"`, in derselben Machart wie
      die Wortmarke in `shell.html` — kein Emoji mehr
- [ ] Bestehende Tests in `notification-bell.spec.ts` bleiben grün
