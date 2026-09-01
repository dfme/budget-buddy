# [INFRA-19] Projekt-Skills sprachlich auf Deutsch vereinheitlichen

- **Issue:** [#129](https://github.com/dfme/budget-buddy/issues/129)
- **Task-ID:** `INFRA-19`
- **Branch:** `feature/INFRA-19-skills-deutsch`
- **Story:** — (kein `us-*`-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-09-01

## Ausgangslage

Die drei Projekt-Skills unter `.claude/skills/` sind pro Datei sprachlich uneinheitlich:

| Skill | Fliesstext vorher |
| ----- | ----------------- |
| `implement-issue` | praktisch komplett Englisch, nur die Schritt-Überschriften deutsch |
| `review-pr` | deutsch bis auf die Intro-Zeilen 9–10 |
| `plan-sprint` | deutsch ausser Intro und Usage-Absatz |

Der Sprachwechsel hat auf die Ausführung durch Claude keinen messbaren Einfluss. Der Preis ist
menschlich: wer `implement-issue` und `review-pr` nacheinander reviewt, wechselt mitten im Vorgang
die Sprache, und derselbe Begriff heisst mal „blockierend", mal „blocking". Vereinheitlicht wird
auf **Deutsch** — CLAUDE.md, Issues, PRs und Commit-Messages sind deutsch, und die Skill-Dateien
werden im Review von Menschen gelesen.

## Entscheide

### Scope-Erweiterung gegenüber den Acceptance Criteria

Die Breitensuche aus Schritt 2 des Skills hat zwei Stellen gefunden, die die ACs nicht nennen.
Beide wurden dem User vor der Planung vorgelegt und von ihm zur Umsetzung freigegeben:

1. **`review-pr/SKILL.md:9-10`** — die einzige englische Passage in `review-pr`, und sie enthält
   ausgerechnet „so that **blocking** ones actually block the merge". Die Tabelle im Issue
   behauptet, `review-pr` sei „praktisch komplett Deutsch"; das stimmt bis auf diese zwei Zeilen.
   AC 3 lässt Angleichung bei Begriffsabweichung ausdrücklich zu, und AC 5 nennt „blockierend" vs.
   „blocking" als den zu vereinheitlichenden Begriff — die Stelle fällt also unter beide ACs, nur
   listet sie keine davon auf. **Entscheid: mitübersetzen.**

2. **`## Usage` und `## Workflow` (jeweils alle drei Dateien)** — englische
   Strukturüberschriften über deutschem Fliesstext. AC 4 führt sie nicht in der Schutzliste: sie
   sind weder Board-Feld noch Statuswert, Label, Befehl oder Pfad. Nur in `implement-issue` zu
   übersetzen erzeugte genau die Inkonsistenz, die AC 5 verbietet. **Entscheid: in allen drei
   Dateien übersetzen** — `## Usage` → `## Verwendung`, `## Workflow` → `## Ablauf`.

`### 0. PREFLIGHT` bleibt stehen: technischer Lehnbegriff, in `implement-issue` und `review-pr`
bereits identisch, also weder Sprachwechsel noch Inkonsistenz.

### Nicht im Scope

`.claude/skills/README.md` bleibt **unverändert**. Die Datei wurde geprüft (alle 22 Überschriften
und der Fliesstext) und ist bereits vollständig deutsch — es gibt nichts anzugleichen.

### Glossar

Bindend für alle drei Dateien, damit AC 5 nachweisbar erfüllt ist:

| Englisch | Deutsch |
| -------- | ------- |
| blocking / blocking decision | blockierend / blockierende Entscheidung |
| findings | Befunde |
| proof, evidence | Nachweis |
| affected files | Betroffene Dateien (vorher „Betroffene **Files**") |
| Usage / Workflow | Verwendung / Ablauf |
| (when needed) | (bei Bedarf) |

**Unübersetzt** (AC 4): `Story Points`, `Priority`, `Area`, `Sprint`, `Status`, `Backlog`, `Todo`,
`In Progress`, `Review`, `Done`, `Blocked by`, `P0 - Critical` … `P3 - Low`, Label (`us-*`, `bug`,
`enhancement`), Task-ID-Präfixe, sämtliche Befehle, Code-Blöcke, Pfade und GraphQL-Queries. Dazu
die stehenden Fachbegriffe des Repos: `Acceptance Criteria`, `Definition of Done`, `PREFLIGHT`,
`Closes #NN`, `REQUEST_CHANGES`, `CategorizationPort`, `BigDecimal`.

## Betroffene Dateien

| Datei | Art | Umfang |
| ----- | --- | ------ |
| `.claude/skills/implement-issue/SKILL.md` | ändern | Intro, `## Usage`, `## Workflow`, Schritte 1a, 2, 3, 4, 5, 7, 9, 10, 11a. Schritte 0, 1b, 8 und 11b sind bereits deutsch |
| `.claude/skills/plan-sprint/SKILL.md` | ändern | Intro (Z. 8–10), `## Usage`, Usage-Absatz (Z. 18), `## Workflow` |
| `.claude/skills/review-pr/SKILL.md` | ändern | Intro (Z. 9–10), `## Usage`, `## Workflow` — sonst nichts |
| `.claude/skills/README.md` | **unverändert** | bereits vollständig deutsch |
| `docs/plans/INFRA-19-skills-deutsch.md` | neu | dieser Plan |
| `docs/plans/README.md` | ändern | eine Index-Zeile |

## Implementierungsschritte

1. `implement-issue/SKILL.md`: Intro (Z. 9) sowie `## Usage` und `## Workflow` übersetzen.
2. `implement-issue/SKILL.md`: Schritte 1a, 2, 3, 4 übersetzen — inklusive `(when needed)` →
   `(bei Bedarf)` und `Betroffene Files` → `Betroffene Dateien`.
3. `implement-issue/SKILL.md`: Schritte 5, 7, 9, 10 und 11a übersetzen. Der eingebettete
   Markdown-Header-Block und die Index-Zeile in Schritt 5 bleiben zeichengleich — sie stehen in
   Code-Fences und sind damit nach AC 4 geschützt.
4. `plan-sprint/SKILL.md`: Intro, `## Usage`, Usage-Absatz, `## Workflow`.
5. `review-pr/SKILL.md`: Intro-Zeilen 9–10, `## Usage`, `## Workflow` (Scope-Erweiterung oben).
6. Konsistenzlauf: Glossar-Greps über alle vier Dateien.
7. Diesen Plan und die Index-Zeile in `docs/plans/README.md` ablegen.

## Test-Strategie

Keine automatisierten Tests. Die Änderung fasst weder Backend- noch Frontend-Code an; die
Definition of Done im Issue streicht `mvn package`, `ng build`, Swagger-Sichtbarkeit und den
Happy-Path-Test ausdrücklich als n/a. Der Nachweis läuft stattdessen über den Diff:

| Prüfung | Kommando | Erwartung |
| ------- | -------- | --------- |
| Anweisungsgleichheit | `git diff main` Absatz für Absatz lesen | jede geänderte Passage gibt dieselbe Anweisung; kein Absatz entfällt oder kommt hinzu (AC 6) |
| Strukturinvarianz | `grep -c '^##'` je Datei vor/nach | Überschriftenzahl identisch |
| Code-Block-Invarianz | Fenced Blocks per `awk` extrahieren und gegen `main` diffen | leerer Diff (AC 4) |
| Schutzliste | Grep auf Board-Felder, Statuswerte, Label | Trefferzahl unverändert (AC 4) |
| Terminologie | `grep -niE 'blocking\|findings\|proof'` über alle drei Dateien | 0 Treffer ausserhalb von Code und Bezeichnern (AC 5) |

## Acceptance Criteria

- [ ] `.claude/skills/implement-issue/SKILL.md` ist vollständig auf Deutsch, inklusive
      Frontmatter-Beschreibung (bereits deutsch) und Fliesstext
- [ ] `.claude/skills/plan-sprint/SKILL.md`: Einleitung und Usage-Absatz sind auf Deutsch
      angeglichen — kein Sprachwechsel innerhalb der Datei
- [ ] `review-pr` bleibt inhaltlich unverändert; nur wenn Begriffe von den anderen beiden
      abweichen, wird angeglichen
- [ ] Board-Feldnamen, Statuswerte, Label, Befehle, Pfade und Code-Blöcke sind unübersetzt
- [ ] Terminologie ist über alle drei Skills konsistent — insbesondere „blockierend" vs.
      „blocking" einheitlich
- [ ] Keine inhaltliche Änderung am Ablauf eines Skills: der Diff ist reine Übersetzung, keine
      neue oder entfallene Anweisung

## Nachtrag aus der Umsetzung

Zwei Stellen wichen von der Planung ab; beide sind reine Präzisierungen desselben, bereits
bestätigten Entscheids und erweitern den Scope nicht:

- **`## Workflow` steht auch in `plan-sprint`** (dort Z. 64), nicht nur in `implement-issue` und
  `review-pr`. Der bestätigte Entscheid lautet „in allen drei Dateien übersetzen"; die Datei ist
  entsprechend mitgezogen worden. Wäre sie ausgelassen worden, hätte genau die Inkonsistenz
  bestanden, die AC 5 verbietet.
- **Schritt 11a von `implement-issue` war englisch**, nicht deutsch. Die Planung hatte Schritt 11
  pauschal als „bereits deutsch" geführt; das trifft nur auf 11b zu. 11a fällt ohnehin unter AC 1
  (Datei vollständig auf Deutsch) und ist mitübersetzt.
