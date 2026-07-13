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

### 5. PLAN ABLEGEN
After the user confirms the plan, persist it as markdown under `docs/plans/` before creating the branch:

- File path: `docs/plans/<TASK-ID>-<kurztext>.md` (same `<kurztext>` as the branch name, e.g. `docs/plans/INFRA-01-spring-boot-skeleton.md`)
- Content: the confirmed plan — issue reference, Task-ID, branch name, decisions, affected/new files, implementation steps, test strategy, and the acceptance criteria from the issue.

`docs/plans/` is listed in `.claudeignore`, so these files stay out of Claude's automatic context/search. They serve as a human-readable artifact and git history; do not rely on reading them back in later runs.

### 6. BRANCH ERSTELLEN
```bash
git checkout main && git pull
git checkout -b feature/<TASK-ID>-<kurztext>
```

### 7. IMPLEMENTIEREN
Implement code and tests according to the confirmed plan. Follow all conventions in CLAUDE.md:
- Package structure by domain (not layer)
- `BigDecimal` for all CHF amounts — never `double` or `float`
- No secrets in git — API keys and JWT secret via environment variables only
- Claude API always behind `CategorizationPort` interface
- Timeouts + fallback to `"Sonstiges"` for all external calls

### 8. LOKALER REVIEW
Review all changes before creating a PR:
- Run `git diff main` and check for correctness, security issues, and convention violations
- Present review findings to the user
- Wait for explicit user confirmation that the PR may be created

### 9. PR ERSTELLEN
```bash
gh pr create \
  --title "[<TASK-ID>] <concise title>" \
  --body "..."
```

PR body must include:
- Closing keyword that links the issue: `Closes #<issue-number>` — creates the formal
  link in the issue's Development panel (PR targets `main`, the default branch) and
  auto-closes the issue when the PR is merged.
- Summary (2–3 bullet points)
- Test plan (checklist)

### 10. ISSUE VERLINKEN (Rückrichtung)
`gh pr create` prints the new PR URL. Post a backlink comment on the issue so the link is
also explicit in the issue timeline:

```bash
gh issue comment <issue-number> --body "🔀 PR erstellt: <pr-url>"
```

Confirm to the user that PR and issue are now linked in both directions (PR → issue via
`Closes #<issue-number>` + Development panel, issue → PR via the backlink comment).
