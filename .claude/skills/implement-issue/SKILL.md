---
name: implement-issue
description: GitHub Issue end-to-end umsetzen — Issue einlesen und übernehmen (Assignee + Board-Status In Progress), Fragen klären, Plan präsentieren (mit Bestätigung), Branch erstellen, Code + Tests implementieren, Security-Review und lokalen Review durchführen (mit Bestätigung), PR öffnen und die Board-Karte auf Review setzen. Auslösen via /implement-issue <issue-number>.
argument-hint: "<issue-number>"
---

# implement-issue

Ein GitHub Issue end-to-end umsetzen: Issue lesen, bei Bedarf Rückfragen stellen, einen Plan zur Bestätigung vorlegen, mit Tests implementieren, einen Security-Review und einen lokalen Review durchführen, dann einen PR öffnen.

## Verwendung

```
/implement-issue <issue-number>
```

## Ablauf

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

**1a. Issue lesen.** `gh issue view <issue-number>` ausführen und Titel, Body, Label und
Assignees vollständig lesen.

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
- Die Task-ID aus dem Issue-Titel ziehen — sie steht immer in eckigen Klammern, z. B. `[BE-FC-01]`
- Betroffene und neue Dateien bestimmen
- Anforderungen und Acceptance Criteria verstehen
- **Prüfen, wie jedes AC nachgewiesen wird, nicht nur lesen, was es verlangt.** Nennt ein AC eine
  Suche (grep, Suchbegriff) als seinen Nachweis, diese Suche zweimal ausführen: einmal **eng,
  genau im Wortlaut des AC**, und einmal **breit über den zugrundeliegenden Begriff**. Die
  Treffermengen vergleichen. Alles, was die breite Suche findet und das AC nicht aufführt,
  bedeutet, dass das AC unvollständig ist — zu eng formuliert ist das AC, nicht die
  Implementierung.

  Beispiel (#115): Der grep des AC `kein manueller (Http)?Interceptor` traf 3 Stellen,
  `grep -ri interceptor docs/adr CLAUDE.md` traf 8. Genau diese Differenz war die eigentliche
  Arbeit — `CLAUDE.md:161` sagte „kein HttpInterceptor" ohne „manueller" und rutschte damit
  durch den grep des AC.
- Unklare oder mehrdeutige Punkte markieren

### 3. FRAGEN (bei Bedarf)
Ist etwas unklar, den User fragen, bevor es weitergeht. Bei blockierenden Entscheidungen nichts annehmen — nachfragen. Erst weitermachen, wenn alle offenen Punkte geklärt sind.

Hat die breite Suche aus Schritt 2 Treffer ausserhalb der ACs ergeben, dieses Delta dem User
**vor** dem Präsentieren des Plans vorlegen, mit drei Optionen: (a) mitbeheben und die
Scope-Erweiterung im PR-Body deklarieren, (b) Folge-Issue, (c) bewusst stehen lassen, mit
Begründung. Das nie allein entscheiden — Scope ist eine Teamentscheidung.

### 4. PLAN PRÄSENTIEREN
Dem User den vollständigen Plan vorlegen:

- **Branch-Name** — abgeleitet aus der Task-ID im Issue-Titel und der Art der Änderung:
  - Feature-Arbeit: `feature/<TASK-ID>-<kurztext>` (z. B. `feature/BE-FC-01-fixedcost-entity`)
  - Bugfix: `fix/<TASK-ID>-<kurztext>` (z. B. `fix/INFRA-05-cors-header`)
- **Betroffene Dateien** — bestehende Dateien zum Ändern und neu anzulegende Dateien auflisten
- **Implementierungsschritte** — nummerierte Liste konkreter Schritte
- **Test-Strategie** — welche Tests geschrieben werden (Unit / Integration / E2E)

Auf die ausdrückliche Bestätigung des Users warten, bevor es weitergeht. Wünscht der User Änderungen, überarbeiten und den vollständigen Plan von vorn neu präsentieren.

### 5. PLAN ABLEGEN
Nachdem der User den Plan bestätigt hat, diesen als Markdown unter `docs/plans/` ablegen, bevor der Branch erstellt wird:

- Dateipfad: `docs/plans/<TASK-ID>-<kurztext>.md` (derselbe `<kurztext>` wie im Branch-Namen, z. B. `docs/plans/INFRA-01-spring-boot-skeleton.md`)
- Die Ablage bleibt **flach** — keine Unterverzeichnisse. Die Sprint-Zugehörigkeit ist eine
  Eigenschaft des Boards, nicht der Datei, und sie ändert sich bei Carryover (#13 und #16 wurden
  in Sprint 2 geplant und erst in Sprint 3 fertig). Ein Ordner pro Sprint erzwänge `git mv` und
  bräche die Dateihistorie. Sprint, Bereich und Story sind stattdessen Spalten im Index, was ein
  Verzeichnisbaum nicht gleichzeitig abbilden kann.
- Die Datei mit diesem Header beginnen — **genau diese Felder, in dieser Reihenfolge.** In den
  bestehenden 45 Plänen sind zwei konkurrierende Formate gewachsen (Aufzählung vs. Tabelle); neue
  Pläne verwenden die Aufzählungsform:

  ```markdown
  # [<TASK-ID>] <Titel>

  - **Issue:** [#<nr>](https://github.com/dfme/budget-buddy/issues/<nr>)
  - **Task-ID:** `<TASK-ID>`
  - **Branch:** `feature/<TASK-ID>-<kurztext>`
  - **Story:** US-XX — <Titel>   <!-- oder: — (kein us-*-Label) -->
  - **Sprint:** <Sprint aus dem Board zum Zeitpunkt der Planung>
  - **Bestätigt am:** <YYYY-MM-DD>
  ```

  Die Zeile `Sprint` hält den Sprint fest, in dem der Plan *geschrieben* wurde. Sie später bei
  Carryover eines Issues nicht nachziehen — das Board hält die aktuelle Wahrheit, der Plan die
  historische.
- Inhalt nach dem Header: der bestätigte Plan — Entscheide, betroffene und neue Dateien,
  Implementierungsschritte, Test-Strategie und die Acceptance Criteria aus dem Issue.

Danach eine Zeile an den Index in `docs/plans/README.md` anhängen, in der bestehenden
Sortierreihenfolge der Tabelle (nach Task-ID) — dieselben Werte, die gerade in den Header
geschrieben wurden:

```markdown
| `<TASK-ID>` | [<Titel>](<TASK-ID>-<kurztext>.md) | [#<nr>](https://github.com/dfme/budget-buddy/issues/<nr>) | US-XX | Sprint N |
```

`docs/plans/README.md` zusammen mit dem Plan committen.

Der Index führt bewusst nur Spalten, die sich nach dem Schreiben des Plans nicht mehr ändern.
**Kein Status und keine Story Points ergänzen** — die stehen auf dem Board, ändern sich laufend,
und eine Kopie davon wäre ab ihrer Erzeugung veraltet. Läuft der Index doch einmal auseinander
(fehlende Zeilen, Handarbeit), baut `scripts/plans-index.sh` ihn vollständig aus Dateien plus
Board neu auf; `--check` prüft, ohne zu schreiben. Dieses Skript ist ein Reparaturwerkzeug, kein
Schritt in diesem Ablauf.

`docs/plans/` steht in `.claudeignore`, diese Dateien bleiben also aus Claudes automatischem Kontext und dessen Suche heraus. Sie dienen als menschenlesbares Artefakt und als Git-Historie; nicht darauf bauen, sie in späteren Läufen wieder einlesen zu können.

### 6. BRANCH ERSTELLEN
```bash
git checkout main && git pull
git checkout -b feature/<TASK-ID>-<kurztext>
```

### 7. IMPLEMENTIEREN
Code und Tests gemäss dem bestätigten Plan implementieren. Alle Konventionen aus CLAUDE.md einhalten:
- Package-Struktur nach Domäne (nicht nach Schicht)
- `BigDecimal` für alle CHF-Beträge — nie `double` oder `float`
- Keine Secrets im Git — API-Keys und JWT-Secret ausschliesslich über Umgebungsvariablen
- Claude-API immer hinter dem Interface `CategorizationPort`
- Timeouts + Fallback auf `"Sonstiges"` bei allen externen Calls

Bei Änderungen an der Dokumentation:
- Jede Aussage über den Code mit `file:line` belegen. Dokumentation beschreibt den Ist-Zustand,
  nie den geplanten — `8fb4dab` schrieb „kein manueller `HttpInterceptor` nötig" Monate bevor das
  Angular-Frontend überhaupt existierte, und es brauchte drei Runden (#103, #115 und den
  ursprünglichen Commit), um das zurückzudrehen.
- Nicht eine unqualifizierte Vereinfachung durch die nächste ersetzen.

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
Alle Änderungen prüfen, bevor ein PR erstellt wird:
- `git diff main` ausführen und auf Korrektheit und Konventionsverstösse prüfen — die Sicherheit
  ist in Schritt 8 gesondert abgedeckt und wird hier nicht wiederholt
- Jedes AC einzeln mit seinem konkreten Nachweis auflisten — Kommando plus Ergebnis oder `file:line`
- ACs kennzeichnen, deren einziger Nachweis dieselbe Suche ist, die das Issue selbst vorgeschlagen
  hat: die gelten als unbelegt, nicht als bestätigt
- Die Befunde aus **beiden** Schritten, 8 und 9, dem User in einem Zug vorlegen — zwei getrennte
  Bestätigungs-Gates für einen PR sind Reibung ohne Gewinn
- Auf die ausdrückliche Bestätigung des Users warten, dass der PR erstellt werden darf

### 10. PR ERSTELLEN
```bash
gh pr create \
  --title "[<TASK-ID>] <concise title>" \
  --body "..."
```

Der PR-Body muss enthalten:
- Das Closing-Keyword, das das Issue verlinkt: `Closes #<issue-number>` — es erzeugt die formale
  Verknüpfung im Development-Panel des Issues (der PR zielt auf `main`, den Default-Branch) und
  schliesst das Issue automatisch, sobald der PR gemergt wird.
- Zusammenfassung (2–3 Stichpunkte)
- Test-Plan (Checkliste)
- **Security-Review** — das Ergebnis aus Schritt 8: pro zutreffender Zeile der Auslösematrix ein
  Satz mit Nachweis, plus jede bewusste Auslassung mit Begründung. Zeilen, die der Diff nicht
  berührt, bleiben weg; den Abschnitt nicht mit „n/a" auffüllen. Hat der Review Folge-Issues für
  vorbestehende Lücken erzeugt, hier verlinken.

### 11. ISSUE VERLINKEN UND AUF REVIEW SETZEN

**11a. Backlink.** `gh pr create` gibt die URL des neuen PR aus. Einen Backlink-Kommentar am
Issue setzen, damit der Link auch in der Issue-Timeline ausdrücklich steht:

```bash
gh issue comment <issue-number> --body "🔀 PR erstellt: <pr-url>"
```

Dem User bestätigen, dass PR und Issue jetzt in beide Richtungen verknüpft sind (PR → Issue über
`Closes #<issue-number>` plus Development-Panel, Issue → PR über den Backlink-Kommentar).

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
