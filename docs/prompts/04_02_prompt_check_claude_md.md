## Prompt

Check my CLAUDE.md my knowledge base, my skills and my guardrails. 
For each fo the top 5 gaps, name the file, the problem, and the exact fix
- and flag which risky actions need a hook so I can't bypass them.


## Antwort

● What I checked

  - CLAUDE.md (full content, already in context)
  - .claude/ — does not exist in the repo (confirms the "No project skills found" line in CLAUDE.md, and extends to settings/hooks too)
  - docs/adr/, docs/requirements/, docs/prompts/, Modul2-4/, .claudeignore, .gitignore
  - Persistent memory store for this project (...\memory\MEMORY.md)
  - Local git history on main (no gh CLI / no network in this sandbox, so I couldn't query GitHub branch protection directly — noted where that limits a claim)

  Top 5 gaps

  1. No .claude/settings.json exists at all
  - File: .claude/settings.json (missing)
  - Problem: Every rule in CLAUDE.md framed as a hard guardrail — "Kein direkter Commit auf main," "Keine Secrets im Git," "Merge nur durch Dev, nie von Claude," BigDecimal-only — is prose. Nothing technically stops
  me (or a future careless prompt) from doing the opposite mid-session. There's zero permissions or hooks config to fall back on.
  - Fix: Run the update-config skill to create .claude/settings.json with explicit permissions.deny rules and PreToolUse hooks (see hook table below) so the existential-risk rules are enforced by the harness, not by
  me remembering them.

  2. main-branch protection is unverified and local history suggests it isn't enforced
  - File: CLAUDE.md → "Git: Branching-Strategie" section (the rule), vs. actual repo state
  - Problem: git log main --no-merges shows plain commits sitting directly in main's history (e.g. fcecd1b Add Branching and Review Conventions itself, 0a6e73b Add security guidelines...). I can't tell from local
  history alone whether these were direct pushes or squash-merged PRs (no gh/network access here to check), but either way there is no verified GitHub branch protection stopping a direct push to main — the rule is
  honor-system only.
  - Fix: In GitHub → Settings → Branches, add a protection rule on main: require PR + ≥1 approval, disallow force-push, disallow direct pushes (including for admins, if you want it to bind Sergio too). This is the
  only fix that can't be talked around — local hooks help Claude specifically, but server-side protection is what makes the rule actually unbypassable for everyone.

  3. "No secrets in git" has no automated check
  - File: CLAUDE.md → "Sicherheit: Keine Secrets im Git" (and no corresponding CI/hook file exists)
  - Problem: CLAUDE.md calls a data leak "existential" (Risk #2) and mandates ANTHROPIC_API_KEY/JWT secret stay out of application.properties/git — but enforcement is just a sentence. Once the backend exists, a single
  Edit to application.properties with a hardcoded key would violate this with nothing flagging it.
  - Fix: Add a PreToolUse hook on Edit/Write that blocks writes to application*.properties|.yml or .env* containing literal secret-shaped values (sk-ant-..., =.{20,} after ANTHROPIC_API_KEY/jwt.secret), and add a
  pre-commit secret scan (e.g. gitleaks) once code exists.

  4. Project memory/knowledge base is empty despite 15+ prior sessions
  - File: C:\Users\Sergio\.claude\projects\C--dev-git-budget-buddy\memory\MEMORY.md
  - Problem: The memory directory exists but contains zero files. After 15+ sessions worth of decisions (team-of-3 roles, the AI-role-split doc, the branching convention just added, course context) nothing is
  persisted — each new session re-derives everything from CLAUDE.md alone, with no record of feedback already given or decisions already settled.
  - Fix: Backfill now — at minimum: project team composition (Sergio + 2 others, course project, no hard deadline), the branching/review convention just added, and the "AI roles" doc decisions
  (docs/prompts/04_02_prompt_uebung_claude_support.md) as a project-type memory. Make writing memory before /clear a habit going forward.

  5. No project skills despite two clearly repeatable, format-sensitive workflows
  - File: .claude/skills/ (missing)
  - Problem: CLAUDE.md documents the ADR format (ADR-0…ADR-10, each with a "rejected alternatives" column) and a strict 3-step PR convention (local Claude review → PR → ≥1 human approval → human-only merge). Both
  currently rely on me correctly re-reading and re-applying prose each time — one missed detail (e.g. a future ADR skipping "abgelehnte Alternativen") and the convention silently drifts.
  - Fix: Add .claude/skills/new-adr/SKILL.md encoding the exact ADR template/numbering, and .claude/skills/pr-review-gate/SKILL.md encoding the local-review → PR → approval → human-merge sequence, so both are invoked
  identically every time instead of re-explained.

  Bonus finding (not in the top 5, but worth knowing): .claudeignore blanket-excludes Modul2/, Modul3/, Modul4/, and docs/prompts/ from my normal Read/Grep/Glob access. Those folders hold live planning content
  (backlog, sprint plans, ADR critique) — so if you ask me about backlog/sprint state through normal tools, I'll have a blind spot there with no warning. Worth either narrowing the ignore to just binary/export files
  (*.docx, *.html, *.drawio) or adding a comment explaining the exclusion is intentional.

  Risky actions that need a hook (not just an instruction)

  ┌─────────────────────────────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────────┬─────────────────────────────────────────────────────────────────────┐
  │                      Risky action                       │                              Why prose isn't enough                              │                             Hook to add                             │
  ├─────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ git commit / git push while on main                     │ Directly contradicts the convention you just documented; easy to do by accident  │ PreToolUse on Bash/PowerShell: deny if command matches git          │
  │                                                         │ or under "just ship it" pressure                                                 │ (commit|push) and git branch --show-current == main                 │
  ├─────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ git merge / gh pr merge run by Claude                   │ CLAUDE.md explicitly says merge is human-only, never Claude — but if asked to    │ PreToolUse on Bash: deny any git merge, gh pr merge outright,       │
  │                                                         │ "just merge it," I'd otherwise comply                                            │ regardless of branch                                                │
  ├─────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ Edits to .env*, application*.properties/yml adding      │ "No secrets in git" is currently just a sentence I could be argued past          │ PreToolUse on Edit/Write: deny if target matches those filenames    │
  │ literal secrets                                         │                                                                                  │ and new content contains key-shaped strings                         │
  ├─────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ git push --force / git commit --no-verify to any branch │ Bypasses hooks/CI that would otherwise catch the above                           │ PreToolUse on Bash: deny --force/-f on push, deny --no-verify       │
  ├─────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ Destructive SQL against the dev DB (DROP TABLE, DELETE  │ US-02 (nDSG delete-account) makes deletion logic a real feature soon — a stray   │ PreToolUse on Bash: deny sqlite3/raw SQL commands matching DROP     │
  │ FROM ...  without WHERE)                                │ destructive query during dev work could wipe shared data                         │ TABLE or DELETE FROM \w+;/no WHERE                                  │
  └─────────────────────────────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────────┴─────────────────────────────────────────────────────────────────────┘

  I can implement #1–#5 (settings.json + hooks + the two skills) right now via the update-config skill if you want — just say go and I'll build it instead of just describing it. Branch protection (#2) you'll need to
  flip yourself in GitHub Settings since I don't have gh/network access in this sandbox to do it for you.