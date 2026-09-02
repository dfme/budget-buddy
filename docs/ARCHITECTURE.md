# Architecture

Referenzdokument zu [CLAUDE.md](../CLAUDE.md). Vollständiger ADR-Index inkl. Status,
Kategorien und verworfener Alternativen: [docs/adr/README.md](adr/README.md).

## C2 Container Diagram

```
Browser (Lara, Marc)
     │
     │ HTTPS · statische Assets (HTML/JS/CSS)
     │ Auth: httpOnly Cookie (SameSite=Strict, kein JS-Zugriff)
     ▼
┌─────────────────────────────────────────────────────┐
│  Web SPA  [Angular 21, TypeScript]                  │
│  Onboarding · PDF-Upload · Dashboard · Korrekturen  │
│  HTTP mit withCredentials:true (kein Bearer-Header) │
└──────────────────┬──────────────────────────────────┘
                   │ REST/JSON · HTTPS · Cookie automatisch mitgesendet
                   │ (gleicher Host in Prod → kein CORS)
                   ▼
┌─────────────────────────────────────────────────────┐     ┌────────────────────┐
│  API Application  [Spring Boot 3.5 / Java 25, JAR]  │     │  Anthropic Claude  │
│                                                     │     │   [Ext. System]    │
│  auth/         JWT HS256, bcrypt, httpOnly Cookie   │     └────────▲───────────┘
│  transaction/  PDF-Upload → Parse sync, Kat. async  │             │
│  categorization/ Lookup → CategorizationPort        │─────────────┘
│  budget/       Safe-to-Spend, Sparziele             │  HTTPS / Anthropic Java SDK
│  report/       KI-Monatsbericht (Sonnet 4, 1×/Monat)│  Haiku: Kategorisierung
│                                                     │  Sonnet: Monatsbericht
│  ImportJob-Status: GET /api/import/{jobId}/status   │
└──────────────────┬──────────────────────────────────┘
                   │ JDBC über TLS · JPA/Hibernate
                   │ BigDecimal für alle CHF-Beträge
                   ▼
┌─────────────────────────────────────────────────────┐
│  Database  [PostgreSQL 18 + Flyway]                 │
│  Neon, Frankfurt/EU — ausserhalb von Render         │
│  users · transactions · fixed_costs · import_jobs · │
│  savings_goals · category_lookup                    │
└─────────────────────────────────────────────────────┘
```

**Deployment:** Single JAR auf Render (Frankfurt/EU) — Angular-Build als statische Assets in `BOOT-INF/static/`.
**Dev:** Angular Dev-Server `localhost:4200` + Spring Boot `localhost:8080` + PostgreSQL aus `docker-compose.yml`, CORS für `localhost:4200` konfiguriert.

## Container-Verantwortlichkeiten

| Container       | Technologie                          | Kernaufgabe                                                             |
| --------------- | ------------------------------------ | ----------------------------------------------------------------------- |
| Web SPA         | Angular 21, Signals, Reactive Forms  | UI: Onboarding, PDF-Upload, Dashboard, Korrekturen                      |
| API Application | Spring Boot 3.5, Java 25, Single JAR | Auth, PDF-Parsing, Kategorisierung, Berechnungen, KI-Bericht            |
| Database        | PostgreSQL 18 (Neon) + Flyway        | Persistenz: User, Transaktionen, Fixkosten, Lookup-Tabelle, Import-Jobs |

**Bewusst weggelassen:** Redis/Cache, Message Queue, CDN, Microservices, eigener KI-Worker — alles Overengineering für 3 Devs / 3 Monate.

## Architecture Decision Records

| ADR | Entscheid |
| --- | --------- |
| [ADR-0](adr/ADR-0-frontend-backend-separation.md) | SPA + REST API (Angular ↔ Spring Boot, JWT als httpOnly Cookie) |
| [ADR-1](adr/ADR-1-java-spring-boot-backend.md) | Java 25 + Spring Boot 3.5.x |
| [ADR-2](adr/ADR-2-angular-frontend.md) | Angular 21.x (Standalone Components, Signals, Reactive Forms) |
| [ADR-3](adr/ADR-3-rest-vs-graphql.md) | REST API + OpenAPI 3 (Springdoc) |
| [ADR-4](adr/ADR-4-monolith-vs-microservices.md) | Single Spring Boot JAR (Monolith) |
| [ADR-5](adr/ADR-5-sqlite-mvp-database.md) | ~~SQLite für MVP~~ — superseded by ADR-12 |
| [ADR-6](adr/ADR-6-hybrid-categorization.md) | Hybrid: Lookup-Tabelle zuerst, Claude API nur für unbekannte Tx |
| [ADR-7](adr/ADR-7-jwt-authentication.md) | JWT HS256, bcrypt-Passwörter, httpOnly Cookie, CSRF via SameSite=Strict |
| [ADR-8](adr/ADR-8-apache-pdfbox.md) | Apache PDFBox 3.x (`Loader.loadPDF()`) |
| [ADR-9](adr/ADR-9-bigdecimal-money.md) | `BigDecimal` für alle CHF-Beträge, `DECIMAL(10,2)` in DB |
| [ADR-10](adr/ADR-10-hosting-plattform.md) | Render (Frankfurt/EU), SPA gebündelt im JAR, nDSG-Risiko akzeptiert |
| [ADR-11](adr/ADR-11-ui-design-system.md) | UI-Design-Richtung «Klarheit» (Variante A); Komponenten-Unterbau offen bis FE-UI-02 |
| [ADR-12](adr/ADR-12-datenpersistenz-produktion.md) | PostgreSQL 18 bei Neon (Frankfurt/EU, Free); supersedet ADR-5 |
| [ADR-13](adr/ADR-13-fixkosten-transaktions-zuordnung.md) | Fixkosten-Doppelabzug: betragsbasiertes 1:1-Matching zur Berechnungszeit |
| [ADR-14](adr/ADR-14-asynchroner-pdf-import.md) | Parse synchron, Kategorisierung als `@Async`-Job mit Fortschritts-Polling; 20 Transaktionen pro Claude-Call |
