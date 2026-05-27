# Stack Research

## Confidence: HIGH on framework/library choices, MEDIUM on a few version patches

---

## Recommended Stack

### Backend

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Runtime | Java | 21 (LTS) | Project-locked |
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

---

## Swiss Bank PDF Specifics

**All three target banks (UBS, Raiffeisen, PostFinance) produce text-layer PDFs, not scans.** PDFBox `PDFTextStripper` works — Tabula-java (for scanned tables) is wrong for this use case.

**Raiffeisen layout** (verified from fixture + generator script):
- Columns: Buchungsdatum | Valuta | Text | Belastungen CHF | Gutschriften CHF | Saldo CHF
- Date format: `dd.MM.yyyy`
- Amount format: `1'234.56` (apostrophe thousands separator — requires `replace("'", "")` before `BigDecimal` parse)
- Text field can include multiline wrapping — use Saldo column as row anchor when splitting

**UBS and PostFinance layouts**: unverified — requires real PDFs before writing bank-specific parsers.

**Password-protected PDF detection** (US-4 acceptance criterion):
```java
try (PDDocument doc = Loader.loadPDF(bytes, "")) {
    // proceeds normally
} catch (InvalidPasswordException e) {
    throw new PdfPasswordProtectedException("Das PDF ist passwortgeschützt");
}
```

**ALWAYS use `BigDecimal` for CHF amounts** — `double` cannot represent 0.10 CHF exactly. Applies end-to-end: entity fields, DTOs, calculation logic.

---

## Auth Decision: JWT (Stateless, HS256)

| Factor | JWT (stateless) | Session (server-side) |
|--------|----------------|----------------------|
| SQLite write pressure | None — no session table | Every login/request writes to sessions table |
| Angular SPA integration | Clean Bearer header | Requires cookie + CORS + SameSite config |
| Spring Security support | First-class `oauth2ResourceServer().jwt()` | Also supported but adds Spring Session dep |
| Logout invalidation | Client deletes token (MVP acceptable) | Instant server-side invalidation |
| MVP scope fit | Excellent | Overengineered |

Logout caveat for MVP: stateless JWT cannot be invalidated server-side without a blocklist. Keep token TTL short (15–60 minutes). Refresh token pattern is post-MVP scope.

---

## SQLite + Spring Boot Gotchas (Critical)

1. **No auto-detected dialect** — must set `spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect` explicitly
2. **Single writer** — set `spring.datasource.hikari.maximum-pool-size=1` OR enable WAL mode via `connection-init-sql: "PRAGMA journal_mode=WAL;"` (allows concurrent reads with one writer)
3. **Foreign keys off by default** — enable with `PRAGMA foreign_keys=ON;` in connection init SQL
4. **Limited ALTER TABLE** — SQLite cannot drop columns or change types; Flyway migrations that alter columns must recreate the table (copy-rename pattern)
5. **Never use `ddl-auto=create-drop`** — use `validate` and let Flyway manage schema

---

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

---

## Open Questions

1. **UBS and PostFinance PDF layouts** — require real PDF samples before writing bank-specific parsers; ≥95% extraction accuracy is a real quality bar
2. **JJWT exact patch version** — 0.12.x API confirmed; verify latest patch on Maven Central
3. **Angular 19 vs 20** — if Angular 20 recently reached stable, prefer it; if still RC, pin to 19.x
4. **Flyway Community SQLite DDL completeness** — keep migrations additive-only for safety
5. **Merchant lookup table storage** — should be a `merchant_category_override` table in SQLite (keyed by normalized merchant name), not a flat file

---

## Sources

| Claim | Confidence |
|-------|------------|
| Anthropic Java SDK v2.31.0 | HIGH — GitHub releases |
| Spring Boot 3.5 / Spring Security 6.5 | HIGH — official Spring docs |
| Springdoc 2.8.17 Spring Boot 3.5 compat | HIGH — springdoc.org |
| PDFBox text extraction + password detection | HIGH — Apache PDFBox repo |
| JJWT 0.12.x API | MEDIUM — version patch unverified |
| Raiffeisen PDF layout | HIGH — direct fixture inspection |
