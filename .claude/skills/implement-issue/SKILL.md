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

### 0. PREFLIGHT

```bash
gh auth status
gh api repos/dfme/budget-buddy --jq '.permissions.push'   # erwartet: true
```

Nötig ist der Scope `repo` **und** Write-Zugriff aufs Repo — anders als beim Reviewen genügt
Lesezugriff hier nicht, weil Schritt 6 einen Branch pusht und Schritt 9 via `gh pr create` einen
PR öffnet. Steht `push` auf `false`, hier stoppen und das melden, statt die Arbeit zu machen und
erst am Push zu scheitern. Setup und Fehlerbilder: [.claude/skills/README.md](../README.md).

### 1. EINLESEN
Run `gh issue view <issue-number>` and read title, body, labels, and assignees in full.

### 2. ANALYSE
- Extract the Task-ID from the issue title — it is always in square brackets, e.g. `[BE-FC-01]`
- Identify affected and new files
- Understand requirements and acceptance criteria
- **Check how each AC is verified, don't just read what it demands.** When an AC names a search
  (grep, search string) as its proof, run that search twice: once **narrow, exactly as the AC
  words it**, and once **broad over the underlying concept**. Compare the hit sets. Anything the
  broad search finds that the AC does not list means the AC is incomplete — the AC's wording is
  too narrow, not the implementation.

  Example (#115): the AC's grep `kein manueller (Http)?Interceptor` matched 3 spots,
  `grep -ri interceptor docs/adr CLAUDE.md` matched 8. That difference was the actual work —
  `CLAUDE.md:161` said "kein HttpInterceptor" without "manueller" and slipped the AC's grep.
- Mark any unclear or ambiguous points

### 3. FRAGEN (when needed)
If anything is unclear, ask the user before proceeding. Do not make assumptions on blocking decisions — ask. Only continue once all open points are resolved.

If the broad search from step 2 turned up hits outside the ACs, put that delta to the user
**before** presenting the plan, with three options: (a) fix them along and declare the scope
extension in the PR body, (b) follow-up issue, (c) deliberately leave them, with a reason.
Never decide this alone — scope is a team call.

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
- The directory stays **flat** — no subdirectories. Sprint membership is a property of the board,
  not of the file, and it changes on carryover (#13 and #16 were planned in Sprint 2 and finished
  in Sprint 3). A folder per sprint would force `git mv` and break the file history. Sprint, area
  and story are columns in the index instead, which a directory tree cannot express at once.
- Start the file with this header — **exactly these fields, in this order.** Two competing formats
  grew in the existing 45 plans (bullet list vs. table); new plans use the bullet form:

  ```markdown
  # [<TASK-ID>] <Titel>

  - **Issue:** [#<nr>](https://github.com/dfme/budget-buddy/issues/<nr>)
  - **Task-ID:** `<TASK-ID>`
  - **Branch:** `feature/<TASK-ID>-<kurztext>`
  - **Story:** US-XX — <Titel>   <!-- oder: — (kein us-*-Label) -->
  - **Sprint:** <Sprint aus dem Board zum Zeitpunkt der Planung>
  - **Bestätigt am:** <YYYY-MM-DD>
  ```

  The `Sprint` line records the sprint the plan was *written* in. Do not update it later when an
  issue carries over — the board holds the current truth, the plan holds the historical one.
- Content after the header: the confirmed plan — decisions, affected/new files, implementation
  steps, test strategy, and the acceptance criteria from the issue.

Then add one row to the index in `docs/plans/README.md`, in the table's existing sort order
(by Task-ID) — same values you just wrote into the header:

```markdown
| `<TASK-ID>` | [<Titel>](<TASK-ID>-<kurztext>.md) | [#<nr>](https://github.com/dfme/budget-buddy/issues/<nr>) | US-XX | Sprint N |
```

Commit `docs/plans/README.md` together with the plan.

The index deliberately carries only columns that do not change after the plan is written.
**Do not add Status or Story Points** — those live on the board, change constantly, and a copy
of them would be stale from the moment it is written. If the index ever gets out of sync (missing
rows, hand edits), `scripts/plans-index.sh` rebuilds it completely from files plus board;
`--check` verifies without writing. That script is a repair tool, not a step in this workflow.

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

For documentation changes:
- Back every statement about the code with `file:line`. Documentation describes the state that
  is, never the one that is planned — `8fb4dab` wrote "kein manueller `HttpInterceptor` nötig"
  months before the Angular frontend existed, and it took three rounds (#103, #115, plus the
  original commit) to walk it back.
- Do not replace one unqualified simplification with the next one.

### 8. LOKALER REVIEW
Review all changes before creating a PR:
- Run `git diff main` and check for correctness, security issues, and convention violations
- List every AC individually with its concrete proof — command plus result, or `file:line`
- Flag ACs whose only proof is the same search the issue itself proposed: those are unverified,
  not confirmed
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
