# ADR-1: Java 25 + Spring Boot 3.5.x als Backend-Framework

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy benötigt ein Backend-Framework, das folgende Anforderungen erfüllt:

- REST API mit Authentifizierung und Autorisierung
- PDF-Verarbeitung (Swiss Bank Statement Parsing)
- Integration mit Claude API (LLM-basierte Transaktions-Kategorisierung)
- Relationale Datenbank mit ORM (SQLite MVP)
- Sichere Speicherung von Passwörtern und Finanzdaten (nDSG-konform)
- Automatische API-Dokumentation (OpenAPI 3.0)

Alternative Technologie-Stacks: Node.js + Express, Python + FastAPI, Go, .NET 8

## Decision

Wir nutzen **Java 25 LTS + Spring Boot 3.5.3** mit folgenden Dependencies:

- **Web Layer:** Spring Web MVC (synchron, korrekt für blocking JDBC)
- **Security:** Spring Security 6.5.x (stateless JWT)
- **ORM:** Spring Data JPA + Hibernate mit SQLiteDialect
- **Migrations:** Flyway 10.x
- **API Docs:** Springdoc OpenAPI 2.8.17
- **PDF Parser:** Apache PDFBox 3.0.x
- **JWT:** io.jsonwebtoken:jjwt 0.12.x (HS256)
- **AI Integration:** com.anthropic:anthropic-java 2.31.0

## Consequences

### Positive

- **Typsicherheit:** Compile-time Fehlerdetection vor Produktion
- **Spring Security:** Best-practice Authentication/Authorization out-of-the-box
- **Spring Data JPA:** Repository-Pattern reduziert SQL-Boilerplate
- **Ecosystem:** Großes, bewährtes Ökosystem mit guter Dokumentation
- **Long-Term Support:** Java 25 hat LTS bis 2030+
- **Single JAR:** `java -jar app.jar` — einfaches Deployment
- **Testing:** Ausgereiftes JUnit 5 + Mockito + TestContainers Framework

### Negative

- **JVM Startup:** ~2-3 Sekunden Bootzeit (für MVP irrelevant)
- **Memory:** 100-150 MB RAM + JVM (vs. Go ~20 MB)
- **Learning Curve:** Spring Boot hat viele Konzepte (Beans, Dependency Injection)
- **Docker Image:** Größer als Go/Node Alternativen

## Alternatives

### Node.js + Express / Fastify

**Rejected.** Gut für schnelle Prototypen, aber:

- Keine Compile-time Typsicherheit (Runtime-Fehler möglich)
- PDF-Verarbeitung weniger reif als PDFBox
- JWT-Sicherheit weniger standardisiert als Spring Security
- Team müsste JavaScript für Frontend UND Node Backend lernen

### Python + FastAPI

**Rejected.** Schnell zu prototypen, aber:

- Keine Compile-time Typsicherheit
- Global Interpreter Lock (GIL) limitiert Multicore-Nutzung
- Security-Patterns weniger bewährt als Spring Security
- Team hat keine Python-Erfahrung

### Go

**Rejected.** Sehr performant, aber:

- Ökosystem weniger reif für Enterprise-Muster
- PDF-Verarbeitung suboptimal
- Kleine Standard Library für Web
- Team hat keine Go-Erfahrung

### .NET 8 / ASP.NET Core

**Rejected.** Gute Technologie, aber:

- Microsoft-Ökosystem schafft Licensing-Unsicherheit
- Team hat keine C#-Erfahrung
- Java bereits in der Zielgruppe bekannt

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (SPA + REST)
- **ADR-5:** SQLite als MVP-Datenbank
- **ADR-7:** JWT für Authentifizierung
- **ADR-8:** Apache PDFBox für PDF-Verarbeitung
