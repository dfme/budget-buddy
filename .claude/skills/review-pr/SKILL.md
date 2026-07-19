---
name: review-pr
description: GitHub Pull Request reviewen — PR und Issue einlesen, Diff gegen die Gegenseite kreuzprüfen, Tests ausführen, Befunde in blockierend/nicht-blockierend trennen, Review präsentieren (mit Bestätigung), als REQUEST_CHANGES mit Inline-Threads absetzen. Auslösen via /review-pr <pr-number>.
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

### 1. EINLESEN

```bash
gh pr view <pr-number> --json number,title,author,body,state,headRefName,baseRefName,mergeStateStatus,reviewDecision
gh pr diff <pr-number> --name-only
gh pr diff <pr-number>
```

Das verlinkte Issue mitlesen (`Closes #NN` im PR-Body) — die **Acceptance Criteria und die
Definition of Done dort sind der Massstab**, nicht der PR-Text.

**Bestehende Reviews und Threads immer mitlesen** — auch bei `reviewDecision: null`, denn ein
`COMMENTED`-Review ohne Threads lässt das Feld leer, obwohl inhaltlich schon reviewt wurde:

```bash
gh api graphql -f query='
{
  repository(owner: "dfme", name: "budget-buddy") {
    pullRequest(number: <pr-number>) {
      reviews(first: 20) { nodes { author{login} state submittedAt body } }
      reviewThreads(first: 30) {
        nodes { isResolved path line comments(first: 5) { nodes { author{login} body } } }
      }
    }
  }
}'
```

Reviews laufen parallel. Wer das überspringt, postet Threads zu Befunden, die schon jemand
anders angebracht hat — der Autor muss dann dasselbe Problem zweimal auflösen.

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

### 8. REVIEW ABSETZEN

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

**Vor dem Absetzen gegen die Threads aus Schritt 1 abgleichen.** Deckt sich ein eigener Befund
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

- **Nie approven.** Die Freigabe kommt von mindestens einem Dev (CLAUDE.md, Review-Konvention).
- **Nie mergen.** Der Merge auf `main` wird ausschliesslich von einem Dev getriggert.
- **Nie ungefragt absetzen.** Schritt 7 ist ein verbindliches Gate.
- **Fremde Threads nie anfassen** — weder auflösen noch löschen noch bearbeiten. Ob ein Befund
  erledigt ist, entscheidet, wer ihn angebracht hat.
- **Eigene Threads dürfen korrigiert werden.** Ein selbst gepostetes Duplikat zu löschen ist
  richtig und ausdrücklich erlaubt — Zögern führt sonst zu doppelter Auflösearbeit beim Autor.
  Auflösen des eigenen Threads bleibt dem Autor des PR überlassen.

## Sprache

Reviews werden auf Deutsch verfasst — konsistent mit den bestehenden Reviews im Repo. Befunde
benennen das Problem und die Folge, nicht nur die Regel: *warum* etwas bricht, unter welchen
konkreten Umständen, und wie der Minimalfix aussieht.
