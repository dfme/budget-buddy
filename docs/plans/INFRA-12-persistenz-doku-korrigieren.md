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

## Faktenlage und Optionen

> **Single Source of Truth: [ADR-5, Abschnitt "Offene Frage: Persistenz in Produktion"](../adr/ADR-5-sqlite-mvp-database.md#offene-frage-persistenz-in-produktion).**
>
> Dort stehen die Varianten, die verifizierten Limits, Kosten und Quellenzitate. Dieser Plan
> hält sie bewusst **nicht** zusätzlich vor — das war der erste Entscheid oben ("ein Ort,
> keine Doppelpflege"), und eine Kopie hier ist genau die Stelle, die beim nächsten
> Recherchefund veraltet. Der Voting-Kommentar in #78 ist die zweite, bewusst befristete
> Kopie für die Diskussion; er verschwindet mit dem Entscheid.

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
7. **Voting-Kommentar in #78** mit der nummerierten Optionen-Tabelle, den entscheidtragenden
   Punkten und zwei getrennten Abstimmungsfragen: (1) welche Variante, (2) Pro-Workspace
   ja/nein. Devs antworten mit der Variantennummer, damit die Auszählung eindeutig ist.

## Verlauf — was während der Umsetzung dazukam

Die Recherche lief über die Umsetzung hinaus weiter, angestossen durch Rückfragen und einen
Dashboard-Gegencheck. Alle inhaltlichen Ergebnisse sind in ADR-5 eingeflossen; hier nur die
Chronologie, damit die Commit-Historie lesbar bleibt.

| Commit | Was und warum |
| --- | --- |
| `5b79e83` | Ursprüngliche Korrektur (ADR-5, ADR-10, Properties) |
| `ee909c0` | **Variante 7** (Render Postgres Basic, $6) ergänzt. Aufgefallen beim Dashboard-Check: Die bezahlte Basic-Stufe läuft *nicht* ab — das 30-Tage-Limit gilt nur für Free. Zugleich Korrektur der Aussage "nur 6a/6b bieten Backups". Dazu eine Lesehilfe zum Preismodell, weil "Pro" bei Render sowohl einen Workspace-Plan ($25) als auch einen Postgres-Instance-Type ($55) bezeichnet. |
| `bfe13f7` | Spalten **DB-Technologie** und **Migration nötig** — machen sichtbar, dass nur 1/6a/6b bei SQLite bleiben und alle übrigen einen Migrations-Task bedeuten. |
| `f4758ec` | Spalte **Spin-Down Web-Service** — dritte, von DB-Wahl und Kosten unabhängige Achse. |
| `c7f25b1` | **Backup-Bewertung von 6a korrigiert.** Render warnt: *"Do not restore a snapshot of a disk that's used for a custom database instance."* Damit sichert Variante 7 auf Datenbankebene, 6a nur auf Dateisystemebene — die Gewichtung dreht sich um. Zweiter Befund: Das Repo hat **keinerlei SQLite-PRAGMA-Konfiguration** (kein WAL, kein `busy_timeout`) bei HikariCP-Default von 10 Verbindungen. |
| `23f2d05` | **Variante 8** (Starter + Neon Free, $7.00). Anbieterwahl Neon statt Supabase: Supabase Free pausiert nach 7 Tagen, Restore ist manuell, nach 90 Tagen endgültige Löschung — derselbe Ausfallpfad, den die Migration beseitigen soll. Neon hat Auto-Resume, Frankfurt/EU und 6 h PITR. |

**Offener Nebenbefund (unabhängig vom Entscheid):** Die fehlende PRAGMA-Konfiguration ist
bereits heute ein latenter Bug — HikariCP öffnet 10 Verbindungen gegen eine Datei mit
Whole-File-Lock, ohne `busy_timeout` wirft ein blockierter Write sofort `SQLITE_BUSY`. Der
Free-Tier maskiert das (0.1 CPU, ständiger Spin-Down). Braucht ein eigenes Issue, sofern der
Entscheid bei SQLite bleibt (Varianten 1, 6a, 6b).

## Ergebnis der Abstimmung (18.07.2026)

| Frage | Ergebnis |
| --- | --- |
| **Variante** | **4 — Neon Free (PostgreSQL, Frankfurt/EU), $0/Monat** |
| **Pro-Workspace ($25/Mt)** | **nein** |

Konsequenzen: Die Datenbank wechselt von SQLite auf PostgreSQL bei Neon. Der Render
Web-Service bleibt auf Free — der Spin-Down nach 15 Min bleibt bestehen, kostet ab dann aber
nur noch ~1 Min Latenz und **keine Daten** mehr. Der Workspace bleibt Hobby, Bus-Faktor 1
wird bewusst in Kauf genommen. Upgrades auf Starter (Variante 8, $7/Mt) bzw. Pro-Workspace
sind jederzeit ohne Nacharbeit möglich.

Ergebnis-Kommentar: <https://github.com/dfme/budget-buddy/issues/78#issuecomment-5015065128>

## Nach diesem Task (nicht Teil davon)

- **Folge-Issue [#89](https://github.com/dfme/budget-buddy/issues/89) — `[DB-05] Migration von
  SQLite auf Neon Postgres (inkl. Entscheid-ADR)`.** Umfasst: Neon-Projekt aufsetzen,
  ADR-11 (supersedet ADR-5), Flyway- und Dialect-Migration, lokales Dev-Setup via
  `docker-compose.yml`, Umstellung der Tests auf Testcontainers. Liegt im Backlog ohne
  Milestone — die Einplanung entscheidet das Team im Sprint-Planning.
- **#78 schliessen**, sobald PR #87 gemergt ist. Passiert nicht automatisch, da der PR
  `Refs #78` statt `Closes #78` trägt.

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

- [x] Keine Stelle in der Doku nennt den Render Persistent Disk mehr als verfügbare
      Mitigation, ohne das Plan-Upgrade als Voraussetzung zu benennen
- [x] ADR-5 "Deployment-Voraussetzung" beschreibt den tatsächlichen Stand: Free-Plan
      schliesst Disks aus, Frage ist offen, Verweis auf dieses Issue
- [x] ADR-5 "Migration Path to PostgreSQL" nennt Persistenz — nicht Skalierung — als
      tatsächlichen Trigger
- [x] ADR-10 nennt unter "SQLite-Persistenz" keine Mitigation mehr, die auf dem Free-Plan
      nicht existiert
- [x] ADR-10 stellt den Bezug zwischen Spin-Down und Datenverlust her
- [x] `application-prod.properties` verspricht keinen Disk mehr, der nicht kommen kann
- [x] Die Optionen inkl. ihrer Limits (30-Tage-Ablauf bei Render Free Postgres) sind
      dokumentiert, sodass der Entscheid auf Fakten fällt
- [x] Kein Code- oder Config-Verhalten geändert — reine Doku-Änderung
- [x] Folge-Issue existiert und ist hier verlinkt, bevor dieser Task geschlossen wird
      → **[#89](https://github.com/dfme/budget-buddy/issues/89)**, in #78 verlinkt. Abgedeckt
      über den Voting-Kommentar + Umsetzungs-Task statt über ein vorab angelegtes Issue —
      deshalb trägt PR #87 `Refs #78` statt `Closes #78`, damit der Merge die Abstimmung nicht
      mitschliesst.
