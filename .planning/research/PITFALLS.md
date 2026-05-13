# Domain Pitfalls

**Domain:** Swiss Personal Finance Manager (PFM) — Angular + Spring Boot + PDF parsing + LLM categorization
**Researched:** 2026-05-13
**Sources:** Apache PDFBox docs, SQLite official WAL docs, Spring Security 6.5 docs, Spring Data JPA docs, Anthropic Java SDK docs, Angular docs
**Confidence:** HIGH

---

## Critical Pitfalls

Mistakes that cause rewrites or major incidents.

---

### Pitfall 1: PDF Column Extraction Merges Multi-Column Tables Into Garbage

**What goes wrong:** Swiss bank statements (UBS, Raiffeisen, PostFinance) use multi-column layouts. PDFBox's `PDFTextStripper` extracts text in PDF content-stream order — NOT visual reading order. Without `setSortByPosition(true)`, date tokens from column 1 concatenate with description tokens from column 2, producing strings like `01.04.2026 Coop 04.05.2026 SBB` where two transactions bleed into each other.

**Consequences:** Date parsing fails silently, amounts are misread, transactions split or merge. The 95% extraction accuracy (US 4) is immediately violated. Worst case: negative amounts parsed as positive (credit/debit column swap).

**Prevention:**
- Always call `stripper.setSortByPosition(true)` before extracting text
- Use `PDFTextStripperByArea` to define explicit bounding rectangles for each column per bank
- Build a regression fixture for each bank; extend `tools/generate_raiffeisen_statement.py` for edge cases
- Validate extraction output against known totals before persisting (sum of parsed debits must match balance delta)

**Warning signs:** Transactions with dates outside the statement period; description text containing numbers; implausibly large amount fields.

**Phase:** PDF Import (US 4) — cannot defer.

---

### Pitfall 2: Password-Protected PDFs Throw Uncaught Exceptions Instead of User Errors

**What goes wrong:** PDFBox throws `InvalidPasswordException` when loading password-protected PDFs. If uncaught, Spring Boot returns 500 with a stack trace instead of the required user-friendly error.

**Prevention:**
- After loading: check `document.getCurrentAccessPermission().canExtractContent()` before extraction
- Catch `InvalidPasswordException` at the service boundary, map to HTTP 422: "Das PDF ist passwortgeschützt — bitte entferne den Schutz vor dem Upload"
- Add file-size check (>10 MB → reject before opening)

**Phase:** PDF Import (US 4).

---

### Pitfall 3: LLM Categorization Returns Free-Text Outside the Allowed Taxonomy

**What goes wrong:** The Claude API returns "Lebensmittel & Haushalt" or "Grocery" or a full sentence instead of exactly "Lebensmittel". String equality checks fail, the fallback triggers, and transactions land in "Sonstiges".

**Consequences:** Categorization accuracy drops below the 80% threshold (US 5). Users see most transactions as "Sonstiges", lose trust, and abandon (Churn-Falle risk).

**Prevention:**
- Use the Anthropic Java SDK's `outputConfig(CategoryResult.class)` structured output feature (GA, no beta header required)
- Define `CategoryResult` with a single `category` field with `@JsonPropertyDescription` listing all 13 valid values
- Keep `maxTokens` at 32–64 for categorization calls
- Build an evaluation harness: 200 manually labelled transactions, fail build if accuracy < 80%

**Warning signs:** "Sonstiges" > 30% of staging data. API responses containing > 5 tokens.

**Phase:** Categorization (US 5) — evaluation harness must exist before launch.

---

### Pitfall 4: Floating-Point Arithmetic Corrupts CHF Amounts

**What goes wrong:** Storing amounts as `double` or `float` produces rounding errors (`0.1 + 0.2 = 0.30000000000000004`). Category totals deviate from true sums. Safe-to-spend shows "200.0000000001 CHF/Woche".

**Consequences:** US 5 "auf den Rappen genau" and US 6 safe-to-spend calculation both violated. CHF has no sub-Rappen denomination — any fractional error is a bug.

**Prevention:**
- Store all monetary amounts as `INTEGER` (Rappen = CHF × 100) in SQLite
- Use `BigDecimal` for all arithmetic in Java — never cast to `double` mid-calculation
- In Angular, use `CurrencyPipe` with locale `de-CH` and currency code `CHF`
- Code review rule: any `double`/`float` in a money model class is a blocking issue

**Phase:** Data model — establish before writing any service code. Fixing retroactively requires DB migration.

---

### Pitfall 5: SQLite Write Contention Causes SQLITE_BUSY Under Spring Boot's Default HikariCP Pool

**What goes wrong:** Spring Boot configures HikariCP with pool size 10. SQLite allows only one writer at a time even in WAL mode. Two simultaneous HTTP requests compete for the write lock → `SQLITE_BUSY` → 500 error.

**Prevention:**
- Set `spring.datasource.hikari.maximum-pool-size=1` — serialises all DB access, eliminating write contention
- Enable WAL mode on startup: `PRAGMA journal_mode=WAL` and `PRAGMA busy_timeout=5000`
- Run PDF parsing entirely outside the DB transaction (extract in memory first, then persist in one write transaction)

**Warning signs:** `org.sqlite.SQLiteException: SQLITE_BUSY` in logs. Tests passing individually but failing when run in parallel.

**Phase:** Initial infrastructure — set pool-size=1 before writing any service code.

---

### Pitfall 6: nDSG Right-to-Deletion Leaves Orphaned Data in JPA Cascade Chains

**What goes wrong:** Account deletion (US 2) deletes the `User` entity but child entities — `Transaction`, `FixedCost`, `SavingsGoal`, `MerchantLookup`, `MonthlyReport` — survive because cascade is misconfigured OR bulk `@Query` delete bypasses JPA lifecycle.

**Consequences:** nDSG compliance breach. Database admin finds records with deleted userId. Security audit will flag this.

**Prevention:**
- Annotate all `@OneToMany` collections with `cascade = CascadeType.ALL, orphanRemoval = true`
- For deletion, use derived delete queries (`deleteByUserId`) or explicit `@Modifying @Query` per table in correct FK order
- Integration test: create user + all child entities → delete account → assert zero rows remain for that userId across all tables

**Phase:** Authentication & Data Privacy (US 1, US 2) — must be verified before any production data is stored.

---

### Pitfall 7: Angular + Spring Boot CORS Breaks With Credentials and Wildcards

**What goes wrong:**
- `allowedOrigins("*")` + `allowCredentials(true)` → rejected by all browsers (spec violation)
- Spring Security intercepts OPTIONS preflight before CORS filter → returns 401 → Angular logs "HTTP 0"

**Prevention:**
```java
configuration.setAllowedOrigins(List.of("http://localhost:4200", "https://app.budgetbuddy.ch"));
configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
configuration.setAllowCredentials(true);
```
- Call `http.cors(withDefaults())` before `.authorizeHttpRequests()` in SecurityFilterChain
- Set `SessionCreationPolicy.STATELESS` and disable CSRF for stateless JWT APIs

**Phase:** Authentication (US 1) — set up correctly from day one.

---

### Pitfall 8: Safe-to-Spend Edge Cases Produce NaN, Infinity, or Absurd Values

**What goes wrong:**
- `RemainingWeeks = 0` when computed on the last day of the month
- `Income = 0` (not configured) — null check bypassed → large negative number
- Quarterly/yearly cost normalisation using day-count introduces fractional Rappen errors
- Timezone mismatch: server in UTC, user in CET/CEST — Swiss user at 23:30 CET on the 31st gets next-month calculation

**Prevention:**
- Use `remainingWeeks = Math.max(1, Math.ceilDiv(daysRemainingInMonth, 7))`
- Always null-check income before computing; return sentinel for UI prompt, never compute
- Use `ZoneId.of("Europe/Zurich")` for all date/time calculations server-side
- Unit test explicitly: zero income, negative result, last day of month, February 28/29, quarterly normalisation

**Phase:** Safe-to-Spend (US 6) — edge case tests must exist before shipping.

---

### Pitfall 9: Onboarding Wizard Completion State Gets Out of Sync With Backend

**What goes wrong:** Wizard completion flag stored only in Angular `localStorage` → clears on cache clear or new device → wizard re-appears for users who already completed it.

**Prevention:**
- Store `onboardingCompleted: boolean` on the `User` entity server-side
- Angular `CanActivateFn` guard reads from API response, not localStorage
- Mark-complete endpoint (`POST /api/users/me/onboarding`) must be idempotent

**Phase:** Onboarding (US 3).

---

### Pitfall 10: Duplicate PDF Detection Uses File Hash and Misses Re-Exports

**What goes wrong:** Banks allow re-downloading the same statement. Each download can produce a different binary (different PDF creation timestamp). Byte-hash comparison catches nothing → transactions imported twice → category amounts double.

**Prevention:**
- Detect duplicates by comparing parsed (date, amount, recipient) tuples against existing transactions for same user and date range — not by hashing the PDF binary
- Store a `(userId, statementPeriodStart, statementPeriodEnd, bankIdentifier)` record per import as a fast pre-check
- Define "duplicate" as: > 80% of parsed transactions already exist for that user in the date range

**Phase:** PDF Import (US 4).

---

## Moderate Pitfalls

### Pitfall 11: LLM API Calls in the Hot Path Block HTTP Response

**What goes wrong:** Synchronous Claude calls during PDF import (10–15 calls × 1–3 seconds) exceed the 30-second timeout. Anthropic SDK default timeout is 10 minutes — a hung call holds the HTTP thread.

**Prevention:**
- Configure `AnthropicOkHttpClient` with `timeout(Duration.ofSeconds(8))` for categorization calls
- Run Claude API calls in a bounded thread pool separate from Spring's HTTP pool (`CompletableFuture` with fixed executor)
- Batch: collect all unique unknown merchant names, make parallel API calls, apply results — never call per-transaction
- Keep `maxRetries(2)` (SDK default)

**Phase:** PDF Import + Categorization.

---

### Pitfall 12: Angular Route Guards Are Client-Side Only and Can Be Bypassed

**What goes wrong:** Guards based on `localStorage` token existence (not validity) pass with expired tokens. Users navigate directly to protected routes.

**Prevention:**
- Route guards call `GET /api/users/me` on app bootstrap; cache result in a Signal
- Every protected API endpoint independently validates JWT — Angular guards are UX-only, not security boundaries
- Set JWT expiry to 24 hours for MVP

**Phase:** Authentication (US 1).

---

### Pitfall 13: Merchant Lookup Table Is Global Instead of User-Scoped

**What goes wrong:** Shared global lookup → User A's correction overwrites category for User B. Also persists after account deletion (nDSG violation).

**Prevention:**
- Schema: `(userId, merchantNormalized, category)` with composite PK
- Include in cascade delete chain (see Pitfall 6)
- Normalise merchant names: lowercase, strip punctuation and branch-code suffixes ("COOP-3412" → "coop")

**Phase:** Categorization (US 5).

---

## Minor Pitfalls

### Pitfall 14: PDF Fixture Generator Produces Non-Representative Test Data

Generated PDFs have idealised layouts and ASCII-only characters. Real Swiss bank statements contain umlauts, "Kartenzahlung"/"TWINT" prefixes, multi-line descriptions.

**Prevention:** Collect real anonymised statements from UBS, Raiffeisen, and PostFinance before writing parsers. Use generated fixtures only for regression once the parser is validated on real data.

**Phase:** PDF Import — collect fixtures before writing the parser.

---

### Pitfall 15: "Sonstiges" Hides Categorization Failures in Production

"Sonstiges" is both a legitimate category and the fallback for failures. 40% "Sonstiges" is indistinguishable from a silently failing categorizer.

**Prevention:** Track categorization source separately: `(auto-lookup, auto-llm, user-corrected, fallback-sonstiges)`. Log a warning if `fallback-sonstiges` exceeds 20% of any import batch.

**Phase:** Categorization (US 5).

---

## Phase-Specific Warning Summary

| Phase Topic | Likely Pitfall | Mitigation |
|---|---|---|
| Initial infrastructure | SQLite pool size 10 causes SQLITE_BUSY | Set `maximum-pool-size=1` and `PRAGMA busy_timeout=5000` first |
| Data model | Monetary amounts as double/float | INTEGER (Rappen) in DB, BigDecimal in Java |
| Authentication | CORS wildcard + credentials rejected | Explicit origins list, OPTIONS before auth filter |
| Authentication | nDSG deletion misses child entities | Cascade + orphanRemoval + integration test |
| Onboarding | Wizard state in localStorage desyncs | Server-side `onboardingCompleted` flag |
| PDF Import | Text extraction column ordering garbage | `setSortByPosition(true)` + `PDFTextStripperByArea` per bank |
| PDF Import | Password-protected PDF throws 500 | Catch `InvalidPasswordException`, map to HTTP 422 |
| PDF Import | Re-export binary differs, hash detection misses duplicates | Tuple-based duplicate detection |
| Categorization | LLM returns free text outside taxonomy | Structured output with `outputConfig(CategoryResult.class)` |
| Categorization | Sync LLM calls exceed 30s timeout | Async bounded pool, batch unknowns, 8s SDK timeout |
| Categorization | Global lookup table violates nDSG | Per-user scope, include in deletion cascade |
| Safe-to-Spend | Division by zero on last day of month | `Math.max(1, remainingWeeks)` |
| Safe-to-Spend | Timezone mismatch (UTC vs CET/CEST) | `ZoneId.of("Europe/Zurich")` server-side |
| Safe-to-Spend | Unconfigured income produces NaN | Null-check before formula; sentinel for UI |
