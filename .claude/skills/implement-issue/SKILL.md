---
name: implement-issue
description: GitHub Issue end-to-end umsetzen — Issue einlesen und übernehmen (Assignee + Board-Status In Progress), Fragen klären, Plan präsentieren (mit Bestätigung), Branch erstellen, Code + Tests implementieren, Security-Review und lokalen Review durchführen (mit Bestätigung), PR öffnen und die Board-Karte auf Review setzen. Auslösen via /implement-issue <issue-number>.
argument-hint: "<issue-number>"
---

# implement-issue

Implement a GitHub Issue end-to-end: read the issue, ask clarifying questions if needed, present a plan for confirmation, implement with tests, run a security review and a local review, then open a PR.

## Usage

```
/implement-issue <issue-number>
```

## Workflow

### 0. PREFLIGHT

```bash
gh auth status
gh api repos/dfme/budget-buddy --jq '.permissions.push'   # erwartet: true
gh api graphql -f query='{ user(login:"dfme"){ projectV2(number:4){ viewerCanUpdate } } }'
```

Nötig ist der Scope `repo` **und** Write-Zugriff aufs Repo — anders als beim Reviewen genügt
Lesezugriff hier nicht, weil Schritt 6 einen Branch pusht und Schritt 10 via `gh pr create` einen
PR öffnet. Steht `push` auf `false`, hier stoppen und das melden, statt die Arbeit zu machen und
erst am Push zu scheitern. Setup und Fehlerbilder: [.claude/skills/README.md](../README.md).

Der dritte Aufruf prüft den Zugriff aufs [Sprint Board](https://github.com/users/dfme/projects/4)
für die Schritte 1b und 11b. **Er ist ausdrücklich nicht blockierend.** Fehlt der Scope `project` oder die
Freigabe am Board (`Could not resolve to a ProjectV2`), wird das einmal gemeldet und der Lauf geht
weiter — die Karte bleibt dann von Hand zu setzen. Ein fehlendes Board-Metadatum darf die
Implementierung nicht aufhalten. Der Assignee in Schritt 1b hängt nicht daran: der läuft über
`gh issue edit` und braucht nur `repo` plus Write.

### 1. EINLESEN UND ÜBERNEHMEN

**1a. Issue lesen.** Run `gh issue view <issue-number>` and read title, body, labels, and
assignees in full.

**1b. Issue übernehmen.** Das Issue wird der Person zugewiesen, die das Kommando abgesetzt hat,
und die Board-Karte auf `In Progress` gesetzt — **hier am Anfang, nicht erst beim Branch.** Der
Zweck ist, dass ein Teammitglied sofort sieht, dass jemand dran ist; nach dem Plan-Gate wäre das
Zeitfenster für Doppelarbeit bereits verstrichen.

Wer übernimmt, ist der bei `gh` angemeldete Account — in den Kommandos als `@me`:

```bash
gh api user --jq .login
gh issue view <issue-number> --json assignees --jq '[.assignees[].login]'
```

| Zustand | Vorgehen |
| ------- | -------- |
| keine Assignees | `gh issue edit <issue-number> --add-assignee @me` |
| bereits `@me` | nichts tun, nicht melden |
| **fremder Assignee** | **anhalten** — siehe unten |

Der fremde Assignee ist der wichtige Fall und das einzige **verbindliche Gate** in diesem Schritt.
Er bedeutet, dass jemand anderes das Issue schon hat. `--add-assignee` würde still einen zweiten
danebensetzen, und dann implementieren zwei Leute denselben Task — die Arbeit des einen ist am
Ende verloren. Deshalb hier **nicht** einfach melden und weiterlaufen, sondern anhalten und dem
User genau zwei Optionen vorlegen:

- **abbrechen** — der Normalfall. Nichts wird geschrieben, kein Branch, kein Assignee, keine
  Karte. Erst mit der Person reden, die drauf steht.
- **trotzdem übernehmen** — nur auf ausdrückliche Ansage. Dann `--add-assignee @me` zusätzlich
  zum bestehenden, den fremden nie entfernen, und den Umstand später im PR-Body benennen.

Ohne Antwort wird nicht weitergearbeitet: ein Lauf, der bis Schritt 7 durchläuft und erst dann
auffliegt, hat die Doppelarbeit bereits produziert.

Der Unterschied zu `review-pr` (dort ein blosser Hinweis) ist beabsichtigt und folgt aus der
Sache: zwei Reviews am selben PR sind ein Gewinn, zwei Implementierungen desselben Issues sind
verlorene Arbeit.

Dann die Board-Karte. Feld- und Options-IDs zuerst auflösen, nie raten — das Board ist ein
User-Project, seine IDs sind nirgends im Repo hinterlegt:

```bash
gh api graphql -f query='{ user(login:"dfme"){ projectV2(number:4){ id } } }'
gh project field-list 4 --owner dfme --format json \
  --jq '.fields[] | select(.name=="Status") | {fieldId: .id, options: [.options[] | {name, id}]}'
```

`--jq` ist der in `gh` eingebaute Filter und braucht das `jq`-Binary nicht — nur `--format json`
muss dabeistehen, sonst bricht der Aufruf mit *cannot use `--jq` without specifying `--format
json`* ab.

Die Item-ID der Karte hängt am Issue selbst; `gh issue`-Befehle zeigen sie nicht:

```bash
gh api graphql -f query='
{ repository(owner: "dfme", name: "budget-buddy") {
    issue(number: <issue-number>) {
      projectItems(first: 5) { nodes {
        id
        project { number }
        fieldValueByName(name: "Status") {
          ... on ProjectV2ItemFieldSingleSelectValue { name }
        } } } } } }'
```

Nur die Karte aus `project.number == 4` ist gemeint. Kommt die Liste leer zurück, liegt das Issue
nicht auf dem Board — melden und weitermachen, nicht selbst hinzufügen.

Geschrieben wird nur aus `Backlog` oder `Todo` heraus:

```bash
gh project item-edit \
  --project-id <projekt-id> \
  --id <item-id> \
  --field-id <status-field-id> \
  --single-select-option-id <options-id von "In Progress">
```

| Status der Karte | Vorgehen |
| ---------------- | -------- |
| `Backlog` / `Todo` | auf `In Progress` setzen |
| `In Progress` | so lassen — jemand arbeitet bereits daran, zusammen mit dem Assignee-Befund bewerten |
| `Review` / `Done` | **nicht** zurücksetzen, melden und fragen — das Issue gilt als weiter fortgeschritten, als der Lauf annimmt |

Danach mit einer erneuten Abfrage verifizieren, nicht auf den Erfolg der Mutation vertrauen, und
dem User in einem Satz sagen, was gesetzt wurde.

**Rückabwicklung, wenn der Lauf abbricht.** Weil hier früh übernommen wird, hinterlässt ein
Abbruch in Schritt 3 oder 4 — der User verwirft den Plan oder bricht ab — eine falsche Spur auf
dem Board. In diesem Fall Assignee und Status wieder auf den vorher notierten Zustand
zurückdrehen und das dem User bestätigen. Den Ausgangszustand deshalb festhalten, bevor
geschrieben wird.

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

### 8. SECURITY-REVIEW

Läuft **vor** dem allgemeinen Review, weil ein Sicherheitsbefund die Implementierung ändert und
nicht nur den PR-Text. Der Grund steht in README.md als Risiko #2: die App hält Kontoauszüge, ein
Datenleck ist existenzbedrohend. Ein Bug in der Safe-to-Spend-Rechnung zeigt eine falsche Zahl; ein
Bug in der Mandantentrennung zeigt Laras Kontoauszug an Marc.

**Nur prüfen, was der Diff berührt.** Ein Doku-PR braucht keinen Upload-Check. Die Matrix
entscheidet, nicht das Gefühl:

| Berührt der Diff … | dann prüfen |
| ------------------ | ----------- |
| Repository- oder Service-Zugriff auf Nutzerdaten | 1 Mandantentrennung |
| einen Controller-Endpoint oder `SecurityConfig` | 2 Endpoint-Exposition |
| Auth, JWT, Cookie, Passwörter | 3 ADR-7-Invarianten |
| `application*.properties`, `render.yaml`, CI-Workflow, `pom.xml`, Doku mit Beispielwerten | 4 Secrets |
| Datei-Upload oder PDF-Parsing | 5 Upload-Grenzen |
| Claude-Call oder Prompt-Text | 6 Datenminimierung + Prompt-Injection |
| Fehlerbehandlung oder Logging | 7 Ausgabe-Hygiene |
| Kontolöschung oder Consent (US-02, US-14) | 8 nDSG-Pfade |
| nur Markdown/Doku | nur 4 |

Jeder Punkt braucht einen **Nachweis**: Kommando plus Ergebnis oder `file:line`. „Sieht sauber aus"
ist kein Nachweis — dieselbe Regel wie bei den ACs in Schritt 9.

**1. Mandantentrennung** — die wahrscheinlichste echte Lücke in dieser App. Jede Query auf
Nutzerdaten muss auf den authentifizierten User eingeschränkt sein. Ein `findById(id)` auf einer
Entity mit User-Bezug ist praktisch immer ein IDOR: wer die ID kennt oder hochzählt, liest fremde
Transaktionen.

```bash
git diff main -- 'backend/**/*.java' | grep -nE 'findById|findAll|getReferenceById|deleteById|@Query'
```

Für jeden Treffer belegen, **wo** die User-Einschränkung passiert. „Der Controller prüft das schon"
genügt nicht, wenn der Service auch von anderswo aufrufbar ist — die Einschränkung gehört dorthin,
wo die Query steht. Gegenprobe im Test: User B greift auf die Ressource von User A zu und bekommt
404 oder 403. Fehlt dieser Test, ist die Trennung **unbelegt** — ein grüner Happy-Path beweist sie
nicht.

**2. Endpoint-Exposition** — steht ein neuer oder geänderter Endpoint hinter der
Authentifizierung? `permitAll`-Listen wachsen unauffällig:

```bash
grep -rn 'permitAll\|requestMatchers' backend/src/main/java --include='*.java'
```

`--include` muss gequotet sein: unter zsh versucht die Shell `*.java` sonst selbst zu expandieren
und bricht mit `no matches found` ab, bevor `grep` überhaupt startet.

Freigaben wirken in beide Richtungen: `2c07cba` musste `/error` freigeben, damit 408/409 den Client
erreichen statt ihn als 401 auszuloggen. Eine Freigabe ist deshalb nie „nur eine Zeile" — begründen,
warum genau dieser Pfad ohne Auth auskommt.

**3. ADR-7-Invarianten** — vier Aussagen, die der Code nicht aufweichen darf:

- JWT ausschliesslich im httpOnly-Cookie mit `SameSite=Strict`; **kein** Token in `localStorage`
  oder `sessionStorage`, **kein** Bearer-Header im Client
- `app.cookie.secure=true` im prod-Profil (abgedeckt durch `JwtCookieFactoryTest`, nicht durch E2E)
- Passwörter bcrypt-gehasht, nie im Log, nie in einer Response
- JWT-Secret und `ANTHROPIC_API_KEY` nur aus Umgebungsvariablen

```bash
git diff main | grep -inE 'localStorage|sessionStorage|Bearer |Authorization'
```

Jeder Treffer im Frontend widerspricht ADR-7 und ist blockierend. Diese Aussage stand in ADR-0,
ADR-2 und CLAUDE.md schon dreimal falsch da und brauchte drei Runden zum Zurückdrehen (#103, #115,
#117) — der Code darf sie nicht ein viertes Mal aufweichen.

**4. Secrets** — der einzige Check, der immer läuft, auch bei reinen Doku-Änderungen:

```bash
git diff main | grep -inE 'sk-ant-|password *=|secret *=|api[_-]?key *=|token *='
git diff main --name-only | grep -E '\.env|\.properties$|\.ya?ml$'
```

Ein Klartext-Treffer ist blockierend **und** löst nach CLAUDE.md („Keine Secrets im Git") sofortige
Key-Rotation plus Incident-Assessment nach nDSG aus — das gilt schon beim Commit auf dem Feature-
Branch, nicht erst beim Merge. Beispielwerte in Doku mitprüfen: ein realistisch aussehender Key wird
kopiert.

Bekannter Selbsttreffer: berührt ein PR diese Datei, matcht das Suchmuster **sich selbst**. Ein
Treffer, der auf die Musterdefinition oben zeigt, ist kein Befund — jeder andere schon.

**5. Upload-Grenzen** — nur wenn der PR den PDF-Pfad berührt:

- Grössenlimit steht in `application.properties` (`max-file-size=10MB`, `max-request-size=11MB`).
  Der Check ist damit eine **Regressionsbremse**, keine Lückensuche: lockert ein PR diese Werte
  oder umgeht sie, ist das begründungspflichtig. Das Parsen läuft laut CLAUDE.md weiterhin im
  Request (ADR-14) — ohne Limit ist es ein DoS-Hebel, keine Komfortlücke.
- Endung und `Content-Type` sind keine Beweise für den Inhalt. Was zählt, ist das Verhalten von
  PDFBox: `Loader.loadPDF()` in try-with-resources, `ParseException` als Fehler an den Caller
  (CLAUDE.md), passwortgeschützte PDFs mit klarer Meldung statt Stacktrace.
- Upload nie in einen vom Nutzer beeinflussbaren Pfad schreiben.

**6. Datenminimierung und Prompt-Injection** — beim Claude-Call:

- Es geht **nur** der Transaktionstext raus — nie Kontonummer, Saldo, Name, E-Mail oder User-ID.
  Genau das ist der offene Punkt aus #134 (BE-CAT-06). Solange der auf `P3` liegt, ist ein neuer
  Call, der *mehr* mitschickt, eine Verschlechterung und blockierend.
- Der Transaktionstext ist Fremdeingabe im Prompt: ein Händlername kann Anweisungen enthalten. Die
  Antwort deshalb **gegen die feste Kategorienliste validieren** und alles ausserhalb auf
  `Sonstiges` abbilden — nie ungeprüft persistieren.
- Timeout gesetzt, Fallback `Sonstiges`, Circuit Breaker (BE-CAT-02) nicht umgangen.

**7. Ausgabe-Hygiene**:

- Keine Stacktraces, SQL-Fragmente oder Dateipfade in einer Response
- Keine Transaktionstexte, Beträge, Tokens, Passwörter oder E-Mail-Adressen im Log. Render-Logs sind
  nicht Teil der Datenbank, aber sie enthalten dann dieselben Daten — mit anderer Zugriffskontrolle.

```bash
git diff main | grep -inE 'printStackTrace|log\.(info|debug|warn).*(amount|betrag|token|password|email)'
```

**8. nDSG-Pfade** — bei US-02/US-14: Kontolöschung muss wirklich löschen, über alle abhängigen
Tabellen (`transactions`, `fixed_costs`, `savings_goals`, `category_lookup`-Korrekturen,
`import_jobs`). Nachweis ist ein Test, der nach dem Löschen die abhängigen Zeilen zählt — nicht die
Annahme, dass ein Cascade greift.

#### Befunde einsortieren

**🔴 Blockierend, wenn der eigene Diff es verursacht** → **kein PR.** Zurück zu Schritt 7, fixen,
Security-Review wiederholen. Ein Blocker, der als „bekannt" im PR-Body steht, ist trotzdem ein
Blocker.

**Vorbestehende Lücken, die der Diff nicht verursacht hat** → Folge-Issue nach CLAUDE.md-Konvention
(neue freie ID im betroffenen Bereich, Label `bug`, ohne Milestone und ohne Sprint), und im PR-Body
benennen. **Nicht stillschweigend mitfixen:** wer beim Umsetzen von BE-FC-01 nebenbei die Auth
härtet, liefert einen PR, den niemand mehr sinnvoll reviewen kann. Und nicht gegen die eigene Arbeit
als Blocker werten — sonst hält man den Falschen auf.

Das Ergebnis geht in den PR-Body (Schritt 10): pro zutreffender Matrix-Zeile ein Satz mit Nachweis,
plus jede bewusste Auslassung mit Begründung.

### 9. LOKALER REVIEW
Review all changes before creating a PR:
- Run `git diff main` and check for correctness and convention violations — security is covered
  separately in step 8, so do not re-do it here
- List every AC individually with its concrete proof — command plus result, or `file:line`
- Flag ACs whose only proof is the same search the issue itself proposed: those are unverified,
  not confirmed
- Present the findings from **both** step 8 and step 9 to the user in one go — two separate
  confirmation gates for one PR are friction without gain
- Wait for explicit user confirmation that the PR may be created

### 10. PR ERSTELLEN
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
- **Security-Review** — the result from step 8: one line per applicable row of the trigger matrix
  with its proof, plus any deliberate omission with a reason. Rows the diff does not touch are left
  out; do not pad the section with "n/a". If the review produced follow-up issues for pre-existing
  gaps, link them here.

### 11. ISSUE VERLINKEN UND AUF REVIEW SETZEN

**11a. Backlink.** `gh pr create` prints the new PR URL. Post a backlink comment on the issue so
the link is also explicit in the issue timeline:

```bash
gh issue comment <issue-number> --body "🔀 PR erstellt: <pr-url>"
```

Confirm to the user that PR and issue are now linked in both directions (PR → issue via
`Closes #<issue-number>` + Development panel, issue → PR via the backlink comment).

**11b. Board-Karte auf `Review`.** Mit dem offenen PR ist die Implementierung abgegeben — die
Karte steht ab hier falsch auf `In Progress`. Sie wandert deshalb sofort weiter, ohne auf den
ersten Reviewer zu warten: Item-ID, Feld-ID und Options-ID werden genauso aufgelöst wie in
Schritt 1b, gesetzt wird `Review`.

| Status der Karte | Vorgehen |
| ---------------- | -------- |
| `In Progress` | auf `Review` setzen — der Normalfall, so hat Schritt 1b sie hinterlassen |
| `Backlog` / `Todo` | ebenfalls auf `Review` — sie ist dann nur nie mitgezogen worden (fehlender Board-Zugriff in Schritt 1b) |
| `Review` | so lassen |
| `Done` | **nicht** anfassen, melden — ein offener PR auf ein erledigtes Issue ist selbst ein Befund |

Der Assignee bleibt unverändert: das Issue gehört weiterhin der Person, die implementiert hat.
`/review-pr` setzt später nur den PR auf den Reviewer, nicht das Issue.

Fehlt der Board-Zugriff, gilt dasselbe wie in Schritt 1b — einmal melden, nicht abbrechen. Der
PR ist zu diesem Zeitpunkt bereits offen; ihn wegen eines Metadatums als gescheitert zu melden,
wäre schlicht falsch.
