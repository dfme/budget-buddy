# BudgetBuddy — Roadmap

**Project:** BudgetBuddy  
**Milestone:** v1 MVP  
**Granularity:** Fine (10 phases)  
**Coverage:** 46/46 v1 requirements mapped  
**Last updated:** 2026-05-14

---

## Phases

- [ ] **Phase 1: Project Skeleton + Authentication** — Users can register, log in, and access protected routes via JWT
- [ ] **Phase 2: Privacy Consent + Account Deletion (nDSG)** — Users complete a mandatory consent step and can permanently delete their account and all data
- [ ] **Phase 3: Fixed Costs Onboarding** — Users capture fixed monthly costs in a first-login wizard and can edit them later
- [ ] **Phase 4: PDF Import** — Users can upload bank statement PDFs from PostFinance, Raiffeisen, and UBS and see extracted transactions
- [ ] **Phase 5: Categorization Engine** — Every transaction is automatically categorised with ≥80% accuracy; users can correct and persist rules
- [ ] **Phase 6: Dashboard + Safe-to-Spend** — Users see a weekly Safe-to-Spend number on the dashboard with category spending summary
- [ ] **Phase 7: Savings Goal** — Users can track progress toward a CHF savings target with actionable monthly hints
- [ ] **Phase 8: Month Navigation** — Users can browse any month with data and see context-appropriate views for past vs. current months
- [ ] **Phase 9: Category Drill-Down** — Users can click a category and inspect its individual transactions with pagination
- [ ] **Phase 10: Settings** — Users can change their password and update monthly income, with immediate Safe-to-Spend recalculation

---

## Phase Details

### Phase 1: Project Skeleton + Authentication
**Goal:** Users can register, log in, and access protected routes — the secure foundation every other phase depends on
**Mode:** mvp
**Depends on:** Nothing (first phase)
**Requirements:** AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05
**Success Criteria:**
1. A new user can register with a valid email and password (min. 8 characters); bcrypt hash is stored — no plaintext password exists in the database
2. Attempting to register with an already-used email returns "E-Mail bereits vergeben"
3. A registered user can log in with correct credentials and their JWT session persists across page refreshes
4. Entering wrong credentials returns "E-Mail oder Passwort falsch" with no hint about which field is wrong
5. A logged-in user who clicks "Abmelden" is redirected to the login page; directly navigating to a protected URL redirects back to login
**Plans:** TBD
**UI hint:** yes

### Phase 2: Privacy Consent + Account Deletion (nDSG)
**Goal:** Users give informed consent before any personal data is stored, and can delete their account along with every associated record
**Mode:** mvp
**Depends on:** Phase 1
**Requirements:** AUTH-06, AUTH-07
**Success Criteria:**
1. A first-time user is shown a data privacy consent screen before any data is written; they cannot proceed without actively accepting
2. Declining consent or leaving the consent screen leaves zero rows in any user-associated table
3. A user who deletes their account cannot log in again with the same credentials; a database admin finds no rows referencing the deleted user ID across all tables (profile, transactions, fixed costs, savings goals, settings, merchant rules)
**Plans:** TBD
**UI hint:** yes

### Phase 3: Fixed Costs Onboarding
**Goal:** First-time users capture their fixed monthly expenses in a guided wizard so Safe-to-Spend can be accurately calculated from day one
**Mode:** mvp
**Depends on:** Phase 2
**Requirements:** ONBD-01, ONBD-02, ONBD-03, ONBD-04, ONBD-05, ONBD-06
**Success Criteria:**
1. On first login after consent, a fixed costs wizard is shown that cannot be dismissed until the user enters at least one valid entry or explicitly confirms "Keine Fixkosten"
2. Saving a fixed cost entry with a missing Bezeichnung, zero/negative Betrag, or invalid Intervall is rejected with a field-specific error message
3. A quarterly entry of 300 CHF and an annual entry of 1200 CHF each contribute exactly 100 CHF/month to the Safe-to-Spend calculation
4. Once the wizard is completed, subsequent logins go directly to the dashboard without showing the wizard again
5. If the sum of fixed costs (monthly basis) meets or exceeds monthly income, the dashboard shows "Deine Fixkosten übersteigen dein Einkommen — Safe-to-Spend kann nicht berechnet werden" and displays "–" in place of the Safe-to-Spend value
6. Editing or deleting a fixed cost entry causes Safe-to-Spend to recalculate and display the updated value immediately
**Plans:** TBD
**UI hint:** yes

### Phase 4: PDF Import
**Goal:** Users can upload bank statement PDFs from Swiss banks and see their transactions extracted accurately with clear feedback on any error
**Mode:** mvp
**Depends on:** Phase 3
**Requirements:** IMPT-01, IMPT-02, IMPT-03, IMPT-04, IMPT-05, IMPT-06, IMPT-07
**Success Criteria:**
1. Uploading a valid PDF from PostFinance, Raiffeisen, or UBS extracts date, amount (exact to the Rappen), and recipient for ≥95% of transactions in the defined test set
2. Uploading a non-PDF, an unsupported format, or a file from an unsupported bank returns "Nur PDF-Dateien von Schweizer Banken werden unterstützt"
3. Uploading a password-protected PDF returns "Das PDF ist passwortgeschützt — bitte entferne den Schutz vor dem Upload"
4. Uploading a file larger than 10 MB is rejected before any processing begins with "Maximale Dateigrösse: 10 MB"
5. Uploading a PDF whose date range and bank already cover >80% of the user's stored transactions warns "Dieser Kontoauszug wurde bereits importiert" with "Trotzdem importieren" and "Abbrechen" options; no duplicates are stored without explicit confirmation
6. If PDF processing exceeds 30 seconds, the operation is aborted and "Verarbeitung fehlgeschlagen — bitte versuche es erneut" is shown
**Plans:** TBD
**UI hint:** yes

### Phase 5: Categorization Engine
**Goal:** Every imported transaction is automatically assigned a category with high accuracy; users can correct mistakes and those corrections apply to future imports
**Mode:** mvp
**Depends on:** Phase 4
**Requirements:** CATG-01, CATG-02, CATG-03, CATG-04, CATG-05
**Success Criteria:**
1. Every transaction in the system is assigned exactly one of the 13 defined categories; no transaction is uncategorised; "Sonstiges" is the fallback when the pipeline cannot determine a category
2. The categorization pipeline processes in order: per-user correction rules → global merchant lookup → Claude API (structured output via `outputConfig(CategoryResult.class)`) → "Sonstiges" fallback; Claude API is never called for merchants already in the lookup or correction tables
3. Running the evaluation harness against ≥200 manually labelled transactions achieves ≥80% correct category assignments
4. When a user corrects a transaction's category, that correction is persisted and the same recipient's future imports are automatically assigned the corrected category
5. After a manual correction, category totals in the UI update within 1 second and the per-category sums match the sum of their constituent transactions to the Rappen
**Plans:** TBD
**UI hint:** yes

### Phase 6: Dashboard + Safe-to-Spend
**Goal:** Users see a single weekly Safe-to-Spend number on the dashboard alongside a category spending breakdown — the core value of the product
**Mode:** mvp
**Depends on:** Phase 5
**Requirements:** SAFE-01, SAFE-02, SAFE-03, SAFE-04, SAFE-05, CATG-06, CATG-07
**Success Criteria:**
1. The dashboard shows Safe-to-Spend = `(Income − Fixed Costs − Expenses So Far) ÷ Remaining Weeks` where Expenses So Far excludes Einkommen and Sparen category transactions; the value is exact to the Rappen
2. When Safe-to-Spend is negative, a red banner reading "Achtung: Dein Budget für diese Woche ist überzogen" is displayed at the top of the dashboard
3. When no monthly income is configured, the dashboard shows "Bitte erfasse dein Monatseinkommen in den Einstellungen" in place of the Safe-to-Spend value; no calculation is performed
4. When fewer than 7 days remain in the current month, the divisor is set to 1 and the label "Letzte Woche des Monats" is displayed alongside the value
5. The category overview shows, per category: sum in CHF, transaction count, and percentage of the monthly total
6. The category overview includes a bar or pie chart visualising the spending breakdown across all 13 categories
**Plans:** TBD
**UI hint:** yes

### Phase 7: Savings Goal
**Goal:** Users can set a CHF savings target with a deadline and track their progress, with a hint if they fail to save in a given month
**Mode:** mvp
**Depends on:** Phase 6
**Requirements:** GOAL-01, GOAL-02, GOAL-03, GOAL-04, GOAL-05
**Success Criteria:**
1. Creating a savings goal requires Betrag > 0 CHF and a Zieldatum in the future; saving without either field is rejected with a validation message
2. Goal progress is displayed as "X CHF / Y CHF (Z%)" where X is the sum of all Sparen-category transactions since the goal's creation date; this is verified correct at 0%, 25%, 50%, 100%, and >100%
3. A goal that is reached (≥100%) is displayed with a success state
4. A goal whose Zieldatum has passed without reaching 100% shows "Ziel verpasst" together with the remaining CHF amount
5. When a calendar month passes with no Sparen-category transactions, the user sees a hint that names the highest non-essential spending category and a concrete CHF reduction suggestion
**Plans:** TBD
**UI hint:** yes

### Phase 8: Month Navigation
**Goal:** Users can navigate to any month with imported data and see correctly scoped views; the current month shows Safe-to-Spend while past months show a closed state
**Mode:** mvp
**Depends on:** Phase 6
**Requirements:** NAVG-01, NAVG-02, NAVG-03, NAVG-04
**Success Criteria:**
1. When a user opens the dashboard, the most recent month with imported data is shown by default
2. Switching to a different month updates the dashboard, category view, and transaction views to reflect that month's data
3. For any past month, Safe-to-Spend is replaced with the label "Abgeschlossen" — the formula is not run for historical months
4. Selecting a month for which no PDF has been imported shows "Keine Daten für [Monat Jahr] — PDF hochladen?" in place of data
**Plans:** TBD
**UI hint:** yes

### Phase 9: Category Drill-Down
**Goal:** Users can click any spending category and inspect its individual transactions in a paginated list sorted by date
**Mode:** mvp
**Depends on:** Phase 6
**Requirements:** TRAN-01, TRAN-02
**Success Criteria:**
1. Clicking a category in the category overview opens a list showing each transaction's date, amount, and recipient, sorted by date descending
2. When a category contains more than 20 transactions, only the first 20 are loaded initially; a "Weitere laden" button fetches the next batch — no unpaginated full load occurs
**Plans:** TBD
**UI hint:** yes

### Phase 10: Settings
**Goal:** Users can change their password and update their monthly income, with immediate recalculation of Safe-to-Spend
**Mode:** mvp
**Depends on:** Phase 6
**Requirements:** SETT-01, SETT-02, SETT-03
**Success Criteria:**
1. A logged-in user can change their password by providing the correct current password, a new password (min. 8 characters), and a matching confirmation; success is confirmed with an in-app message
2. Providing an incorrect current password during a password change is rejected with "Aktuelles Passwort falsch" and the password is not changed
3. Updating the monthly income value in Settings causes the Safe-to-Spend value on the dashboard to recalculate immediately and reflect the new income
**Plans:** TBD
**UI hint:** yes

---

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Project Skeleton + Authentication | 0/? | Not started | — |
| 2. Privacy Consent + Account Deletion (nDSG) | 0/? | Not started | — |
| 3. Fixed Costs Onboarding | 0/? | Not started | — |
| 4. PDF Import | 0/? | Not started | — |
| 5. Categorization Engine | 0/? | Not started | — |
| 6. Dashboard + Safe-to-Spend | 0/? | Not started | — |
| 7. Savings Goal | 0/? | Not started | — |
| 8. Month Navigation | 0/? | Not started | — |
| 9. Category Drill-Down | 0/? | Not started | — |
| 10. Settings | 0/? | Not started | — |
