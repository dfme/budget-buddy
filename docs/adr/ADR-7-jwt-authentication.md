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

- **JWT Creation:** User login → Backend erstellt signiertes Token (1 Stunde Expiry)
- **Token Storage:** `httpOnly; Secure; SameSite=Strict` Cookie — kein JavaScript-Zugriff möglich
- **JWT Transport:** Browser sendet Cookie automatisch mit; kein `Authorization`-Header, kein localStorage
- **Backend Validation:** Spring Security validiert Signature + Expiry automatisch
- **Password Storage:** bcrypt (12 Rounds = ~100ms Hashing)
- **Logout:** Backend setzt Cookie mit abgelaufenem Datum (`Max-Age=0`) — sofort invalidiert

**Angular:** Requests mit `withCredentials: true` senden; kein manueller `HttpInterceptor` für Token-Handling nötig.

**CSRF-Mitigation:** `SameSite=Strict` verhindert Cross-Site-Requests in modernen Browsern. Zusätzlich Double-Submit-Cookie-Pattern oder Spring Security CSRF-Token für ältere Browser.

## Consequences

### Positive

- **Stateless:** Keine Session-Tabelle in DB nötig (spart DB-Zugriffe)
- **XSS-sicher:** httpOnly Cookie ist für JavaScript nicht lesbar — Token kann nicht via XSS gestohlen werden
- **Sofort-Logout:** Server setzt Cookie auf abgelaufen → kein "Logout Delay" wie bei clientseitigem Löschen
- **Spring Native:** Spring Boot 3.5 hat erste Klasse JWT-Support
- **Kein manueller Interceptor:** Angular sendet Cookie automatisch mit `withCredentials: true`

### Negative

- **CSRF-Risiko:** Cookies werden automatisch mitgesendet → Cross-Site-Request-Forgery möglich
  - Mitigation: `SameSite=Strict` (primär) + Spring Security CSRF-Token (Fallback für ältere Browser)
- **CORS-Konfiguration:** `withCredentials` erfordert explizites `Access-Control-Allow-Origin` (kein Wildcard `*`)
  - Mitigation: Origin-Whitelist in Spring CORS-Config; im Prod-Betrieb mit gebündelter SPA kein CORS-Problem
- **Token Revocation:** Token bleibt bis Expiry technisch gültig — Cookie-Clearing ist nur clientseitig sicher
  - Mitigation: Kurze Expiry (1 Stunde) + Backend setzt `Max-Age=0` beim Logout; für MVP ausreichend

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
- **ADR-2:** Angular Frontend (`withCredentials: true` auf HttpClient, kein manueller Interceptor für Token)
