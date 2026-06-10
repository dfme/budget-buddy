## Prompt

Kannst du die Tasks auch in github.com als issues anlegen? Du sollst nur die Frage beantworten und nicht direkt etwas anlegen.

## Antwort

Ja, das ist möglich — mit dem `gh` CLI-Tool, das ich über die Bash-Shell aufrufen kann.

**Voraussetzungen:**
1. `gh auth login` wurde bereits ausgeführt (du bist authentifiziert)
2. Das Repo auf GitHub existiert (z.B. `dfme/budget-buddy`)

Ich würde für jeden Task ein Issue erstellen mit:

- **Titel:** z.B. `[US-01] B3 Spring Security Config (JWT Cookie + CORS)`
- **Body:** Task-Beschreibung + Akzeptanzkriterien
- **Labels:** z.B. `backend`, `frontend`, `risk:high`
- **Milestone:** z.B. `Sprint 1`

Soll ich das für alle 9 Tasks aus dem Breakdown oben anlegen?

---

## Prompt

Es gibt ja noch Github Projects. Kennst du das? Falls ja, welches Template würdest du uns empfehlen? Wir sind ein Team von drei Software Entwickler. Wir würden aber gerne die Tasks auch einplanen können und priorisieren.

## Antwort

Ja, GitHub Projects kenne ich gut. Es gibt aktuell folgende Templates beim Erstellen eines neuen Projects:

| Template | Was es bietet |
|----------|--------------|
| **Backlog** | Einfaches Kanban (Todo / In Progress / Done) — kein Sprint-Konzept |
| **Iterative development** | Backlog + Sprints (Iterations) + Board-View + Priority-Feld |
| **Bug tracker** | Fokus auf Status, Severity, Reproduzierbarkeit |
| **Blank** | Komplett leer, selbst aufbauen |

**Meine Empfehlung: "Iterative development"**

Das passt am besten zu euch, weil:

- **Iterations** = ihr könnt Sprints definieren (z.B. 2 Wochen) und Tasks direkt einem Sprint zuweisen
- **Priority-Feld** (P0–P3) ist bereits eingebaut — ideal um MoSCoW-Prioritäten abzubilden
- **Backlog-View** + **Board-View** sind beide vorhanden — Backlog für Planung, Board für Daily
- Passt zu eurem Workflow: Issues anlegen → Backlog → Sprint zuweisen → Board abarbeiten

Was ich euch zusätzlich empfehlen würde: ein eigenes **Label-System** (`backend`, `frontend`, `risk:high`) damit ihr im Board schnell filtern könnt, wer an was arbeitet — bei einem 3-köpfigen Team sehr praktisch.

Soll ich die Issues inklusive Labels und Milestone anlegen?
