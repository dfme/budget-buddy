# BE-AUTH-06 — «Kein manueller Interceptor»-Vereinfachung präzisieren

- **Issue:** #115 — `[BE-AUTH-06] «Kein manueller Interceptor»-Vereinfachung präzisieren (ADR-0, ADR-7, CLAUDE.md)`
- **Task-ID:** `BE-AUTH-06` (im Issue-Kommentar von `BE-AUTH-05` korrigiert — die ID war bereits durch #114 belegt)
- **Branch:** `fix/BE-AUTH-06-interceptor-doku-praezisieren`
- **Typ:** Bugfix, rein dokumentarisch — kein Produktivcode betroffen
- **Depends on:** #110 (BE-AUTH-04 — etabliert die Referenzformulierung in ADR-2 Z. 29)

## Ausgangslage (Code-Abgleich)

Die Doku behauptet an drei Stellen unqualifiziert «kein manueller `HttpInterceptor` nötig».
Gegen den tatsächlichen Code ist das falsch:

- `frontend/src/app/app.config.ts:26` registriert **zwei** funktionale Interceptor:
  `credentialsInterceptor` und `authErrorInterceptor`.
- `frontend/src/app/core/interceptors/credentials.interceptor.ts:11-12` setzt ausschliesslich
  `withCredentials: true` — Angulars `provideHttpClient` hat keine globale Option dafür.
- `frontend/src/app/core/interceptors/auth-error.interceptor.ts` behandelt 401 zentral
  (Redirect auf `/login`) — auth-relevant, gehört also inhaltlich in ADR-7.

Korrekt ist: Es gibt keinen **Token**-Interceptor (kein Bearer-Header, kein
`Authorization`-Header) — sehr wohl aber Interceptor.

## Entscheide

1. **Scope explizit machen statt nur «kein Token-Interceptor».**
   Die Referenzformulierung aus ADR-2 Z. 29 funktioniert dort, weil ADR-2 eine reine
   Frontend-ADR ist. ADR-0 und ADR-7 beschreiben Frontend **und** Backend. Ein unscoped
   «kein Token-Interceptor» steht in ADR-7 direkt neben «Spring Native: JWT-Support» —
   während `backend/.../auth/JwtCookieAuthenticationFilter.java` das JWT serverseitig sehr
   wohl aus dem Request zieht. Deshalb: «clientseitig» / «im Client» als Qualifikator.
   Das AC verlangt «Formulierung analog ADR-2 Z. 29», nicht wortgleich.

2. **`authErrorInterceptor` nicht wegdefinieren.**
   Eine Formulierung «*nur* ein schlanker `credentialsInterceptor`» impliziert, das sei der
   einzige Interceptor. Es sind zwei. ADR-7 nennt daher beide plus den serverseitigen Filter.

3. **CLAUDE.md Z. 161 wird mitkorrigiert** (Fund ausserhalb des Issue-Texts).
   Die Zeile «httpOnly Cookie + `withCredentials: true`; kein HttpInterceptor» in der
   Auth-Decision-Vergleichstabelle matcht den AC-Grep nicht (kein «manueller»), ist aber
   dieselbe Fehlerklasse — und stünde sonst 24 Zeilen unter der korrigierten Z. 137.

4. **Nicht angefasst:**
   - ADR-7 Z. 28 und Z. 71 — bereits korrekt mit «für Token-Handling» / «für Token» qualifiziert (so auch im Issue festgehalten).
   - ADR-2 Z. 93 — beschreibt eine abgelehnte Alternative, nicht BudgetBuddy.
   - `docs/prompts/` und `docs/plans/` — historische Artefakte, die den damaligen Stand dokumentieren; eine nachträgliche Korrektur würde die Historie verfälschen.
   - Der Auth-Entscheid selbst (httpOnly-Cookie, kein Bearer) bleibt inhaltlich unangetastet.

## Betroffene Files

Nur bestehende Dateien, keine neuen Code-Files:

- `docs/adr/ADR-0-frontend-backend-separation.md` — Z. 22
- `docs/adr/ADR-7-jwt-authentication.md` — Z. 40
- `CLAUDE.md` — Z. 137 («HTTP auth» im Frontend-Tech-Stack) und Z. 161 (Auth-Decision-Tabelle)

## Implementierungsschritte

1. ADR-0 Z. 22 präzisieren:
   `- **Angular:** Requests mit \`withCredentials: true\`; Browser sendet Cookie automatisch — clientseitig kein Token-Interceptor nötig. Ein schlanker \`credentialsInterceptor\` setzt lediglich \`withCredentials\` (Angular hat dafür keine globale Option).`
2. ADR-7 Z. 40 präzisieren:
   `- **Kein Token-Handling im Client:** Browser sendet das Cookie automatisch; der \`credentialsInterceptor\` setzt nur \`withCredentials: true\`, ein \`authErrorInterceptor\` behandelt 401 zentral. Serverseitig liest der \`JwtCookieAuthenticationFilter\` das Cookie.`
3. CLAUDE.md Z. 137 präzisieren:
   `| HTTP auth | \`withCredentials: true\` auf HttpClient | (bundled) | Cookie automatisch mitgesendet; kein Token-Interceptor — \`credentialsInterceptor\` setzt nur \`withCredentials\` (ADR-7) |`
4. CLAUDE.md Z. 161 präzisieren:
   `| Angular SPA integration | httpOnly Cookie + \`withCredentials: true\`; kein Token-Interceptor im Client | … |`

## Test-Strategie

Keine automatisierten Tests — reine Dokumentationsänderung ohne Code-Bezug; die DoD des
Issues markiert Build/Test/Swagger explizit als n/a. Verifikation stattdessen:

- `grep -niE "kein (manueller )?(Http)?Interceptor" docs/adr/ CLAUDE.md` liefert keine
  unqualifizierte Aussage mehr (verbleibende Treffer: ADR-7 Z. 28 «für Token-Handling»,
  ADR-7 Z. 71 «für Token»).
- Manueller Abgleich jeder neuen Aussage gegen `app.config.ts`,
  `credentials.interceptor.ts`, `auth-error.interceptor.ts` und
  `JwtCookieAuthenticationFilter.java`.

## Acceptance Criteria (aus Issue #115)

- [ ] ADR-0 Z. 22 präzisiert: kein **Token**-Interceptor; der schlanke `credentialsInterceptor` setzt nur `withCredentials` (Formulierung analog ADR-2 Z. 29)
- [ ] ADR-7 Z. 40 präzisiert (gleiche Formulierung)
- [ ] CLAUDE.md-Zeile «HTTP auth» (Frontend-Tech-Stack) präzisiert (gleiche Aussage, gekürzt)
- [ ] `grep -niE "kein manueller (Http)?Interceptor" docs/adr/ CLAUDE.md` liefert keine unqualifizierte Aussage mehr
- [ ] Keine inhaltliche Änderung am Auth-Entscheid selbst (httpOnly-Cookie, kein Bearer — bleibt unangetastet)
