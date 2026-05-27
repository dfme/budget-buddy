# Research Summary — BudgetBuddy

**Synthesized from:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md
**Date:** 2026-05-13

---

## Stack Decisions (Locked In)

| Layer | Choice | Key Rationale |
|-------|--------|---------------|
| Backend | Java 21 + Spring Boot 3.5.3 | Project-locked |
| Frontend | Angular 21.x (standalone + Signals) | Project-locked; use OnPush everywhere |
| Database | SQLite (pool-size=1, WAL mode) | MVP-appropriate; **must set `maximum-pool-size=1` before any code** |
| ORM | Spring Data JPA + `hibernate-community-dialects` (SQLiteDialect) | Requires explicit dialect configuration |
| PDF Parsing | Apache PDFBox 3.0.x | Text-layer Swiss PDFs — NOT Tabula-java; use `Loader.loadPDF()`, not deprecated 2.x API |
| AI | Anthropic Java SDK 2.31.0 | Use `claude-haiku-3-5` for categorization, `claude-sonnet-4` for monthly reports |
| Auth | JWT (JJWT 0.12.x, HS256, stateless) | Angular `sessionStorage`; functional `HttpInterceptorFn` |
| Money | `INTEGER` (Rappen = CHF × 100) + `BigDecimal` in Java | **Never `double`/`float` — any decimal error violates "auf den Rappen genau"** |

---

## Table Stakes (Must Ship)

Features users expect — missing = product feels broken:
- Transaction list with date, amount, recipient
- Auto-categorization (13 categories) with manual correction
- Monthly spending summary per category (sum + count + %)
- Visual chart of category breakdown (simple bar/pie)
- Secure login / session management
- Data privacy consent + account deletion (nDSG)
- Import flow with clear feedback (success, error, duplicate warning)
- Mobile-responsive layout

---

## Core Differentiators

What makes BudgetBuddy stand out — especially in the Swiss market:

1. **Weekly Safe-to-Spend number** — no Swiss-market competitor gives a single actionable weekly number for users who don't switch banks
2. **PDF import for Swiss banks** — works with UBS, Raiffeisen, PostFinance (no bank-switch required)
3. **Fixed costs wizard with Swiss examples** — Krankenkasse, Miete, Handyabo captured upfront; quarterly/yearly normalisation
4. **Recipient-learning categorization** — corrections persist and apply to future imports; creates switching cost

---

## Recommended Build Order

The critical dependency chain is: **Auth → Fixed Costs → PDF + Categorization → Dashboard**

| Phase | Focus | Why This Order |
|-------|-------|----------------|
| 1 | Project skeleton + Auth (Spring Boot + SQLite + Angular routing + JWT) | Hard dependency for every protected route and endpoint |
| 2 | Data privacy consent + account deletion (nDSG gate) | Must be verified before real data is stored; cascade deletion integration test required |
| 3 | Fixed costs wizard + income setting (onboarding) | Prerequisite for Safe-to-Spend formula inputs |
| 4 | PDF upload + parsing (PDFBox, per-bank column maps) | Highest technical risk; validate early with real fixtures |
| 5 | Categorization engine (lookup table → Claude API → fallback) | Builds on PDF import; evaluation harness (200 labelled transactions, ≥80% accuracy) required before ship |
| 6 | Dashboard + Safe-to-Spend calculation | Assembles all prior phases; this is the core value delivery |
| 7+ | Should features: savings goals, month navigation, drill-down, comparison, recurring detection, AI report | Parallel development possible once Phase 6 is stable |

---

## Top 5 Pitfalls (with Phase Impact)

| # | Pitfall | Phase | Prevention |
|---|---------|-------|------------|
| 1 | **SQLite pool-size=10 causes SQLITE_BUSY** | All phases | Set `maximum-pool-size=1` and `PRAGMA busy_timeout=5000` before any service code |
| 2 | **PDF column extraction produces garbage** | Phase 4 | `setSortByPosition(true)` + `PDFTextStripperByArea` per bank; regression fixture per bank |
| 3 | **LLM categorization returns free text outside taxonomy** | Phase 5 | Use `outputConfig(CategoryResult.class)` structured output; evaluation harness with 200 labelled transactions |
| 4 | **nDSG deletion leaves orphaned child entities** | Phase 2 | `cascade = ALL + orphanRemoval = true`; integration test asserting zero rows for deleted userId across all tables |
| 5 | **`double`/`float` for CHF amounts** | Phase 1 | INTEGER (Rappen) in DB, BigDecimal in Java — establish in data model before writing any service code |

---

## Key Open Questions (Resolve Before These Phases)

| Question | Must Resolve Before |
|----------|---------------------|
| UBS and PostFinance PDF layouts (column positions) — need real anonymised fixtures | Phase 4 (PDF Import) |
| JWT storage: `sessionStorage` or `HttpOnly` cookie? (affects CORS `allowCredentials`) | Phase 1 (Auth) |
| Flyway vs `ddl-auto=update` for schema management | Phase 1 (Infrastructure) |
| Merchant lookup table storage: SQLite table (recommended) vs bundled JSON | Phase 5 (Categorization) |
| Deployment target (Docker on VPS, etc.) — affects SQLite file path config | Phase 1 (Infrastructure) |
| Neon current feature set — if they added Safe-to-Spend-equivalent, differentiation argument weakens | Pre-launch |

---

## Swiss Market Notes

- **PostFinance is the dominant first account for young Swiss adults** — prioritise PostFinance parser first (not Raiffeisen, despite having that fixture)
- **Krankenkasse is the most important Swiss fixed cost** — must be a named example in the onboarding wizard
- **Marc's data-privacy skepticism is representative, not an outlier** — consent screen and "your data is not sold" messaging are disproportionately important
- **YNAB's envelope-budgeting model is wrong for this demographic** — Lara's "Aufschieberitis" persona confirms the single-number, passive-entry approach is correct
- **Mint's shutdown (Jan 2024) validated the market gap** — no well-known free PFM for passive trackers exists in the Swiss market
