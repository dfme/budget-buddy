# ADR-3: REST API vs. GraphQL

**Status:** Accepted (REST)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Backend API-Design

---

## Context

BudgetBuddy benötigt eine API zwischen Angular Frontend und Spring Boot Backend. Hauptanforderungen:

- Simple CRUD-Operationen (User, Transaktionen, Fixkosten, Sparziele)
- Authentifizierung + Autorisierung
- File Upload (PDF-Import)
- Dokumentation (API-Konsumenten)
- Performance bei ~100-1000 gleichzeitigen Benutzern

### Optionen

1. **REST (Representational State Transfer)** — klassisch, simple, standardisiert
2. **GraphQL** — query language, flexible Datenabfragen, aber komplexer
3. **gRPC** — binary protocol, sehr performant, aber Web-untauglich (HTTP/2 WebSocket nötig)

---

## Decision

**REST API via Spring Web MVC + OpenAPI 3.0**

- **Style:** RESTful convention (resources, HTTP verbs)
- **Versioning:** URL path-based (`/api/v1/...`)
- **Authentication:** JWT Bearer Token in Authorization header
- **Documentation:** Springdoc OpenAPI 3.0 + Auto-generated Swagger UI
- **Serialization:** JSON (application/json)
- **Error Handling:** Standard HTTP status codes (400, 401, 404, 500) + JSON error payload

### Endpoints (Beispiele)

```
POST   /api/v1/auth/register          — User erstellen
POST   /api/v1/auth/login             — JWT Token abrufen
POST   /api/v1/transactions/import    — PDF hochladen + parsen
GET    /api/v1/transactions?month=5   — Transaktionen für Monat abrufen
PUT    /api/v1/transactions/{id}      — Kategorie ändern
GET    /api/v1/reports/monthly/{month}— KI-Monatsbericht
POST   /api/v1/savings-goals          — Sparziel erstellen
```

---

## Rationale

| Kriterium | REST | GraphQL | gRPC |
|-----------|------|---------|------|
| **Simplicity** | ✅✅ Einfach | ⚠️ Steile Lernkurve | ❌ Sehr komplex |
| **Learning Curve** | ✅ HTTP + verständlich | ⚠️ Query Language, Schema | ❌ Binär, Protobuf |
| **Standardisierung** | ✅✅ HTTP/REST standard | ⚠️ Noch relativ neu | ❌ Nischig (Web-unfähig) |
| **Tooling** | ✅✅ OpenAPI/Swagger | ⚠️ Apollo Studio | ❌ Compiler-basiert |
| **Over-fetching** | ⚠️ Möglich | ✅ Vermieden (nur geforderte Felder) | ✅ Binär, effizient |
| **Under-fetching** | ⚠️ Möglich (N+1) | ✅ Single Query möglich | ✅ Effizient |
| **Caching** | ✅✅ HTTP Cache-Headers | ⚠️ Schwächer (POST-basiert) | ✅ Sehr schnell |
| **File Upload** | ✅ Nativ | ⚠️ Umständlich (base64) | ⚠️ Umständlich |
| **Browser Support** | ✅✅ Nativ | ⚠️ Fetch + JS required | ❌ Nicht Browser-native |
| **CDN-friendly** | ✅✅ Cache-Header | ❌ POST-basiert, nicht cached | ❌ HTTP/2 Connection |
| **Einfaches Testen** | ✅ curl, Postman | ⚠️ Spezielle Tools | ❌ Protobuf-Compiler nötig |

**Konkrete Vorteile für BudgetBuddy:**

1. **Einfachheit:** HTTP + JSON jeder kennt; keine zusätzliche Lernkurve
2. **File Upload:** PDF-Import nativ möglich (multipart/form-data)
3. **Caching:** HTTP Cache-Header für GET → Browser/CDN cachen automatisch
4. **OpenAPI/Swagger:** Automatische Dokumentation + API-Clients generierbar
5. **Monitoring:** Standard HTTP Status Codes → einfaches Monitoring
6. **Mobile:** Später native Mobile-Apps (iOS/Android) können selbe REST-API nutzen
7. **Skalierbarkeit:** REST ist stateless → Load Balancing einfach

**Wann REST nicht ideal:**
- Komplexe, verschachtelte Datenabrufe (GraphQL besser)
- Real-time Datenströme (GraphQL Subscriptions oder WebSockets besser)
- Mobile mit schlechtem Netzwerk (GraphQL Over-fetching sparen)

→ BudgetBuddy hat keine dieser Anforderungen im MVP

---

## Consequences

### ✅ Positive

- **Developer Experience:** Einfach zu verstehen und zu dokumentieren
- **Skalierbarkeit:** Stateless; einfaches Horizontal-Scaling
- **HTTP Caching:** Browser + CDN cachen GET-Requests automatisch
- **Monitoring:** Standard Tools (curl, Postman, Browser DevTools)
- **Standardisierung:** HTTP Verbs, Status Codes, Headers sind universell
- **Testing:** Einfach mit Curl, REST Clients

### ⚠️ Negative

- **Over-fetching:** GET /transactions gibt alle Felder, nicht nur die gebrauchten
- **Multiple Requests:** Komplexe Views brauchen mehrere API-Calls (aber mitigation: pagination + careful endpoint design)
- **Versionierung:** API-Versioning erfordert manuelle Verwaltung (`/api/v1/`, `/api/v2/`)
- **Schema Definition:** Weniger struktur als GraphQL Schema (nur OpenAPI-Dokumentation)

### 🔄 Mitigations

- **Over-fetching:** Sorgfältige Endpoint-Design (z.B. `/transactions` ohne Transaktions-Details)
- **Multiple Requests:** Batch-Endpoints (z.B. `POST /transactions/batch` für mehrere)
- **Versionierung:** Semantische Versioning + Deprecation Headers
- **Schema:** OpenAPI/Springdoc gibt automatische Dokumentation

---

## Alternatives Considered

### ⚠️ Option 1: GraphQL

**Entscheidung:** Abgelehnt (für MVP)

**Begründung:**
- **Komplexität:** Für BudgetBuddy einfache CRUD-Anforderungen overkill
  - 10-15 Entitäten (User, Transaction, SaveGoal, etc.)
  - Keine tiefen nested queries
- **Apollo Server:** Zusätzliche Abhängigkeit + Lernkurve für Team
- **File Upload:** GraphQL hat keine native Dateiverarbeitung (üblicherweise über REST-Fallback)
- **Caching:** GraphQL POST-basiert → HTTP-Caching schwächer
- **Browser DevTools:** Weniger native Unterstützung als HTTP

**Aber:** Wenn später Datenabfragen komplexer werden (z.B. mobiles Client mit langen Latenzen) könnte GraphQL sinnvoll sein

### ❌ Option 2: gRPC

**Entscheidung:** Abgelehnt

**Begründung:**
- Browser-Untauglich: gRPC braucht HTTP/2 mit grpc-web
- Zu performant für diese Anwendung (Latenz ist nicht Bottleneck)
- Binary Protocol: Debugging schwerer (curl, Browser DevTools gehen nicht)
- Overkill für MVP

---

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (REST ist Standard hier)
- **ADR-1:** Spring Boot Backend (Spring Web MVC = REST-native)

---

## Future Migration Path

Falls später GraphQL sinnvoll wird:
1. GraphQL-Schema definieren (basierend auf Entity-Modellen)
2. Apollo Server / graphql-java hinzufügen
3. REST parallel laufen lassen während Migration
4. REST nach 2-3 Releases deprecaten

Beides gleichzeitig ist aber Overkill für MVP.

---

## References

- [REST Architectural Constraints](https://en.wikipedia.org/wiki/Representational_state_transfer#Architectural_constraints)
- [GraphQL Official Docs](https://graphql.org/)
- [REST vs GraphQL — Comparison](https://www.apollographql.com/docs/apollo-server/why-graphql/)
- [Spring Web MVC](https://spring.io/projects/spring-framework)
- [Springdoc OpenAPI](https://springdoc.org/)
- CLAUDE.md — Technology Stack
