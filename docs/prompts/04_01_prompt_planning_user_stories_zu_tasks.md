## Prompt

Wir haben in @docs/prompts/04_01_prompt_lab1_l1_jason.md tasks erstellen lassen und in @docs/prompts/04_01_prompt_lab1_l2_jason.md den ersten spring geplant. In @docs/prompts/04_01_prompt_lab1_l3_jason.md haben wir danach ein template für github erstellen lassen. Mit @docs/prompts/04_01_prompt_planning_github_project_jason.md haben wir dann gefragt ob du die tasks in einem github project erstellen kannst. 
Was uns nun fehlt ist die Verbindung von den Tasks zu den User Stories. Kann man das noch überall wo tasks vorkommen irgendwie verbinden? Eine Verbinung in der Markdown Dateien und in github? Bitte zuerst einfach erläutern wie du das machen würdest. Die Antwort kannst du dann auch unter @docs/prompts/04_01_prompt_planning_user_stories_zu_tasks.md hinterlegen. 

## Antwort

## Tasks ↔ User Stories verbinden

Es gibt zwei Orte, wo die Verbindung hergestellt werden muss: **Markdown-Dateien** (lokal im Repo) und **GitHub** (Issues + Projects). Beide Ansätze ergänzen sich.

---

### 1. Verbindung in Markdown-Dateien

#### A — Spalte "User Story" in den Backlog-Tabellen

In [docs/prompts/04_01_prompt_lab1_l1_jason.md](04_01_prompt_lab1_l1_jason.md) jede Tabelle um eine `US`-Spalte ergänzen:

```markdown
| ID | Task | SP | Label | Dep | US |
|----|------|----|-------|-----|----|
| FE-FC-01 | Wizard-Component … | 5 | Frontend | INFRA-02 | [US-03](../../requirements/US-03-fixkosten-wizard.md) |
| FE-PDF-01 | Upload-Component … | 3 | Frontend | INFRA-02 | [US-04](../../requirements/US-04-pdf-upload.md) |
```

Der Verweis ist ein relativer Link — im VS Code Editor und auf GitHub direkt klickbar.

#### B — Issue-Template um "User Story"-Feld erweitern

In [.github/ISSUE_TEMPLATE/task.md](../../.github/ISSUE_TEMPLATE/task.md) einen neuen Block hinzufügen:

```markdown
## User Story
Belongs to: <!-- z.B. [US-03](../../docs/requirements/US-03-fixkosten-wizard.md) -->
```

#### C — User Story-Dateien um Taskliste erweitern

In jeder Requirement-Datei (z.B. `docs/requirements/US-03-fixkosten-wizard.md`) einen Abschnitt "Tasks" hinzufügen:

```markdown
## Tasks (Backlog-Referenzen)
- BE-FC-01, BE-FC-02, BE-FC-03 (Backend)
- FE-FC-01, FE-FC-02, FE-FC-03 (Frontend)
- DB-03 (Datenbank)
```

So ist die Verbindung **bidirektional**: vom Task zur User Story und umgekehrt.

---

### 2. Verbindung in GitHub

#### Option A — Labels (einfachste Methode)

Labels `us-03`, `us-04`, `us-05`, `us-06` anlegen und bei jedem Task-Issue setzen:

```bash
gh label create "us-03" --color "#0075ca" --description "US-03: Fixkosten-Wizard"
gh label create "us-04" --color "#e4e669" --description "US-04: PDF-Upload"
```

Dann beim Erstellen eines Issues:
```bash
gh issue create --title "[BE-FC-01] FixedCost Entity + Repository" \
  --label "backend,us-03"
```

**Vorteil:** Im GitHub Projects Board nach User Story filtern — z.B. "zeige alle Tasks für US-04".

#### Option B — Issues für User Stories + Referenzen (empfohlen)

Zusätzlich zu den Task-Issues je ein **User Story-Issue** anlegen:

```bash
gh issue create --title "[US-03] Fixkosten erfassen (Onboarding-Wizard)" \
  --label "user-story,must-have"
```

In jedem Task-Issue dann im Body:
```markdown
Part of #42 (US-03 Fixkosten-Wizard)
```

GitHub verlinkt das automatisch — das User Story-Issue zeigt dann unter "Mentioned in" alle zugehörigen Tasks.

#### Option C — Sub-Issues (GitHub native, neueste Methode)

GitHub unterstützt seit 2025 nativ **Sub-Issues**: Ein User Story-Issue enthält direkt die Task-Issues als Kind-Elemente. Das gibt Fortschrittsbalken direkt im Issue und im Project Board.

Ablauf:
1. User Story-Issue erstellen (z.B. `#42 [US-03] Fixkosten-Wizard`)
2. Task-Issues erstellen (z.B. `#50 [BE-FC-01] FixedCost Entity`)
3. Im User Story-Issue: "Add sub-issue" → `#50` hinzufügen

**Vorteil:** Automatischer Fortschrittsbalken — z.B. "3/6 Tasks erledigt" direkt auf dem US-Issue sichtbar.

#### Option D — GitHub Projects: Benutzerdefiniertes Feld "User Story"

Im GitHub Project (Iterative development) ein Custom Field "User Story" hinzufügen:
- Typ: **Single select** (Werte: US-03, US-04, US-05, US-06)
- Bei jedem Task-Issue in der Project-Ansicht das Feld setzen

**Vorteil:** Im Board nach User Story gruppieren (`Group by: User Story`) — alle US-04-Tasks in einer Spalte, alle US-05-Tasks in der nächsten.

---

### Empfehlung: kombinierter Ansatz

| Wo | Was | Methode |
|----|-----|---------|
| Repo (Markdown) | Backlog-Tabellen | `US`-Spalte mit relativem Link |
| Repo (Markdown) | Issue Template | `Belongs to:` Feld |
| Repo (Markdown) | Requirement-Dateien | `Tasks:`-Abschnitt (bidirektional) |
| GitHub Issues | User Story-Issues | Separate Issues, `#nummer` Referenz im Task |
| GitHub Issues | Task-Issues | `Part of #X` im Body + Label `us-XX` |
| GitHub Projects | Board | Custom Field "User Story" + Grouping |

**Wichtig:** Sub-Issues (Option C) sind am elegantesten — aber nur sinnvoll, wenn ihr sowieso User Story-Issues anlegt. Wenn ihr nur Task-Issues habt, reichen Labels + `Part of #X` vollständig.