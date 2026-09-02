---
name: plan-sprint
description: Sprint planen — Backlog-Hygiene prüfen, Velocity und Carryover aus dem Board ableiten, Abhängigkeiten kreuzprüfen, Sprint-Vorschlag als docs/plans/sprints/SPRINT-NN.md ablegen und das Board erst nach separater Bestätigung schreiben. Auslösen via /plan-sprint.
---

# plan-sprint

Plan the next sprint from the board: check backlog hygiene, derive capacity from real velocity,
collect carryover, cross-check dependencies, and produce a sprint proposal. The board is written
only on a separate, explicit go-ahead.

## Usage

```
/plan-sprint
```

No argument. The sprint number is derived from the board and confirmed with the user.

## Die zentrale Regel

**Der Skill schlägt vor, das Team entscheidet.** Die Einplanung ist eine Kapazitätsentscheidung,
keine Ableitung aus Daten — deshalb schreibt dieser Skill nichts ins Board, solange der User es
nicht nach dem Vorschlag ausdrücklich verlangt. Das gilt auch, wenn der Vorschlag offensichtlich
richtig aussieht.

Zweite Regel, die daraus folgt: **nie einen bestehenden Wert überschreiben.** Leere Felder füllen
ist Hygiene, gesetzte Felder ändern ist eine Entscheidung. Ein manuell auf `P0 - Critical`
eskaliertes Issue bleibt P0, auch wenn MoSCoW etwas anderes sagt — melden, nicht korrigieren.

## Board-Zugriff

Das Board ist ein **User-Project** (`users/dfme/projects/4`), kein Repo-Project. `gh issue`-Befehle
sehen dessen Felder nicht — alles läuft über GraphQL. Feld- und Options-IDs zuerst auflösen, nie
raten:

```bash
gh project field-list 4 --owner dfme --format json
```

Dieser Aufruf ist zugleich der Preflight. Scheitert er, hier stoppen und melden, statt mit
unvollständigen Board-Daten weiterzuplanen. Zwei Ursachen, die auseinandergehalten werden
müssen — `gh auth status` unterscheidet sie:

- **Projects-Scope fehlt** — die Meldung nennt ihn beim Namen
  (`missing required scopes [read:project]`). `repo` allein genügt für Boards nicht. Fix:
  `gh auth refresh -h github.com -s repo,project`. Zum Lesen reicht `read:project`; Schritt 5
  („Board schreiben") braucht `project`.
- **Scope ist da, Aufruf scheitert trotzdem** — dann fehlt die Freigabe am Board selbst. Es ist
  ein privates User-Project; Repo-Zugriff vererbt keinen Board-Zugriff. Der Board-Owner (dfme)
  muss die Person unter *Settings → Manage access* eintragen; am Token schrauben hilft nicht.

Welcher der beiden Fälle vorliegt, beantwortet dieser Aufruf direkt — auflösender Node bedeutet
Lesezugriff, `viewerCanUpdate: true` deckt zusätzlich Schritt 5:

```bash
gh api graphql -f query='{ user(login:"dfme"){ projectV2(number:4){ viewerCanUpdate } } }'
```

Details in [.claude/skills/README.md](../README.md).

Relevante Felder: `Status`, `Priority`, `Area`, `Story Points` (Number), `Sprint` (Iteration).

## Workflow

### 1. SPRINT BESTIMMEN

Das **Iteration-Feld `Sprint` ist führend** — nicht Milestones. Die bestehenden Milestones
`Sprint 1`–`Sprint 3` sind Historie und werden nicht fortgeführt (siehe docs/CONVENTIONS.md).

Aktuelle und kommende Iteration auslesen, dem User die erkannte Sprint-Nummer nennen und
bestätigen lassen, bevor irgendetwas anderes passiert.

### 2. BACKLOG-HYGIENE

Läuft **vor** der Planung, weil ein Vorschlag auf lückenhaften Daten wertlos ist. Alles hier ist
melden oder nachfragen — nur der Priority-Schritt schreibt, und auch der nur in leere Felder.

**2a. Priority aus MoSCoW.** Die Zuordnung Issue → User Story läuft über das `us-*`-Label, nicht
über die Body-Zeile „Gehört zu der User-Story" (die ist nur in einem Bruchteil der Issues
gepflegt). MoSCoW steht in der User-Story-Tabelle in docs/requirements/README.md:

| MoSCoW | Priority      |
| ------ | ------------- |
| Must   | `P1 - High`   |
| Should | `P2 - Medium` |
| Could  | `P3 - Low`    |

- Mehrere `us-*`-Label an einem Issue → höchste Priorität gewinnt.
- Nur setzen, wo `Priority` leer ist. Abweichungen zwischen gesetztem Wert und MoSCoW auflisten
  und dem User vorlegen — nicht anfassen.
- **Issues ohne `us-*`-Label** (INFRA, DB, Bugs) bekommen **keine** automatische Priority.
  MoSCoW gilt für User Stories; für alles andere gibt es keine ableitbare Wahrheit. Solche Issues
  ohne Priority als Lücke auflisten, damit das Team im Planning bewusst entscheidet.

**2b. Fehlende Metadaten.** Issues ohne `Story Points` oder ohne `Area` auflisten. Für Issues, die
im Vorschlag landen sollen, eine SP-Schätzung anhand vergleichbarer abgeschlossener Issues
vorschlagen (gleicher Bereich, ähnlicher Zuschnitt, mit Nennung des Vergleichsissues) — und erst
nach Bestätigung eintragen. Ohne SP ist die Kapazitätsrechnung geraten, nicht gerechnet.

**2c. Plan-Index.** Einmal pro Sprint prüfen, ob der Index zu den Plänen passt:

```bash
scripts/plans-index.sh --check
```

Bei Abweichung `scripts/plans-index.sh` ausführen und das Ergebnis mit in den Sprint-Commit nehmen.
Diese Kadenz ist Absicht: `/implement-issue` hängt seine Index-Zeile selbst an, hier wird nur
nachkontrolliert, ob dabei etwas durchgerutscht ist.

### 3. DATENBASIS

**3a. Velocity.** Erledigte Story Points pro abgeschlossenem Sprint aus dem Board rechnen
(`Status = Done`, gruppiert nach Iteration). Daraus eine Kapazität vorschlagen — Mittel der
letzten Sprints, nicht der beste Sprint. Die Zahl dem User zur Bestätigung oder Korrektur
vorlegen; sie ist ein Vorschlag, kein Ergebnis.

**3b. Carryover.** Issues des laufenden Sprints mit Status `In Progress` oder `Review` gehören in
den neuen Sprint, bevor Neues dazukommt. Sie als eigenen Block ausweisen — mit Original-SP und
dem Hinweis, dass nur der **Restaufwand** ansteht. Nicht voll auf die Kapazität anrechnen, aber
auch nicht verschweigen: Carryover bindet das Team zu Sprint-Beginn.

**3c. Abhängigkeiten.** `Blocked by`-Relationships der Kandidaten lesen (native Relationships, nicht
Freitext im Issue). Prüfen:

- Hängt ein eingeplantes Issue an einem **nicht** eingeplanten? → Warnung, entweder mit einplanen
  oder aus dem Vorschlag nehmen.
- Hängen mehrere eingeplante Issues in einer Kette? → das ist der kritische Pfad, er gehört in die
  Bearbeitungsreihenfolge.

Über die REST-API lesbar:

```bash
gh api repos/dfme/budget-buddy/issues/<nr>/dependencies/blocked_by --jq '.[].number'
```

### 4. VORSCHLAG ERSTELLEN

Ablage: `docs/plans/sprints/SPRINT-NN.md` (zweistellig, `SPRINT-04.md`). Struktur wie beim
Sprint-3-Vorschlag, der sich bewährt hat:

1. **Kopf** — Team-Grösse, Neu-Commitment in SP, Sprint-Nummer
2. **Sprint-Ziel** — ein Satz, fachlich formuliert: welcher Nutzen ist am Ende lauffähig
3. **Ausgangslage** — was steht aus den Vorsprints, welche Carryover-Items binden das Team
4. **Sprint-Backlog** — nach Themenblöcken, nicht als flache Liste. Pro Block eine Tabelle mit
   Issue, Titel, SP, Abhängigkeit, „bereit wann"
5. **Empfohlene Bearbeitungsreihenfolge** — pro Entwickler, mit explizit benanntem kritischem Pfad
6. **Bewusst nicht in Sprint N** — mit Begründung je Bereich. Dieser Abschnitt ist kein Beiwerk:
   er verhindert, dass im Planning dieselbe Diskussion nochmal geführt wird
7. **Risiken & Gegenmassnahmen** — konkret, mit Tauschoption bei Ausfall des kritischen Pfads

Den Vorschlag dem User präsentieren und auf Rückmeldung warten. Bei Änderungswünschen überarbeiten
und **vollständig** neu präsentieren, nicht nur das Delta.

### 5. BOARD SCHREIBEN — nur auf ausdrücklichen Zuruf

Erst wenn der User nach dem Vorschlag ausdrücklich sagt, dass das Board geschrieben werden soll:

- `Sprint`-Iteration auf den neuen Sprint setzen
- `Status` von `Backlog` auf `Todo` setzen

Beides nur für die Issues aus dem bestätigten Vorschlag. Danach auflisten, was geschrieben wurde,
und mit einer erneuten Board-Abfrage verifizieren — nicht auf den Erfolg der Mutation vertrauen.

Bleibt der Zuruf aus, ist der Lauf trotzdem vollständig: das Markdown-Dokument ist das Ergebnis.

## Was dieser Skill nicht tut

- **Keine Issues anlegen.** Fehlt ein Task, gehört er über das Issue-Template ins Backlog — mit
  eigener Task-ID, ohne Milestone und ohne Sprint.
- **Kein Sprint-Abschluss, keine Retro.** Der Skill plant nach vorn; Aufräumen des alten Sprints
  ist ein eigener Vorgang.
- **Keine Milestones.** Milestone und Iteration-Feld sind unabhängig, und zwei parallel gepflegte
  Sprint-Quellen sind im Juli 2026 nachweislich auseinandergelaufen.
- **Kein Umpriorisieren bestehender Werte.** Siehe „Die zentrale Regel".
