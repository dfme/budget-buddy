# Feature Landscape: Swiss PFM for Students and Young Professionals

**Domain:** Personal Finance Management (PFM) — Switzerland, 18-30 demographic
**Researched:** 2026-05-13
**Confidence note:** WebSearch and WebFetch were unavailable during research. All findings are drawn from training knowledge of YNAB, Mint (shutdown Jan 2024), Revolut, Neon, and Swiss banking. Confidence ratings reflect source quality — mature, well-documented products are HIGH confidence; Swiss-specific behavioral claims are MEDIUM.

---

## Competitive Landscape Summary

| Product | Model | Target | PFM Depth | Swiss Fit |
|---------|-------|--------|-----------|-----------|
| YNAB | SaaS subscription (~$15/mo) | Budgeters with income/expense discipline | Very deep (envelope budgeting, goals, reports) | No (USD-centric, no CH banks) |
| Mint | Free, ad-supported (shut down Jan 2024) | Passive trackers | Medium (auto-categorize, credit score, net worth) | No (US only) |
| Revolut | Neobank + PFM overlay | Young mobile-first users | Medium (spending analytics, vaults, subscription tracker) | Partial (operates in CH, CHF supported, not CH-banking-native) |
| Neon | Swiss neobank, free tier | Swiss students/youth | Light PFM built into banking | Yes (Swiss IBAN, CHF, Swiss regulations) |
| PostFinance / UBS / Raiffeisen e-banking | Traditional banking app | Existing customers | Basic (category charts, search) | Yes (but closed ecosystem) |
| Toshl Finance | Subscription SaaS | Manual budget tracking | Medium | Multi-currency OK, not CH-specific |

**Key gap BudgetBuddy fills:** None of the above combines (1) works with existing Swiss bank accounts via PDF import, (2) gives a single actionable weekly number, and (3) is not a bank itself. Neon requires switching banks. Traditional e-banking is passive and reactive.

---

## Table Stakes

Features users expect in a PFM app. Missing = product feels incomplete or broken.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Transaction list with date, amount, recipient | Foundation of every PFM — users verify data | Low | Must show raw imported data |
| Spending categories (auto-assigned) | Users expect "smart" sorting without manual work — Mint normalized this expectation | Medium | 13-category taxonomy already defined in project |
| Manual category correction | Every auto-categorizer makes mistakes; correction path is essential for trust | Low | Correction must persist per recipient |
| Monthly spending summary per category | Users want "where did my money go" answer | Low | Sum + count + % of total per category |
| Visual breakdown (chart/graph) | Bar or pie chart of category spend — YNAB, Mint, Revolut all have this | Medium | Even a simple horizontal bar chart satisfies expectation |
| Import flow with clear feedback | User needs to know import succeeded, how many transactions were found, what period | Low | Success state, error state, duplicate warning |
| Secure login / session management | Financial data = users expect basic auth | Medium | Already planned: bcrypt, server-side session invalidation |
| Data privacy / consent screen | Swiss nDSG requires informed consent; users are increasingly aware | Low | Already planned |
| Right to account deletion | nDSG mandates this; savvy users (Marc persona) will check | Low | Already planned |
| Mobile-usable layout | 18-30 demographic uses mobile heavily — not necessarily a native app, but responsive | Medium | Angular responsive layout; not native app |

---

## Differentiators

Features that distinguish BudgetBuddy from passive bank apps and generic PFMs. Not universally expected, but high perceived value for the Swiss student/young professional demographic.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Weekly "Safe-to-Spend" number | Translates raw spending data into one actionable answer: "Can I go out tonight?" — no other Swiss-market product does this with a weekly cadence for existing-bank users | Medium | Core differentiator; formula must handle edge cases (last week, no income, negative) |
| Fixed costs onboarding wizard | Krankenkasse, Miete, Handyabo — Swiss fixed costs are predictable and high; capturing them upfront makes Safe-to-Spend realistic from day 1 | Medium | Quarterly/yearly normalization is Swiss-relevant (e.g., KK can be quarterly) |
| PDF import for Swiss banks | Neon/Revolut require switching banks; BudgetBuddy works with the bank the user already has | High | PDF parsing per bank format is technically the hardest part; UBS / Raiffeisen / PostFinance formats differ |
| Recipient-learning categorization | Manual corrections teach the system — future imports of same recipient auto-apply the correction | Low | Feels like it "gets smarter"; creates switching cost |
| Recurring payment detector | Identifies Spotify, Netflix, Krankenkasse, gym memberships without user doing anything — Marc persona discovers "Kleinvieh" costs | High | Requires 2+ months of data; ±2% tolerance per project plan |
| AI monthly report with Swiss-context spending suggestions | Concrete CHF amounts and category-specific tips (not generic "spend less on coffee") — Claude API already in stack | High | Quality depends on prompt engineering; must reference actual user data |
| Month-over-month comparison with anomaly highlighting | Shows "Restaurant +28% vs last month" — turns abstract numbers into behavioral insight | Medium | Requires 2 full months of data; already a user story |
| Savings goal tracker with progress bar | Marc's explicit goal: first 1000 CHF emergency fund; visual progress is motivating | Low | Sum of "Sparen" category transactions since goal creation |

---

## Anti-Features

Features to deliberately NOT build in MVP, with clear rationale.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| OpenBanking / direct bank API connection | Swiss banks (UBS, Raiffeisen, PostFinance) have limited open API access; Swiss OpenBanking (SIX) coverage is incomplete; OAuth flows add auth complexity; banks actively restrict third-party data access | PDF import as primary path; OpenBanking as post-MVP when coverage matures |
| Credit score display | Not relevant in Switzerland — Swiss credit system uses ZEK/IKO registry (not a numeric score); building this would be misleading | Omit entirely; focus on cash flow |
| Net worth tracking (depot, Pillar 3a, real estate) | Zahlungskonten only per project decision; depot/investment accounts are out of scope; adds enormous complexity with little MVP value | Explicit "This app covers spending accounts only" message |
| Push notifications / email alerts | Server infrastructure for reliable email/push at MVP adds ops burden; risk of being flagged as spam | In-app banners and status indicators suffice; post-MVP |
| Social / sharing features | Not appropriate for financial data; privacy-skeptical users (Marc) actively don't want this | Omit entirely |
| Multi-currency support | Swiss users earn and spend in CHF; Revolut handles multi-currency travel; out of scope for Swiss-domestic PFM | Single currency CHF only |
| Gamification (badges, streaks) | Feels infantilizing for the trust relationship required with financial data; adds scope without core value | Simple visual progress (savings goal progress bar) is sufficient |
| Budget envelope system (YNAB-style) | YNAB's envelope budgeting requires active daily engagement and mental model shift; Lara persona has "Aufschieberitis" — high friction = churn | Safe-to-Spend is the simpler, lower-friction equivalent for passive users |
| Bill payment reminders / calendar | Requires manual input of due dates; overlaps with calendar apps users already have | Not in scope; reduces scope creep |
| Investment recommendations / robo-advisor | Regulated financial advice; liability risk; completely out of scope for a course project | Omit; reference to established services if relevant |
| Shared household budgeting | Couples / flatmates sharing accounts is a real Swiss need but adds multi-user data model complexity | Post-MVP if validated; single-user for now |

---

## Swiss-Specific Considerations

### Regulatory

| Requirement | Detail | Complexity |
|-------------|--------|------------|
| nDSG compliance | Swiss Data Protection Act — requires explicit informed consent before processing personal data, right to deletion, data minimization principle | Low-Medium; consent screen + delete endpoint already planned |
| Right to deletion | All user data (profile, transactions, settings) must be deletable on request; production DB must show no records after deletion | Low; SQL cascade deletes; already in user stories |
| Data residency | nDSG does not mandate Swiss-only hosting (unlike some EU sector regulations) but privacy policy must state where data is stored | Low; disclose hosting location in privacy policy |
| No investment advice | BudgetBuddy must not give investment advice — FINMA regulated activity; AI report must be framed as "insight" not "recommendation" | Low; wording discipline in prompts and UI |

### Swiss Banking Behavior

| Observation | Implication | Confidence |
|-------------|-------------|------------|
| Majority of young Swiss still use traditional banks (Raiffeisen, PostFinance, cantonal banks) despite neobank options | PDF import from these banks is the right primary path — don't force a bank switch | HIGH |
| UBS, PostFinance, Raiffeisen each have distinct PDF statement formats | Three separate parsers needed at minimum; test fixtures for each are critical | HIGH |
| PostFinance is dominant for students and young adults (often opened at post office as first account) | Prioritize PostFinance PDF parser — highest user coverage | MEDIUM |
| Krankenkasse (mandatory health insurance, 200-500 CHF/month) is Switzerland's largest recurring fixed cost for young people | Must appear in fixed costs wizard as a labeled example; many users won't think to list it | HIGH |
| Swiss salaries typically paid monthly (monthly payroll cycle, not bi-weekly) | Safe-to-Spend weekly calculation based on monthly income is correct | HIGH |
| Stipendien (student grants) are cantonal, irregular, and not always monthly | Income field should allow irregular income entry or note limitation | MEDIUM |
| 13th month salary is common in Switzerland (paid in December or split) | "Einkommen" category must not flag the 13th payment as anomalous | MEDIUM |
| Swiss banking privacy culture — Marc persona's data privacy concern is representative, not edge case | Consent screen, local processing emphasis, and clear "your data is not sold" statement matter disproportionately here | HIGH |
| Camt.053 XML is the ISO standard format Swiss banks use for OpenBanking data exchange | If OpenBanking is added post-MVP, parse Camt.053 — do not build proprietary CSV parsers | MEDIUM |

### Swiss Spending Categories

The 13-category taxonomy in the project is solid but could benefit from Swiss-specific labeling:

| Category | Swiss Context | Suggestion |
|----------|--------------|------------|
| Versicherung | Krankenkasse, Hausrat, RC — all common for this demo | Keep; ensure KK is recognized in lookup table |
| Wohnen | Nebenkosten (utilities bundled with rent in CH) often appear separately in bank statements | Nebenkosten should map to Wohnen, not Sonstiges |
| Transport | GA (Generalabonnement SBB), Halbtax, ZVV — Swiss transit passes show up as large annual/monthly debits | Transit passes should be in lookup table |
| Bildung | Semestergebühren, Prüfungsgebühren, Lehrmittel | Student-relevant; keep |
| Gesundheit | Franchise-Zahlung, Arztkosten after KK | Distinguish from Versicherung in lookup table |
| Sparen | Pillar 3a transfers show as bank transfers — easy to miscategorize | 3a-provider names (Frankly, finpension, VIAC) should be in lookup table as Sparen |

---

## Feature Dependencies

```
PDF Import ──────────────────────────────────────────┐
                                                      ▼
Fixed Costs Onboarding ──────────────────────────> Safe-to-Spend (requires: income + fixed costs + expenses)
                                                      │
Income configuration (Settings) ─────────────────────┘

Transaction list ──────> Category view ──────> Month comparison (requires 2 months)
                                 │
                                 └──────────> Recurring payment detection (requires 2+ months)

Savings goal ──────────> Progress tracking (requires: Sparen category transactions)

All transaction data ──────> AI monthly report (requires: full month of data)
```

**Critical path for MVP:**
1. Auth + consent (gate for everything)
2. PDF import (no data without this)
3. Categorization (no insight without this)
4. Fixed costs onboarding (Safe-to-Spend meaningless without this)
5. Safe-to-Spend (core value proposition)

Everything else (comparison, recurring, AI report, savings goal) builds on top of steps 1-5.

---

## MVP Recommendation

**Prioritize (table stakes + core differentiator):**
1. Auth, consent, account deletion (nDSG gate — must have)
2. PDF import for PostFinance, Raiffeisen, UBS (no app without data)
3. Auto-categorization with manual correction (trust-building core loop)
4. Category spending summary view (table stakes PFM expectation)
5. Fixed costs wizard with monthly/quarterly/yearly normalization
6. Safe-to-Spend calculation + dashboard display (the differentiator)

**Second tier (Should — build after core works):**
7. Savings goal tracker (Marc's primary motivation — high emotional value, low technical complexity)
8. Month navigator (view past months)
9. Drill-down into category transactions
10. Month-over-month comparison (requires 2 months of data; timing makes it hard to test early)
11. Settings: password change, income update

**Defer (Could / Post-MVP):**
12. Recurring payment detector (requires 2+ months data; complex matching logic)
13. AI monthly report (prompts need tuning; generates cost per user per month)
14. OpenBanking connection

---

## Sources

- YNAB feature set: training knowledge (stable product, HIGH confidence)
- Mint shutdown: announced by Intuit October 2023, completed January 2024 (HIGH confidence)
- Revolut PFM features: training knowledge, well-documented product (HIGH confidence)
- Neon Switzerland: training knowledge (MEDIUM confidence — verify current feature set at neon-free.ch)
- Swiss banking landscape (PostFinance dominance, Krankenkasse costs, salary structure): training knowledge (HIGH confidence — stable market facts)
- Camt.053: ISO 20022 standard, used by Swiss banks for OpenBanking (HIGH confidence — ISO standard)
- nDSG requirements: training knowledge (MEDIUM confidence — verify current enforcement guidance at edoeb.admin.ch)
- Swiss PFM competitive gap: assessed from above; no primary source available due to tool restrictions — treat as MEDIUM confidence
