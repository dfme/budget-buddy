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

Wir nutzen **JWT (JSON Web Token) mit HS256 Signing und bcrypt Password Hashing**:

- **JWT Creation:** User login → Backend erstellt signiertes Token (1 Stunde Expiry)
- **JWT Validation:** Frontend schickt Token in `Authorization: Bearer <token>` Header
- **Backend Validation:** Spring Security validiert Signature + Expiry automatisch
- **Password Storage:** bcrypt (12 Rounds = ~100ms Hashing)
- **Logout:** Client löscht Token aus localStorage (stateless)

## Consequences

### Positive

- **Stateless:** Keine Session-Tabelle in DB nötig (spart DB-Zugriffe)
- **Mobile-Ready:** Native Bearer Token Pattern für iOS/Android Apps
- **Scalability:** No session bottleneck (scales horizontally)
- **Spring Native:** Spring Boot 3.5 hat erste Klasse JWT-Support
- **Simple:** Keine OAuth Provider Integration nötig (Google/GitHub)

### Negative

- **Logout Delay:** Token bleibt gültig bis Expiry (1 Stunde)
  - Mitigation: Short Expiry + Client-seitiges Löschen = sofort logged out
- **Token Revocation:** Kein Weg, Token vor Expiry zu invalidieren
  - Mitigation: Für MVP nicht nötig; später Blacklist implementieren
- **XSS Vulnerability:** localStorage kann XSS-kompromittiert werden
  - Mitigation: HTTPS + httpOnly Cookies (Phase 2)

## Alternatives

### Server-Side Session (Cookies)

**Rejected.** Traditional Approach, aber:
- Session-Table in SQLite für jede Login
- Schreibdruck auf DB steigt mit User-Count
- Später Skalierung zu PostgreSQL nötig
- CORS-Komplexität bei Cross-Origin Requests

### OAuth 2.0 (Google/GitHub Login)

**Rejected.** Nice-to-have, aber nicht für MVP:
- Zusätzliche Komplexität (OAuth Provider Setup)
- Nicht nötig für einfache Email/Password-Authentifizierung
- Kann später als "Login with Google" hinzugefügt werden

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (JWT perfekt für Stateless SPA)
- **ADR-1:** Java + Spring Boot (OAuth2ResourceServer Native Support)
- **ADR-2:** Angular Frontend (localStorage + HttpInterceptor)
