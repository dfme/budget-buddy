---
name: review-pr
description: GitHub Pull Request reviewen — PR und Issue einlesen und übernehmen (Assignee am PR, PR-Karte In Progress, Issue-Karte Review), Diff gegen die Gegenseite kreuzprüfen, Tests ausführen, Befunde in blockierend/nicht-blockierend trennen, Review präsentieren (mit Bestätigung), als REQUEST_CHANGES mit Inline-Threads absetzen. Auslösen via /review-pr <pr-number>.
argument-hint: "<pr-number>"
---

# review-pr

Review a GitHub Pull Request and post the findings so that blocking ones actually
block the merge.

## Usage

```
/review-pr <pr-number>
```

## Die zentrale Mechanik

Alles in diesem Skill folgt aus einer einzigen Eigenschaft von GitHub:

> **Jeder Review-Thread blockiert. Einen nicht-blockierenden Thread gibt es nicht.**

Der Ruleset „protect main" hat `required_review_thread_resolution: true`. Diese Regel sieht
ausschliesslich **Inline-Review-Threads** — Kommentare, die an einer Zeile im Diff hängen und
einen „Resolve conversation"-Button haben. Ein Review-**Body** ist für sie unsichtbar, egal wie
deutlich dort „Änderungen erbeten" steht.

Daraus folgt die Aufteilung:

| Befund | Wohin | Wirkung |
| ------ | ----- | ------- |
| Blockierend | Inline-Thread am Diff | PR geht auf `BLOCKED` |
| Nicht blockierend | Review-Body | rein informativ |

Und daraus folgt die Warnung: Einen 🟡-Punkt „nur zur Info" als Thread zu setzen, blockiert den
PR ungewollt. Die Klassifikation ist deshalb eine echte Entscheidung, kein Formatierungsdetail.

## Workflow

### 0. PREFLIGHT

```bash
gh auth status
gh api user --jq .login
gh pr view <pr-number> --json author --jq .author.login
gh api repos/dfme/budget-buddy --jq '.permissions.push'
gh api graphql -f query='{ user(login:"dfme"){ projectV2(number:4){ viewerCanUpdate } } }'
```

Bricht `gh auth status` ab, hier stoppen und die Ursache melden statt in Schritt 1 an einem
`gh`-Fehler zu stranden — nötig ist der Scope `repo`. Setup und die häufigen Fehlerbilder:
[.claude/skills/README.md](../README.md). Ein Timeout allein ist kein Befund: `gh auth status`
validiert das Token per API-Call und schlägt bei schlechter Verbindung fehl, obwohl die
Anmeldung intakt ist — einmal wiederholen.

**Sind die beiden Logins identisch, hier abbrechen.** GitHub lehnt `REQUEST_CHANGES` am eigenen
PR mit `HTTP 422` ab, und `COMMENT` blockiert nicht (siehe Schritt 8). Ein Review, der nicht
blockieren kann, verfehlt den Zweck dieses Skills — das muss vor der Arbeit feststehen, nicht
erst beim Absetzen.

Die letzten beiden Aufrufe gehören zur Übernahme in Schritt 1c und sind **beide nicht
blockierend**. Der Review selbst kommt weiterhin mit Lesezugriff aus; nur das Setzen des
Assignees braucht `push: true` und das Setzen der Karten den Scope `project` plus die Freigabe am
Board. Fehlt eines davon, wird es einmal gemeldet und der Review läuft normal weiter — ohne
Assignee bzw. ohne Kartenbewegung. Ein fehlendes Board-Metadatum darf einen Review nicht
verhindern.

### 1. EINLESEN UND ÜBERNEHMEN

**1a. PR und Issue lesen.**

```bash
gh pr view <pr-number> --json number,title,author,body,state,headRefName,baseRefName,mergeStateStatus,reviewDecision
gh pr diff <pr-number> --name-only
gh pr diff <pr-number>
```

Das verlinkte Issue mitlesen (`Closes #NN` im PR-Body) — die **Acceptance Criteria und die
Definition of Done dort sind der Massstab**, nicht der PR-Text.

**1b. Bestehende Reviews und Threads immer mitlesen** — auch bei `reviewDecision: null`, denn ein
`COMMENTED`-Review ohne Threads lässt das Feld leer, obwohl inhaltlich schon reviewt wurde:

```bash
gh api graphql -f query='
{
  repository(owner: "dfme", name: "budget-buddy") {
    pullRequest(number: <pr-number>) {
      reviews(first: 20) { nodes { databaseId author{login} state submittedAt body } }
      reviewThreads(first: 30) {
        nodes { isResolved path line comments(first: 5) { nodes { author{login} body } } }
      }
    }
  }
}'
```

Reviews laufen parallel. Wer das überspringt, postet Threads zu Befunden, die schon jemand
anders angebracht hat — der Autor muss dann dasselbe Problem zweimal auflösen.

`databaseId` wird für Schritt 8 mitgeholt: dort geht es darum, einen eigenen alten
`CHANGES_REQUESTED`-Review aufzuheben, und das REST-Dismiss-Endpoint verlangt die numerische ID,
nicht die GraphQL-Node-ID.

**1c. Review übernehmen.** Erst jetzt, nach 1b: das Ergebnis von 1b entscheidet mit, ob überhaupt
übernommen werden soll. Reviewt bereits jemand anders, ist eine stille zweite Übernahme genau der
Fall, den 1b verhindern will.

Übernommen wird in drei Schritten — der PR bekommt den Reviewer als Assignee, die PR-Karte geht
auf `In Progress`, die Karte des verlinkten Issues auf `Review`:

```bash
gh pr view <pr-number> --json assignees --jq '[.assignees[].login]'
gh pr edit <pr-number> --add-assignee @me
```

`@me` ist der bei `gh` angemeldete Account, also die Person, die das Kommando abgesetzt hat.
Assignee und Reviewer sind auf GitHub zwei verschiedene Felder; gesetzt wird **Assignee**, weil
das Board diese Spalte anzeigt.

Hängt bereits ein fremder Assignee dran, ist das **ein Hinweis, kein Gate**: melden, `@me`
zusätzlich setzen, weiterarbeiten. Zwei Leute dürfen denselben PR reviewen — mehr Augen finden
mehr, und Schritt 1b sorgt dafür, dass keine doppelten Threads entstehen. Der fremde Assignee
wird dabei nie entfernt. (`implement-issue` hält an derselben Stelle an; dort wäre das Ergebnis
doppelte Implementierungsarbeit, hier ist es ein zweites Paar Augen.)

Feld- und Options-IDs zuerst auflösen, nie raten; das Board ist ein User-Project, seine IDs sind
nirgends im Repo hinterlegt:

```bash
gh api graphql -f query='{ user(login:"dfme"){ projectV2(number:4){ id } } }'
gh project field-list 4 --owner dfme --format json \
  --jq '.fields[] | select(.name=="Status") | {fieldId: .id, options: [.options[] | {name, id}]}'
```

`--jq` ist der in `gh` eingebaute Filter und braucht das `jq`-Binary nicht — nur `--format json`
muss dabeistehen, sonst bricht der Aufruf mit *cannot use `--jq` without specifying `--format
json`* ab.

Die Item-IDs beider Karten in einem Aufruf holen (`<issue-number>` ist das `Closes #NN` aus 1a):

```bash
gh api graphql -f query='
{ repository(owner: "dfme", name: "budget-buddy") {
    pullRequest(number: <pr-number>) {
      projectItems(first: 5) { nodes {
        id project { number }
        fieldValueByName(name: "Status") { ... on ProjectV2ItemFieldSingleSelectValue { name } } } } }
    issue(number: <issue-number>) {
      projectItems(first: 5) { nodes {
        id project { number }
        fieldValueByName(name: "Status") { ... on ProjectV2ItemFieldSingleSelectValue { name } } } } } } }'
```

Nur die Karten aus `project.number == 4` sind gemeint. PRs landen per `Auto-add to project` auf
dem Board, aber nicht garantiert — kommt die Liste leer zurück, melden und weitermachen, nicht
selbst hinzufügen. Nennt der PR-Body kein `Closes #NN`, entfällt der Issue-Teil; das ist dann
zugleich ein 🔴-Befund für Schritt 5, weil ohne Closing-Keyword das Issue beim Merge offen bleibt.

Gesetzt wird je Karte mit:

```bash
gh project item-edit \
  --project-id <projekt-id> \
  --id <item-id> \
  --field-id <status-field-id> \
  --single-select-option-id <options-id>
```

| Karte | Ausgangsstatus | Vorgehen |
| ----- | -------------- | -------- |
| PR | `Backlog` / `Todo` | auf `In Progress` |
| PR | `In Progress` | so lassen — jemand reviewt bereits; melden und weiterarbeiten |
| PR | `Review` / `Done` | **nicht** zurücksetzen, melden und fragen |
| Issue | `Review` | so lassen — der Normalfall, wenn der PR aus `implement-issue` kam (dessen Schritt 11b) |
| Issue | `Todo` / `In Progress` | auf `Review` — greift bei PRs, die von Hand geöffnet wurden |
| Issue | `Backlog` / `Done` | **nicht** anfassen, melden — ein PR auf ein Backlog-Issue oder ein bereits erledigtes Issue ist selbst ein Befund |

**Der Assignee des Issues wird nie angefasst.** Der gehört der Person, die implementiert hat;
bewegt wird dort nur die Karte. Nur der PR bekommt den Reviewer.

Danach mit einer erneuten Abfrage verifizieren, nicht auf den Erfolg der Mutation vertrauen, und
dem User in einem Satz sagen, was gesetzt wurde.

### 2. RULESET PRÜFEN

Nicht annehmen, was durchgesetzt wird — nachsehen:

```bash
gh api repos/dfme/budget-buddy/rulesets
gh api repos/dfme/budget-buddy/rulesets/<id>
```

Relevant sind `required_review_thread_resolution` und `required_approving_review_count`. Steht
letzterer auf `0`, sind Approvals wirkungslos und die Threads sind die einzige Bremse. Ändert das
Team den Wert, verschiebt sich die Logik — deshalb prüfen statt annehmen.

### 3. DIFF LESEN — mit Kreuzprobe gegen die Gegenseite

Den Diff nie isoliert bewerten. Berührt der PR eine API-Grenze, immer die Gegenseite mitlesen:

- **Frontend-PR:** das Backend-DTO **und dessen Integrationstest** (`jsonPath`-Assertions zeigen
  das echte Wire-Format), plus die Jackson-Konfiguration.
- **Backend-PR:** die konsumierenden Frontend-Models und Services.

Der stärkste Befund aus PR #90 (`amount: string`, tatsächlich JSON-Number) war **im Diff allein
nicht sichtbar**. Reine Diff-Reviews finden Contract-Mismatches grundsätzlich nicht.

Zusätzlich gegen CLAUDE.md prüfen: `BigDecimal` für CHF, Domänen-Package-Struktur, Claude-API
hinter `CategorizationPort`, Timeout + Fallback bei externen Calls, keine Secrets im Git,
Flyway-Namensschema, OnPush + Signals im Frontend.

### 4. VERIFIZIEREN — Behauptungen des PR-Texts prüfen

Die Test-Plan-Checkliste im PR ist eine Behauptung, kein Nachweis. Selbst ausführen:

```bash
git fetch origin <headRefName> && git checkout <headRefName>
cd frontend && npx ng test --watch=false     # Frontend
cd backend && ./mvnw package                  # Backend
```

Dabei zwei verschiedene Fragen trennen:

- **Läuft der Test grün?** — billig zu prüfen, fängt falsche Behauptungen.
- **Beweist der Test etwas?** — Assertions suchen, die nicht fehlschlagen *können*
  (`expect(row).toContain('1')` matcht gegen jede Zahl mit einer `1`), und ACs, die von keinem
  Test berührt werden. Beides ist in einem grünen Lauf unsichtbar.

#### Ausnahme: nicht-interaktive Läufe — synchron statt im Hintergrund

Im interaktiven CLI-Lauf darf ein langer Verifikationsbefehl in den Hintergrund gehen: eine
spätere Gesprächsrunde holt die Benachrichtigung ab, sobald er fertig ist. Im nicht-interaktiven
Lauf (`GITHUB_ACTIONS=true`, siehe Schritt 7) gibt es diese spätere Runde nicht — die GitHub
Action führt einen einzelnen, einmaligen SDK-Query aus. Ein im Hintergrund gestarteter
`./mvnw package` oder `npx ng test` liefert sein Ergebnis dann an niemanden mehr; das Modell kann
die Session beenden, ohne je zu erfahren, ob der Befehl grün oder rot war. Beobachtet an PR #223,
Lauf [33239595160](https://github.com/dfme/budget-buddy/actions/runs/33239595160): der SDK-Query
endete nach 19 Turns mit `is_error: false` — kein Crash, das Modell hat die Session einfach vor
dem Ergebnis beendet. Für den betroffenen Commit existiert entsprechend kein Review-Objekt.

| `GITHUB_ACTIONS` | Verifikationsbefehle |
| ---------------- | --------------------- |
| nicht gesetzt | Hintergrundausführung erlaubt — der Normalfall bei langen Läufen |
| `true` | **synchron ausführen und auf das Ergebnis warten**, nie im Hintergrund |

### 5. BEFUNDE KLASSIFIZIEREN

Jeden Befund vor dem Posten einsortieren:

**🔴 Blockierend** — Korrektheitsfehler, Sicherheitsprobleme, Verletzungen von CLAUDE.md-Regeln,
nicht erfüllte Acceptance Criteria, Contract-Mismatches, ACs ohne echte Testabdeckung.

**🟡 Nicht blockierend** — Stil, Konventionsabweichungen ohne Funktionsfolge, Verbesserungs-
vorschläge, Beobachtungen.

Zwei Sonderfälle:

- **Projektweite Lücken, die der PR nicht verursacht hat** (z. B. fehlende E2E-Tests, wenn `e2e/`
  im Repo noch gar nicht existiert) gehören in den Body plus Folge-Issue — **nie** als
  blockierender Thread. Sonst hält man den Falschen auf.
- **Nicht-Code-Feedback** (Korrektur am PR-Text, Prozessfragen) hat keinen natürlichen Anker.
  Entweder gezwungener Anker an der thematisch nächsten Codezeile (blockiert) oder Body
  (blockiert nicht). Bewusst entscheiden und den Trade-off benennen.

### 6. ANKERZEILEN VERIFIZIEREN

Nur Zeilen, die **im Diff** stehen, sind kommentierbar. Hunk-Offsets täuschen — Zeilennummern
immer am PR-Head gegenprüfen, bevor sie in die Payload gehen:

```bash
grep -n "<suchmuster>" <datei>
```

### 7. REVIEW PRÄSENTIEREN

Dem User die vollständigen Befunde zeigen, gruppiert nach 🔴/🟡, jeweils mit Ankerzeile und
Begründung. Explizit ausweisen:

- welche Punkte als Thread gehen und damit blockieren
- welche im Body bleiben
- Sonderfälle aus Schritt 5, bei denen die Einordnung diskutabel ist

**Auf ausdrückliche Bestätigung warten.** Ein Review ist teamsichtbar und ändert den
Merge-Status — das ist nichts, was ungefragt rausgeht.

#### Ausnahme: nicht-interaktive Läufe

Im automatischen Lauf (INFRA-31) sitzt niemand daneben. Das Gate hätte dort keinen Adressaten:
Warten bedeutet nicht «sicherheitshalber nachfragen», sondern **das Review verfällt**.

Die Erkennung läuft über ein prüfbares Signal, nicht über den Wortlaut des Prompts:

```bash
[ "$GITHUB_ACTIONS" = "true" ] && echo "nicht-interaktiv"
```

| `GITHUB_ACTIONS` | Schritt 7 |
| ---------------- | --------- |
| nicht gesetzt | Befunde zeigen, **auf Bestätigung warten** — der Normalfall |
| `true` | Befunde in den Fortschritts-Kommentar schreiben und **direkt zu Schritt 8** |

**Warum ein Umgebungssignal und keine Formulierung im Prompt:** Am 26.08. liefen zwei Instanzen
dieses Skills auf demselben Commit von PR #212 (Läufe 33021243731 und 33021327534, 72 Sekunden
auseinander). Eine wertete die Automatik als implizite Freigabe und setzte `CHANGES_REQUESTED`
ab, die andere hielt sich ans Gate und wartet bis heute auf eine Bestätigung, die nie kommt.
Gleicher Skill, gleicher Code, verschiedenes Ergebnis — weil die Regel Auslegung zuliess. Ein
Environment-Check tut das nicht.

Die harten Grenzen unten gelten **unverändert weiter**: auch der automatische Lauf approved nie,
merged nie und fasst fremde Threads nicht an. Entfallen tut ausschliesslich die Rückfrage vor dem
Absetzen — und nur, weil das Team mit INFRA-31 genau diesen Automatismus beschlossen hat.

### 8. REVIEW ABSETZEN

**Jeder Lauf setzt ein echtes Review-Objekt ab — ohne Ausnahme.** Auch wenn das Fazit exakt dem
eines vorherigen Laufs entspricht (keine neuen Blocker, nichts hat sich am Ergebnis geändert), muss
der `gh api .../reviews`-Aufruf trotzdem erfolgen. Die Versuchung, das Fazit stattdessen nur in den
Fortschritts-Kommentar zu schreiben ("das vorherige Review deckt das ja schon ab"), ist real:
beobachtet an PR #223 (Lauf 33247869625) — der Agent führte die volle Verifikation korrekt und
synchron durch, schrieb ein ausführliches "kein Blocker"-Fazit in den Fortschritts-Kommentar, setzte
aber nie ein Review ab. Für den aktuellen Head-Commit existierte danach weiterhin kein
Review-Objekt — exakt der Zustand, den der Guard in `claude-pr-review.yml` erkennen soll, nur mit
einer anderen Ursache als der ursprünglich vermuteten (Backgrounding, INFRA-35). Ein
Fortschritts-Kommentar ist niemals ein Ersatz für ein Review: nur Review-Objekte zählen für
`reviewDecision`, blockierende Inline-Threads und die Wirkungsverifikation in Schritt 9.

Review-State ist **`REQUEST_CHANGES`**, sobald mindestens ein Thread gesetzt wird. `COMMENTED`
blockiert nie — das war die Ursache bei PR #88.

Payload über eine JSON-Datei absetzen, nicht über Shell-Quoting (Markdown mit Code-Fences und
Umlauten zerlegt es sonst):

```python
import json, subprocess
payload = {
    "event": "REQUEST_CHANGES",
    "body": "<Body: Einordnung, Lob, 🟡-Punkte>",
    "comments": [
        {"path": "<pfad>", "line": <n>, "side": "RIGHT", "body": "<🔴-Befund>"},
    ],
}
open("payload.json", "w").write(json.dumps(payload))
subprocess.run(["gh", "api", "repos/dfme/budget-buddy/pulls/<pr>/reviews",
                "--method", "POST", "--input", "payload.json"])
```

**Vor dem Absetzen gegen die Threads aus Schritt 1b abgleichen.** Deckt sich ein eigener Befund
inhaltlich mit einem fremden Thread, den fremden stehen lassen und den eigenen weglassen —
zwei Threads zum selben Problem bedeuten für den Autor doppelte Auflösearbeit ohne
Erkenntnisgewinn. Fällt die Doppelung erst nach dem Posten auf, den **eigenen** Thread löschen
(`gh api repos/dfme/budget-buddy/pulls/comments/<comment-id> --method DELETE`) und die Korrektur
im Body transparent machen. Ergänzt ein eigener Befund den fremden an anderer Stelle — etwa
Deklaration vs. Test-Fixtures derselben Ursache —, sind zwei Threads richtig: es sind zwei
Fixstellen.

Zwei Regeln für den Body:

- **Lob und Kontext gehören hinein.** Ein Review, das nur aus Blockern besteht, liest sich
  feindselig und verschweigt, was gut gelöst ist.
- **Duplikat-Hygiene.** Was in Threads wandert, im Body kürzen — sonst steht derselbe Text
  zweimal da. Ein bereits abgesetzter Body lässt sich nachträglich anpassen:
  `gh api repos/dfme/budget-buddy/pulls/<pr>/reviews/<review-id> --method PUT --input body.json`

#### Veralteten eigenen `CHANGES_REQUESTED`-Review aufheben

GitHub berechnet den Review-Status pro Reviewer aus dessen letztem **formellen** Review
(`APPROVED`/`CHANGES_REQUESTED`) — ein späterer `COMMENTED`-Review desselben Accounts überschreibt
das **nicht**. Postet die aktuelle Runde `COMMENTED`, weil sie keine blockierenden Punkte mehr
findet, und liegt unter den in Schritt 1b gelesenen Reviews ein eigener mit
`state == CHANGES_REQUESTED`, bleibt die PR ohne Eingriff technisch blockiert, obwohl nichts mehr
zu beanstanden ist — beobachtet an PR #212 (#224): trotz eines späteren `COMMENTED` von `claude`
ohne verbleibende Blocker und einem menschlichen `APPROVED` blieb `reviewDecision` auf
`CHANGES_REQUESTED` stehen. Eine neue **eigene** `REQUEST_CHANGES` braucht diesen Schritt dagegen
nicht — die ersetzt den vorherigen Stand desselben Reviewers automatisch, weil beides formelle
Zustände sind.

Die eigene Login für den Abgleich **nicht** über `gh api user --jq .login` ermitteln (das ist
Preflight Schritt 0 vorbehalten und dort für den interaktiven, personenbezogenen Token gedacht) —
`/user` ist ein reiner Nutzer-Endpoint und liefert für ein GitHub-App-Installationstoken (der
automatische Lauf) `403`, weil eine App kein „Nutzer" im Sinne dieses Endpoints ist. Stattdessen
die GraphQL-`viewer`-Abfrage, die für beide Token-Typen funktioniert:

```bash
gh api graphql -f query='{ viewer { login } }' --jq .data.viewer.login
```

Belegt an PR #227 (#224, Testlauf zur Dismiss-Verifikation): der automatische Lauf identifizierte
sich selbst korrekt als `claude[bot]`, meldete aber im Review-Body live einen `403` auf
`gh api user --jq .login` und schlug die `viewer`-Abfrage als funktionierende Alternative vor —
das eigene Review hat den Fehler in dieser Anleitung selbst gefunden.

Trifft die Bedingung zu, nach dem Posten des neuen Reviews den alten automatisch dismissen:

```bash
gh api repos/dfme/budget-buddy/pulls/<pr>/reviews/<alter-review-databaseId>/dismissals \
  --method PUT \
  -f message="Alle blockierenden Punkte aus dem Review vom <Datum> sind behoben, siehe <Link zum neuen Review>." \
  -f event=DISMISS
```

**Jeder Fehlschlag auf dem Weg dorthin zählt als Fehlschlag** — nicht nur eine `403`/`404` auf den
Dismiss-Aufruf selbst, sondern genauso ein Fehlschlag schon beim Ermitteln der eigenen Login. Ein
Fehlschlag darf nie zum stillen Auslassen des ganzen Abschnitts führen (beobachtet an PR #227: der
Lauf erkannte den `403` korrekt, unterliess dann aber sowohl den Dismiss-Versuch als auch den
dokumentierten Fallback-Kommentar — die PR blieb kommentarlos blockiert). Bei jedem Fehlschlag
stattdessen einen PR-Kommentar setzen, der auf den veralteten Review verlinkt und um manuelles
Dismiss durch jemanden mit ausreichender Berechtigung bittet:

```bash
gh pr comment <pr> --body "Automatisches Dismiss des veralteten CHANGES_REQUESTED-Reviews (<Link>) ist fehlgeschlagen (<Ursache: Berechtigung fehlt / eigene Login nicht ermittelbar>) — bitte manuell im GitHub-UI dismissen, sonst bleibt die PR trotz behobener Punkte blockiert."
```

Laut GitHub-Doku verlangt Dismiss selbst (wenn die Login-Ermittlung gelingt) Repo-Admin-Rechte
oder Eintrag in einer eigens konfigurierten Dismiss-Liste — mehr als das ohnehin vorhandene
`pull-requests: write`. Beim App-Token der GitHub Action ist ein Fehlschlag deshalb der
wahrscheinliche Fall, beim persönlichen Token im interaktiven Lauf eher nicht — in beiden Fällen
greift derselbe Fallback-Kommentar.

Gelingt der Dismiss, ebenfalls kurz kommentieren, damit der Vorgang im PR-Verlauf nachvollziehbar
bleibt.

### 9. WIRKUNG VERIFIZIEREN

Nach dem Absetzen prüfen, dass die Threads tatsächlich greifen:

```bash
gh pr view <pr-number> --json mergeStateStatus --jq '.mergeStateStatus'   # erwartet: BLOCKED
```

Zusätzlich per GraphQL `reviewThreads { totalCount, nodes { isResolved } }` gegenprüfen. Steht der
PR trotz Threads auf `CLEAN`, stimmt die Annahme über den Ruleset nicht — dem nachgehen statt
Vollzug zu melden.

### 10. FOLGE-ISSUES

Für die in Schritt 5 ausgeklammerten projektweiten Lücken Issues vorschlagen. Nach CLAUDE.md:
Titel `[TASK-ID] Kurzbeschreibung`, neue freie ID im betroffenen Bereich, Label `bug` bei Bugs,
**ohne Milestone und ohne Sprint** — die Einplanung ist eine Kapazitätsentscheidung des Teams.

## Harte Grenzen

- **Nie approven.** Die Freigabe kommt von mindestens einem Dev (docs/CONVENTIONS.md, Review-Konvention).
- **Nie mergen.** Der Merge auf `main` wird ausschliesslich von einem Dev getriggert.
- **Nie ungefragt absetzen** — in der interaktiven Sitzung. Schritt 7 ist dort ein verbindliches
  Gate. Im nicht-interaktiven Lauf (`GITHUB_ACTIONS=true`) entfällt es, weil es keinen Adressaten
  hat; siehe Schritt 7 → «Ausnahme». Eine zweite `GITHUB_ACTIONS`-bedingte Ausnahme gilt für
  Schritt 4: dort wird nicht das Gate aufgehoben, sondern Hintergrundausführung durch synchrones
  Warten ersetzt, weil eine im Hintergrund gestartete Verifikation sonst spurlos verlorengeht
  (siehe Schritt 4 → «Ausnahme»).
- **Fremde Threads nie anfassen** — weder auflösen noch löschen noch bearbeiten. Ob ein Befund
  erledigt ist, entscheidet, wer ihn angebracht hat.
- **Keine Karte auf `Done`.** Schritt 1c bewegt Karten nur in den Review hinein. `Done` folgt aus
  dem Merge, und der wird ausschliesslich von einem Dev getriggert.
- **Fremde Assignees nie überschreiben** — weder am PR noch am Issue. Der Assignee des Issues
  wird grundsätzlich nicht angefasst.
- **Eigene Threads dürfen korrigiert werden.** Ein selbst gepostetes Duplikat zu löschen ist
  richtig und ausdrücklich erlaubt — Zögern führt sonst zu doppelter Auflösearbeit beim Autor.
  Auflösen des eigenen Threads bleibt dem Autor des PR überlassen.
- **Eigene veraltete Reviews dürfen dismissed werden.** Kein Widerspruch zu „Nie ungefragt
  absetzen" oder „Fremde Threads nie anfassen": ein Dismiss ändert nichts an fremden Inhalten, es
  räumt nur den eigenen, durch Nachbesserung überholten `CHANGES_REQUESTED`-Stand auf (siehe
  Schritt 8 → «Veralteten eigenen CHANGES_REQUESTED-Review aufheben»).

## Sprache

Reviews werden auf Deutsch verfasst — konsistent mit den bestehenden Reviews im Repo. Befunde
benennen das Problem und die Folge, nicht nur die Regel: *warum* etwas bricht, unter welchen
konkreten Umständen, und wie der Minimalfix aussieht.
