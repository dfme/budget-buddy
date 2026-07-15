# BudgetBuddy

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

| #                                                                | Story                                          | Persona | MoSCoW |
| ---------------------------------------------------------------- | ---------------------------------------------- | ------- | ------ |
| [US-01](docs/requirements/US-01-konto-login.md)                  | Konto erstellen und einloggen                  | Lara    | Should |
| [US-02](docs/requirements/US-02-datenschutz-consent.md)          | Datenschutz-Consent + Konto löschen (nDSG)     | Marc    | Should |
| [US-03](docs/requirements/US-03-fixkosten-wizard.md)             | Fixkosten erfassen (Onboarding-Wizard)         | Lara    | Must   |
| [US-04](docs/requirements/US-04-pdf-upload.md)                   | Kontoauszug als PDF hochladen                  | Lara    | Must   |
| [US-05](docs/requirements/US-05-transaktionen-kategorisieren.md) | Transaktionen kategorisieren (Auto + manuell)  | Lara    | Must   |
| [US-06](docs/requirements/US-06-safe-to-spend.md)                | Wöchentlicher Safe-to-Spend-Betrag             | Lara    | Must   |
| [US-07](docs/requirements/US-07-sparziel.md)                     | Sparziel definieren und verfolgen              | Marc    | Could  |
| [US-08](docs/requirements/US-08-wiederkehrende-ausgaben.md)      | Wiederkehrende Ausgaben (Abos) erkennen        | Marc    | Should |
| [US-09](docs/requirements/US-09-ki-monatsbericht.md)             | KI-generierter Monatsbericht                   | Lara    | Should |
| [US-10](docs/requirements/US-10-monatsvergleich.md)              | Monatsvergleich (aktuell vs. Vormonat)         | Lara    | Could  |
| [US-11](docs/requirements/US-11-openbanking.md)                  | OpenBanking-Anbindung                          | Lara    | Could  |
| [US-12](docs/requirements/US-12-monatswechsel.md)                | Zwischen Monaten wechseln                      | Lara    | Should |
| [US-13](docs/requirements/US-13-transaktionen-pro-kategorie.md)  | Einzeltransaktionen pro Kategorie einsehen     | Lara    | Should |
| [US-14](docs/requirements/US-14-einstellungen.md)                | Passwort und Einkommen in Einstellungen ändern | Marc    | Should |

## Project

**BudgetBuddy**

BudgetBuddy is a web app for students and young professionals living in Switzerland that ingests bank statement PDFs, automatically categorizes transactions, and displays a weekly "Safe-to-Spend" budget — so users always know how much they can spend without worry. Built with Angular (frontend), Spring Boot 3.x (backend), SQLite (database), and Claude API (AI categorization + monthly reports).

**Core Value:** A weekly Safe-to-Spend number users can trust — calculated from real transaction data, not manual entry.

## Technology Stack

### Backend

| Layer       | Technology                                     | Version             | Rationale                                               |
| ----------- | ---------------------------------------------- | ------------------- | ------------------------------------------------------- |
| Runtime     | Java                                           | 25 (LTS)            | Project-locked                                          |
| Framework   | Spring Boot                                    | 3.5.3               | Project-locked; latest 3.x stable                       |
| Build Tool  | Maven                                          | 3.9.x               | Project-locked; via Maven Wrapper (`mvnw`)              |
| Web layer   | Spring Web MVC                                 | (bundled)           | Synchronous; correct for blocking SQLite JDBC           |
| Security    | Spring Security                                | 6.5.x               | Stateless JWT resource server pattern                   |
| ORM         | Spring Data JPA + Hibernate                    | (bundled)           | Repository pattern; needs community dialect for SQLite  |
| DB          | SQLite                                         | 3.x                 | Project-locked for MVP                                  |
| JDBC driver | org.xerial:sqlite-jdbc                         | 3.49.x              | Only production JDBC driver for SQLite                  |
| Dialect     | org.hibernate.orm:hibernate-community-dialects | (Hibernate version) | Provides `SQLiteDialect`                                |
| Migrations  | Flyway                                         | 10.x                | SQLite-confirmed; essential for team schema sync        |
| JWT         | io.jsonwebtoken:jjwt-\*                        | 0.12.x              | HS256 signing, fluent builder API                       |
| API docs    | Springdoc OpenAPI                              | 2.8.17              | Spring Boot 3.5 compatible; zero-config Swagger UI      |
| AI          | com.anthropic:anthropic-java                   | 2.31.0              | Official Anthropic SDK                                  |
| PDF parsing | org.apache.pdfbox:pdfbox                       | 3.0.x               | Apache-licensed; correct for text-layer Swiss bank PDFs |

### Frontend

| Layer            | Technology                             | Version   | Rationale                                                                    |
| ---------------- | -------------------------------------- | --------- | ---------------------------------------------------------------------------- |
| Framework        | Angular                                | 21.x      | Project-locked; standalone components, Signals                               |
| Build Tool       | Angular CLI (`@angular/cli`)           | 21.x      | Standard; esbuild-basiert seit Angular 17+                                   |
| Package Manager  | npm                                    | (bundled) | Bundled mit Node.js; kein Mehraufwand gegenüber pnpm/yarn für MVP-Scope      |
| State            | Angular Signals + Services             | (bundled) | No NgRx needed for MVP scope                                                 |
| Forms            | Reactive Forms (FormGroup)             | (bundled) | Stable; Signal Forms still experimental                                      |
| HTTP auth        | `withCredentials: true` auf HttpClient | (bundled) | Cookie automatisch mitgesendet; kein manueller HttpInterceptor nötig (ADR-7) |
| Charts           | Chart.js + ng2-charts                  | 4.x / 8.x | Lightweight, Angular-native wrapper for pie/bar                              |
| Change detection | OnPush everywhere                      | (bundled) | Required for Signals to work correctly                                       |

### AI/ML

- **Categorization model**: `claude-haiku-4-5` — fast, cheap, single-label output. Konfigurierbar via `anthropic.api.model`.
- **Monthly AI report model**: `claude-sonnet-5` — richer language, called once/user/month
- **Fallback**: catch `AnthropicException`, return `"Sonstiges"` — Claude unavailability must never block import flow
- **Circuit Breaker** (BE-CAT-02): Nach 3 fehlgeschlagenen Claude-Calls in Folge werden weitere Transaktionen 60s lang ohne API-Call als `Sonstiges` eingestuft. Der Fallback allein genügt nicht — bei ~20 unbekannten Transaktionen pro Import würde ein API-Ausfall den synchronen Upload sonst minutenlang blockieren.

## Swiss Bank PDF Specifics

- Columns: Buchungsdatum | Valuta | Text | Belastungen CHF | Gutschriften CHF | Saldo CHF
- Date format: `dd.MM.yyyy`
- Amount format: `1'234.56` (apostrophe thousands separator — requires `replace("'", "")` before `BigDecimal` parse)
- Text field can include multiline wrapping — use Saldo column as row anchor when splitting

## Auth Decision: JWT (Stateless, HS256)

| Factor                  | JWT (stateless)                                                 | Session (server-side)                        |
| ----------------------- | --------------------------------------------------------------- | -------------------------------------------- |
| SQLite write pressure   | None — no session table                                         | Every login/request writes to sessions table |
| Angular SPA integration | httpOnly Cookie + `withCredentials: true`; kein HttpInterceptor | Requires cookie + CORS + SameSite config     |
| Spring Security support | JWT in Cookie; Spring Security liest Token aus Cookie           | Also supported but adds Spring Session dep   |
| Logout invalidation     | Backend setzt `Max-Age=0` → sofort invalidiert                  | Instant server-side invalidation             |
| MVP scope fit           | Excellent                                                       | Overengineered                               |

## SQLite + Spring Boot Gotchas (Critical)

## What NOT to Use

| Technology                 | Why Not                                                                         |
| -------------------------- | ------------------------------------------------------------------------------- |
| Spring Boot 4              | Explicit project risk decision — milestone releases only                        |
| Gradle                     | Maven ist Build-Tool-Entscheid; Team-Konsistenz mit Standard-Spring-Boot-Setup  |
| Spring WebFlux             | SQLite JDBC is blocking; reactive wrapping adds complexity with no benefit      |
| iText 7                    | AGPL license — requires open-sourcing or commercial license                     |
| Tabula-java                | Designed for scanned PDFs; Swiss bank PDFs have a text layer                    |
| NgRx                       | Over-engineered for 2-3 person course project with simple state                 |
| D3.js                      | Steep learning curve, no Angular integration, overkill for pie + bar            |
| Highcharts                 | Commercial license for non-personal projects                                    |
| Redis + Spring Session     | Unnecessary infrastructure when using stateless JWT                             |
| H2 in-memory (for testing) | Dialect mismatch vs SQLite; use `jdbc:sqlite::memory:` in tests instead         |
| PDFBox 2.x                 | Deprecated API (`PDDocument.load()`); use 3.x `Loader.loadPDF()` from the start |
| `double`/`float` for money | Binary floating point cannot represent CHF amounts exactly                      |

## Open Questions

## Sources

| Claim                                       | Confidence                        |
| ------------------------------------------- | --------------------------------- |
| Anthropic Java SDK v2.31.0                  | HIGH — GitHub releases            |
| Spring Boot 3.5 / Spring Security 6.5       | HIGH — official Spring docs       |
| Springdoc 2.8.17 Spring Boot 3.5 compat     | HIGH — springdoc.org              |
| PDFBox text extraction + password detection | HIGH — Apache PDFBox repo         |
| JJWT 0.12.x API                             | MEDIUM — version patch unverified |
| Raiffeisen PDF layout                       | HIGH — direct fixture inspection  |

## Conventions

### Git: Branching-Strategie

| Branch         | Zweck                              | Format                         |
| -------------- | ---------------------------------- | ------------------------------ |
| `main`         | Production-ready — immer deploybar | —                              |
| Feature Branch | Für Tasks/User Stories             | `feature/<TASK-ID>-<Freitext>` |
| Bugfix Branch  | Für Bugfixes                       | `fix/<TASK-ID>-<Freitext>`     |

Beispiele: `feature/US-04-pdf-upload`, `fix/INFRA-05-cors-header`

Regel: Kein direkter Commit auf `main`. Jeder Branch wird per Pull Request gegen `main` gemergt.

**Für Claude:** Niemals direkt auf `main` committen oder pushen — auch nicht auf explizite Benutzeranfrage. Immer einen Feature-/Bugfix-Branch erstellen und einen PR öffnen. Bei einer solchen Anfrage den Benutzer auf diese Regel hinweisen.

### Git: Task-ID-Konvention (GitHub Issues)

Issue-Titel folgen dem Format `[TASK-ID] Kurzbeschreibung`. Die Task-ID kodiert Bereich und Feature:

| Präfix | Bereich | Beispiel |
| ------ | ------- | -------- |
| `INFRA-XX` | Infrastruktur / DevOps | `INFRA-01`, `INFRA-05` |
| `DB-XX` | Datenbank / Flyway-Migrationen | `DB-01`, `DB-04` |
| `BE-AUTH-XX` | Backend — Authentifizierung | `BE-AUTH-01` |
| `BE-FC-XX` | Backend — Fixkosten | `BE-FC-01` |
| `BE-PDF-XX` | Backend — PDF-Import | `BE-PDF-01` |
| `BE-CAT-XX` | Backend — Kategorisierung | `BE-CAT-02` |
| `BE-STS-XX` | Backend — Safe-to-Spend | `BE-STS-01` |
| `FE-FC-XX` | Frontend — Fixkosten | `FE-FC-01` |
| `FE-PDF-XX` | Frontend — PDF-Upload | `FE-PDF-01` |
| `FE-CAT-XX` | Frontend — Kategorisierung | `FE-CAT-01` |
| `FE-STS-XX` | Frontend — Safe-to-Spend | `FE-STS-01` |

Die Task-ID im Issue-Titel wird direkt als `<TASK-ID>` in der Branch-Namenskonvention verwendet (siehe Branching-Strategie oben).

### Git: Bug-Tickets

Bugs bekommen **keine eigene ID-Reihe**. Die Task-ID kodiert den **Bereich**, nicht den Typ — der Typ steckt bereits im Branch-Präfix (`feature/` vs. `fix/`) und im Label. Regeln:

1. **Neue, freie ID im betroffenen Bereich** — z. B. `INFRA-08` für einen Bug in der CD-Pipeline. Niemals eine bestehende Task-ID wiederverwenden, auch nicht die des Tasks, der den Bug eingebaut hat: Eine ID = eine Arbeitseinheit = ein Branch = ein PR. Wiederverwendung zerstört die Rückverfolgbarkeit.
2. **Bereich = wo der Fix landet.** Ein Bug ist immer einem Bereich zuordenbar, auch wenn er zu keinem bestehenden Task gehört. Bei bereichsübergreifenden Bugs entscheidet der Ort des Fixes.
3. **Typ via Label** — `bug` am Issue setzen. Nicht als Freitext-Präfix in den Titel (`Bug: …`) schreiben.
4. **Branch:** `fix/<TASK-ID>-<Freitext>` (siehe Branching-Strategie oben).
5. **Titel wie bei jedem Issue:** `[TASK-ID] Kurzbeschreibung`.

Beispiel: [#68](https://github.com/dfme/budget-buddy/issues/68) — `[INFRA-08] Smoke-Test verifiziert nicht die neue Version`, Label `bug`, Branch `fix/INFRA-08-deploy-version-check`.

### Git: Review-Konvention

1. **Lokaler Review durch Claude** — bevor ein PR erstellt wird, prüft Claude die Änderungen lokal
2. **PR-Erstellung in GitHub** — erst nach erfolgreichem lokalem Review wird der PR in GitHub erstellt
3. **Freigabe durch mind. 1 Dev** — der PR muss von mindestens einem Dev genehmigt werden, bevor er gemergt werden darf
4. **Merge nur durch Dev** — der Merge auf `main` wird ausschliesslich von einem Dev getriggert, nie von Claude

### Datenbank: Flyway-Migrationen

- Versionsnummer immer **zweistellig mit führender Null**: `V01__`, `V02__`, … `V10__`. Sichert korrekte alphabetische Sortierung im Dateisystem bei vielen Migrationen.
- Dateiname: `V<NN>__<snake_case_beschreibung>.sql` (z. B. `V01__create_users_table.sql`).
- Geldbeträge als `DECIMAL(10,2)`, nie `FLOAT`/`REAL` (siehe ADR-9).

### Backend: Package-Struktur (Modular Monolith)

Packages nach Domäne, nicht nach Schicht, unterhalb von `backend/src/main/java/com/budgetbuddy/`:

```
backend/
  └── src/main/java/com/budgetbuddy/
        ├── auth/           (AuthController, AuthService, User-Entity, JWT-Config)
        ├── transaction/    (TransactionController, PdfImportService, Transaction-Entity)
        ├── categorization/ (CategorizationService, LookupTable, CategorizationPort)
        ├── budget/         (BudgetController, SafeToSpendService, SavingsGoalService)
        └── report/         (ReportController, AiReportService)
```

Regel: Kein direkter Zugriff auf Repositories oder Services eines anderen Moduls. Cross-Modul-Kommunikation nur über definierte Interfaces.

### Frontend: Feature-Struktur (nach Domäne)

Angular Feature-Folders analog zu den Backend-Modulen, unterhalb von `frontend/src/app/`:

```
frontend/
  └── src/app/
        ├── auth/          (US-01: Login/Register)
        ├── onboarding/    (US-03: Fixkosten-Wizard)
        ├── transactions/  (US-04: Upload, US-05: Kategorisierung, US-13: pro Kategorie)
        ├── dashboard/     (US-06: Safe-to-Spend, US-10: Monatsvergleich, US-12: Monatswechsel)
        ├── savings/       (US-07: Sparziel)
        ├── reports/       (US-09: KI-Monatsbericht)
        ├── settings/      (US-02: Consent/Löschen, US-14: Passwort/Einkommen)
        ├── shared/        (domänenübergreifende UI-Komponenten, Pipes)
        └── core/          (Guards, Auth-State, HTTP-Error-Handling)
```

Regel: Kein NgRx — State liegt direkt in den Feature-Services via Signals (siehe Tech-Stack).

### E2E: Verzeichnisstruktur

Playwright-Tests in eigenem Verzeichnis ausserhalb von `backend/` und `frontend/`, da sie Frontend und Backend gemeinsam end-to-end testen:

```
e2e/
  └── tests/   (1 Testfall pro Must-Have User Story: US-03, US-04, US-05, US-06)
```

Regel: Pro Must-Have Story je 1 Happy Path + 1 Fehlerpfad (siehe Testing: Frameworks).

### Backend: Claude API hinter Interface

Die Claude-API immer hinter einem `CategorizationPort`-Interface kapseln:

```java
public interface CategorizationPort {
    Category categorize(String transactionText);
}
```

Das erlaubt Mock in Tests und Austausch des Modells ohne Refactoring im Rest der Codebase.

### Backend: Import Flow

MVP: synchron — der Upload-Endpoint blockiert bis Import und Kategorisierung abgeschlossen sind.

Upgrade-Pfad (wenn Wartezeiten zu Churn führen): Spring `@Async` + `ImportJob`-Entity in SQLite + Status-Polling `GET /import/{jobId}/status`. Kein Kafka, kein Redis nötig.

### Sicherheit: Keine Secrets im Git

Credentials, API-Keys und Passwörter dürfen nie ins Git-Repository gelangen. `.env`-Dateien müssen in `.gitignore` stehen. Der `ANTHROPIC_API_KEY` und der JWT-Secret werden ausschliesslich als Umgebungsvariablen übergeben — nie hardcodiert im Code oder in `application.properties`. Bei versehentlichem Commit: sofortiger Key-Rotation + Incident-Assessment nach nDSG.

### Backend: Geldbeträge immer als `BigDecimal`

Alle CHF-Beträge — in Entities, Services, DTOs und Berechnungen — müssen `BigDecimal` verwenden. `double` und `float` sind verboten (ADR-9: Binäre Gleitkomma-Arithmetik kann CHF-Beträge nicht exakt darstellen und erzeugt Rundungsfehler in der Safe-to-Spend-Berechnung).

In der Datenbank: `DECIMAL(10,2)`. Beim Parsen von PDF-Beträgen: `replace("'", "")` vor `new BigDecimal(...)`.

### Backend: Timeouts + Fallback für externe Calls

Alle Calls zu Claude API und PDFBox müssen einen Timeout haben und bei Fehler auf `"Sonstiges"` fallen:

```java
// Claude API: Timeout setzen, bei AnthropicException → "Sonstiges"
// PDFBox: bei ParseException → Fehler an den Caller zurückgeben
```

Ein fehlgeschlagener Claude-Call darf nie den gesamten Import-Flow blockieren (Churn-Risiko #1).

### Testing: Frameworks

| Stufe       | Backend                                                                                 | Frontend        | Coverage-Ziel |
| ----------- | ---------------------------------------------------------------------------------------- | ---------------- | -------------- |
| Unit        | JUnit 5 + Mockito + AssertJ                                                               | Vitest            | Backend 80% (90%+ für `budget/`, `categorization/`); Frontend 70–75% |
| Integration | Spring Boot Test (`@DataJpaTest`, `@WebMvcTest`, `@SpringBootTest`) mit `jdbc:sqlite::memory:` | Angular TestBed | Keine eigene %-Zahl — jeder Endpoint und jede Migration mind. 1× getestet |
| E2E         | Playwright                                                                                 | Playwright        | Keine Coverage-Metrik — alle Must-Have User Stories (US-03/04/05/06): 1 Happy Path + 1 Fehlerpfad |

## Architecture

### C2 Container Diagram

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
│  transaction/  PDF-Upload → sync, Timeout+Fallback  │             │
│  categorization/ Lookup → CategorizationPort        │─────────────┘
│  budget/       Safe-to-Spend, Sparziele             │  HTTPS / Anthropic Java SDK
│  report/       KI-Monatsbericht (Sonnet 4, 1×/Monat)│  Haiku: Kategorisierung
│                                                     │  Sonnet: Monatsbericht
│  ImportJob-Status: GET /import/{jobId}/status       │
└──────────────────┬──────────────────────────────────┘
                   │ JDBC in-process · JPA/Hibernate
                   │ BigDecimal für alle CHF-Beträge
                   ▼
┌─────────────────────────────────────────────────────┐
│  Database  [SQLite 3.x + Flyway]                    │
│  users · transactions · fixed_costs ·               │
│  savings_goals · category_lookup · import_jobs      │
└─────────────────────────────────────────────────────┘
```

**Deployment:** Single JAR auf Render (Frankfurt/EU) — Angular-Build als statische Assets in `BOOT-INF/static/`.  
**Dev:** Angular Dev-Server `localhost:4200` + Spring Boot `localhost:8080`, CORS für `localhost:4200` konfiguriert.

### Container-Verantwortlichkeiten

| Container       | Technologie                          | Kernaufgabe                                                             |
| --------------- | ------------------------------------ | ----------------------------------------------------------------------- |
| Web SPA         | Angular 21, Signals, Reactive Forms  | UI: Onboarding, PDF-Upload, Dashboard, Korrekturen                      |
| API Application | Spring Boot 3.5, Java 25, Single JAR | Auth, PDF-Parsing, Kategorisierung, Berechnungen, KI-Bericht            |
| Database        | SQLite 3.x + Flyway                  | Persistenz: User, Transaktionen, Fixkosten, Lookup-Tabelle, Import-Jobs |

**Bewusst weggelassen:** Redis/Cache, Message Queue, CDN, Microservices, eigener KI-Worker — alles Overengineering für 3 Devs / 3 Monate.

### Architecture Decision Records

Vollständige ADRs: [docs/adr/README.md](docs/adr/README.md)

| ADR                                                    | Entscheid                                                                            | Abgelehnte Alternativen                                                             |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| [ADR-0](docs/adr/ADR-0-frontend-backend-separation.md) | SPA + REST API (Angular ↔ Spring Boot, JWT als httpOnly Cookie)                      | SSR (Next.js/Thymeleaf), Monolith mit JSP                                           |
| [ADR-1](docs/adr/ADR-1-java-spring-boot-backend.md)    | Java 25 + Spring Boot 3.5.x                                                          | Node.js/Express, Python/FastAPI, Go, .NET 8                                         |
| [ADR-2](docs/adr/ADR-2-angular-frontend.md)            | Angular 21.x (Standalone Components, Signals, Reactive Forms)                        | React, Vue 3, Svelte, Astro                                                         |
| [ADR-3](docs/adr/ADR-3-rest-vs-graphql.md)             | REST API + OpenAPI 3 (Springdoc)                                                     | GraphQL (Overkill, kein nativer File-Upload), gRPC                                  |
| [ADR-4](docs/adr/ADR-4-monolith-vs-microservices.md)   | Single Spring Boot JAR (Monolith)                                                    | Microservices/K8s (zu komplex), Serverless (JVM Cold-Start)                         |
| [ADR-5](docs/adr/ADR-5-sqlite-mvp-database.md)         | SQLite für MVP; Migration zu PostgreSQL möglich                                      | PostgreSQL from Day One (Overkill), MongoDB (nicht relational)                      |
| [ADR-6](docs/adr/ADR-6-hybrid-categorization.md)       | Hybrid: Lookup-Tabelle zuerst, Claude API nur für unbekannte Tx                      | LLM-Only ($750/Monat, zu teuer), Fine-tuned ML Model (kein Trainingsdata)           |
| [ADR-7](docs/adr/ADR-7-jwt-authentication.md)          | JWT HS256, bcrypt-Passwörter, httpOnly Cookie (XSS-sicher), CSRF via SameSite=Strict | Server-Side Sessions (DB-Schreibdruck), OAuth 2.0 (Overkill für MVP)                |
| [ADR-8](docs/adr/ADR-8-apache-pdfbox.md)               | Apache PDFBox 3.x (`Loader.loadPDF()`)                                               | iText 7 (AGPL-Lizenz!), Tabula-java (langsam, kein Text-Layer), pdfplumber (Python) |
| [ADR-9](docs/adr/ADR-9-bigdecimal-money.md)            | `BigDecimal` für alle CHF-Beträge, `DECIMAL(10,2)` in DB                             | `double`/`float` (Rundungsfehler!), `long` (Cent-Speicherung), Joda-Money           |
| [ADR-10](docs/adr/ADR-10-hosting-plattform.md)         | Render (Frankfurt/EU), SPA gebündelt im JAR, nDSG-Risiko akzeptiert                  | Exoscale/Nine.ch (CH, teurer), SPA auf CDN (zwei Pipelines)                         |

## Project Skills

| Skill | Befehl | Beschreibung |
|-------|--------|--------------|
| implement-issue | `/implement-issue <issue-number>` | GitHub Issue end-to-end umsetzen: Issue einlesen, Fragen klären, Plan präsentieren (mit Bestätigung), Branch erstellen, Code + Tests implementieren, lokalen Review durchführen (mit Bestätigung), PR öffnen. |
