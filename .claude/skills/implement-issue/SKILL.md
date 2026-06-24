---
name: implement-issue
description: GitHub Issue end-to-end umsetzen — Issue einlesen, Fragen klären, Plan präsentieren (mit Bestätigung), Branch erstellen, Code + Tests implementieren, lokalen Review durchführen (mit Bestätigung), PR öffnen. Auslösen via /implement-issue <issue-number>.
argument-hint: "<issue-number>"
---

# implement-issue

Implement a GitHub Issue end-to-end: read the issue, ask clarifying questions if needed, present a plan for confirmation, implement with tests, do a local review, then open a PR.

## Usage

```
/implement-issue <issue-number>
```

## Workflow

### 1. EINLESEN
Run `gh issue view <issue-number>` and read title, body, labels, and assignees in full.

### 2. ANALYSE
- Extract the Task-ID from the issue title — it is always in square brackets, e.g. `[BE-FC-01]`
- Identify affected and new files
- Understand requirements and acceptance criteria
- Mark any unclear or ambiguous points

### 3. FRAGEN (when needed)
If anything is unclear, ask the user before proceeding. Do not make assumptions on blocking decisions — ask. Only continue once all open points are resolved.

### 4. PLAN PRÄSENTIEREN
Present the full plan to the user:

- **Branch name** — derived from the Task-ID in the issue title and the nature of the change:
  - Feature work: `feature/<TASK-ID>-<kurztext>` (e.g. `feature/BE-FC-01-fixedcost-entity`)
  - Bug fix: `fix/<TASK-ID>-<kurztext>` (e.g. `fix/INFRA-05-cors-header`)
- **Betroffene Files** — list existing files to modify and new files to create
- **Implementierungsschritte** — numbered list of concrete steps
- **Test-Strategie** — which tests will be written (unit / integration / E2E)

Wait for explicit user confirmation before continuing. If the user requests changes, revise and re-present the full plan from the top.

### 5. BRANCH ERSTELLEN
```bash
git checkout main && git pull
git checkout -b feature/<TASK-ID>-<kurztext>
```

### 6. IMPLEMENTIEREN
Implement code and tests according to the confirmed plan. Follow all conventions in CLAUDE.md:
- Package structure by domain (not layer)
- `BigDecimal` for all CHF amounts — never `double` or `float`
- No secrets in git — API keys and JWT secret via environment variables only
- Claude API always behind `CategorizationPort` interface
- Timeouts + fallback to `"Sonstiges"` for all external calls

### 7. LOKALER REVIEW
Review all changes before creating a PR:
- Run `git diff main` and check for correctness, security issues, and convention violations
- Present review findings to the user
- Wait for explicit user confirmation that the PR may be created

### 8. PR ERSTELLEN
```bash
gh pr create \
  --title "[<TASK-ID>] <concise title>" \
  --body "..."
```

PR body must include:
- Reference to the issue: `Part of #<issue-number>`
- Summary (2–3 bullet points)
- Test plan (checklist)
