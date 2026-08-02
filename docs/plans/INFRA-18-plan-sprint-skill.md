# [INFRA-18] Sprint-Planung als /plan-sprint-Skill + Index für docs/plans/

- **Issue:** [#127](https://github.com/dfme/budget-buddy/issues/127)
- **Task-ID:** `INFRA-18`
- **Branch:** `feature/INFRA-18-plan-sprint-skill`
- **Story:** — (kein `us-*`-Label, reiner Tooling-/Prozess-Task)
- **Sprint:** Sprint 3
- **Bestätigt am:** 2026-07-31

## Ausgangslage

Die Sprint-Planung lief bisher als frei formulierter Prompt — `docs/prompts/05_01_prompt_planning_sprint_2.md`
und `..._sprint_3.md` sind derselbe Vorgang, zweimal neu beschrieben. Der Prompt lehrt dabei
Kontext, den das Repo längst kennt (3 Entwickler, Backlog im Board, Abhängigkeiten in den Issues),
und lässt Regeln aus CLAUDE.md aus, die für die Planung zentral sind: dass das Iteration-Feld
führend ist und Milestones **nicht** für Sprints verwendet werden. Dass Sprint 3 trotzdem sauber
wurde, lag daran, dass der Planende die Regeln im Kopf hatte.

`implement-issue` und `review-pr` decken die Ausführungshälfte ab. Die Planung ist der letzte
Schritt der Schleife, der noch ad hoc läuft.

Parallel dazu: 45 Pläne liegen flach in `docs/plans/`, ohne Einstiegspunkt.

## Entscheide (mit User bestätigt)

### 1. Priority folgt MoSCoW statt pauschal P1

Ausgangspunkt war der Wunsch, allen US-Issues `P1 - High` zu geben. Umgesetzt, dann verworfen:
wenn jedes Story-Issue dieselbe Priorität trägt, dupliziert das Feld nur das `us-*`-Label und
trägt keine Information. Stattdessen:

| MoSCoW | Priority      | Stories                    |
| ------ | ------------- | -------------------------- |
| Must   | `P1 - High`   | US-03, US-04, US-05, US-06 |
| Should | `P2 - Medium` | US-01, US-02, US-08, US-09, US-12, US-13, US-14 |
| Could  | `P3 - Low`    | US-07, US-10, US-11        |

Zuordnung über das `us-*`-Label (die Body-Zeile „Gehört zu der User-Story" ist nur in 14 von 73
Issues gepflegt und taugt nicht als Quelle). Mehrere Label → höchste Priorität gewinnt.
Bestand bereits korrigiert: 37× P1, 6× P2 (`us-01`).

Issues ohne `us-*`-Label (INFRA, DB, Bugs — aktuell 30 Stück) bekommen **keine** automatische
Priority. Sie werden nur als Lücke gemeldet.

### 2. Der Skill schreibt nicht von sich aus ins Board

Vorschlag als Markdown; Sprint-Iteration und Status erst nach separater, expliziter Bestätigung.
Die Einplanung bleibt eine Kapazitätsentscheidung des Teams.

### 3. Die Pläne selbst bleiben flach

Ein Ordner pro Sprint wäre die naheliegende Gliederung, schreibt aber eine Dimension fest:
Sprint-Zugehörigkeit ist eine Eigenschaft des Boards und ändert sich bei Carryover (#13 und #16
wurden in Sprint 2 geplant, in Sprint 3 fertig). Ordner hätten Umzüge erzwungen und die Historie
gebrochen. Stattdessen ein Index mit Bereich, Story und Sprint als gleichzeitigen Spalten.

Das einzige Unterverzeichnis ist `docs/plans/sprints/` — und das gliedert nicht die Pläne, sondern
nimmt eine **andere Artefakt-Art** auf (pro Sprint statt pro Issue). Ein eigenes Top-Level-Verzeichnis
braucht es dafür nicht; der Index globt nicht rekursiv und bleibt unberührt.

### 4. Der Index führt keine driftenden Werte

Nur Task-ID, Titel, Issue, Story, Sprint — alles unveränderlich, nachdem der Plan geschrieben ist.
**Status und Story Points bewusst nicht**: die leben im Board, ändern sich laufend, und eine Kopie
wäre ab ihrer Erzeugung veraltet.

### 5. Kein Skriptaufruf im Pro-Issue-Ablauf

Erste Fassung liess `implement-issue` einen Generator aufrufen. Verworfen — der Skill kennt alle
Werte ohnehin und hängt die Zeile selbst an. `scripts/plans-index.sh` bleibt Reparaturwerkzeug;
sein `--check` läuft in der Hygiene-Phase von `/plan-sprint`, also einmal pro Sprint statt einmal
pro Issue. Weil der einzige Aufrufer damit `/plan-sprint` ist, liegt das Skript auch in dessen
Commit, nicht im Ablage-Commit.

## Betroffene Files

**Neu**
- `.claude/skills/plan-sprint/SKILL.md`
- `scripts/plans-index.sh`
- `docs/plans/README.md`
- `docs/plans/INFRA-18-plan-sprint-skill.md` (diese Datei)
- `docs/plans/sprints/` (Zielverzeichnis der Sprint-Dokumente)

**Geändert**
- `.claude/skills/implement-issue/SKILL.md` — standardisierter Plan-Kopf inkl. Story/Sprint, Index-Zeile anhängen
- `CLAUDE.md` — Zeile in der Skill-Tabelle

## Implementierungsschritte

Zwei Commits im selben PR (bewusst ein Issue statt zwei — Abweichung von „eine ID = eine
Arbeitseinheit", vom User so entschieden):

1. **Commit 1 — Ablage:** `docs/plans/README.md`, Plan-Kopf-Standard und Index-Zeilen-Pflege in
   `implement-issue`, dieser Plan.
2. **Commit 2 — Skill:** `/plan-sprint` mit Hygiene-, Datenbasis-, Vorschlags- und
   Board-Schreibphase, `scripts/plans-index.sh` als dessen Werkzeug, `docs/plans/sprints/` als
   Zielverzeichnis, Eintrag in der Skill-Tabelle in CLAUDE.md.

## Test-Strategie

Keine Applikationslogik, also keine JUnit-/Vitest-/Playwright-Abdeckung. Verifikation stattdessen:

- `scripts/plans-index.sh` erzeugt 45 Zeilen, alle mit aufgelöster Issue-Nummer — inklusive der
  drei Pläne im Tabellen-Format und der acht in freier Prosa.
- `--check` läuft grün gegen den erzeugten Index und rot gegen einen manipulierten (beides
  ausgeführt, Exit-Code 1 bestätigt).
- Die von Hand angehängte Index-Zeile dieses Plans ist identisch mit der, die der Generator
  erzeugt — belegt, dass der Pro-Issue-Ablauf ohne Skript denselben Stand produziert.

## Acceptance Criteria

Siehe [#127](https://github.com/dfme/budget-buddy/issues/127) — Abnahme dort, gegliedert nach
Skill `/plan-sprint` und Ablage `docs/plans/`.
