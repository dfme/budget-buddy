# ADR-1: Java 25 + Spring Boot 3.5.x Backend

**Status:** Accepted (locked)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Gesamte Backend-Architektur

---

## Context

BudgetBuddy benötigt ein Backend, das:

- REST API mit Authentifizierung und Autorisierung
- PDF-Verarbeitung (Kontoauszüge parsen)
- Integration mit Claude API (LLM-basierte Kategorisierung)
- Transaktionsdatenbank (SQLite) mit ORM
- Sichere Speicherung von User-Passwörtern und Finanz-Daten (nDSG-konform)
- Robuste Error-Handling und Logging
- API-Dokumentation (OpenAPI 3.0)

### Optionen

1. **Java 25 + Spring Boot 3.5.x** — typsicher, großes Ökosystem, Industriestandard
2. **Node.js + Express / Fastify** — schnell zu schreiben, TypeScript native
3. **Python + FastAPI / Django** — schnell zu prototypen, gutes ML-Ecosystem
4. **Go + Gin / Echo** — hohe Performance, minimal dependencies
5. **.NET 8 + ASP.NET Core** — typsicher, Microsoft-Ökosystem

---

## Decision

**Java 25 + Spring Boot 3.5.3**

- **Runtime:** Java 25 (LTS)
- **Framework:** Spring Boot 3.5.3
- **Web Layer:** Spring Web MVC (synchron, für blocking JDBC korrekt)
- **Security:** Spring Security 6.5.x (stateless JWT)
- **ORM:** Spring Data JPA + Hibernate (mit SQLite-Dialect)
- **Migrations:** Flyway 10.x
- **API Docs:** Springdoc OpenAPI 2.8.17
- **PDF Parser:** Apache PDFBox 3.0.x
- **JWT:** io.jsonwebtoken:jjwt 0.12.x (HS256 signing)
- **AI SDK:** com.anthropic:anthropic-java 2.31.0

---

## Rationale

| Kriterium | Java/Spring | Node.js | Python | Go | .NET |
|-----------|------------|---------|--------|----|----|
| **Typsicherheit** | ✅ Compile-time | ❌ Runtime | ❌ Runtime | ✅ Compile-time | ✅ Compile-time |
| **Ecosystem** | ✅✅ Riesig | ✅ Groß | ✅ Gut | ⚠️ Mittel | ✅ Groß |
| **PDF-Verarbeitung** | ✅ PDFBox mature | ⚠️ pdf-lib, pdfkit | ✅ PyPDF2, pdfplumber | ⚠️ Wenige Optionen | ✅ iTextSharp |
| **Datenbank ORM** | ✅✅ Hibernate | ✅ Sequelize, TypeORM | ✅ SQLAlchemy | ⚠️ sqlc, gorm | ✅ Entity Framework |
| **Spring Ecosystem** | ✅✅ Spring Security, Data, Cloud | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A |
| **Deployment** | ✅ Docker, Kubernetes | ✅ Docker, Node | ✅ Docker, uwsgi | ✅ Docker, Go binary | ✅ Docker, IIS |
| **Performance** | ✅ Gut | ✅ Gut | ⚠️ Langsamer | ✅✅ Sehr schnell | ✅ Gut |
| **Team-Readiness** | ✅ Bekannt (Uni-Stoff) | ⚠️ Weniger Erfahrung | ⚠️ Weniger Erfahrung | ❌ Keine Erfahrung | ❌ Keine Erfahrung |

**Konkrete Vorteile für BudgetBuddy:**

1. **Spring Security + JWT:** Best-practice Authentication/Authorization out-of-the-box
2. **Spring Data JPA:** SQL-Boilerplate eliminiert; Repository-Pattern für Clean Code
3. **Springdoc OpenAPI:** Automatische API-Dokumentation (zero-config Swagger UI)
4. **Type Safety:** Compile-time Fehler (nicht erst zur Laufzeit)
5. **Anthropic Java SDK:** Offizielle Claude-API-Integration (nicht third-party)
6. **Single JAR Deployment:** `java -jar app.jar` — keine Node/Python Runtime nötig
7. **Long-Term Support:** Java 25 = LTS, Support bis 2030+

---

## Consequences

### ✅ Positive

- **Sicherheit:** Typsicherheit + Spring Security bewährte Patterns reduzieren Bugs
- **Wartbarkeit:** Großes Ecosystem, viel Dokumentation, breite Industrie-Nutzung
- **Vertikale Skalierung:** JVM-Optimierungen für Multicore/Memory sind bewährt
- **Testing:** JUnit 5, Mockito, TestContainers — ausgereift
- **Performance:** JVM JIT-Compiler für lange laufende Prozesse optimal

### ⚠️ Negative

- **Startup-Zeit:** JVM braucht ~2-3s zum Booten (relevant für Serverless, aber nicht MVP)
- **Memory-Footprint:** JAR + JVM = mindestens 100-150 MB RAM (vs. Go binary ~20 MB)
- **Deployment:** Docker Image größer als Go/Node
- **Lernkurve:** Spring Boot hat viele Konzepte (Beans, Dependency Injection, etc.)

### 🔄 Mitigations

- Startup-Zeit ist für BudgetBuddy irrelevant (kontinuierlich laufend)
- Memory ist ok für Studierende (lokale Dev) und kleiner Userbase
- Docker macht Deployment einfach (einmal konfiguriert)
- Spring Boot hat sehr gute Dokumentation

---

## Alternatives Considered

### ⚠️ Option 1: Node.js + Express / Fastify

**Entscheidung:** Abgelehnt

**Begründung:**
- TypeScript runtime-only (keine Compile-time Safety)
- PDF-Verarbeitung weniger mature als PDFBox
- Keine eingebaute Dependency Injection (manuell oder fastify-Plugins)
- JWT-Implementierung nicht standardisiert (viele Optionen, schwächer vs. Spring Security)
- Testing: Weniger standardisiert als Java/JUnit
- **Team:** Zu viel neuer Tech-Stack auf einmal (Angular + Node = ganz neue Toolchain)

### ❌ Option 2: Python + FastAPI

**Entscheidung:** Abgelehnt

**Begründung:**
- Keine Compile-time Typsicherheit (Laufzeitfehler in Produktion möglich)
- Performance nicht ideal für blocking JDBC (müsste async/await wrappen)
- nDSG-Sicherheit: weniger bewährte Patterns als Spring Security
- Skalierung: GIL (Global Interpreter Lock) limitiert Multicore-Nutzung
- **Team:** Kein Python-Background vorhanden

### ❌ Option 3: Go

**Entscheidung:** Abgelehnt

**Begründung:**
- Sehr klein/performant, aber: Ökosystem weniger reif für Enterprise-Patterns
- PDF-Verarbeitung: nicht ideal
- Kleine Standardlib für Web (müsste Gin/Echo + viele Packages kombinieren)
- **Team:** Kein Go-Background, zu große Lernkurve für MVP

### ❌ Option 4: .NET 8 / ASP.NET Core

**Entscheidung:** Abgelehnt

**Begründung:**
- Würde funktionieren (Typsicherheit, großes Ecosystem) — aber:
- Microsoft-Ökosystem: Licensing-Unsicherheit für Startup
- Windows-Last in Deployment (obwohl .NET Core cross-platform)
- **Team:** Kein C#-Background, Java bekannter

---

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (SPA + REST)
- **ADR-5:** SQLite (MVP) + Spring Data JPA
- **ADR-7:** JWT für Authentifizierung
- **ADR-9:** Apache PDFBox für PDF-Verarbeitung

---

## References

- [Spring Boot 3.5 Documentation](https://spring.io/projects/spring-boot)
- [Spring Security — Best Practices](https://spring.io/projects/spring-security)
- [Java 25 LTS Release Notes](https://openjdk.org/projects/jdk/25/)
- [Apache PDFBox Documentation](https://pdfbox.apache.org/)
- CLAUDE.md — Technology Stack
