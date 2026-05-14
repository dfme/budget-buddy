# Project State

## Current Phase
Phase 1 — Not Started

## Project Reference
See: .planning/PROJECT.md

**Core value:** A weekly Safe-to-Spend number users can trust
**Current focus:** Phase 1

---

## Current Position

| Field | Value |
|-------|-------|
| Phase | 1 — Project Skeleton + Authentication |
| Plan | None started |
| Status | Not started |
| Progress | ░░░░░░░░░░ 0% (0/10 phases) |

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Phases total | 10 |
| Phases complete | 0 |
| Requirements total | 46 |
| Requirements complete | 0 |
| Plans written | 0 |
| Plans complete | 0 |

---

## Accumulated Context

### Key Decisions
- SQLite `maximum-pool-size=1` must be set in Phase 1 before any other service code
- All monetary amounts stored as INTEGER (Rappen = CHF × 100); BigDecimal in Java — never double/float
- JWT stored in Angular `sessionStorage`; stateless backend (JJWT 0.12.x, HS256)
- PDF parsing: PDFBox 3.0.x with `setSortByPosition(true)` + `PDFTextStripperByArea` per bank
- Prioritise PostFinance parser first (dominant first account for young Swiss users), then Raiffeisen, then UBS
- Claude API categorization uses `outputConfig(CategoryResult.class)` structured output to prevent free-text responses outside taxonomy
- nDSG: `cascade = ALL + orphanRemoval = true` on all user-owned entities; integration test asserting zero rows after deletion required before Phase 2 is done
- Categorization pipeline order: per-user correction rules → global merchant lookup → Claude API → "Sonstiges" fallback
- Use `claude-haiku-3-5` for categorization; `claude-sonnet-4` for monthly reports (v2)

### Open Questions
- JWT storage: `sessionStorage` (current plan) or `HttpOnly` cookie? Affects CORS `allowCredentials`
- Flyway vs `ddl-auto=update` for schema management — resolve before Phase 1 plan
- UBS and PostFinance PDF column layouts — need real anonymised fixtures before Phase 4
- Merchant lookup table storage: SQLite table (recommended) vs bundled JSON — resolve before Phase 5
- Deployment target (Docker on VPS, etc.) — affects SQLite file path config

### Blockers
None

### Todos
- Resolve JWT storage decision before Phase 1 plan is written
- Obtain anonymised UBS and PostFinance PDF samples before Phase 4
- Build 200-transaction labelled test set before Phase 5 evaluation harness

---

## Session Continuity

**Last session:** 2026-05-14 — Roadmap created (10 phases, 46 requirements mapped)
**Next action:** `/gsd-plan-phase 1` — plan Phase 1: Project Skeleton + Authentication
