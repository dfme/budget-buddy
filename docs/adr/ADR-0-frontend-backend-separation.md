# ADR-0: Frontend-Backend-Trennung mit SPA + REST API

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy benötigt eine Systemarchitektur zur Verwaltung sensibler Finanzdaten von Schweizer Studierenden. Die Entscheidung muss folgende Anforderungen berücksichtigen:

- Sichere Trennung von Authentifizierung (Backend) und Präsentation (Frontend)
- Potenzielle Unterstützung für zukünftige mobile Apps (iOS/Android)
- Unabhängige Deploymentzyklen für Frontend und Backend
- Performance bei langsamen Netzwerkverbindungen (Zielgruppe: Studierende)

## Decision

Wir implementieren eine **Single Page App (SPA) + REST API Architektur**:

- **Frontend:** Angular 21.x (TypeScript), lädt im Browser
- **Backend:** Java 21 + Spring Boot 3.5.x; exponiert REST API via OpenAPI 3.0
- **Kommunikation:** Stateless JWT Bearer Token in HTTP Headers
- **CORS:** Explizit konfiguriert für Entwicklung (`http://localhost:4200`) und später Produktion

## Consequences

### Positive

- **Separation of Concerns:** Frontend und Backend können unabhängig von verschiedenen Teams entwickelt werden
- **Wartbarkeit:** UI-Änderungen beeinflussen nicht die Geschäftslogik
- **Testbarkeit:** Frontend und Backend können isoliert getestet werden
- **Zukunftssicherheit:** Selbe REST API kann für native mobile Apps (Flutter, React Native) genutzt werden
- **Skalierbarkeit:** Frontend kann auf CDN liegen, Backend unabhängig skaliert werden

### Negative

- **CORS-Komplexität:** Browser-seitige Same-Origin-Policy erfordert explizite CORS-Konfiguration
- **HTTP-Overhead:** Jede Operation erfordert mindestens einen HTTP-Request
- **Initial Load:** JavaScript wird zuerst geladen, dann API-Calls ausgeführt (vs. SSR: HTML direkt sichtbar)
- **API-Sichtbarkeit:** REST-Endpoints sind im Browser sichtbar (mitigiert durch JWT + AuthN/AuthZ)

## Alternatives

### Server-Side Rendering (Next.js / Thymeleaf)

**Rejected.** SSR würde den Initial Pageload schneller machen, aber:
- Jede Benutzerinteraktion = neuer HTTP-Request
- Erschwert zukünftige mobile App-Entwicklung (separate APIs nötig)
- Für MVP-Tempo ist SPA-Setup schneller

### Monolith mit integriertem Frontend (JSP/Thymeleaf)

**Rejected.** Frontend und Backend wären an denselben Release-Zyklus gebunden:
- Gemischte Toolchains (JavaScript + Java in einer Codebase)
- Skalierung erzwingt gemeinsame Skalierung von UI und API
- Nicht zukunftssicher für mobile Apps

## Related Decisions

- **ADR-1:** Java + Spring Boot für Backend
- **ADR-2:** Angular für Frontend
- **ADR-3:** REST vs. GraphQL
- **ADR-7:** JWT für Authentifizierung
