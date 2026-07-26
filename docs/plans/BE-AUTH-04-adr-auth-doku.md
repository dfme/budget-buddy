# BE-AUTH-04 — ADR-Auth-Doku korrigieren (httpOnly-Cookie statt Bearer/Interceptor)

- **Issue:** #103 — `[BE-AUTH-04] ADR-2 dokumentiert Auth falsch (Bearer/Interceptor statt httpOnly-Cookie)`
- **Task-ID:** BE-AUTH-04
- **Branch:** `fix/BE-AUTH-04-adr-auth-doku`
- **Typ:** Reine Doku-Änderung (kein Code)
- **Verbindliche Quelle:** [ADR-7](../adr/ADR-7-jwt-authentication.md)

## Entscheidungen

- **Code-genaue Formulierung** statt AC-wörtlicher Übernahme. Der tatsächliche Code
  (`frontend/src/app/app.config.ts`) registriert **zwei** funktionale Interceptor:
  `credentialsInterceptor` (setzt `withCredentials: true`, weil Angulars `provideHttpClient`
  keine globale Option hat) und `authErrorInterceptor` (globales 401 → Redirect). Es gibt
  **keinen** Bearer-Token / Authorization-Header / Token-Interceptor.
  → Die AC-Formulierung „kein manueller Interceptor" wäre gegen den Code falsch und würde
  einen neuen Doku-vs-Code-Widerspruch erzeugen. Deshalb: „kein Bearer-Header, kein
  Token-Interceptor" + expliziter Verweis auf den schlanken `credentialsInterceptor`.
- **ADR-3 zusätzlich korrigiert** (Z. 24 behauptete ebenfalls „JWT Bearer Token im
  Authorization Header") — abgedeckt durch AC #4 („Keine weiteren ADRs behaupten
  Bearer/Interceptor für die Auth").
- **CLAUDE.md bewusst nicht angefasst** — Scope-Creep über Issue #103 hinaus. Die
  vereinfachte CLAUDE.md-Zeile widerspricht dem AC nicht (beide: kein Bearer); präziseste
  Quelle ist ab jetzt ADR-2.

## Betroffene Files (beide bestehend, nur editiert)

- `docs/adr/ADR-2-angular-frontend.md` — Z. 29 (HTTP-Zeile im Decision-Abschnitt)
- `docs/adr/ADR-3-rest-vs-graphql.md` — Z. 24 (Authentication-Zeile im Decision-Abschnitt)

## Implementierungsschritte

1. ADR-2 Z. 29 ersetzt →
   `- **HTTP:** \`HttpClient\` mit \`withCredentials: true\`; JWT als httpOnly-Cookie (SameSite=Strict). Kein Bearer-Header und kein Token-Interceptor — ein schlanker \`credentialsInterceptor\` setzt nur \`withCredentials\` (Angular hat keine globale Option); ADR-7.`
2. ADR-3 Z. 24 ersetzt →
   `- **Authentication:** JWT als httpOnly-Cookie (SameSite=Strict); kein Bearer-Header (ADR-7)`
3. ADR-2 Z. 93 (`HTTP/Interceptors: Keine eingebaute Lösung…`) unverändert — steht im
   React-Ablehnungsabschnitt, beschreibt Reacts Fehlen, nicht die BudgetBuddy-Lösung.

## Nicht im Scope

- `docs/plans/`, `docs/presentations/`, `docs/prompts/`, `docs/modules/` — historische
  Artefakte/Prompts, keine ADRs. AC #2/#4 betreffen ausdrücklich nur ADRs + CLAUDE.md.

## Test-Strategie

Keine automatisierten Tests — reine Markdown-Doku-Änderung (DoD-Zeilen
`mvn package`/`ng build`/Happy-Path-Test sind im Issue als n/a markiert).
Verifikation: `grep -niE "bearer|interceptor" docs/adr/` bestätigt, dass keine ADR mehr
Bearer/Token-Interceptor als Auth-Lösung führt; Abgleich gegen den tatsächlichen Code.

## Acceptance Criteria (aus Issue #103)

- [ ] HTTP-Zeile im Decision-Abschnitt von ADR-2 korrigiert (httpOnly-Cookie + `withCredentials: true`, kein Bearer, kein Token-Interceptor)
- [ ] Kein weiterer Abschnitt in ADR-2 stellt den Bearer-/Interceptor-Ansatz als BudgetBuddy-Lösung dar
- [ ] Übereinstimmung mit CLAUDE.md (Frontend-Tech-Stack „HTTP auth" + ADR-Tabelle ADR-7) verifiziert
- [ ] Keine weiteren ADRs behaupten Bearer/Interceptor für die Auth (ADR-3 mitkorrigiert)
