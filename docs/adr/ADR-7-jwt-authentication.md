# ADR-7: JWT (Stateless) Authentication mit HS256

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy benötigt ein Authentifizierungs-System für:

- User Registration (Email + Password)
- Login (Email + Password → Token)
- Protected API Endpoints (nur eingeloggte User)
- Token-basierte Session Management

Alternative Authentifizierungs-Methoden: Server-Side Session, OAuth 2.0, API Keys

## Decision

Wir nutzen **JWT (JSON Web Token) mit HS256 Signing, bcrypt Password Hashing und httpOnly Cookie als Token-Storage**:

- **JWT Creation:** User login → Backend erstellt signiertes Token (24 Stunden Expiry,
  `app.jwt.expiration`)
- **Token Storage:** `httpOnly; Secure; SameSite=Strict` Cookie — kein JavaScript-Zugriff möglich
- **JWT Transport:** Browser sendet Cookie automatisch mit; kein `Authorization`-Header, kein localStorage
- **Backend Validation:** Spring Security validiert Signature + Expiry automatisch
- **Password Storage:** bcrypt (12 Rounds = ~100ms Hashing)
- **Logout:** Backend setzt Cookie mit abgelaufenem Datum (`Max-Age=0`) — sofort invalidiert

**Angular:** Requests mit `withCredentials: true` senden; clientseitig kein Token-Handling nötig — die Frontend-Interceptor führt ADR-2.

**CSRF-Mitigation:** `SameSite=Strict` verhindert Cross-Site-Requests in modernen Browsern. Zusätzlich Double-Submit-Cookie-Pattern oder Spring Security CSRF-Token für ältere Browser.

## Consequences

### Positive

- **Stateless:** Keine Session-Tabelle in DB nötig (spart DB-Zugriffe)
- **XSS-sicher:** httpOnly Cookie ist für JavaScript nicht lesbar — Token kann nicht via XSS gestohlen werden
- **Sofort-Logout:** Server setzt Cookie auf abgelaufen → kein "Logout Delay" wie bei clientseitigem Löschen
- **Spring Native:** Spring Boot 3.5 hat erste Klasse JWT-Support
- **Kein Token-Handling im Client:** Browser sendet das Cookie automatisch; serverseitig liest der `JwtCookieAuthenticationFilter` das Cookie. Die Frontend-Interceptor führt ADR-2.

### Negative

- **CSRF-Risiko:** Cookies werden automatisch mitgesendet → Cross-Site-Request-Forgery möglich
  - Mitigation: `SameSite=Strict` (primär) + Spring Security CSRF-Token (Fallback für ältere Browser)
- **CORS-Konfiguration:** `withCredentials` erfordert explizites `Access-Control-Allow-Origin` (kein Wildcard `*`)
  - Mitigation: Origin-Whitelist in Spring CORS-Config; im Prod-Betrieb mit gebündelter SPA kein CORS-Problem
- **Token Revocation:** Token bleibt bis Expiry technisch gültig — Cookie-Clearing ist nur clientseitig sicher
  - Mitigation: Backend setzt `Max-Age=0` beim Logout; für den Passwort-Änderung-Pfad zusätzlich
    `token_version` am User (BE-AUTH-11, #201, siehe unten) — für alle anderen Fälle (gestohlenes,
    nie explizit invalidiertes Token) bleibt die 24-Stunden-Expiry die Obergrenze des Zeitfensters

### Nachtrag (BE-AUTH-11, #201): Token-Invalidierung bei Passwort-Änderung

Ohne serverseitige Session gab es ursprünglich keinen Weg, ein einzelnes JWT vor Ablauf gezielt
zu invalidieren — auch nicht, wenn ein Nutzer sein Passwort ändert, weil er einen Missbrauch
vermutet. Die Users-Tabelle trägt seit Flyway `V08` eine `token_version`-Spalte
(`BIGINT NOT NULL DEFAULT 0`), die als eigener Claim ins JWT geschrieben wird; eine
Passwort-Änderung erhöht sie um 1. Der `JwtCookieAuthenticationFilter` lädt den User und vergleicht
die `token_version` bei **jedem** authentifizierten Request gegen den Claim — bei Abweichung wird
das Token wie ein ungültiges behandelt.

Das hebt die Kernannahme „kein Session-Lookup, keine DB-Abhängigkeit im Filter" (siehe „Positive:
Stateless" oben) für den Filter **teilweise** auf: ein DB-Read pro authentifiziertem Request kommt
hinzu. Akzeptiert, weil es der einzige der im Ticket diskutierten Wege war, der das eigentliche
Bedrohungsszenario löst — ein Angreifer mit gestohlenem Token bleibt sonst bis zum natürlichen
Ablauf eingeloggt, obwohl der Nutzer mit der Passwort-Änderung genau das beenden wollte.

Bewusst kein automatischer Cookie-Reissue beim Passwortwechsel: die aufrufende Session wird durch
das Hochzählen ebenfalls ausgeloggt und muss sich neu einloggen — das bestätigt dem Nutzer aktiv,
dass die Änderung wirksam war.

## Alternatives

### Server-Side Session (Cookies)

**Rejected.** Traditional Approach, aber:
- Session-Tabelle in SQLite für jeden Login → Schreibdruck steigt mit User-Count
- Spring Session als zusätzliche Abhängigkeit nötig
- Stateful → horizontales Skalieren braucht Sticky Sessions oder shared Session Store

### OAuth 2.0 (Google/GitHub Login)

**Rejected.** Nice-to-have, aber nicht für MVP:
- Zusätzliche Komplexität (OAuth Provider Setup)
- Nicht nötig für einfache Email/Password-Authentifizierung
- Kann später als "Login with Google" hinzugefügt werden

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (JWT perfekt für Stateless SPA)
- **ADR-1:** Java + Spring Boot (OAuth2ResourceServer Native Support)
- **ADR-2:** Angular Frontend (`withCredentials: true` auf HttpClient; führt die Interceptor-Details)
