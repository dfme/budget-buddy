# BudgetBuddy

## What This Is

BudgetBuddy is a web app for students and young professionals living in Switzerland that ingests bank statement PDFs, automatically categorizes transactions, and displays a weekly "Safe-to-Spend" budget — so users always know how much they can spend without worry. Built with Angular (frontend), Spring Boot 3.x (backend), SQLite (database), and Claude API (AI categorization + monthly reports).

## Core Value

A weekly Safe-to-Spend number users can trust — calculated from real transaction data, not manual entry.

## Requirements

### Validated

(None yet — ship to validate)

### Active

#### Authentication & Privacy
- [ ] User can create account with email + password (bcrypt hash, no plaintext)
- [ ] User can log in and stay logged in across sessions
- [ ] User can log out (session invalidated server-side)
- [ ] User is shown data privacy consent before any data is stored (nDSG)
- [ ] User can delete their account and all associated data

#### Onboarding
- [ ] User is prompted to enter fixed monthly costs on first login (Miete, Krankenkasse, etc.)
- [ ] Onboarding wizard requires at least one entry or explicit "Keine Fixkosten" confirmation
- [ ] Fixed costs support monthly, quarterly, and yearly intervals (normalized to monthly)

#### PDF Import
- [ ] User can upload a bank statement PDF (UBS, Raiffeisen, PostFinance)
- [ ] System extracts date, amount, and recipient from ≥95% of transactions
- [ ] Upload is rejected with clear error: unsupported format, password-protected, >10 MB, duplicate
- [ ] Duplicate detection warns user before re-importing same period

#### Categorization
- [ ] Every transaction is assigned exactly one of 13 categories (Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit, Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges)
- [ ] Hybrid approach: lookup table first (≥70%), Claude API for unknowns, fallback to "Sonstiges"
- [ ] User can manually correct a category; correction is persisted and applied to future transactions from same recipient
- [ ] Category view shows per-category: sum in CHF, count, percentage of monthly total

#### Safe-to-Spend
- [ ] Dashboard shows Safe-to-Spend = (Income − Fixed Costs − Expenses So Far) ÷ Remaining Weeks
- [ ] Negative Safe-to-Spend shows red banner: "Achtung: Dein Budget für diese Woche ist überzogen"
- [ ] No income configured → shows prompt instead of Safe-to-Spend value
- [ ] Last week of month uses divisor of 1 minimum (no division-by-zero)

### Out of Scope

- OpenBanking / direct bank connection — Post-MVP; complexity and Swiss bank API availability
- B2B / advisor tool — Not target audience; no multi-account management per user
- International rollout — Swiss-only (CHF, Swiss banks, nDSG compliance focus)
- Email verification, password reset via email, rate limiting — Post-MVP; reduces auth complexity for course timeline
- PLZ validation against Swiss postal list — Post-MVP
- Depot / Festgeld accounts — Zahlungskonten only per project decision
- Spring Boot 4 — Removed from stack as risk factor

## Context

- **Course project**: BFH CAS Application Development with AI (ADAI) 2026, supervised by Ilja Rasin
- **Team**: Small (2-3 people), feature-based split — each person owns end-to-end features
- **Prototypes**: Two HTML mockups exist (`prototypes/prototype_jason.html`, `prototypes/prototype_sergio.html`) — internal only, not yet user-tested
- **Test fixtures**: Raiffeisen PDF fixture at `tests/fixtures/statements/raiffeisen_kontoauszug.pdf`
- **PDF generation tool**: `tools/generate_raiffeisen_statement.py` for generating test data
- **Personas**: Lara (22, student, Bern) — needs clarity mid-month; Marc (25, junior professional, Zürich) — wants to build first savings, concerned about data privacy

## Constraints

- **Tech Stack**: Angular (frontend), Java 25 + Spring Boot 3.x (backend), SQLite (MVP DB), Claude API via Anthropic Java SDK, OpenAPI 3 / Springdoc — locked in
- **Database**: SQLite for MVP; migration path to PostgreSQL exists if concurrent writes become bottleneck
- **Geography**: Switzerland only — CHF, Swiss banks (UBS, Raiffeisen, PostFinance), nDSG
- **Privacy**: Sensitive financial data — security is existential; compliance with Swiss nDSG required (including right to deletion)
- **Timeline**: No hard deadline; MVP-first mentality — validate core safe-to-spend concept, then iterate

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Hybrid categorization (lookup table + Claude API) | Lookup covers ~70-80%, LLM handles unknowns — reduces API costs, deterministic for known merchants | — Pending |
| SQLite for MVP | No separate DB server needed; sufficient for single-user MVP | — Pending |
| Angular over React/Vue | Component-based, Two-Way-Binding good for forms, team familiarity | — Pending |
| Auth minimal for MVP | Email verification / rate limiting deferred — reduces scope for course timeline | — Pending |
| No Spring Boot 4 | Identified as risk factor — Spring Boot 3.x is stable and well-supported | — Pending |
| Fallback category "Sonstiges" | When Claude API is unavailable or uncertain, safe default | — Pending |
| User corrections extend lookup table | Manual corrections train the system without model retraining | — Pending |

## Risks

1. **Churn-Falle** — manual PDF import + first-run categorization may cause user drop-off after initial "aha" moment; onboarding must be frictionless
2. **Liability & Compliance** — transaction data is a hacking target; a breach is existential; nDSG right-to-deletion must be implemented correctly
3. **Feature gap** — UBS/Raiffeisen/PostFinance building own PFM tools; business case could disappear

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-13 after initialization*
