# BE-AUTH-07 — Interceptor-Beschreibung nur in ADR-2 führen

- **Issue:** #117 — `[BE-AUTH-07] Interceptor-Beschreibung nur in ADR-2 führen — ADR-0, ADR-7 und CLAUDE.md verweisen`
- **Task-ID:** `BE-AUTH-07`
- **Branch:** `feature/BE-AUTH-07-interceptor-single-source`
- **Typ:** Task (Label `documentation`, nicht `bug`) — keine der betroffenen Aussagen ist falsch;
  es geht um Wartbarkeit. Nach der Branching-Konvention («Feature Branch für Tasks») daher
  `feature/`, nicht `fix/`.
- **Depends on:** #116 (BE-AUTH-06) — gemergt als `e71569b`, Branch startet auf diesem Stand.

## Ausgangslage

Die Beschreibung der Frontend-Interceptor wird an vier Stellen parallel gepflegt. Zwei
Probleme laut Issue:

1. **Implikatur «einziger Interceptor»** — ADR-0 Z. 22 und CLAUDE.md Z. 137 nennen nur den
   `credentialsInterceptor`; tatsächlich registriert `frontend/src/app/app.config.ts:26` zwei.
2. **Drift-Risiko durch Duplikation** — vier parallele Beschreibungen sind die Fehlerklasse,
   die bereits drei Korrekturrunden gebraucht hat (`8fb4dab` → #103/PR #110 → #115/PR #116).
   ADR-7 Z. 40 dupliziert zusätzlich die Konstante `AUTH_BOOTSTRAP_PATHS` aus
   `auth-error.interceptor.ts:15` wörtlich.

## Entscheide

1. **AC 4 ist strenger als AC 1–3 — drei zusätzliche Stellen kommen zwingend in den Scope.**
   AC 1–3 nennen ADR-0:22, ADR-7:40 und CLAUDE.md:137. AC 4 verlangt dagegen, dass *jede*
   Fundstelle des Greps entweder ADR-2:29, ein Verweis darauf oder die abgelehnte Alternative
   ADR-2:93 ist. Der Grep findet acht Stellen; **ADR-7:28**, **ADR-7:71** und **CLAUDE.md:161**
   werden von AC 1–3 nicht erfasst, von AC 4 aber sehr wohl. Sie sind sachlich korrekt
   (deshalb hat #115 sie ausgeklammert), fallen hier aber in den Scope — sonst ist AC 4
   nicht erfüllbar.

2. **ADR-2:29 muss ausgebaut werden, bevor die anderen Stellen darauf verweisen.**
   Die Zeile nennt heute nur den `credentialsInterceptor`. Der `authErrorInterceptor` und das
   401-Verhalten stehen aktuell ausschliesslich in ADR-7:40 und gingen sonst ersatzlos
   verloren. Reihenfolge daher: erst Quelle ausbauen, dann Verweise setzen.

3. **Keine Endpoint-Aufzählung mehr in der Doku (AC 3).**
   Statt `/auth/login`, `/auth/register`, `/users/me` wörtlich zu wiederholen, verweist ADR-2
   auf `auth-error.interceptor.ts` als Quelle. Ändert sich `AUTH_BOOTSTRAP_PATHS`, driftet
   die ADR nicht mehr still.

4. **Delta ausserhalb des AC-Greps: bewusst liegen gelassen** (Team-Entscheid).
   Der AC-Grep deckt nur `docs/adr/` + `CLAUDE.md` ab. Repo-weit gibt es zusätzlich:
   - `docs/presentations/03_02_arch-pitch.html:524` — «Kein HttpInterceptor nötig.»
     (unqualifiziert, dieselbe Fehlerklasse wie #115)
   - `docs/modules/Modul3/c2_container_daniel.drawio:31` — «Functional HTTP-Interceptor (JWT)»
     (sachlich falsch, einen JWT-Interceptor gab es nie)
   - 5× Code-Kommentar «durch den `credentialsInterceptor` … (ADR-7)» in `auth.service.ts:12`,
     `login.ts:20`, `register.ts:20`, `pdf-import.service.ts:13`,
     `transaction-summary.service.ts:13`

   Begründung: Präsentation und drawio sind gehaltene Kursabgaben — Zeitdokumente derselben
   Kategorie wie `docs/prompts/`, die in #116 bewusst unangetastet blieben. Die Code-Kommentare
   verweisen mit `(ADR-7)` auf den Cookie-Entscheid, der in ADR-7 verbleibt; kein Drift.
   Wird im PR-Body deklariert.

5. **Nicht angefasst:** `docs/plans/`, `docs/prompts/`, `.claude/skills/` (Zeitdokumente) sowie
   ADR-2:93 (abgelehnte Alternative, von AC 4 explizit erlaubt). Der Auth-Entscheid selbst
   (httpOnly-Cookie, kein Bearer) bleibt inhaltlich unangetastet.

## Betroffene Files

Alle bestehend, keine neuen Code-Files:

- `docs/adr/ADR-2-angular-frontend.md` — Z. 29 (ausbauen, wird Single Source)
- `docs/adr/ADR-0-frontend-backend-separation.md` — Z. 22
- `docs/adr/ADR-7-jwt-authentication.md` — Z. 28, Z. 40, Z. 71
- `CLAUDE.md` — Z. 137, Z. 161

## Implementierungsschritte

1. **ADR-2 Z. 29 ausbauen** (zuerst — Quelle vor Verweisen): beide Interceptor namentlich,
   `app.config.ts` als Registrierungsort, 401-Verhalten mit Verweis auf
   `auth-error.interceptor.ts` statt Endpoint-Aufzählung.
2. ADR-0 Z. 22 → Kernaussage (clientseitig kein Token-Interceptor) + «Die Frontend-Interceptor führt ADR-2.»
3. ADR-7 Z. 28 → Kernaussage + Verweis auf ADR-2.
4. ADR-7 Z. 40 → Kernaussage + serverseitiger `JwtCookieAuthenticationFilter` + Verweis auf ADR-2;
   401-Details und Endpoint-Liste entfallen (AC 3).
5. ADR-7 Z. 71 → Related-Decisions-Eintrag als Verweis formulieren.
6. CLAUDE.md Z. 137 und Z. 161 → Kernaussage + Verweis auf ADR-2.

## Test-Strategie

Keine automatisierten Tests — reine Dokumentationsänderung; die DoD des Issues markiert
Build/Test/Swagger als n/a. Stattdessen drei Prüfungen:

- **AC-4-Grep:** `grep -rni interceptor docs/adr/ CLAUDE.md` — jede Fundstelle einzeln
  klassifizieren als Quelle (ADR-2:29) / Verweis / abgelehnte Alternative (ADR-2:93).
- **Kein Informationsverlust:** Jede Aussage, die vor der Änderung in ADR-7:40 stand, muss in
  ADR-2:29 wiederzufinden sein — Diff-Abgleich alt gegen neu.
- **Code-Gegenprobe:** neue ADR-2:29 mit `file:line` gegen `app.config.ts:26`,
  `credentials.interceptor.ts:11-12`, `auth-error.interceptor.ts:15` und `:26-39` belegen.

## Acceptance Criteria (aus Issue #117)

- [ ] ADR-2 Z. 29 ist die einzige Stelle, die die Frontend-Interceptor namentlich aufzählt
- [ ] ADR-0 Z. 22, ADR-7 Z. 40 und CLAUDE.md «HTTP auth» behalten die Kernaussage (clientseitig kein Token-Interceptor) und verweisen für Details auf ADR-2
- [ ] ADR-7 Z. 40 zählt die 401-Ausnahmeliste nicht mehr auf, sondern verweist auf `auth-error.interceptor.ts`
- [ ] `grep -rni interceptor docs/adr/ CLAUDE.md` — jede Fundstelle ist entweder ADR-2 Z. 29, ein Verweis darauf, oder eine abgelehnte Alternative (ADR-2 Z. 93)
- [ ] Keine inhaltliche Änderung am Auth-Entscheid selbst (httpOnly-Cookie, kein Bearer — bleibt unangetastet)
