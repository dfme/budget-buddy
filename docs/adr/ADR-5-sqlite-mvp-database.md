# ADR-5: SQLite als MVP-Datenbank

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy benötigt eine Datenbank zur Persistierung von sensiblen Finanzdaten:

- User (Email, Password, Settings)
- Transaktionen (Datum, Betrag, Empfänger, Kategorie)
- Fixkosten, Sparziele, KI-Berichte
- Kategorie-Mappings (Empfänger → Kategorie)

**Anforderungen:**
- Einfaches Deployment ohne separate DB-Server-Infrastruktur
- ACID-Transaktionen (finanzkritisch)
- MVP-Scale: ~1.000 User × ~1.000 Transaktionen = 1M Rows

Alternative Datenbanken: PostgreSQL, MySQL, MongoDB

## Decision

Wir nutzen **SQLite 3.x** mit folgende Konfiguration:

- **JDBC Driver:** `org.xerial:sqlite-jdbc:3.49.x`
- **Hibernate Dialect:** `org.hibernate.orm:hibernate-community-dialects` (SQLiteDialect)
- **Storage:** Single File `budget-buddy.db`
- **Migrations:** Flyway 10.x
- **Connection Pool:** HikariCP (Spring Boot Standard)

## Consequences

### Positive

- **Zero Infrastructure:** Alles in einer JAR — keine separate DB-Server nötig
- **Einfaches Deployment:** Single File kann überall gespeichert werden
- **MVP-Tempo:** Keine DevOps-Komplexität, schneller produktiv
- **Offline Development:** Lokal entwickeln ohne Internet oder DB-Server
- **Easy Testing:** Tests können `jdbc:sqlite::memory:` nutzen
- **Backup:** Single File → Copy-and-Paste Backup
- **Kostenlos:** Open Source, keine Cloud-DB-Gebühren

### Negative

- **Limited Concurrent Writes:** SQLite locked die ganze Datei (nicht Row-level)
  - ~50 Schreibzugriffe pro Sekunde max
- **Keine Horizontal Scaling:** Single File kann nicht über mehrere Server verteilt werden
- **Skalierungslimit:** Max ~1-2 Million Rows bevor Performance problematisch wird
- **Kein Built-in Replication:** Keine automatische Redundanz

## Offene Frage: Persistenz in Produktion

> **Status: ungelöst.** Dieser Abschnitt beschreibt ein bekanntes, nicht mitigiertes Problem.
> Der Entscheid wird in [Issue #78](https://github.com/dfme/budget-buddy/issues/78) vorbereitet
> und anschliessend in einem eigenen ADR festgehalten, der dieses ADR supersedet.

SQLite speichert alle Daten in einer einzigen Datei (`budget-buddy.db`). Auf dem Render Free-Plan liegt diese Datei auf einem **ephemeren Filesystem**. Laut [Render-Doku](https://render.com/docs/free) gilt: *"any changes to your web service's filesystem (uploaded images, local SQLite databases, etc.) are lost every time the service **redeploys, restarts, or spins down**."*

Betroffen ist also nicht nur der Redeploy. Ein Free-Service spinnt bereits nach **15 Minuten ohne Traffic** herunter — im Kursbetrieb der Normalfall. Faktisch überlebt die Datenbank keine Nacht.

**Die bisher hier dokumentierte Mitigation existiert nicht.** Frühere Fassungen dieses ADR nannten den Render Persistent Disk als Pflichtmassnahme vor Produktionsbetrieb. Free Web Services können jedoch überhaupt keinen Disk anhängen: Nur bezahlte Instance-Types können *"preserve local filesystem changes by attaching a persistent disk."* Der Disk ist nicht "kostenpflichtig, aber verfügbar" — er setzt ein **Upgrade des Instance-Types** voraus, das bisher in keinem ADR erfasst war.

### Optionen (Recherchestand 18.07.2026, noch nicht entschieden)

| # | Variante | DB-Technologie | Migration nötig | Persistenz | Backups | Spin-Down Web-Service | Kosten/Monat | Wesentliche Limits |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Status quo (SQLite, ephemer) | **SQLite** (Datei im Container) | nein | ✗ | ✗ | **Ja** (~1 Min) | $0 | Datenverlust bei Redeploy, Restart und Spin-Down (alle 15 Min Inaktivität) |
| 2 | Postgres als eigener Render-Container | PostgreSQL (selbst betrieben) | **ja** | ✗ | ✗ | **Ja** (~1 Min) | $0 | DB-Container bräuchte selbst einen Disk → dasselbe Problem, und schläft zusätzlich ein. Netto schlechter als 1. |
| 3 | Render Managed Postgres (Free) | PostgreSQL (Render managed) | **ja** | ✓ | ✗ | **Ja** (~1 Min) | $0 | **Läuft 30 Tage nach Erstellung ab**, 14 Tage Grace, dann Löschung · 1 GB · kein Connection Pooling · nur 1 DB pro Workspace |
| 4 | Neon Free (Frankfurt/EU) | PostgreSQL (Neon, serverless) | **ja** | ✓ | ~ (6 h PITR) | **Ja** (~1 Min) | $0 | Permanent, kein Ablaufdatum · 0.5 GB/Projekt · 100 CU-h/Projekt · zusätzlich Neon-Scale-to-Zero nach 5 Min (nur Latenz, kein Datenverlust) |
| 5 | Supabase Free | PostgreSQL (Supabase managed) | **ja** | ✓ | ✗ | **Ja** (~1 Min) | $0 | 500 MB · **DB pausiert nach 1 Woche Inaktivität** · 2 aktive Projekte/Org |
| 6a | Render Paid Instance + Disk | **SQLite** (Datei auf Persistent Disk) | nein | ✓ | ✓ 24 h-Snapshots, ≥7 Tage | **Nein** | $7.25 | kein 750 h-Deckel · Shell-Access · kein Zero-Downtime-Deploy · keine Multi-Instanz · behält `hibernate-community-dialects` |
| 6b | 6a + Pro-Workspace | **SQLite** (Datei auf Persistent Disk) | nein | ✓ | ✓ 24 h-Snapshots, ≥7 Tage | **Nein** | $32.25 | wie 6a, zusätzlich alle Devs administrationsfähig (heute erlaubt der Hobby-Workspace genau 1 Team-Member) |
| 7 | Web-Service bleibt Free + **Render Postgres Basic-256MB** | PostgreSQL (Render managed) | **ja** | ✓ | ✓ PITR 3 Tage (Hobby) + Logical Backups, 7 Tage | **Ja** (~1 Min) | $6.00 | **Kein Ablaufdatum** (das 30-Tage-Limit gilt nur für die Free-Stufe) · 750 h-Deckel bleibt |

**Die Spalte „DB-Technologie" trennt das Feld in zwei Lager:** Nur 1, 6a und 6b bleiben bei SQLite — dort entfällt jede Migration, dafür bleibt der `hibernate-community-dialects`-Workaround bestehen. Alle übrigen Varianten bedeuten PostgreSQL und damit Flyway-Anpassungen, Dialect-Wechsel und einen eigenen Umsetzungs-Task (~1–2 Sprints, siehe *Migration Path* unten).

**Die Spalte „Spin-Down Web-Service" ist eine dritte, unabhängige Achse.** Der Spin-Down hängt allein am Instance-Type des Web-Services, nicht an der DB-Wahl. Nur 6a/6b beheben ihn, weil nur dort der Web-Service auf Starter läuft. Jede andere Variante lässt sich für +$7/Monat ebenfalls always-on machen (z. B. Variante 7 + Starter-Web-Service = $13/Monat). Entscheidend für die Bewertung: Sobald die Daten in einer externen DB liegen (3–5, 7), kostet der Spin-Down nur noch **Latenz, keine Daten** — bei Variante 1 dagegen beides.

Anmerkungen zur Bewertung:

- **Backups haben nur die bezahlten Stufen** — 6a/6b über Disk-Snapshots, 7 über PITR. Render Free Postgres *"don't support any form of backups"*, Supabase Free ebenso wenig; Render bietet für Free-Instanzen ausdrücklich *"no recovery capabilities"*.
- **Varianten 6a und 7 lösen unterschiedliche Probleme.** 7 macht die *Daten* sicher und ist die billigste Option dafür — der Web-Service schläft weiter ein, was dann aber nur noch Latenz kostet. 6a macht zusätzlich den *Service* always-on und spart die Migration, bleibt aber bei SQLite. Kombinierbar: Starter-Web-Service + Postgres Basic ≈ $13/Monat.
- **Instance-Type und Workspace-Plan sind entkoppelt** — ein bezahlter Instance-Type läuft im gratis Hobby-Workspace (im Dashboard verifiziert). Der Mehr-Admin-Zugriff (6b) ist eine eigenständige Entscheidung und liesse sich auch mit Variante 4 oder 7 kombinieren.
- Die Disk-Nachteile (kein Zero-Downtime-Deploy, keine Multi-Instanz) wiegen hier gering: Horizontal Scaling ist unter SQLite ohnehin ausgeschlossen (siehe *Negative* oben).

### Renders Preismodell — Lesehilfe

Die Preise sind leicht misszuverstehen, weil **„Pro" zwei verschiedene Dinge bezeichnet**:

| „Pro" als … | Was es ist | Preis |
| --- | --- | --- |
| **Workspace-Plan** | Kontoebene: Team-Members, Audit-Logs, längeres PITR-Fenster | $25/Mt flat |
| **Postgres Instance-Type** | eine Datenbank-Maschine (RAM/CPU) | $55/Mt |

Beide sind unabhängig voneinander und frei kombinierbar.

**Gesamtkosten = Workspace-Plan + Summe aller laufenden Ressourcen.** Der Workspace-Plan enthält *kein* Compute — jede Ressource wird einzeln abgerechnet:

| Achse | Optionen |
| --- | --- |
| Workspace-Plan (1×) | Hobby $0 · Pro $25 · Scale $499 |
| Web-Service (pro Service) | Free $0 · Starter $7 · … |
| Postgres (pro DB) | Free $0 · Basic-256MB $6 · Basic-1GB $19 · Basic-4GB $75 · Pro $55 · Accelerated $160 |
| Disk (pro GB) | $0.25/GB/Mt |

Für die Datenbank bringt der Pro-**Workspace** genau eine Verbesserung: PITR-Fenster 3 → 7 Tage. Ein Pro-Workspace lohnt sich also wegen des Bus-Faktors, nicht wegen der DB.

## Migration Path to PostgreSQL

**Tatsächlicher Trigger: Datenpersistenz in Produktion — nicht Skalierung.**

Der Migrationsdruck entsteht nicht aus Last, sondern aus dem ephemeren Filesystem (siehe Abschnitt oben). Er besteht **ab dem ersten produktiven Nutzer**, im Kursbetrieb also bereits bei drei Personen. Die Skalierungsgrenzen von SQLite (>100 concurrent writes/s, >2M Rows) liegen für dieses Projekt ausser Reichweite und sind als Auslöser irrelevant.

Falls der Entscheid aus #78 auf PostgreSQL fällt:

1. Erstelle PostgreSQL Datenbank parallel
2. Flyway Schema automatisch migrierbar
3. Wechsel Hibernate Dialect: SQLite → PostgreSQL (`hibernate-community-dialects` entfällt)
4. Tests gegen beide DBs validieren
5. Production Connection String aktualisieren
6. Aufwand: ~1-2 Sprints

Fällt der Entscheid auf 6a/6b, entfällt die Migration — SQLite bleibt, die Datei liegt dann auf dem Persistent Disk.

## Alternatives

### PostgreSQL from Day One

**Rejected.** Wäre technisch besser, aber:
- Extra Komplexität: Docker Compose, Datenbankserver, Netzwerk
- Overkill für MVP-Scale
- Deployment schwieriger für Team

### MongoDB (NoSQL)

**Rejected.** Nicht für relationale Finanzdaten geeignet:
- User → Transaktionen → Kategorien ist natürlicherweise relational
- NoSQL würde zu Document-Duplication führen
- Joins auf Applikations-Layer sind komplizierter
- ACID-Semantik weniger bewährt

## Related Decisions

- **ADR-1:** Java + Spring Boot (JPA + Hibernate)
- **ADR-6:** Hybrid-Kategorisierung (Lookup + LLM)
