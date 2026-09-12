# [FE-NOTIF-01] Notification-Glocke in der App-Shell

- **Issue:** [#247](https://github.com/dfme/budget-buddy/issues/247)
- **Task-ID:** `FE-NOTIF-01`
- **Branch:** `feature/FE-NOTIF-01-notification-bell`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen (Vorlauf-Task)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-09

## Ausgangslage

Letzter Baustein des Notification-Fundaments (siehe
[docs/plans/us-08-09-12-breakdown.md](us-08-09-12-breakdown.md)): sichtbarer Einstiegspunkt in der
App-Shell für die Endpoints aus `BE-NOTIF-01` (#246, gemergt) — `GET /api/notifications`,
`POST /api/notifications/{id}/read`.

## Entscheide

| Punkt | Entscheid | Begründung |
| --- | --- | --- |
| Sortierung der Dropdown-Liste | Backend-Reihenfolge unverändert übernehmen (ungelesen zuerst, dann neueste zuerst), kein Neusortieren im Frontend | Bestätigt durch den User. #247 sagt „neueste zuerst", #246 spezifiziert und implementiert bewusst „ungelesene zuerst" — der Wortlaut in #247 gilt als unpräzise gegenüber der bewusst getroffenen Backend-Entscheidung. |
| Modellname | `NotificationResponse`, nicht `Notification` | Kollidiert sonst mit der globalen Browser-API `window.Notification`. Folgt damit dem Muster von `SafeToSpendResponse` (1:1-Spiegel des Backend-DTOs), nicht dem von `User` (dort ein eigenständiger Domänenbegriff). |
| Platzierung der Glocke | Zweimal im Markup: mobile Topbar + Desktop-Sidebar-Konto-Block (`nav__account`) | Exakt das bestehende Duplizierungsmuster von Avatar/Initialen/Wortmarke — Topbar und Sidebar sind laut `shell.scss` nie gleichzeitig sichtbar. Nicht in der 4er-Tab-Bar (`navItems`): die ist laut Kommentar in `shell.ts` auf 375px bereits an der Overflow-Grenze. |
| Laden bei Login/Navigation | `NotificationBell` lädt einmal im Konstruktor und abonniert `Router.events` auf `NavigationEnd` (`takeUntilDestroyed()`) | Deckt die AC „Laden beim Login/Navigation — kein Polling" ab; folgt demselben Subscription-Muster wie `CategoryOverview` (`route.queryParamMap.pipe(takeUntilDestroyed())`). Die Glocke wird nur gerendert, wenn `isAuthenticated()` true ist (`@if`), lädt also automatisch neu, sobald das nach einem Login der Fall wird. |
| Dedup gleichzeitiger Requests | `NotificationService.load()` bündelt parallele Aufrufer auf einen Request (`shareReplay`/`finalize`) | Zwei Glocken-Instanzen (Topbar + Sidebar) lösen sonst pro Login/Navigation zwei identische GETs aus. Exakt das Muster aus `AuthService.ensureCurrentUser`. |
| State-Leerung beim Logout | `NotificationService.clear()`, aufgerufen aus `Shell.logout()` | Verhindert, dass die Benachrichtigungen des vorherigen Users kurz aufblitzen, bevor der nächste Login neu lädt (der Service ist `providedIn: 'root'` und überlebt einen Login-Wechsel in derselben Tab-Session). Analog zu `AuthService.resetState()`. |
| Klick auf bereits gelesene Zeile | Löst keinen erneuten `POST .../read` aus | Reine Effizienz — das Backend ist zwar idempotent, ein Call ohne Wirkung ist trotzdem unnötig. |

## Betroffene Dateien

### Neu — `frontend/src/app/notifications/`

- `notification.model.ts` — `NotificationResponse`-Interface
- `notification.service.ts` — State-Signal, `load()`, `markAsRead()`, `clear()`
- `notification.service.spec.ts`
- `notification-bell.ts` / `.html` / `.scss` — Icon, Badge, Dropdown
- `notification-bell.spec.ts`

### Geändert

- `core/layout/shell.ts` — `NotificationService` injizieren, `clear()` in `logout()`
- `core/layout/shell.html` — `<app-notification-bell />` in Topbar und `nav__account`
- `core/layout/shell.spec.ts` — `login()`-Helper flusht zusätzlich `GET /api/notifications`; die
  drei Tests mit eigenem `router.navigate(...)` flushen den dadurch ausgelösten Reload zusätzlich

## Implementierungsschritte

1. `NotificationResponse`-Modell
2. `NotificationService`: Signal-State, `load()` mit Dedup, `markAsRead()`, `clear()`
3. `NotificationBell`-Komponente: Badge, Popover (Klick aussen/Escape schliesst, analog
   Konto-Popover), Liste, Mark-as-read-Klick
4. Einbindung in `Shell` (zwei Stellen) + `clear()` beim Logout
5. Tests je Ebene

## Test-Strategie

- `notification.service.spec.ts`: `load()`/`markAsRead()` gegen `HttpTestingController` (analog
  `safe-to-spend.service.spec.ts`), Dedup gleichzeitiger `load()`-Aufrufe, `clear()`
- `notification-bell.spec.ts`: Badge-Anzahl, Popover öffnen/schliessen (Klick aussen, Escape),
  Klick auf ungelesene vs. bereits gelesene Zeile, Laden bei Mount und bei `NavigationEnd`
- `shell.spec.ts`: angepasste Helper/Tests wie oben; Glocke erscheint an beiden Stellen

## Acceptance Criteria (aus #247)

- [ ] Glocken-Icon in der Navigation mit Badge für Anzahl ungelesener Benachrichtigungen
- [ ] Dropdown/Liste zeigt Benachrichtigungen, neueste zuerst
- [ ] Klick auf eine Benachrichtigung markiert sie als gelesen (`POST /api/notifications/{id}/read`)
- [ ] Laden beim Login/Navigation — kein Polling-Intervall im MVP
