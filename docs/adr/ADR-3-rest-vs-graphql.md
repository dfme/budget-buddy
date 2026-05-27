# ADR-3: REST API statt GraphQL

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy benötigt eine API zwischen Angular Frontend und Spring Boot Backend mit folgenden Anforderungen:

- CRUD-Operationen (User, Transaktionen, Fixkosten, Sparziele)
- Authentifizierung + Autorisierung
- File Upload (PDF-Import)
- Automatische Dokumentation
- Performance bei ~100-1000 gleichzeitigen Benutzern

Alternative API-Styles: GraphQL, gRPC

## Decision

Wir nutzen **REST API via Spring Web MVC + OpenAPI 3.0**:

- **Style:** RESTful Convention (HTTP Verbs auf Ressourcen)
- **Versionierung:** URL path-based (`/api/v1/...`)
- **Authentication:** JWT Bearer Token im Authorization Header
- **Dokumentation:** Springdoc OpenAPI 3.0 mit Auto-generated Swagger UI
- **Format:** JSON
- **Error Handling:** Standard HTTP Status Codes (400, 401, 404, 500) + JSON Payload

## Consequences

### Positive

- **Einfachheit:** HTTP + JSON sind Standard; keine zusätzliche Lernkurve
- **File Upload:** PDF-Import nativ möglich (multipart/form-data)
- **HTTP Caching:** Browser + CDN cachen GET-Requests automatisch
- **OpenAPI/Swagger:** Automatische API-Dokumentation
- **Standardisierung:** Universelle HTTP Verbs, Status Codes, Headers
- **Monitoring:** Standard Tools (curl, Postman, Browser DevTools)
- **Mobile Future:** Selbe REST-API für zukünftige native Apps

### Negative

- **Over-fetching:** GET `/transactions` gibt alle Felder (nicht nur nötige)
- **Multiple Requests:** Komplexe Views brauchen mehrere API-Calls
- **Versionierung:** Manuelle API-Versioning nötig (`/api/v1/`, `/api/v2/`)
- **Schema Definition:** Weniger Struktur als GraphQL Schema

## Alternatives

### GraphQL

**Rejected.** Flexible Query Language, aber:
- Overkill für BudgetBuddy's einfache CRUD-Anforderungen
- File Upload nicht nativ (würde REST-Fallback brauchen)
- HTTP-Caching schwächer bei POST-basierten Queries
- Team müsste Apollo Server + GraphQL-Schema-Sprache lernen

**Future Option:** Falls Datenabfragen später komplexer werden, könnte GraphQL sinnvoll sein

### gRPC

**Rejected.** Hochperformant, aber:
- Nicht Browser-nativ (braucht grpc-web)
- Binary Protocol → schwerer zu debuggen
- Zu performant für diese Anwendung (Latenz ist nicht Bottleneck)

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung
- **ADR-1:** Spring Boot Backend (nativ REST-freundlich)
- **ADR-2:** Angular Frontend (REST-freundlich)
