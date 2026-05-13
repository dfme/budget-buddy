# Architecture Research

## Confidence: HIGH
Findings verified against Spring Boot 3.5 docs, Angular docs, Anthropic Java SDK docs, and Springdoc.

---

## Component Boundaries

```
Angular SPA
  AuthInterceptor (HttpInterceptorFn) → injects Bearer token on every request
  AuthGuard (CanActivateFn) → protects all non-auth routes
  Feature pages: lazy-loaded (loadComponent) to reduce initial bundle
  State: Angular Signals — computed() for derived values; no NgRx needed at MVP scale
  Zero business logic in components — all computation delegated to backend

Spring Boot 3.x
  SecurityFilterChain: CorsFilter → JwtAuthFilter → route authorization
  Controllers: AuthController, StatementController, TransactionController,
               FixedCostController, SavingsGoalController, DashboardController
  Services: PdfParsingService (PDFBox), CategorizationService (3-level chain),
            SafeToSpendService (formula), JwtService, UserService
  Data: Spring Data JPA repositories → SQLite via xerial jdbc + community dialect

Claude API
  Called via AnthropicClientAsync (CompletableFuture<Message>)
  Batched with CompletableFuture.allOf() for parallel classification
  10s timeout per call; Exception → fallback "Sonstiges"

SQLite file (budget_buddy.db)
  WAL mode enabled at startup
  IDENTITY strategy on all @Id fields
  Migration path: swap to PostgreSQL = driver + dialect change
```

---

## Key Architecture Decisions

### 1. PDF Processing: Synchronous for MVP, Claude calls parallelized
Use `AnthropicClientAsync` with `CompletableFuture.allOf()` to fan out all unknown-merchant LLM calls simultaneously. No queue needed for MVP — the 30s timeout is achievable with parallel calls. Use 202-Accepted + polling as fallback if timeout fires in practice.

### 2. Categorization Pipeline: 3-level priority chain
1. **Per-user correction rules** — `MerchantRule` table, `userId`-scoped
2. **Global merchant lookup table** — JSON bundled in JAR, loaded into `HashMap` at startup
3. **Claude API** → fallback `"Sonstiges"` on timeout/error

Normalize recipient strings (uppercase, trim, collapse whitespace) before every lookup.

### 3. Safe-to-Spend lives entirely in backend `SafeToSpendService`
`DashboardController` returns a DTO with the computed value, a `status` enum (`OK | NEGATIVE | NO_INCOME | LAST_WEEK`), and a breakdown. Angular is a rendering layer only — no formula duplication.

### 4. Auth: Stateless JWT with HttpInterceptorFn
- Custom `JwtAuthenticationFilter extends OncePerRequestFilter`
- `SessionCreationPolicy.STATELESS` + CSRF disabled
- Angular: functional `HttpInterceptorFn` (Angular 17+ preferred, not class-based) injects Bearer token
- Store token in `sessionStorage` (not `localStorage` for XSS reasons)
- Logout writes token JTI to an in-memory blocklist

### 5. SQLite + JPA Setup
- `GenerationType.IDENTITY` on all `@Id` fields
- `org.hibernate.community.dialect.SQLiteDialect`
- Enable WAL mode via startup PRAGMA
- Main risk: community-maintained dialect — validate schema generation in Phase 1

### 6. OpenAPI: Code-first with Springdoc
`springdoc-openapi-starter-webmvc-ui:2.8.17` — spec at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`. Contract-first (OpenAPI Generator) is over-engineering for a 2-3 person course project.

---

## Data Flow

### PDF Upload Critical Path
```
POST /api/statements/upload (multipart, max 10MB)
  → validate size / type / password / duplicate
  → PdfParsingService.parse()
  → CategorizationService.categorize() [parallel Claude calls via CompletableFuture.allOf]
  → TransactionRepository.saveAll()
  → 201 response
```

### Manual Recategorization (Learning Loop)
```
PATCH /api/transactions/{id}/category
  → update Transaction
  → MerchantRuleRepository.upsert(userId, normalizedRecipient, category)
  → next import: rule hit, no Claude call needed
```

### Safe-to-Spend
```
GET /api/dashboard
  → SafeToSpendService.calculate()
     queries: income + fixedCosts (normalized to monthly) + expensesSoFar (excluding Einkommen/Sparen)
  → DashboardDTO { safeToSpend, status, breakdown }
```

### Auth Flow
```
Login → { token }
  → Angular: store in sessionStorage
  → AuthInterceptor: inject on every request
  → JwtAuthFilter: validate
  → SecurityContextHolder.getAuthentication()
  → controller sees authenticated user
```

---

## Suggested Build Order

| Phase | Focus | Rationale |
|-------|-------|-----------|
| Phase 1 | Auth + project skeleton (Spring Boot, SQLite, Angular routing) | Hard dependency for every protected endpoint |
| Phase 2 | Fixed Costs + User Profile (income) + Onboarding wizard | Prerequisite for Safe-to-Spend formula inputs |
| Phase 3 | PDF Upload + Categorization (PDFBox + lookup + Claude) | Highest technical risk; validate early |
| Phase 4 | Dashboard + Safe-to-Spend (aggregates all prior data) | Core value proposition; builds on Phases 1–3 |
| Phase 5+ | Should features: Savings Goals, Month Navigation, Subscriptions, AI Report | Parallel development once Phase 4 stable |

---

## Anti-Patterns to Avoid

- **SafeToSpend in Angular** — formula in two places = bugs; keep backend-only
- **Sequential Claude API calls** — 30 unknown transactions × 1.5s = 45s timeout; must parallelize
- **JWT in localStorage** — XSS risk for financial app; use `sessionStorage` for MVP
- **`ddl-auto=create-drop` in shared envs** — drops tables on restart; use only in `@DataJpaTest` slices
- **Fat StatementController** — PDF parsing + Claude calls + DB writes in one class = untestable; delegate to services

---

## Open Questions

1. **Apache PDFBox vs alternatives** — validate with `tests/fixtures/statements/raiffeisen_kontoauszug.pdf` early; PostFinance format unknown
2. **Claude API prompt reliability** — ≥80% categorization accuracy depends on prompt quality; needs evaluation against labeled test set
3. **JJWT vs Nimbus for JWT** — JJWT simpler for MVP with local secret key
4. **Flyway vs `ddl-auto=update`** — Flyway adds migration history at cost of extra files; worth flagging in Phase 1
