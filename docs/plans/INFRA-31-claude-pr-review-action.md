# [INFRA-31] Automatisches PR-Review via GitHub Action einrichten (Claude Code)

- **Issue:** [#215](https://github.com/dfme/budget-buddy/issues/215)
- **Task-ID:** `INFRA-31`
- **Branch:** `feature/INFRA-31-claude-pr-review-action`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-26

## Ausgangslage

`/review-pr` läuft heute ausschliesslich manuell: jemand muss in einer lokalen Claude-Code-Session
bewusst den Befehl absetzen. Der Skill selbst ist im Repo eingecheckt
(`.claude/skills/review-pr/SKILL.md`) und kommt mit jedem Clone mit — er ist also bereits alles,
was ein automatischer Lauf bräuchte. Was fehlt, ist der Auslöser.

Die offizielle `anthropics/claude-code-action` kann diesen Auslöser liefern: sie startet Claude
Code in einem GitHub-Actions-Runner und übergibt einen `prompt`. Liegt das Repo per
`actions/checkout` im Workspace, liegt `.claude/skills/` darin — und damit auch `review-pr`.

## Recherche-Ergebnisse (offizielle Doku, Stand 2026-08-26)

Belege stammen aus `github.com/anthropics/claude-code-action` (README, `action.yml`,
`docs/setup.md`, `docs/security.md`, `docs/configuration.md`, `docs/usage.md`,
`examples/pr-review-comprehensive.yml`).

| Frage | Befund |
| ----- | ------ |
| Trigger-Form | `examples/pr-review-comprehensive.yml` triggert exakt auf `pull_request: types: [opened, synchronize, ready_for_review, reopened]` — deckungsgleich mit der AC |
| Permissions | Dasselbe Beispiel setzt `contents: read`, `pull-requests: write`, `id-token: write`. Die AC verlangt zusätzlich `issues: write`; die App-Permissions listen *Issues (Read & Write)* als aktiv genutzt |
| Auth | Zwei Inputs: `anthropic_api_key` und `claude_code_oauth_token`. OAuth-Token nur für Pro/Max, lokal via `claude setup-token` erzeugt |
| Bash | **Nicht** per Default erlaubt: *"Claude does not have access to execute arbitrary Bash commands by default"* — braucht explizit `--allowedTools` in `claude_args` |
| Slash-Command im `prompt` | In der Doku **weder bestätigt noch verneint**. `docs/usage.md` zeigt nur Freitext-Prompts. Das ist der eigentliche Restrisiko-Punkt und der Grund, warum AC 7 einen echten Test-PR verlangt |
| ProjectV2 / Board | In der gesamten Doku **nicht erwähnt**. Die App-Permissions decken Contents, Pull Requests und Issues ab — Projects steht nicht darunter |

## Entscheide

### 1. Beide Auth-Varianten gleichzeitig unterstützen

Gewünscht war, dass sowohl `ANTHROPIC_API_KEY` als auch `CLAUDE_CODE_OAUTH_TOKEN` funktionieren.
Beide Inputs unbedingt zu setzen wäre mehrdeutig — welcher gewinnt, ist nicht dokumentiert.
Stattdessen wird per Expression genau einer nicht-leer belegt, mit Vorrang für den OAuth-Token:

```yaml
anthropic_api_key: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN == '' && secrets.ANTHROPIC_API_KEY || '' }}
claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
```

Ist nur `ANTHROPIC_API_KEY` gesetzt, bleibt `claude_code_oauth_token` leer und der Key greift.
Ist der OAuth-Token gesetzt, ist der andere Input leer. So ist keine der beiden Varianten
bevorzugt konfiguriert und keine Kollision möglich.

### 2. Board-Schritt entfällt im automatischen Lauf

Die GitHub App bringt keinen Projects-Scope mit (Recherche oben). `/review-pr` behandelt fehlenden
Board-Zugriff seit INFRA-27 bereits als nicht-blockierend: Schritt 1c meldet den Fehlschlag einmal
und läuft weiter. Es braucht deshalb **keine** Sonderbehandlung im Workflow — das dokumentierte
Verhalten greift von selbst. Dokumentiert wird der Umstand trotzdem (AC 4), damit niemand den
fehlenden Kartenzug für einen Bug hält.

### 3. Das automatische Review ersetzt die Dev-Freigabe nicht

CLAUDE.md → „Git: Review-Konvention" Punkt 3 verlangt die Freigabe durch mindestens einen Dev.
Ein Action-Lauf ist kein Dev. Das Repo-Ruleset erzwingt das ohnehin; die Doku hält es zusätzlich
fest, damit die Automatisierung nicht als Ersatz missverstanden wird (AC 5).

## Betroffene Files

| Datei | Art | Inhalt |
| ----- | --- | ------ |
| `.github/workflows/claude-pr-review.yml` | **neu** | Der Workflow |
| `.claude/skills/README.md` | geändert | Abschnitt „Automatischer Trigger via GitHub Action" inkl. Setup-Anleitung; `review-pr`-Zeile in der Skill-Tabelle ergänzt |
| `CLAUDE.md` | geändert | Review-Konvention: Verhältnis automatisches Review ↔ Dev-Freigabe |

## Implementierungsschritte

1. `.github/workflows/claude-pr-review.yml` anlegen — Trigger, Permissions, Checkout,
   `anthropics/claude-code-action@v1` mit `prompt: /review-pr <nr>` und
   `claude_args: --allowedTools "Bash,Read"`.
2. `.claude/skills/README.md`: neuen Abschnitt mit Setup-Anleitung (App installieren, Secret
   setzen, Test-PR) und den Grenzen des automatischen Laufs (kein Board, keine Dev-Freigabe).
3. `CLAUDE.md`: Review-Konvention um den Zusatz zur Automatisierung ergänzen.
4. YAML-Syntax verifizieren.

## Test-Strategie

Es entsteht kein Anwendungscode — die Definition of Done des Issues hält ausdrücklich fest, dass
JUnit, Vitest und Playwright hier nicht greifen. Nachweis läuft über:

- **YAML-Syntax:** `python3 -c "import yaml; yaml.safe_load(open(...))"`
- **AC-Abgleich:** jede AC einzeln mit `file:line` oder Kommando-Ergebnis belegt
- **AC 7 (Test-PR):** erst nach Installation der App und Hinterlegen des Secrets verifizierbar.
  Beides erfordert Browser-OAuth bzw. einen echten API-Key und kann aus dieser Session heraus
  nicht ausgeführt werden. Die Anleitung dafür ist Teil der Lieferung; die Verifikation selbst
  bleibt offen und wird im PR-Body als solche ausgewiesen.

## Acceptance Criteria (aus dem Issue)

- [ ] Claude GitHub App ist installiert, Secret (`ANTHROPIC_API_KEY` oder
      `CLAUDE_CODE_OAUTH_TOKEN`) liegt als Repo-Secret vor
- [ ] Workflow triggert auf `pull_request: [opened, synchronize, ready_for_review, reopened]`
      und ruft `/review-pr <PR-Nummer>` auf
- [ ] Permissions minimal: `contents: read`, `pull-requests: write`, `issues: write`,
      `id-token: write`
- [ ] Geklärt und dokumentiert, ob die GitHub App den Board-Schritt ausführen kann
- [ ] Verhältnis zur Dev-Freigabepflicht dokumentiert
- [ ] `.claude/skills/README.md` bzw. CLAUDE.md dokumentieren den automatischen Trigger
- [ ] An einem echten Test-PR verifiziert: Review kommt als `REQUEST_CHANGES` mit Inline-Threads
