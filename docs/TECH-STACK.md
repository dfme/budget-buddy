# Tech Stack, PDF-Spezifika, Auth, Gotchas

Referenzdokument zu [CLAUDE.md](../../CLAUDE.md). ADRs mit vollständiger Begründung und
verworfenen Alternativen: [docs/adr/README.md](adr/README.md).

## Backend

| Layer       | Technology                                     | Version             | Rationale                                               |
| ----------- | ---------------------------------------------- | ------------------- | ------------------------------------------------------- |
| Runtime     | Java                                           | 25 (LTS)            | Project-locked                                          |
| Framework   | Spring Boot                                    | 3.5.3               | Project-locked; latest 3.x stable                       |
| Build Tool  | Maven                                          | 3.9.x               | Project-locked; via Maven Wrapper (`mvnw`)              |
| Web layer   | Spring Web MVC                                 | (bundled)           | Synchronous; correct for blocking JDBC                  |
| Security    | Spring Security                                | 6.5.x               | Stateless JWT resource server pattern                   |
| ORM         | Spring Data JPA + Hibernate                    | (bundled)           | Repository pattern; dialect auto-detected, no extra dependency |
| DB          | PostgreSQL (Neon, Frankfurt/EU)                | 18                  | ADR-12; lokal via `docker compose up -d`                |
| JDBC driver | org.postgresql:postgresql                      | 42.7.x              | Spring-Boot-managed                                     |
| Migrations  | Flyway + `flyway-database-postgresql`          | 11.x                | Seit Flyway 10 liegt der DB-Support in eigenen Modulen  |
| JWT         | io.jsonwebtoken:jjwt-\*                        | 0.12.x              | HS256 signing, fluent builder API                       |
| API docs    | Springdoc OpenAPI                              | 2.8.17              | Spring Boot 3.5 compatible; zero-config Swagger UI      |
| AI          | com.anthropic:anthropic-java                   | 2.31.0              | Official Anthropic SDK                                  |
| PDF parsing | org.apache.pdfbox:pdfbox                       | 3.0.x               | Apache-licensed; correct for text-layer Swiss bank PDFs |

## Frontend

| Layer            | Technology                             | Version   | Rationale                                                                    |
| ---------------- | -------------------------------------- | --------- | ---------------------------------------------------------------------------- |
| Framework        | Angular                                | 21.x      | Project-locked; standalone components, Signals                               |
| Build Tool       | Angular CLI (`@angular/cli`)           | 21.x      | Standard; esbuild-basiert seit Angular 17+                                   |
| Package Manager  | npm                                    | (bundled) | Bundled mit Node.js; kein Mehraufwand gegenüber pnpm/yarn für MVP-Scope      |
| State            | Angular Signals + Services             | (bundled) | No NgRx needed for MVP scope                                                 |
| Forms            | Reactive Forms (FormGroup)             | (bundled) | Stable; Signal Forms still experimental                                      |
| HTTP auth        | `withCredentials: true` auf HttpClient | (bundled) | Cookie automatisch mitgesendet; kein Token-Interceptor im Client (ADR-7; Interceptor-Details: ADR-2) |
| Charts           | Chart.js + ng2-charts                  | 4.x / 10.x | Lightweight, Angular-native wrapper for pie/bar. ng2-charts 10 ist die gegen Angular 21 gebaute Linie (Peer `@angular/core >=21.0.0`); 8.x zielt auf Angular 19 |
| Change detection | OnPush everywhere                      | (bundled) | Required for Signals to work correctly                                       |
| Design-System    | Custom SCSS, Variante A «Klarheit»     | (n/a)     | Design-Entscheid FE-UI-01 / ADR-11; Baseline `design/variant-a/`. Komponenten-Unterbau (Custom SCSS vs. `@angular/cdk`) offen bis FE-UI-02 (#99) |

## AI/ML

- **Categorization model**: `claude-haiku-4-5` — fast, cheap, single-label output. Konfigurierbar via `anthropic.api.model`.
- **Monthly AI report model**: `claude-sonnet-5` — richer language, called once/user/month
- **Fallback**: catch `AnthropicException`, return `"Sonstiges"` — Claude unavailability must never block import flow
- **Circuit Breaker** (BE-CAT-02): Nach 3 fehlgeschlagenen Claude-Calls in Folge werden weitere Bündel 60s lang ohne API-Call als `Sonstiges` eingestuft. Der Fallback allein genügt nicht — ohne Breaker liefe jedes Bündel eines Imports in seinen eigenen Timeout. Seit der Bündelung (ADR-14) zählt der Breaker Bündel statt Einzeltransaktionen und greift damit früher: Ein Ausfall kostet höchstens 3 Timeouts statt 3 pro 20 Transaktionen.

## Swiss Bank PDF Specifics

- Columns: Buchungsdatum | Valuta | Text | Belastungen CHF | Gutschriften CHF | Saldo CHF
- Date format: `dd.MM.yyyy`
- Amount format: `1'234.56` (apostrophe thousands separator — requires `replace("'", "")` before `BigDecimal` parse)
- Text field can include multiline wrapping — use Saldo column as row anchor when splitting

## Auth: JWT (Stateless, HS256)

| Factor                  | JWT (stateless)                                                 | Session (server-side)                        |
| ----------------------- | --------------------------------------------------------------- | -------------------------------------------- |
| DB write pressure       | None — no session table                                         | Every login/request writes to sessions table |
| Angular SPA integration | httpOnly Cookie + `withCredentials: true`; kein Token-Interceptor im Client (Interceptor-Details: ADR-2) | Requires cookie + CORS + SameSite config |
| Spring Security support | JWT in Cookie; Spring Security liest Token aus Cookie           | Also supported but adds Spring Session dep   |
| Logout invalidation     | Backend setzt `Max-Age=0` → sofort invalidiert                  | Instant server-side invalidation             |
| MVP scope fit           | Excellent                                                       | Overengineered                               |

Begründung und verworfene Alternativen: [ADR-7](adr/ADR-7-jwt-authentication.md).

## PostgreSQL + Neon Gotchas (Critical)

- **Flyway braucht `flyway-database-postgresql`.** `flyway-core` allein kennt Postgres seit
  Flyway 10 nicht mehr und bricht beim Start mit *Unsupported Database* ab.
- **Neon nimmt nur TLS an** — `?sslmode=require` gehört an die JDBC-URL.
- **Benutzer und Passwort gehören nicht in die URL.** Eingebettet landen sie in jeder Logzeile,
  die die Datasource-URL ausgibt. Getrennt als `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` setzen.
- **Scale-to-Zero nach 5 Min** (Neon Free): der erste Request danach ist langsam, die Daten
  bleiben. Das ist seit dem Wechsel des Render-Web-Service auf den Starter-Plan (INFRA-24) der
  einzige Cold Start — Renders Spin-Down nach 15 Min entfällt.
- **Scale-to-Zero greift nur, wenn wirklich nichts die DB anfasst.** Das Monatskontingent von
  100 CU-h entspricht bei 0,25 CU rund 400 Stunden — keine 17 Tage Dauerbetrieb. Zwei Quellen für
  ungewollten Dauerzugriff, beide in INFRA-28 abgestellt: (1) `/actuator/health` enthält
  automatisch den `DataSourceHealthIndicator` (ein `SELECT 1` pro Aufruf) — deshalb pingt Render
  `/actuator/health/liveness`; (2) HikariCP ersetzt jede gehaltene Verbindung nach `maxLifetime`
  (30 Min) durch eine neue — deshalb `minimum-idle=0` in `application-prod.properties`.
- **`COLLATE NOCASE` gibt es nicht.** Case-insensitive Zuordnung liegt in der Anwendung:
  Patterns werden grossgeschrieben gespeichert, Vergleiche laufen über `upper()`.
- **Testcontainers braucht `-Dapi.version=1.44`** (in `pom.xml` gesetzt): das gebündelte
  docker-java handelt sonst API 1.32 aus, die Docker Engine 29 ablehnt — sichtbar als
  irreführendes *Could not find a valid Docker environment*.
- **Volume-Mount ist `/var/lib/postgresql`**, nicht `.../data`: das Postgres-18-Image legt die
  Daten in einem versionsbenannten Unterverzeichnis ab.

## What NOT to Use

| Technology                 | Why Not                                                                         |
| -------------------------- | ------------------------------------------------------------------------------- |
| Spring Boot 4              | Explicit project risk decision — milestone releases only                        |
| Gradle                     | Maven ist Build-Tool-Entscheid; Team-Konsistenz mit Standard-Spring-Boot-Setup  |
| Spring WebFlux             | JDBC is blocking; reactive wrapping adds complexity with no benefit             |
| iText 7                    | AGPL license — requires open-sourcing or commercial license                     |
| Tabula-java                | Designed for scanned PDFs; Swiss bank PDFs have a text layer                    |
| NgRx                       | Over-engineered for 2-3 person course project with simple state                 |
| D3.js                      | Steep learning curve, no Angular integration, overkill for pie + bar            |
| Highcharts                 | Commercial license for non-personal projects                                    |
| Redis + Spring Session     | Unnecessary infrastructure when using stateless JWT                             |
| H2 / SQLite (for testing)  | Dialect mismatch vs PostgreSQL in prod; use Testcontainers PostgreSQL instead   |
| PDFBox 2.x                 | Deprecated API (`PDDocument.load()`); use 3.x `Loader.loadPDF()` from the start |
| `double`/`float` for money | Binary floating point cannot represent CHF amounts exactly                      |
