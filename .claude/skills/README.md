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
Sprint Board**. Beide melden sich mit Fehlern, die nicht nach einem Rechteproblem aussehen.

## Was jedes Skill braucht

| | `review-pr` | `implement-issue` | `plan-sprint` |
| --- | --- | --- | --- |
| gh-Scopes | `repo` | `repo` | `repo` **+ `project`** |
| Rechte am Repo | Lesen genügt | **Write** (Collaborator) | Lesen |
| Rechte am Board | — | — | **eigene Freigabe** (s. u.) |
| Toolchain | JDK 25, Node, `python3` | JDK 25, Node | — |
| Typische Fehlermeldung | `HTTP 422` beim Absetzen | `permission denied` beim Push | `Could not resolve to a ProjectV2` |

Erläuterungen zu den nicht offensichtlichen Zeilen:

- **`plan-sprint` braucht `project` — und eine Freigabe am Board.** Das Skill liest das
  [Sprint Board](https://github.com/users/dfme/projects/4) über `gh project field-list`. Der
  Scope ist die eine Hälfte (wer sich mit den Defaults von `gh auth login` angemeldet hat, hat
  `repo`, aber nicht `project`), der Board-Zugriff die andere — siehe nächster Abschnitt.
- **`implement-issue` braucht Write-Zugriff**, weil es einen Branch pusht und via `gh pr create`
  einen PR öffnet. Ein reiner Lesezugriff reicht hier — anders als bei `review-pr` — nicht.
- **`review-pr` kommt mit Lesezugriff aus.** Das Repo ist public; auch der Ruleset-Check in
  Schritt 2 ist ohne Sonderrechte lesbar. Admin-Rechte sind ausdrücklich **nicht** nötig.
- **`python3`** braucht nur `review-pr`: die Review-Payload wird als JSON-Datei geschrieben, weil
  Markdown mit Code-Fences und Umlauten am Shell-Quoting zerbricht.

## Board-Zugriff für `plan-sprint` (nicht über das Repo geregelt)

Das [Sprint Board](https://github.com/users/dfme/projects/4) ist ein **privates User-Project**
(`users/dfme/projects/4`), kein Repo-Project. Daraus folgt der Punkt, den man leicht übersieht:

> **Zugriff aufs Repo vererbt keinen Zugriff aufs Board.** Wer Collaborator von
> `dfme/budget-buddy` ist, kann Issues lesen und schreiben — und sieht das Board trotzdem nicht.

Die Freigabe vergibt der Board-Owner (**dfme**) separat: Board öffnen → *Settings* →
*Manage access* → Person einladen. Rollen:

| Rolle | Reicht für |
| ----- | ---------- |
| **Read** | den Sprint-Vorschlag — das Skill liest Felder, Velocity und Carryover |
| **Write** | zusätzlich das Zurückschreiben ins Board, wenn das Team den Vorschlag annimmt |

Es gibt **keinen API-Weg**, die eigene Board-Berechtigung abzufragen — das GraphQL-Objekt
`ProjectV2` hat kein `collaborators`-Feld. Der praktische Test ist der Aufruf selbst:

```bash
gh project field-list 4 --owner dfme --format json
```

Läuft er durch, passt beides. Scheitert er, **obwohl** `gh auth status` den Scope `project`
ausweist, fehlt die Freigabe am Board — dann bei dfme melden, nicht am Token schrauben.

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

# 2. Toolchain (identisch zu ../../README.md → "Lokal starten")
java -version    # JDK 25
node -v          # Node 20+
python3 -V

# 3. Frontend-Dependencies — sonst scheitert der Testlauf im Review
cd frontend && npm ci
```

Zusätzlich muss Claude Code **aus dem Repo-Root** gestartet werden, und das Repo muss ein Clone
von `dfme/budget-buddy` sein — kein Fork. Die Skills adressieren das Repo teilweise fest über
`gh api repos/dfme/budget-buddy/…`; aus einem Fork heraus zeigen diese Aufrufe und die
`gh pr`-Kommandos auf verschiedene Repositories.

## Wenn es klemmt

Diese fünf Zeilen der Reihe nach ausführen — die erste, die bricht, benennt die Ursache:

```bash
gh --version && git --version && python3 -V && java -version && node -v
gh auth status                                        # Scopes stehen in der Ausgabe
gh api repos/dfme/budget-buddy --jq '.permissions'    # push:true für implement-issue
gh project field-list 4 --owner dfme --format json    # nur plan-sprint
gh pr view <pr-number> --json number,title            # nur review-pr
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
export GH_TOKEN="<token>"   # z. B. in ~/.zshrc; hat Vorrang vor gespeicherten Credentials
```

Scopes wie oben (`repo`, `project`). Bei einem Fine-grained Token entsprechen sie
*Pull requests: Read & Write*, *Contents: Read & Write*, *Metadata: Read* und *Projects: Read*.

Ein solcher Token ist ein Secret wie jedes andere: **nie ins Repo**, weder in `.env` noch
sonstwo — es gilt CLAUDE.md → „Sicherheit: Keine Secrets im Git". Bei versehentlichem Commit
sofort widerrufen und neu erzeugen.

## Harte Grenzen (gelten für alle Skills)

Unabhängig von den Rechten, die ein Token technisch hergibt (siehe CLAUDE.md →
„Git: Review-Konvention"):

- **Nie auf `main` committen oder pushen** — immer Feature-/Bugfix-Branch plus PR.
- **Nie approven** — die Freigabe kommt von mindestens einem Dev.
- **Nie mergen** — der Merge auf `main` wird ausschliesslich von einem Dev getriggert.
