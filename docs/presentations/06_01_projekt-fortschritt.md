# BudgetBuddy — Projektfortschritt

**Stand:** 05.08.2026 · Sprint 1–3 abgeschlossen
**Quelle:** GitHub Issues, Milestones und Pull Requests des Repos [dfme/budget-buddy](https://github.com/dfme/budget-buddy)

---

## Kennzahlen auf einen Blick

**Zeitraum:** 24.06.2026 – 05.08.2026 · 3 Sprints · 51 gemergte PRs · 51 geschlossene Issues

| | Sprint 1 | Sprint 2 | Sprint 3 |
| --- | --- | --- | --- |
| **Zeitraum** | bis 08.07. | 09.07.–18.07. | 19.07.–05.08. |
| **Issues erledigt** | 11 | 13 | 25 |
| **Story Points** | **24** | **24** (+3 ohne SP) | **31** (+15 ohne SP) |
| **PRs gemergt** | 11 | 17 | 23 |

Die Velocity ist über die drei Sprints deutlich gestiegen — Sprint 3 hat bei gleicher Länge
rund doppelt so viele Issues abgeschlossen wie Sprint 1.

---

## Sprint 1 — Fundament

**11 Issues · 24 Story Points · 11 PRs**

Der Sprint hat die komplette Grundlage gelegt, auf der alles Weitere aufsetzt:

- **Infrastruktur:** Spring-Boot- und Angular-Skeleton, GitHub-Actions-CI,
  Render-Deployment inklusive Bundling der SPA ins Spring-Boot-JAR
- **Datenbank:** Flyway-Migrationen V1–V3 (`users`, `transactions`, `fixed_costs`)
- **Authentifizierung (Backend):** JWT-HS256-Filter, Register-/Login-/Logout-Endpoints,
  `GET /users/me` und `PUT /users/me/income`

Ergebnis: eine deployte, lauffähige Anwendung mit funktionierender Auth-API.

---

## Sprint 2 — Auth-Frontend und Kategorisierungs-Kern

**13 Issues · 24 Story Points · 17 PRs**

Zwei Stränge parallel:

- **Auth-Frontend (US-01) end-to-end:** AuthService mit Signal-State, Login- und
  Register-Formulare als Reactive Forms, `authGuard` mit 401-Redirect, Logout-Button
  mit Nav-Anbindung, Angular-Dev-Proxy
- **Kategorisierung (US-05, Backend-Kern):** `CategorizationPort`-Interface,
  `LookupTableService`, `ClaudeCategorizationService` inklusive Circuit Breaker,
  `category_lookup`-Tabelle mit Seed-Daten
- **DevOps:** CD-Pipeline mit Render Deploy Hook, dazu drei Bugfixes am Smoke-Test
  und an der Deployment-Meldung

---

## Sprint 3 — PDF-Import und Design-System

**25 Issues · 31+ Story Points · 23 PRs** — der mit Abstand grösste Sprint

### Strang A: PDF-Import und Kategorisierung (Must-Have US-04 / US-05)

- PDFBox-Parser für Schweizer Bank-PDFs (8 SP — das grösste Einzel-Issue des Projekts)
- `PdfImportService` und `POST /import/pdf`
- `HybridCategorizationService` (Lookup zuerst, Claude nur für Unbekanntes, ADR-6)
- `PUT /transactions/{id}/category` für manuelle Korrekturen
- `GET /transactions/summary`
- Frontend: PDF-Upload-Komponente, Ergebnis-Anzeige nach Import, Kategorie-Übersicht

Damit sind US-04 (PDF-Upload) und US-05 (Kategorisierung) durchgängig umgesetzt.

### Strang B: UI-Design-System

Vom Entscheid bis zur fertigen Basis in einem Sprint:

- Designentscheidung «Klarheit» / Variante A (ADR-11), abgestimmt anhand dreier
  klickbarer Mobile-First-Varianten
- Design-Token-Fundament (theme-fähig)
- Shared-Basiskomponenten
- App-Shell mit Navigation, Topbar/Sidebar und Konto/Logout
- Chart-Integration mit ng2-charts (Donut- und Bar-Basiskomponenten)
- Migration der bestehenden Screens auf das neue Fundament

### Weiteres

Playwright-E2E-Setup, `ANTHROPIC_API_KEY` in Render, Korrektur der Auth-Dokumentation
in ADR-0/2/7 sowie der `/plan-sprint`-Skill zur Automatisierung der Sprint-Planung.

---

## Stand und Ausblick

### Abgeschlossen

| Bereich | Story |
| --- | --- |
| Infrastruktur, CI/CD, Deployment | — |
| Authentifizierung | US-01 |
| PDF-Upload | US-04 (Must) |
| Transaktionen kategorisieren | US-05 (Must) |
| Design-System «Klarheit» | ADR-11 |

### Offen

**Backlog: ~42 Story Points** (plus 11 noch ungeschätzte Issues)

- **US-03 Fixkosten-Wizard** (Must) — Backend `BE-FC-01…03`, Frontend `FE-FC-01…03`
- **US-06 Safe-to-Spend** (Must) — Backend `BE-STS-01…03`, Frontend `FE-STS-01…03`
- **E2E-Tests** für alle Must-Have-Stories (`E2E-AUTH/PDF/FC/CAT/STS`)
- Diverse Infrastruktur-Themen (Prettier, Node-24-Runtime, SPA-Deep-Links,
  Migration SQLite → Postgres)

### Kritischer Pfad

Die beiden verbleibenden Must-Have-Stories **US-03 (Fixkosten-Wizard)** und
**US-06 (Safe-to-Spend)** sind noch nicht begonnen. Beide zusammen umfassen rund
22 Story Points und bilden das Kernversprechen der App — der wöchentliche
Safe-to-Spend-Betrag lässt sich ohne erfasste Fixkosten nicht berechnen.
Sie gehören damit in den nächsten Sprint.

---

## Hinweis zur Datenqualität

Die Story Points stammen aus den Metadaten-Tabellen in den Issue-Bodies. Seit
[INFRA-13](https://github.com/dfme/budget-buddy/issues/84) (18.07.2026) werden sie
ausschliesslich im [Sprint Board](https://github.com/users/dfme/projects/4) geführt
und nicht mehr im Issue dupliziert. Für 18 Issues — überwiegend in Sprint 3 — sind
sie hier deshalb nicht enthalten. Die tatsächliche Sprint-3-Velocity liegt spürbar
über den ausgewiesenen 31 Punkten.

Die Sprint-Zuordnung folgt für Sprint 1–3 den Milestones; Issues ohne Milestone
wurden anhand ihres Schliessdatums dem passenden Sprint-Zeitfenster zugeordnet.
Führend für die Sprint-Zugehörigkeit bleibt gemäss CLAUDE.md das Iteration-Feld
im Sprint Board.
