# BudgetBuddy — Projektstatus

**Stand:** 2026-05-06  
**Kurs:** CAS Application Development with AI (ADAI) 2026 · BFH Biel · Ilja Rasin

---

## Projektidee

**BudgetBuddy** ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die ihnen durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben gibt. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an — damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können. Durch gezielte, lebensnahe Sparvorschläge hilft BudgetBuddy jungen Menschen, finanzielle Kontrolle zu gewinnen und erste Rücklagen aufzubauen.

---

## Zielgruppe (Personas)

### Persona 1 — Lara (22), Studentin (Bern)

- Studium Soziale Arbeit, arbeitet 20% in einer Bar, wohnhaft in der Schweiz
- **Problem:** Verliert Mitte des Monats den Überblick, ob das Geld noch für Miete und Lebensmittel reicht
- **Frustration:** Mühsame Excel-Tabellen, die sie nie aktualisiert; scrollt panisch durch Banking-App
- **Ziel:** "Safe-to-Spend"-Betrag pro Woche
- **Hürde:** Aufschieberitis — wenn der erste PDF-Upload zu kompliziert ist, bricht sie sofort ab

### Persona 2 — Marc (25), Junior-Verkäufer (Zürich)

- Hat gerade Lehre abgeschlossen, arbeitet im Detailhandel, wohnhaft in der Schweiz
- **Problem:** 0 CHF übrig am Monatsende trotz Vollzeitjob ("Kleinvieh" frisst Budget auf)
- **Frustration:** Bank warnt ihn nicht proaktiv vor unnötigen Ausgaben
- **Ziel:** Ersten Notgroschen von 1.000 CHF ansparen
- **Hürde:** Datenschutz-Skepsis — warum private Daten einer Web-App anvertrauen?

---

## Key Decisions

| Entscheid                  | Status                                                           |
| -------------------------- | ---------------------------------------------------------------- |
| OpenBanking-Anbindung      | Nice-to-Have (nicht MVP)                                         |
| Fokus                      | Zahlungskonten                                                   |
| Kategorisierung            | Automatisch + manuelle Korrektur als Feature                     |
| Nutzer                     | Nur Kunden mit Wohnsitz in der Schweiz (kein B2B / Berater-Tool) |
| Geografische Einschränkung | Schweiz (kein internationaler Rollout im MVP)                    |

---

## Technische Entscheide

### Tech Stack

| Schicht | Technologie | Begründung |
|---|---|---|
| Frontend | Angular (TypeScript) | Component-basiert, Two-Way-Binding, gut für Forms |
| Backend | Java 25 + Spring Boot 3.x | Typsicher, breites Ökosystem, Industriestandard |
| API-Dokumentation | OpenAPI 3 (Springdoc) | Automatisch generierte Doku, Contract-First möglich |
| Datenbank | SQLite | Einfach, kein separater DB-Server nötig — ideal für MVP |
| KI | Claude API (Anthropic Java SDK) | Kategorisierung + KI-Monatsbericht |

> **Hinweis SQLite:** Für das MVP ausreichend. Bei gleichzeitigen Schreibzugriffen mehrerer User kann SQLite zum Bottleneck werden — Migration zu PostgreSQL möglich, wenn nötig.

---

### Transaktions-Kategorisierung: Hybrid-Ansatz

| Schritt                     | Methode                                  | Begründung                                                                    |
| --------------------------- | ---------------------------------------- | ----------------------------------------------------------------------------- |
| 1. Bekannte Händler         | Lookup-Tabelle (Händlername → Kategorie) | Schnell, kostenlos, deterministisch — deckt ~70–80% der Transaktionen ab      |
| 2. Unbekannte Transaktionen | Claude API (LLM)                         | Flexibel für unbekannte/mehrdeutige Einträge; reduziert API-Calls auf ~20–30% |
| 3. Manuelle Korrekturen     | Lookup-Tabelle wird erweitert            | User-Korrekturen trainieren das System — Lerneffekt ohne Retraining           |

**Fallback-Kategorie:** `Sonstiges` (wenn LLM unsicher oder API nicht erreichbar)

**Beispiel-Prompt an Claude API:**

```
Kategorisiere diese Transaktion in genau eine der folgenden Kategorien:
[Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit,
 Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges]

Transaktion: "DIGITEC GALAXUS AG 044 913 2323"
Antwort (nur Kategoriename):
```

---

## 3 Grösste Risiken

1. **Churn-Falle** — manueller PDF-Import + Kategorisierung führt zu Nutzungsabbruch nach erstem Aha-Effekt
2. **Liability & Compliance** — sensible Transaktionsdaten = Hacking-Ziel; ein Datenleck ist existenzbedrohend
3. **Feature-Lücke der Banken** — UBS, Raiffeisen etc. bauen eigene PFM-Tools; Business Case kann über Nacht wegfallen

---

## User Stories

Vollständige Acceptance Criteria: [docs/requirements/](docs/requirements/)

| #  | Story | Persona | MoSCoW |
|----|-------|---------|--------|
| [US-01](docs/requirements/US-01-konto-login.md) | Konto erstellen und einloggen | Lara | Should |
| [US-02](docs/requirements/US-02-datenschutz-consent.md) | Datenschutz-Consent + Konto löschen (nDSG) | Marc | Should |
| [US-03](docs/requirements/US-03-fixkosten-wizard.md) | Fixkosten erfassen (Onboarding-Wizard) | Lara | Must |
| [US-04](docs/requirements/US-04-pdf-upload.md) | Kontoauszug als PDF hochladen | Lara | Must |
| [US-05](docs/requirements/US-05-transaktionen-kategorisieren.md) | Transaktionen kategorisieren (Auto + manuell) | Lara | Must |
| [US-06](docs/requirements/US-06-safe-to-spend.md) | Wöchentlicher Safe-to-Spend-Betrag | Lara | Must |
| [US-07](docs/requirements/US-07-sparziel.md) | Sparziel definieren und verfolgen | Marc | Could |
| [US-08](docs/requirements/US-08-wiederkehrende-ausgaben.md) | Wiederkehrende Ausgaben (Abos) erkennen | Marc | Should |
| [US-09](docs/requirements/US-09-ki-monatsbericht.md) | KI-generierter Monatsbericht | Lara | Should |
| [US-10](docs/requirements/US-10-monatsvergleich.md) | Monatsvergleich (aktuell vs. Vormonat) | Lara | Could |
| [US-11](docs/requirements/US-11-openbanking.md) | OpenBanking-Anbindung | Lara | Could |
| [US-12](docs/requirements/US-12-monatswechsel.md) | Zwischen Monaten wechseln | Lara | Should |
| [US-13](docs/requirements/US-13-transaktionen-pro-kategorie.md) | Einzeltransaktionen pro Kategorie einsehen | Lara | Should |
| [US-14](docs/requirements/US-14-einstellungen.md) | Passwort und Einkommen in Einstellungen ändern | Marc | Should |

<!-- GSD:project-start source:PROJECT.md -->
## Project

**BudgetBuddy**

BudgetBuddy is a web app for students and young professionals living in Switzerland that ingests bank statement PDFs, automatically categorizes transactions, and displays a weekly "Safe-to-Spend" budget — so users always know how much they can spend without worry. Built with Angular (frontend), Spring Boot 3.x (backend), SQLite (database), and Claude API (AI categorization + monthly reports).

**Core Value:** A weekly Safe-to-Spend number users can trust — calculated from real transaction data, not manual entry.

### Constraints

- **Tech Stack**: Angular (frontend), Java 25 + Spring Boot 3.x (backend), SQLite (MVP DB), Claude API via Anthropic Java SDK, OpenAPI 3 / Springdoc — locked in
- **Database**: SQLite for MVP; migration path to PostgreSQL exists if concurrent writes become bottleneck
- **Geography**: Switzerland only — CHF, Swiss banks (UBS, Raiffeisen, PostFinance), nDSG
- **Privacy**: Sensitive financial data — security is existential; compliance with Swiss nDSG required (including right to deletion)
- **Timeline**: No hard deadline; MVP-first mentality — validate core safe-to-spend concept, then iterate
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Confidence: HIGH on framework/library choices, MEDIUM on a few version patches
## Recommended Stack
### Backend
| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Runtime | Java | 25 (LTS) | Project-locked |
| Framework | Spring Boot | 3.5.3 | Project-locked; latest 3.x stable |
| Web layer | Spring Web MVC | (bundled) | Synchronous; correct for blocking SQLite JDBC |
| Security | Spring Security | 6.5.x | Stateless JWT resource server pattern |
| ORM | Spring Data JPA + Hibernate | (bundled) | Repository pattern; needs community dialect for SQLite |
| DB | SQLite | 3.x | Project-locked for MVP |
| JDBC driver | org.xerial:sqlite-jdbc | 3.49.x | Only production JDBC driver for SQLite |
| Dialect | org.hibernate.orm:hibernate-community-dialects | (Hibernate version) | Provides `SQLiteDialect` |
| Migrations | Flyway | 10.x | SQLite-confirmed; essential for team schema sync |
| JWT | io.jsonwebtoken:jjwt-* | 0.12.x | HS256 signing, fluent builder API |
| API docs | Springdoc OpenAPI | 2.8.17 | Spring Boot 3.5 compatible; zero-config Swagger UI |
| AI | com.anthropic:anthropic-java | 2.31.0 | Official Anthropic SDK |
| PDF parsing | org.apache.pdfbox:pdfbox | 3.0.x | Apache-licensed; correct for text-layer Swiss bank PDFs |
### Frontend
| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Framework | Angular | 19.x | Project-locked; standalone components, Signals |
| State | Angular Signals + Services | (bundled) | No NgRx needed for MVP scope |
| Forms | Reactive Forms (FormGroup) | (bundled) | Stable; Signal Forms still experimental |
| HTTP auth | Functional HTTP interceptor | (bundled) | Inject JWT Bearer token per request |
| Charts | Chart.js + ng2-charts | 4.x / 6.x | Lightweight, Angular-native wrapper for pie/bar |
| Change detection | OnPush everywhere | (bundled) | Required for Signals to work correctly |
### AI/ML
- **Categorization model**: `claude-haiku-3-5-20241022` — fast (~200ms), cheap, single-label output
- **Monthly AI report model**: `claude-sonnet-4-20250514` (latest Sonnet) — richer language, called once/user/month
- **Fallback**: catch `AnthropicException`, return `"Sonstiges"` — Claude unavailability must never block import flow
## Swiss Bank PDF Specifics
- Columns: Buchungsdatum | Valuta | Text | Belastungen CHF | Gutschriften CHF | Saldo CHF
- Date format: `dd.MM.yyyy`
- Amount format: `1'234.56` (apostrophe thousands separator — requires `replace("'", "")` before `BigDecimal` parse)
- Text field can include multiline wrapping — use Saldo column as row anchor when splitting
## Auth Decision: JWT (Stateless, HS256)
| Factor | JWT (stateless) | Session (server-side) |
|--------|----------------|----------------------|
| SQLite write pressure | None — no session table | Every login/request writes to sessions table |
| Angular SPA integration | Clean Bearer header | Requires cookie + CORS + SameSite config |
| Spring Security support | First-class `oauth2ResourceServer().jwt()` | Also supported but adds Spring Session dep |
| Logout invalidation | Client deletes token (MVP acceptable) | Instant server-side invalidation |
| MVP scope fit | Excellent | Overengineered |
## SQLite + Spring Boot Gotchas (Critical)
## What NOT to Use
| Technology | Why Not |
|-----------|---------|
| Spring Boot 4 | Explicit project risk decision — milestone releases only |
| Spring WebFlux | SQLite JDBC is blocking; reactive wrapping adds complexity with no benefit |
| iText 7 | AGPL license — requires open-sourcing or commercial license |
| Tabula-java | Designed for scanned PDFs; Swiss bank PDFs have a text layer |
| NgRx | Over-engineered for 2-3 person course project with simple state |
| D3.js | Steep learning curve, no Angular integration, overkill for pie + bar |
| Highcharts | Commercial license for non-personal projects |
| Redis + Spring Session | Unnecessary infrastructure when using stateless JWT |
| H2 in-memory (for testing) | Dialect mismatch vs SQLite; use `jdbc:sqlite::memory:` in tests instead |
| PDFBox 2.x | Deprecated API (`PDDocument.load()`); use 3.x `Loader.loadPDF()` from the start |
| `double`/`float` for money | Binary floating point cannot represent CHF amounts exactly |
## Open Questions
## Sources
| Claim | Confidence |
|-------|------------|
| Anthropic Java SDK v2.31.0 | HIGH — GitHub releases |
| Spring Boot 3.5 / Spring Security 6.5 | HIGH — official Spring docs |
| Springdoc 2.8.17 Spring Boot 3.5 compat | HIGH — springdoc.org |
| PDFBox text extraction + password detection | HIGH — Apache PDFBox repo |
| JJWT 0.12.x API | MEDIUM — version patch unverified |
| Raiffeisen PDF layout | HIGH — direct fixture inspection |
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

### Architecture Decision Records

Vollständige ADRs: [docs/adr/README.md](docs/adr/README.md)

| ADR | Entscheid | Abgelehnte Alternativen |
|-----|-----------|------------------------|
| [ADR-0](docs/adr/ADR-0-frontend-backend-separation.md) | SPA + REST API (Angular ↔ Spring Boot, JWT in Header) | SSR (Next.js/Thymeleaf), Monolith mit JSP |
| [ADR-1](docs/adr/ADR-1-java-spring-boot-backend.md) | Java 25 + Spring Boot 3.5.x | Node.js/Express, Python/FastAPI, Go, .NET 8 |
| [ADR-2](docs/adr/ADR-2-angular-frontend.md) | Angular 19.x (Standalone Components, Signals, Reactive Forms) | React, Vue 3, Svelte, Astro |
| [ADR-3](docs/adr/ADR-3-rest-vs-graphql.md) | REST API + OpenAPI 3 (Springdoc) | GraphQL (Overkill, kein nativer File-Upload), gRPC |
| [ADR-4](docs/adr/ADR-4-monolith-vs-microservices.md) | Single Spring Boot JAR (Monolith) | Microservices/K8s (zu komplex), Serverless (JVM Cold-Start) |
| [ADR-5](docs/adr/ADR-5-sqlite-mvp-database.md) | SQLite für MVP; Migration zu PostgreSQL möglich | PostgreSQL from Day One (Overkill), MongoDB (nicht relational) |
| [ADR-6](docs/adr/ADR-6-hybrid-categorization.md) | Hybrid: Lookup-Tabelle zuerst, Claude API nur für unbekannte Tx | LLM-Only ($750/Monat, zu teuer), Fine-tuned ML Model (kein Trainingsdata) |
| [ADR-7](docs/adr/ADR-7-jwt-authentication.md) | JWT HS256, bcrypt-Passwörter, stateless; Logout = Client löscht Token | Server-Side Sessions (DB-Schreibdruck), OAuth 2.0 (Overkill für MVP) |
| [ADR-8](docs/adr/ADR-8-apache-pdfbox.md) | Apache PDFBox 3.x (`Loader.loadPDF()`) | iText 7 (AGPL-Lizenz!), Tabula-java (langsam, kein Text-Layer), pdfplumber (Python) |
| [ADR-9](docs/adr/ADR-9-bigdecimal-money.md) | `BigDecimal` für alle CHF-Beträge, `DECIMAL(10,2)` in DB | `double`/`float` (Rundungsfehler!), `long` (Cent-Speicherung), Joda-Money |

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
