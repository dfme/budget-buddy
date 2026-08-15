# Projekt-Skills — Vorbedingungen und Setup

Die drei Skills in diesem Verzeichnis werden in Claude Code über `/<name>` ausgelöst und sind
im Repo eingecheckt — sie kommen also mit `git pull` und müssen nicht installiert werden.

| Skill | Befehl | Beschreibung |
| ----- | ------ | ------------ |
| [implement-issue](implement-issue/SKILL.md) | `/implement-issue <issue-number>` | GitHub Issue end-to-end umsetzen |
| [review-pr](review-pr/SKILL.md) | `/review-pr <pr-number>` | Pull Request reviewen und Befunde absetzen |
| [plan-sprint](plan-sprint/SKILL.md) | `/plan-sprint` | Sprint planen, Vorschlag nach `docs/plans/sprints/` |

Was ein Skill braucht, unterscheidet sich pro Skill. Hinter „das Skill geht bei mir nicht"
stecken fast immer zwei Dinge: ein fehlender **gh-Scope** oder eine fehlende **Freigabe am
Sprint Board**. Der Scope-Fall meldet sich deutlich und nennt den Fix gleich mit; der
Board-Fall tarnt sich als „gibt es nicht".

## Was jedes Skill braucht

| | `review-pr` | `implement-issue` | `plan-sprint` |
| --- | --- | --- | --- |
| gh-Scopes | `repo`; `project` für die Übernahme | `repo`; `project` für die Übernahme | `repo` **+ `read:project`**, zum Schreiben `project` |
| Rechte am Repo | Lesen genügt; **Write** für den Assignee | **Write** (Collaborator) | Lesen; **Write**, sobald der Vorschlag ins Repo soll |
| Rechte am Board | **Write**, sonst ohne Kartenbewegung | **Write**, sonst ohne Kartenbewegung | **eigene Freigabe** (s. u.) |
| Toolchain | JDK 25, Node, Python | JDK 25, Node | `bash`, `jq` |
| Typische Fehlermeldung | `HTTP 422` beim Absetzen | `permission denied` beim Push | `missing required scopes [read:project]` (Scope) bzw. `Could not resolve to a ProjectV2` (Freigabe) |

Erläuterungen zu den nicht offensichtlichen Zeilen:

- **Board-Zugriff ist bei `review-pr` und `implement-issue` weich, bei `plan-sprint` hart.** Beide
  erstgenannten Skills übernehmen zu Beginn den Vorgang: Assignee setzen und die Karte auf dem
  Sprint Board bewegen (`implement-issue`: Issue → `In Progress`, nach dem Öffnen des PR weiter
  auf `Review`; `review-pr`: PR → `In Progress` und das verlinkte Issue → `Review`). Fehlt dafür
  der Scope, die Board-Freigabe oder — nur bei
  `review-pr` — der Write-Zugriff aufs Repo, **bricht der Lauf nicht ab**: das Skill meldet es
  einmal und macht ohne diesen Schritt weiter. Die eigentliche Arbeit (implementieren bzw.
  reviewen) hängt nicht daran. Bei `plan-sprint` ist der Board-Zugriff dagegen der Zweck des
  Skills und damit Vorbedingung.

- **`plan-sprint` braucht `read:project` — und eine Freigabe am Board.** Das Skill liest das
  [Sprint Board](https://github.com/users/dfme/projects/4) über `gh project field-list`. Der
  Scope ist die eine Hälfte (wer sich mit den Defaults von `gh auth login` angemeldet hat, hat
  `repo`, aber keinen Projects-Scope), der Board-Zugriff die andere — siehe nächster Abschnitt.
  Zum **Lesen** genügt `read:project`; Schritt 5 („Board schreiben", nur auf Zuruf) braucht das
  umfassendere `project`. Der Setup-Befehl unten setzt deshalb gleich `project`.
- **`plan-sprint` braucht `bash` und `jq`** — die Zelle ist nicht leer. Schritt 2c ruft
  `scripts/plans-index.sh --check`; das Skript ist Bash und bricht ohne `jq` mit
  `jq nicht gefunden` ab. `jq` kommt weder mit `gh` noch mit Node oder dem JDK mit — der
  eingebaute Filter `gh --jq` ist ein anderes Ding als das Binary.
- **`plan-sprint` und Repo-Rechte:** Zum Lesen und für den Vorschlag als lokale Datei genügt
  Lesezugriff. Soll der Vorschlag in `docs/plans/sprints/` tatsächlich ins Repo, geht das nach
  CLAUDE.md nur über Branch + PR — dafür braucht es dann Write wie bei `implement-issue`.
- **`implement-issue` braucht Write-Zugriff**, weil es einen Branch pusht und via `gh pr create`
  einen PR öffnet. Ein reiner Lesezugriff reicht hier — anders als bei `review-pr` — nicht.
- **`review-pr` kommt für den Review selbst mit Lesezugriff aus.** Das Repo ist public; auch der
  Ruleset-Check in Schritt 2 ist ohne Sonderrechte lesbar (`GET /repos/dfme/budget-buddy/rulesets`
  antwortet auch mit `"admin": false`). Admin-Rechte sind ausdrücklich **nicht** nötig. Einzige
  Ausnahme ist der Assignee in Schritt 1c: Assignees setzen darf nur, wer Write-Zugriff aufs Repo
  hat. Ohne den läuft der Review vollständig durch, nur ohne Zuweisung.
- **Python** braucht nur `review-pr`: die Review-Payload wird als JSON-Datei geschrieben, weil
  Markdown mit Code-Fences und Umlauten am Shell-Quoting zerbricht. Achtung beim Aufruf — unter
  Windows heisst der Interpreter `python`, `python3` ist dort der Microsoft-Store-Stub.

## Board-Zugriff (nicht über das Repo geregelt)

Das [Sprint Board](https://github.com/users/dfme/projects/4) ist ein **privates User-Project**
(`users/dfme/projects/4`), kein Repo-Project. Daraus folgt der Punkt, den man leicht übersieht:

> **Zugriff aufs Repo vererbt keinen Zugriff aufs Board.** Wer Collaborator von
> `dfme/budget-buddy` ist, kann Issues lesen und schreiben — und sieht das Board trotzdem nicht.

Die Freigabe vergibt der Board-Owner (**dfme**) separat: Board öffnen → *Settings* →
*Manage access* → Person einladen. Rollen:

| Rolle | Reicht für |
| ----- | ---------- |
| **Read** | den Sprint-Vorschlag von `plan-sprint` — das Skill liest Felder, Velocity und Carryover |
| **Write** | zusätzlich das Zurückschreiben ins Board: `plan-sprint` Schritt 5, und die Übernahme zu Beginn von `implement-issue` und `review-pr` |

Die **eigene** Berechtigung ist abfragbar — `ProjectV2` hat zwar kein `collaborators`-Feld (die
Rechte *anderer* sieht man also nicht), aber `viewerCanUpdate`:

```bash
gh api graphql -f query='{ user(login:"dfme"){ projectV2(number:4){ title viewerCanUpdate } } }'
```

Vier Ausgänge, die die Ursachen sauber trennen:

| Ausgabe | Bedeutung |
| ------- | --------- |
| `"viewerCanUpdate": true` | Lesen **und** Schreiben gedeckt — auch Schritt 5 des Skills |
| `"viewerCanUpdate": false` | Lesezugriff da, Schreibrechte fehlen → Rolle **Write** anfragen |
| `Could not resolve to a ProjectV2` | Board nicht sichtbar → **Freigabe fehlt** (bei vorhandenem Scope) |
| `missing required scopes` | kein Rechteproblem am Board, sondern am Token → `gh auth refresh` |

Scheitert der Aufruf, **obwohl** `gh auth status` einen Projects-Scope ausweist, fehlt die
Freigabe am Board — dann bei dfme melden, nicht am Token schrauben.

## Einmaliges Setup

> **Kein Token von Hand anlegen.** `gh auth login` führt durch einen OAuth-Flow im Browser,
> erzeugt das Token selbst und legt es im System-Schlüsselbund ab. Es muss dafür weder ein
> Personal Access Token auf github.com erstellt noch eine Umgebungsvariable gesetzt werden —
> auch nicht in `.env`. Der Weg über einen selbst erstellten PAT ist die Ausnahme und weiter
> unten beschrieben.

```bash
# 1. GitHub CLI anmelden — mit beiden Scopes auf einmal
gh auth login --hostname github.com --git-protocol https --scopes repo,project

# Bereits angemeldet? Scopes nachziehen statt neu anmelden:
gh auth refresh -h github.com -s repo,project

# 2. Toolchain — Java und Node wie in ../../README.md → "Lokal starten";
#    Python und jq kommen für die Skills dazu und stehen dort nicht.
java -version           # JDK 25
node -v                 # Node 20+
python3 -V || python -V # review-pr; unter Windows heisst es "python"
jq --version            # plan-sprint (scripts/plans-index.sh)

# 3. Frontend-Dependencies — sonst scheitert der Testlauf im Review
cd frontend && npm ci
```

Zusätzlich muss Claude Code **aus dem Repo-Root** gestartet werden, und das Repo muss ein Clone
von `dfme/budget-buddy` sein — kein Fork. Die Skills adressieren das Repo teilweise fest über
`gh api repos/dfme/budget-buddy/…`; aus einem Fork heraus zeigen diese Aufrufe und die
`gh pr`-Kommandos auf verschiedene Repositories.

## Wenn es klemmt

Diese Zeilen der Reihe nach ausführen — bewusst **ohne** `&&`, damit eine fehlschlagende Prüfung
die folgenden nicht verschluckt:

```bash
gh --version; git --version; java -version; node -v
python3 -V || python -V    # Windows: python3 ist der Store-Stub, nicht der Interpreter
jq --version               # nur plan-sprint

gh auth status                                        # Scopes stehen in der Ausgabe
gh api repos/dfme/budget-buddy --jq '.permissions'    # push:true für implement-issue
gh pr view <pr-number> --json number,title            # nur review-pr
gh api graphql -f query='{ user(login:"dfme"){ projectV2(number:4){ viewerCanUpdate } } }'
```

Bekannte Stolpersteine:

- **`gh auth status` meldet einen Timeout.** Der Befehl validiert das Token per API-Call; bei
  schlechter Verbindung schlägt das fehl, obwohl mit der Anmeldung alles in Ordnung ist. Das
  `(keyring)` in der Meldung bezeichnet nur die Token-Quelle, nicht die Fehlerstelle. Einfach
  wiederholen, bevor man an der Anmeldung schraubt.
- **`review-pr` auf den eigenen PR.** GitHub lässt `REQUEST_CHANGES` und `APPROVE` am eigenen PR
  nicht zu — der POST endet mit `HTTP 422`. Das ist kein Konfigurationsfehler: den eigenen PR
  muss jemand anderes reviewen.
- **Viele Permission-Prompts.** `.claude/settings.local.json` steht in `.gitignore` und ist damit
  pro Entwickler lokal — jede/r baut sich die Allowlist selbst auf. Das ist Absicht, aber
  weggeklickte Prompts sehen aus wie Zugriffsfehler. Wer sie dauerhaft loswerden will, bestätigt
  im Prompt „Yes, and don't ask again".

## Ausnahme: Anmeldung über einen eigenen Token

Nur nötig, wenn der OAuth-Flow oben nicht funktioniert — etwa ohne Browser auf der Maschine oder
wenn der Schlüsselbund partout nicht mitspielt. Dann auf github.com unter *Settings → Developer
settings → Personal access tokens* ein Token erzeugen und in die Umgebung legen:

```bash
export GH_TOKEN="<token>"        # macOS/Linux, z. B. in ~/.zshrc
$env:GH_TOKEN = "<token>"        # Windows PowerShell (dauerhaft: setx GH_TOKEN "<token>")
```

Die Variable hat Vorrang vor gespeicherten Credentials. Scopes wie oben — dafür ein **classic**
Token nehmen (`repo`, `project`).

Ein **Fine-grained** Token ist für `plan-sprint` der falsche Weg: dessen `Projects`-Berechtigung
ist eine *Account*-Berechtigung und deckt die Projects des Token-Inhabers ab, nicht das private
Board einer anderen Person. Für `implement-issue` und `review-pr` funktioniert ein Fine-grained
Token dagegen — nötig sind *Contents: Read & Write*, *Issues: Read & Write*,
*Pull requests: Read & Write* und *Metadata: Read*. Dieselbe Projects-Einschränkung trifft
allerdings auch sie: die Kartenbewegung zu Beginn beider Skills bleibt mit einem Fine-grained
Token aus. Da dieser Schritt nicht blockierend ist, laufen sie trotzdem vollständig durch — die
Karte ist dann von Hand zu setzen.

Ein solcher Token ist ein Secret wie jedes andere: **nie ins Repo**, weder in `.env` noch
sonstwo — es gilt CLAUDE.md → „Sicherheit: Keine Secrets im Git". Bei versehentlichem Commit
sofort widerrufen und neu erzeugen.

## Harte Grenzen (gelten für alle Skills)

Unabhängig von den Rechten, die ein Token technisch hergibt (siehe CLAUDE.md →
„Git: Review-Konvention"):

- **Nie auf `main` committen oder pushen** — immer Feature-/Bugfix-Branch plus PR.
- **Nie approven** — die Freigabe kommt von mindestens einem Dev.
- **Nie mergen** — der Merge auf `main` wird ausschliesslich von einem Dev getriggert.
