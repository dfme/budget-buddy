# ADR-0: Frontend-Backend-Trennung (SPA + REST API)

**Status:** Accepted  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Gesamte Systemarchitektur

---

## Context

BudgetBuddy ist eine Web-App für Schweizer Studenten und Berufseinsteiger mit sensiblen Finanzdaten. Die Architektur muss folgende Anforderungen erfüllen:

- Sichere Trennung von Authentifizierung (Backend) und Präsentation (Frontend)
- Potenzielle zukünftige mobile App (iOS/Android)
- Unabhängige Deploymentzyklen für Frontend und Backend
- Performance bei langsamen Netzwerkverbindungen (Studierende)

### Optionen

1. **SPA (Single Page App) + REST Backend** — Frontend lädt in Browser, Backend exposiert JSON API
2. **Server-Side Rendering (Next.js, Django Templates)** — HTML wird auf Server generiert
3. **Monolith mit integriertem Frontend** — JSP/Thymeleaf Templates, alles in einer JAR
4. **Mobile-First API-only** — Zuerst REST API bauen, später Web-Frontend

---

## Decision

**SPA + REST API Architektur:**

- **Frontend:** Angular 19.x (TypeScript), lädt im Browser; responsive Web-App
- **Backend:** Java 25 + Spring Boot 3.5.x; exposiert REST API via OpenAPI 3.0
- **Kommunikation:** Stateless JWT (Bearer Token) in HTTP Headers
- **CORS:** Explizit konfiguriert für `http://localhost:4200` (dev), später produktive Domain

---

## Rationale

| Kriterium | SPA + REST | SSR | Monolith |
|-----------|-----------|-----|----------|
| **Separation of Concerns** | ✅ Frontend ↔ Backend trennbar | ⚠️ Gekoppelt | ❌ Monolithisch |
| **Mobile-Ready** | ✅ Selbe API für iOS/Android | ❌ Web-nur | ❌ Web-nur |
| **Caching** | ✅ Browser + Service Worker | ⚠️ Server-seitiges Caching nur | ⚠️ Komplex |
| **Unabhängiges Deployment** | ✅ Frontend ↔ Backend getrennt | ⚠️ meist zusammen | ❌ Immer zusammen |
| **Team-Parallelisierung** | ✅ Frontend-Team (TS) ↔ Backend-Team (Java) | ⚠️ Überlappend | ❌ Störend |
| **Skalierbarkeit** | ✅ Frontend auf CDN, Backend auf Servern | ⚠️ Schwieriger | ⚠️ Monolith-Grenzen |

**Konkrete Vorteile für BudgetBuddy:**
- Studierende können offline Web-App laden (Service Worker Caching)
- Backend kann als OpenAPI/Swagger documentieret werden → API-first Development
- Später einfach native mobile App (Flutter, React Native) auf selbe Backend-API
- Frontend-Bugs = nur JS-Reload nötig (kein Backend-Neustart)

---

## Consequences

### ✅ Positive

- **Modularer:** Frontend und Backend können von verschiedenen Teams unabhängig entwickelt werden
- **Wartbar:** Änderungen am UI beeinflussen nicht Geschäftslogik und umgekehrt
- **Testbar:** Frontend und Backend können isoliert getestet werden
- **Zukunftssicher:** Mobile App kann auf selbe REST API aufbauen

### ⚠️ Negative

- **CORS-Komplexität:** Browser-seitige Same-Origin-Policy erfordert explizite CORS-Header
- **HTTP-Overhead:** Jede Operation = mindestens 1 HTTP-Request (vs. SSR = direkt HTML)
- **Initial Load:** Erst JavaScript laden, dann API aufrufen (vs. SSR = direkt Seite sichtbar)
- **API-Sichtbarkeit:** REST-Endpoints sind im Browser sichtbar (aber: Datenschutz durch JWT + AuthN/AuthZ)

### 🔄 Mitigations

- CORS auf produktive Domain beschränken
- JWT mit `httpOnly` Cookies (falls Browser-Storage-Bedenken)
- Service Worker für offline-first Caching
- OpenAPI + Springdoc für explizite API-Dokumentation

---

## Alternatives Considered

### ❌ Option 1: Server-Side Rendering (Next.js / Thymeleaf)

**Entscheidung:** Abgelehnt

**Begründung:**
- HTML wird auf Server generiert → Initial Pageload schneller, aber jede Interaktion = neuer Request
- Später schwieriger, mobile App zu bauen (müsste zwei APIs haben)
- Spring Boot + Java für SSR weniger elegant als Node.js (Next.js)
- MVP-Tempo: SPA-Setup schneller mit Angular (CLI, dev tools)

### ❌ Option 2: Monolith mit integriertem Frontend

**Entscheidung:** Abgelehnt

**Begründung:**
- Frontend und Backend gebunden an selben Release-Zyklus
- JavaScript + Java in einer Codebase → gemischte Toolchains
- Skalierung: UI und API müssen gemeinsam skaliert werden
- Nicht zukunftssicher für mobile (keine klare API)

### ✅ Option 3: API-only, Frontend später

**Entscheidung:** Hybrid (Backend APIful bauen, Frontend zeitgleich)

**Begründung:**
- Sinnvoll, um API-Design zu validieren
- BudgetBuddy: Gleichzeitiges Frontend-Entwicklung ist nötig für MVP-Tempo
- Beide Teams parallel arbeiten

---

## Related Decisions

- **ADR-1:** Warum Java + Spring Boot für Backend
- **ADR-2:** Warum Angular für Frontend
- **ADR-3:** REST vs. GraphQL
- **ADR-7:** JWT für Authentifizierung

---

## References

- [Architectural Patterns — Frontend/Backend Separation](https://www.nginx.com/resources/glossary/application-architecture/)
- CLAUDE.md — Tech Stack Entscheidungen
