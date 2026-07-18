# INFRA-12 — Dokumentierte SQLite-Persistenz-Mitigation korrigieren

**Issue:** [#78](https://github.com/dfme/budget-buddy/issues/78)
**Task-ID:** INFRA-12
**Branch:** `fix/INFRA-12-persistenz-doku-korrigieren`
**Labels:** `bug`, `documentation` · **Milestone:** Sprint 3 · **Assignee:** dfme

---

## Problem

Die ADRs nennen den Render Persistent Disk durchgängig als verfügbare Mitigation für das
ephemere Filesystem. Auf dem Render Free-Plan können Web Services überhaupt keinen Disk
anhängen — die Mitigation ist nicht "kostenpflichtig, aber möglich", sondern schlicht nicht
buchbar. Wer die Doku liest, hält das Persistenz-Problem für gelöst.

## Entscheide (mit dem User geklärt)

| Frage | Entscheid | Begründung |
| --- | --- | --- |
| Wo leben die Persistenz-Optionen? | Neuer Abschnitt in ADR-5 ("Offene Frage: Persistenz in Produktion") | Ein Ort, keine Doppelpflege — und genau die Stelle, die der Folge-ADR später supersedet. ADR-10 verweist dorthin. |
| Wie wird der Entscheid vorbereitet? | **Voting-Kommentar in #78**, kein Folge-Issue auf Vorrat | Die Devs geben ihre Präferenz direkt am Issue ab. Der Umsetzungs-Task wird erst formuliert, wenn die Votums da sind — vorher lässt er sich inhaltlich gar nicht schneiden. |
| Korrektur jetzt oder nach dem Vote? | **Jetzt**, parallel zum Vote | Deckt sich mit dem Scope von #78: "Die Korrektur ist dringend und in sich abgeschlossen; sie darf nicht hinter einer offenen Team-Diskussion blockieren." |
| PR-Verknüpfung | `Refs #78` statt `Closes #78` | `Closes` würde das Issue beim Merge automatisch schliessen und den Voting-Thread mitschliessen. Das kollidiert mit dem AC "Folge-Issue existiert […] bevor dieser Task geschlossen wird". #78 wird manuell geschlossen, sobald Votums vorliegen und der Umsetzungs-Task existiert. |

## Betroffene Files

| File | Änderung |
| --- | --- |
| `docs/adr/ADR-5-sqlite-mvp-database.md` | § "Deployment-Voraussetzung" → § "Offene Frage: Persistenz in Produktion" inkl. Optionen-Tabelle; § "Migration Path to PostgreSQL" Trigger korrigieren |
| `docs/adr/ADR-10-hosting-plattform.md` | Falsche Mitigation entfernen, Spin-Down ↔ Datenverlust verknüpfen, Cold-Start-Zahlen korrigieren, Related-Decisions-Zeile korrigieren |
| `backend/src/main/resources/application-prod.properties` | Nur Kommentar (Zeilen 7–10): Disk-Versprechen entfernen |

**Bewusst nicht angefasst:**

- `README.md:33` und `render.yaml:11` — beide sagen bereits korrekt "Free-Tier hat kein
  Persistent Disk", enthalten keine falsche Mitigation.
- `docs/prompts/03_01_prompt_c2_container_diagramm_jason.md` — historisches Prompt-Artefakt
  der Kursabgabe (zeigt `/data`-Mount), wird nicht rückwirkend umgeschrieben.

## Verifizierte Faktenlage (Stand 18.07.2026)

Alle Angaben gegen die Primärquellen geprüft, nicht aus dem Issue übernommen.

### Render Free Web Services — [render.com/docs/free](https://render.com/docs/free)

- Free Web Services **können keinen Disk anhängen**. Nur bezahlte Services können
  *"preserve local filesystem changes by attaching a persistent disk."*
- *"any changes to your web service's filesystem (uploaded images, local SQLite databases,
  etc.) are lost every time the service redeploys, restarts, or spins down."*
  → Betroffen ist **Redeploy, Restart und Spin-Down**, nicht nur der Redeploy.
- Spin-Down nach **15 Minuten** ohne Traffic, Aufwachen dauert *"about one minute"*.
  → ADR-10 behauptet aktuell "~30s Aufwachzeit" — ebenfalls zu optimistisch.
- **750 Free Instance Hours/Monat.** Ein durchgehend laufender Service braucht ~720 h — der
  Puffer ist praktisch null. Bei Überschreitung: *"suspends all of your Free web services
  until the start of the next month."*
- Free unterstützt **kein Shell-Access (SSH/Dashboard)** und **keine One-off Jobs**.

### Render Persistent Disks — [render.com/docs/disks](https://render.com/docs/disks)

- Anhängbar an *"a paid Render web service, private service, or background worker."*
- *"Render automatically creates a snapshot of your persistent disk once every 24 hours"*,
  mindestens 7 Tage vorgehalten, wiederherstellbar.
- Gegenseite: *"Adding a disk to a service prevents zero-downtime deploys"* (Stop-vor-Start)
  und keine Skalierung auf mehrere Instanzen mit Disk.
- Disk-Preis: **$0.25 pro GB/Monat**. Günstigster bezahlter Instance-Type (Starter,
  512 MB RAM / 0.5 CPU): **$7/Monat**.

### Render Workspace-Plans — [render.com/docs/new-workspace-plans](https://render.com/docs/new-workspace-plans)

Relevant für die Frage "wer darf administrieren" — und **unabhängig vom Instance-Type**:

- **Hobby (gratis): *"Team members: 1"*** → das ist der heutige Zustand, Bus-Faktor 1.
- **Pro: $25/Monat flat.** *"Pro removes per-seat billing, letting you invite unlimited team
  members for a flat monthly fee."* → alle drei Devs administrationsfähig, ohne Seat-Kosten.
- Scale: $499/Monat. Für dieses Projekt irrelevant.

**Die beiden Kostenachsen sind entkoppelt** — verifiziert, nicht abgeleitet:

> *"Upgrading your workspace plan does **not** remove limitations on Free instances. Your
> workspace's plan only determines which platform-level features are available. **You change
> a service's instance type independently.**"* — [render.com/docs/free](https://render.com/docs/free)

> *"Render bills your workspace monthly according to its **plan and usage**."*
> — [platform-features-by-plan](https://render.com/docs/platform-features-by-plan)

> *"**Compute pricing is not changing at this time.** These changes only affect the pricing
> structure for your workspace plan."* — [new-workspace-plans](https://render.com/docs/new-workspace-plans)

Daraus folgt zweierlei:

1. **Ein bezahlter Instance-Type läuft im gratis Hobby-Workspace.** Persistenz + Backups
   kosten $7.25/Monat, ohne den Workspace anzufassen. Der Pro-Plan enthält **keine**
   Compute-Credits — er ist eine Plattform-Gebühr, keine Ressourcen-Flatrate.
2. **Der Mehr-Admin-Vorteil hängt ausschliesslich am Workspace-Plan.** Er ist eine *eigene*
   Entscheidung, kombinierbar mit jeder Variante — auch Pro-Workspace + Neon (Variante 4) für
   $25/Monat. Umgekehrt löst ein bezahlter Instance-Type allein den Bus-Faktor **nicht**.

### Optionen im Vergleich

| # | Variante | Persistenz | Backups | Kosten/Monat | Verifizierte Limits |
| --- | --- | --- | --- | --- | --- |
| **1** | **Status quo** (SQLite, ephemer) | ✗ | ✗ | $0 | Verlust bei Redeploy, Restart **und** Spin-Down (alle 15 Min Inaktivität) |
| **2** | **Postgres als eigener Render-Container** | ✗ | ✗ | $0 | Bräuchte selbst einen Disk → dasselbe Problem, plus Spin-Down. Netto schlechter als Variante 1. |
| **3** | **Render Managed Postgres (Free)** | ✓ | ✗ *"don't support any form of backups"* | $0 | **Läuft 30 Tage nach Erstellung ab**, 14 Tage Grace, dann Löschung · 1 GB · kein Connection Pooling · **nur 1 DB pro Workspace** |
| **4** | **Neon Free** (Frankfurt/EU) | ✓ | ~ (6 h PITR, auf 1 GB Änderungen gedeckelt) | $0 | **Permanent, kein Ablaufdatum** · 0.5 GB/Projekt · 100 CU-h/Projekt · Scale-to-Zero nach 5 Min (nur Latenz, **kein** Datenverlust) · bei Limit-Überschreitung Suspend bis nächster Monat |
| **5** | **Supabase Free** | ✓ | ✗ | $0 | 500 MB · **Projekt wird nach 1 Woche Inaktivität pausiert** · 2 aktive Projekte/Org |
| **6a** | **Render Paid + Disk** (Hobby-Workspace) | ✓ | ✓ **24 h-Snapshots, ≥7 Tage** | **$7.25** — Starter $7 + 1 GB Disk $0.25 | **kein Spin-Down** · kein 750 h-Deckel · Shell-Access + One-off Jobs · kein Zero-Downtime-Deploy · keine Multi-Instanz · behält `hibernate-community-dialects` · **weiterhin nur 1 Admin** |
| **6b** | **Render Paid + Disk + Pro-Workspace** | ✓ | ✓ **24 h-Snapshots, ≥7 Tage** | **$32.25** — 6a + Pro-Workspace $25 | alles aus 6a **plus alle Devs administrationsfähig** (Bus-Faktor behoben) · Audit-Logs · höhere Bandwidth-/Domain-Kontingente |

### Vier Punkte, die den Entscheid tragen

1. **Render Free Postgres (Variante 3) läuft mitten im Kurs ab.** 30 Tage ab Erstellung,
   danach 14 Tage Grace. Bei einem Semesterprojekt ist das kein Randfall, sondern der
   Normalfall.
2. **Supabase (Variante 5) pausiert nach 1 Woche Inaktivität** — trifft ein Kursprojekt, das
   zwischen Sprints ruht. Zu unterscheiden von Neons Scale-to-Zero nach 5 Min: das kostet nur
   Anlaufzeit, keine Daten und keine Reaktivierung von Hand.
3. **Variante 6 ist die einzige Option mit echten Backups** — für $7.25/Monat (6a). Alle
   Free-Postgres-Varianten haben keine oder nur rudimentäre. Für Finanzdaten ist das das
   stärkste Argument des Paid-Pfads — und es wiegt schwerer als der Disk-Nachteil (kein
   Zero-Downtime-Deploy, keine Multi-Instanz), denn **beide Nachteile bestehen unter SQLite
   ohnehin**: ADR-5 führt "Keine Horizontal Scaling" bereits als SQLite-Eigenschaft. Der Disk
   nimmt uns nichts, was SQLite nicht schon ausschliesst.
4. **Bus-Faktor 1 bei der Infrastruktur** — der Unterschied zwischen 6a und 6b. Der
   Hobby-Workspace erlaubt genau *einen* Team-Member: aktuell kann nur eine Person deployen,
   Logs lesen, Env-Vars setzen und im Störfall eingreifen. Für ein Team von drei Devs ist das
   ein reales Projektrisiko und heute ungelöst. **Aber:** Es hängt am Workspace-Plan, nicht am
   Disk. 6b ist deshalb nicht "die bessere Variante 6", sondern 6a **plus** eine eigenständige
   Entscheidung, die sich genauso mit Variante 4 (Neon + Pro = $25/Monat) treffen liesse.

Ergänzend: **Bereits 6a** schliesst drei in ADR-10 **getrennt geführte** Nachteile mit einer
Entscheidung — SQLite-Persistenz, Cold Starts (Spin-Down entfällt vollständig) und den
750-Stunden-Deckel. Dazu Shell-Access und One-off Jobs für Flyway-Troubleshooting in Prod.
All das hängt am **Instance-Type**, nicht am Workspace-Plan; 6b fügt ausschliesslich
Workspace-Features hinzu (Team-Members, Audit-Logs, grössere Kontingente).

## Implementierungsschritte

1. **ADR-5 § "Deployment-Voraussetzung"** ersetzen durch **"Offene Frage: Persistenz in
   Produktion"**: Free-Plan schliesst Disks aus (Plan-Upgrade = Voraussetzung, nicht nur
   Kosten); Datenverlust bei Redeploy, Restart und Spin-Down; Optionen-Tabelle mit Limits;
   explizit als **offener Entscheid** markiert mit Verweis auf #78.
2. **ADR-5 § "Migration Path to PostgreSQL"**: Trigger von "concurrent writes > 100/s oder
   >2M Rows" auf **Persistenz beim Deployment** umstellen (greift bereits bei drei Nutzern im
   Kursbetrieb). Skalierung bleibt als sekundärer, ferner Trigger erhalten.
3. **ADR-10 § Negative**: "SQLite-Persistenz"-Bullet — Mitigation streichen, als **ungelöst**
   markieren mit Verweis auf ADR-5. "Render Cold Starts"-Bullet: Zahlen korrigieren
   (15 Min Inaktivität, ~1 Min Aufwachzeit) und ergänzen, dass der Spin-Down nicht nur
   Latenz, sondern **Datenverlust** bedeutet.
4. **ADR-10 § Related Decisions**: Zeile "ADR-5: SQLite (Persistent Disk auf Render nötig für
   Datenpersistenz)" korrigieren.
5. **`application-prod.properties`**: Kommentar auf tatsächlichen Stand ziehen — kein Disk
   ohne Plan-Upgrade buchbar, Verweis auf die offene Frage. `SQLITE_DB_PATH`-Verhalten und
   alle Property-Werte **unverändert**.
6. **PR erstellen** mit `Refs #78`.
7. **Voting-Kommentar in #78** mit der **nummerierten** Optionen-Tabelle (Varianten 1, 2, 3,
   4, 5, 6a, 6b), allen Vor- und Nachteilen und den vier entscheidtragenden Punkten. Devs
   antworten mit der Variantennummer, damit die Auszählung eindeutig ist.
   **Zweite, getrennte Abstimmungsfrage im selben Kommentar:** Pro-Workspace ($25/Monat) für
   Mehr-Admin-Zugriff — ja/nein. Gilt für die Varianten 1–5; bei Variante 6 ist die Antwort
   bereits in der Wahl 6a vs. 6b enthalten (6b = ja). Wer 1–5 wählt, beantwortet sie separat.

## Nach diesem Task (nicht Teil davon)

Sobald alle Votums vorliegen:

- Neuer ADR mit dem Entscheid, supersedet ADR-5 (inkl. Index-Zeile in `docs/adr/README.md`)
- Umsetzungs-Task (`DB-05`) für die Migration — Inhalt hängt vom Entscheid ab
- Erst dann wird #78 geschlossen

## Test-Strategie

Reine Doku-/Kommentar-Änderung — keine Unit-, Integration- oder E2E-Tests, da kein
ausführbarer Code entsteht.

Verifikation:

- `git diff main -- backend/` zeigt ausschliesslich Kommentarzeilen — kein Property-Wert,
  kein Verhalten geändert
- `./mvnw -q test` läuft grün (belegt, dass die Properties-Datei syntaktisch intakt ist)
- Grep-Gegenprobe über `*.md`/`*.properties`/`*.yaml`: keine Fundstelle nennt den Persistent
  Disk noch als verfügbare Mitigation ohne Plan-Upgrade

## Acceptance Criteria (aus Issue #78)

- [ ] Keine Stelle in der Doku nennt den Render Persistent Disk mehr als verfügbare
      Mitigation, ohne das Plan-Upgrade als Voraussetzung zu benennen
- [ ] ADR-5 "Deployment-Voraussetzung" beschreibt den tatsächlichen Stand: Free-Plan
      schliesst Disks aus, Frage ist offen, Verweis auf dieses Issue
- [ ] ADR-5 "Migration Path to PostgreSQL" nennt Persistenz — nicht Skalierung — als
      tatsächlichen Trigger
- [ ] ADR-10 nennt unter "SQLite-Persistenz" keine Mitigation mehr, die auf dem Free-Plan
      nicht existiert
- [ ] ADR-10 stellt den Bezug zwischen Spin-Down und Datenverlust her
- [ ] `application-prod.properties` verspricht keinen Disk mehr, der nicht kommen kann
- [ ] Die Optionen inkl. ihrer Limits (30-Tage-Ablauf bei Render Free Postgres) sind
      dokumentiert, sodass der Entscheid auf Fakten fällt
- [ ] Kein Code- oder Config-Verhalten geändert — reine Doku-Änderung
- [ ] Folge-Issue existiert und ist hier verlinkt, bevor dieser Task geschlossen wird
      → **abgedeckt über den Voting-Kommentar + späteren Umsetzungs-Task**, nicht über ein
      vorab angelegtes Issue. Deshalb `Refs #78` statt `Closes #78`.
