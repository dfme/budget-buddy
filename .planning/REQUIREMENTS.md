# Requirements — BudgetBuddy v1

**Last updated:** 2026-05-14
**Covers:** User Stories US1–US7, US12–US14

---

## v1 Requirements

### Authentication & Privacy (US1, US2)

- [ ] **AUTH-01**: User can create an account with email + password (bcrypt hash; no plaintext stored)
- [ ] **AUTH-02**: Duplicate email registration rejected with "E-Mail bereits vergeben"
- [ ] **AUTH-03**: User can log in with correct credentials and stay logged in across sessions
- [ ] **AUTH-04**: Wrong credentials return "E-Mail oder Passwort falsch" (no disambiguation)
- [ ] **AUTH-05**: User can log out; session invalidated server-side; protected pages inaccessible without re-login
- [ ] **AUTH-06**: User is shown a data privacy consent screen before any data is stored; app unusable without consent
- [ ] **AUTH-07**: User can delete their account; all personal data (profile, transactions, fixed costs, savings goals, settings, merchant rules) deleted from the production database; re-login with same credentials fails

### Onboarding (US3)

- [ ] **ONBD-01**: On first login, a fixed costs wizard is displayed that cannot be skipped without entering at least one entry or explicitly confirming "Keine Fixkosten"
- [ ] **ONBD-02**: Each fixed cost entry requires: Bezeichnung (non-empty), Betrag in CHF (> 0), Intervall ∈ {monatlich, quartalsweise, jährlich} — any missing field rejected with field-specific error
- [ ] **ONBD-03**: Quarterly fixed costs normalized to monthly (÷ 3); yearly (÷ 12) — Safe-to-Spend uses the monthly equivalent
- [ ] **ONBD-04**: After onboarding is completed once, the wizard is not shown on subsequent logins
- [ ] **ONBD-05**: If sum of all fixed costs (monthly basis) ≥ monthly income, dashboard shows warning "Deine Fixkosten übersteigen dein Einkommen — Safe-to-Spend kann nicht berechnet werden" and Safe-to-Spend displays "–"
- [ ] **ONBD-06**: Existing fixed cost entries can be edited or deleted; Safe-to-Spend recalculates immediately

### PDF Import (US4)

- [ ] **IMPT-01**: User can upload a bank statement PDF (UBS, Raiffeisen, PostFinance)
- [ ] **IMPT-02**: System extracts date, amount (CHF, exact to Rappen), and recipient from ≥95% of transactions — validated against a defined test set
- [ ] **IMPT-03**: Non-PDF or unsupported bank format rejected: "Nur PDF-Dateien von Schweizer Banken werden unterstützt"
- [ ] **IMPT-04**: Password-protected PDF rejected: "Das PDF ist passwortgeschützt — bitte entferne den Schutz vor dem Upload"
- [ ] **IMPT-05**: PDF > 10 MB rejected before processing: "Maximale Dateigrösse: 10 MB"
- [ ] **IMPT-06**: Duplicate detection: if > 80% of parsed transactions already exist for same user and date range, user is warned "Dieser Kontoauszug wurde bereits importiert" with options "Trotzdem importieren" / "Abbrechen"
- [ ] **IMPT-07**: PDF processing that exceeds 30 seconds is aborted: "Verarbeitung fehlgeschlagen — bitte versuche es erneut"

### Categorization (US5)

- [ ] **CATG-01**: Every transaction is assigned exactly one of 13 categories: Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit, Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges
- [ ] **CATG-02**: Auto-categorization uses a 3-level pipeline: (1) per-user correction rules, (2) global merchant lookup table, (3) Claude API for unknowns — fallback to "Sonstiges" on API error or uncertainty
- [ ] **CATG-03**: Auto-categorization accuracy ≥ 80% on a defined test set of ≥ 200 manually labelled transactions
- [ ] **CATG-04**: User can manually correct a category; the correction is persisted and applied to future imports from the same recipient (normalized merchant name)
- [ ] **CATG-05**: After a manual correction, category totals update within 1 second and sums match transaction-level data to the Rappen
- [ ] **CATG-06**: Category overview shows per category: sum in CHF, transaction count, and percentage of monthly total
- [ ] **CATG-07**: Category overview includes a visual chart (bar or pie) of spending breakdown

### Safe-to-Spend (US6)

- [ ] **SAFE-01**: Dashboard shows Safe-to-Spend = `(Income − Fixed Costs − Expenses So Far) ÷ Remaining Weeks`; amounts exact to the Rappen
- [ ] **SAFE-02**: Negative Safe-to-Spend displays a red banner: "Achtung: Dein Budget für diese Woche ist überzogen"
- [ ] **SAFE-03**: No monthly income configured → show "Bitte erfasse dein Monatseinkommen in den Einstellungen" instead of value; no division performed
- [ ] **SAFE-04**: Fewer than 7 days remaining in the month → use divisor 1 (minimum); display "Letzte Woche des Monats" label
- [ ] **SAFE-05**: "Expenses So Far" excludes Einkommen and Sparen category transactions

### Savings Goal (US7)

- [ ] **GOAL-01**: User can create a savings goal with Betrag (> 0 CHF) and Zieldatum (future date); both fields required
- [ ] **GOAL-02**: Goal progress displayed as "X CHF / Y CHF (Z%)" — "X" = sum of Sparen category transactions since goal creation date
- [ ] **GOAL-03**: Goal reached or exceeded (≥ 100%) shown with success state
- [ ] **GOAL-04**: Overdue goal (Zieldatum passed, goal not reached) shows "Ziel verpasst" with missing amount
- [ ] **GOAL-05**: If a month passes with no Sparen transactions, user sees a hint referencing the highest non-essential category and a concrete CHF suggestion

### Month Navigation (US12)

- [ ] **NAVG-01**: Dashboard defaults to the most recent month with data
- [ ] **NAVG-02**: User can switch between months; Dashboard, category view, and transaction views all update for the selected month
- [ ] **NAVG-03**: Safe-to-Spend is calculated only for the current calendar month; past months show "Abgeschlossen" in its place
- [ ] **NAVG-04**: Selecting a month with no imported data shows "Keine Daten für [Monat Jahr] — PDF hochladen?"

### Category Drill-Down (US13)

- [ ] **TRAN-01**: Clicking a category shows all its transactions: date, amount, recipient — sorted by date descending
- [ ] **TRAN-02**: Categories with > 20 transactions show 20 initially with a "Weitere laden" button; no full unpaginated load

### Settings (US14)

- [ ] **SETT-01**: User can change password by providing current password + new password (min. 8 characters) + confirmation; success shown with in-app confirmation
- [ ] **SETT-02**: Incorrect current password → rejected with "Aktuelles Passwort falsch"
- [ ] **SETT-03**: User can update monthly income; Safe-to-Spend on dashboard recalculates immediately with the new value

---

## v2 Requirements (Deferred)

Features deferred until v1 is stable and validated:

- **Recurring payments detector (US8)** — Pattern detection across 2+ months; ±2% tolerance; "Neu" label + notification
- **AI monthly report (US9)** — Claude-generated report at month-end with top 3 categories + savings suggestion
- **Month-over-month comparison (US10)** — Per-category delta in CHF and %; anomaly highlighting (> 20% increase, ≥ 50 CHF)

---

## Out of Scope

- **OpenBanking / bank API connection (US11)** — Swiss bank API coverage incomplete; OAuth complexity; post-MVP
- **Email verification** — Post-MVP; reduces auth scope for course timeline
- **Password reset via email** — Post-MVP
- **Rate limiting / login lockout** — Post-MVP
- **PLZ validation (Swiss postal list)** — Post-MVP
- **Automatic session expiry after inactivity** — Post-MVP
- **Multi-currency support** — Swiss-only (CHF); out of scope
- **B2B / advisor tool** — Not target audience
- **International rollout** — Swiss market only
- **Social / sharing features** — Privacy-sensitive; not appropriate for financial data
- **Budget envelope system (YNAB-style)** — Higher friction than Safe-to-Spend for the Lara persona
- **Credit score / net worth** — Not relevant in Swiss context
- **Push notifications / email alerts** — Infrastructure overhead; in-app indicators sufficient for MVP
- **Shared household budgeting** — Single-user for v1

---

## Traceability

_Updated by roadmap agent — 2026-05-14_

| REQ-ID | Phase | Status |
|--------|-------|--------|
| AUTH-01 | Phase 1 — Project Skeleton + Authentication | Pending |
| AUTH-02 | Phase 1 — Project Skeleton + Authentication | Pending |
| AUTH-03 | Phase 1 — Project Skeleton + Authentication | Pending |
| AUTH-04 | Phase 1 — Project Skeleton + Authentication | Pending |
| AUTH-05 | Phase 1 — Project Skeleton + Authentication | Pending |
| AUTH-06 | Phase 2 — Privacy Consent + Account Deletion (nDSG) | Pending |
| AUTH-07 | Phase 2 — Privacy Consent + Account Deletion (nDSG) | Pending |
| ONBD-01 | Phase 3 — Fixed Costs Onboarding | Pending |
| ONBD-02 | Phase 3 — Fixed Costs Onboarding | Pending |
| ONBD-03 | Phase 3 — Fixed Costs Onboarding | Pending |
| ONBD-04 | Phase 3 — Fixed Costs Onboarding | Pending |
| ONBD-05 | Phase 3 — Fixed Costs Onboarding | Pending |
| ONBD-06 | Phase 3 — Fixed Costs Onboarding | Pending |
| IMPT-01 | Phase 4 — PDF Import | Pending |
| IMPT-02 | Phase 4 — PDF Import | Pending |
| IMPT-03 | Phase 4 — PDF Import | Pending |
| IMPT-04 | Phase 4 — PDF Import | Pending |
| IMPT-05 | Phase 4 — PDF Import | Pending |
| IMPT-06 | Phase 4 — PDF Import | Pending |
| IMPT-07 | Phase 4 — PDF Import | Pending |
| CATG-01 | Phase 5 — Categorization Engine | Pending |
| CATG-02 | Phase 5 — Categorization Engine | Pending |
| CATG-03 | Phase 5 — Categorization Engine | Pending |
| CATG-04 | Phase 5 — Categorization Engine | Pending |
| CATG-05 | Phase 5 — Categorization Engine | Pending |
| CATG-06 | Phase 6 — Dashboard + Safe-to-Spend | Pending |
| CATG-07 | Phase 6 — Dashboard + Safe-to-Spend | Pending |
| SAFE-01 | Phase 6 — Dashboard + Safe-to-Spend | Pending |
| SAFE-02 | Phase 6 — Dashboard + Safe-to-Spend | Pending |
| SAFE-03 | Phase 6 — Dashboard + Safe-to-Spend | Pending |
| SAFE-04 | Phase 6 — Dashboard + Safe-to-Spend | Pending |
| SAFE-05 | Phase 6 — Dashboard + Safe-to-Spend | Pending |
| GOAL-01 | Phase 7 — Savings Goal | Pending |
| GOAL-02 | Phase 7 — Savings Goal | Pending |
| GOAL-03 | Phase 7 — Savings Goal | Pending |
| GOAL-04 | Phase 7 — Savings Goal | Pending |
| GOAL-05 | Phase 7 — Savings Goal | Pending |
| NAVG-01 | Phase 8 — Month Navigation | Pending |
| NAVG-02 | Phase 8 — Month Navigation | Pending |
| NAVG-03 | Phase 8 — Month Navigation | Pending |
| NAVG-04 | Phase 8 — Month Navigation | Pending |
| TRAN-01 | Phase 9 — Category Drill-Down | Pending |
| TRAN-02 | Phase 9 — Category Drill-Down | Pending |
| SETT-01 | Phase 10 — Settings | Pending |
| SETT-02 | Phase 10 — Settings | Pending |
| SETT-03 | Phase 10 — Settings | Pending |
