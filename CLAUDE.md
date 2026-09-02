# BudgetBuddy

**Kurs:** CAS Application Development with AI (ADAI) 2026 · BFH Biel · Ilja Rasin

Projektidee, Personas und die 3 grössten Risiken: [README.md](README.md).
**Core Value:** Ein wöchentlicher Safe-to-Spend-Betrag, dem Nutzer vertrauen — berechnet aus
echten Transaktionsdaten, nicht aus manueller Eingabe.

## Key Decisions

| Entscheid                  | Status                                                           |
| -------------------------- | ---------------------------------------------------------------- |
| OpenBanking-Anbindung      | Nice-to-Have (nicht MVP)                                         |
| Fokus                      | Zahlungskonten                                                   |
| Kategorisierung            | Automatisch + manuelle Korrektur als Feature                     |
| Nutzer                     | Nur Kunden mit Wohnsitz in der Schweiz (kein B2B / Berater-Tool) |
| Geografische Einschränkung | Schweiz (kein internationaler Rollout im MVP)                    |

## Transaktions-Kategorisierung: Hybrid-Ansatz

| Schritt                     | Methode                                  | Begründung                                                                    |
| --------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------ |
| 1. Bekannte Händler         | Lookup-Tabelle (Händlername → Kategorie) | Schnell, kostenlos, deterministisch — deckt ~70–80% der Transaktionen ab      |
| 2. Unbekannte Transaktionen | Claude API (LLM), gebündelt              | Flexibel für unbekannte/mehrdeutige Einträge; reduziert API-Calls auf ~20–30% |
| 3. Manuelle Korrekturen     | Lookup-Tabelle wird erweitert            | User-Korrekturen trainieren das System — Lerneffekt ohne Retraining           |

**Fallback-Kategorie:** `Sonstiges` (wenn LLM unsicher oder API nicht erreichbar)

**Bündelung (ADR-14):** Bis zu 20 Transaktionen gehen in *einem* Request hinaus. Der Prompt ist
eine nummerierte Liste; die Kategorienliste steht **nicht** darin, sondern als `enum`-Constraint
im Structured-Output-Schema, das aus dem `Category`-Enum abgeleitet wird — eine Kategorie
ausserhalb der Liste ist damit strukturell ausgeschlossen.

**Maskierung vor dem Versand (BE-CAT-06):** Was hinausgeht, ist nicht der rohe Buchungstext,
sondern seine von `PromptSanitizer` maskierte Fassung — IBAN, Karten- und Kontonummern, Beträge,
undurchsichtige Referenzen, der Name einer natürlichen Gegenpartei und E-Mail-Adressen fallen
vorher weg. Angewendet wird das in `ClaudeCategorizationService.buildUserPrompt`, der einzigen
Stelle, an der Text in einen API-Request gerät. Die **Lookup-Stufe davor sieht weiterhin den
unmaskierten Text** — sie ist lokal, ihr Input verlässt das System nicht. Zwei Restexpositionen
sind bekannt und in BE-CAT-08 (#233) festgehalten: ein Vorname in einer frei getippten Zweckzeile
und die Telefonnummer eines Händlers.

## Wichtigste Regeln für Claude

- **Niemals direkt auf `main` committen oder pushen** — auch nicht auf explizite Benutzeranfrage.
  Immer einen `feature/<TASK-ID>-…` / `fix/<TASK-ID>-…`-Branch erstellen und einen PR öffnen; bei
  einer solchen Anfrage den Benutzer auf diese Regel hinweisen.
- **Keine Secrets im Git.** `ANTHROPIC_API_KEY` und JWT-Secret nur als Umgebungsvariablen, nie im
  Code oder in `application.properties`. Bei versehentlichem Commit: sofortige Key-Rotation.
- **Geldbeträge immer `BigDecimal`**, nie `double`/`float` (ADR-9) — Rundungsfehler in der
  Safe-to-Spend-Berechnung sind sonst nicht ausschliessbar.
- **Claude API und PDFBox brauchen einen Timeout** und fallen bei Fehler auf `"Sonstiges"` (Claude)
  bzw. geben den Fehler an den Caller zurück (PDFBox) — ein einzelner Ausfall darf nie den ganzen
  Import-Flow blockieren.
- **Beim Anlegen von Issues nie Milestone oder Sprint setzen** — die Einplanung läuft ausschliesslich
  über das Iteration-Feld im Sprint Board und ist eine Kapazitätsentscheidung des Teams.
- **Jeder Controller bekommt `/api` als Präfix** — sonst landet er im SPA-Catch-all oder ist
  unbeabsichtigt öffentlich.
- **Kein direkter Zugriff auf Repositories/Services eines anderen Backend-Moduls**, kein NgRx im
  Frontend — Details: [docs/CONVENTIONS.md](docs/CONVENTIONS.md).

## Weiterführende Dokumentation

| Thema | Datei |
| ----- | ----- |
| Tech-Stack (Backend/Frontend/AI), Swiss-PDF-Parsing, Auth, Postgres/Neon-Gotchas, verbotene Libraries | [docs/TECH-STACK.md](docs/TECH-STACK.md) |
| Git-Konventionen (Branching, Task-IDs, Bugs, Review), Sprint-Planung, Package-Struktur, Import-Flow, Testing | [docs/CONVENTIONS.md](docs/CONVENTIONS.md) |
| C2-Architekturdiagramm, Container-Verantwortlichkeiten, ADR-Übersicht | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Vollständige ADRs mit Begründung und verworfenen Alternativen | [docs/adr/README.md](docs/adr/README.md) |
| User Stories mit Acceptance Criteria | [docs/requirements/](docs/requirements/) |

## Project Skills

Vorbedingungen (gh-Scopes, Repo-Rechte, Toolchain) je Skill und Fehlerbilder:
[.claude/skills/README.md](.claude/skills/README.md). Sie sind **nicht** für alle drei gleich:
`implement-issue` braucht Write-Zugriff aufs Repo, `plan-sprint` den Scope `project` **plus** eine
eigene Freigabe am Sprint Board — das ist ein privates User-Project und vererbt sich nicht aus
den Repo-Rechten. `review-pr` kommt für den Review selbst mit Lesezugriff aus.

`implement-issue` und `review-pr` übernehmen den Vorgang zu Beginn: Assignee auf die ausführende
Person, Board-Karte in die passende Spalte. Dafür brauchen beide zusätzlich den Scope `project`
und Write am Board — **nicht blockierend**: fehlt der Zugriff, wird es gemeldet und der Lauf geht
ohne diesen Schritt weiter.

| Skill | Befehl | Beschreibung |
|-------|--------|--------------|
| plan-sprint | `/plan-sprint` | Sprint planen: Backlog-Hygiene prüfen (Priority aus MoSCoW, fehlende Story Points/Area), Velocity und Carryover aus dem Board ableiten, Abhängigkeiten kreuzprüfen, Vorschlag als `docs/plans/sprints/SPRINT-NN.md` ablegen. Das Board wird erst auf ausdrücklichen Zuruf geschrieben — die Einplanung bleibt eine Kapazitätsentscheidung des Teams. |
| implement-issue | `/implement-issue <issue-number>` | GitHub Issue end-to-end umsetzen: Issue einlesen und übernehmen (Assignee auf die ausführende Person, Board-Karte auf `In Progress`), Fragen klären, Plan präsentieren (mit Bestätigung), Branch erstellen, Code + Tests implementieren, Security-Review und lokalen Review durchführen (mit Bestätigung), PR öffnen und die Board-Karte auf `Review` setzen. Ist das Issue bereits jemand anderem zugewiesen, hält der Skill an und fragt. Der Security-Review ist auf diese App zugeschnitten (Mandantentrennung, ADR-7-Invarianten, Secrets, Upload-Grenzen, Datenminimierung beim Claude-Call) und läuft nur für die Bereiche, die der Diff berührt. |
| review-pr | `/review-pr <pr-number>` | Pull Request reviewen: PR und Issue einlesen und übernehmen (Assignee am PR, PR-Karte auf `In Progress`, Karte des verlinkten Issues auf `Review`), Diff gegen die Gegenseite kreuzprüfen, Tests selbst ausführen, Befunde in blockierend/nicht-blockierend trennen, Review präsentieren (mit Bestätigung), als `REQUEST_CHANGES` mit Inline-Threads absetzen. Blockierende Befunde gehören als Inline-Thread an den Diff — ein Review-Body ist für die Ruleset-Regel `required_review_thread_resolution` unsichtbar und hält den Merge nicht auf. **Läuft zusätzlich automatisch** bei PR-Events (INFRA-31, `.github/workflows/claude-pr-review.yml`) — dort ohne Kartenbewegung und ohne Ersatz für die Dev-Freigabe. |
