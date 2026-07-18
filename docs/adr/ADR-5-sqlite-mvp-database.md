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

| # | Variante | Persistenz | Backups | Kosten/Monat | Wesentliche Limits |
| --- | --- | --- | --- | --- | --- |
| 1 | Status quo (SQLite, ephemer) | ✗ | ✗ | $0 | Datenverlust bei Redeploy, Restart und Spin-Down (alle 15 Min Inaktivität) |
| 2 | Postgres als eigener Render-Container | ✗ | ✗ | $0 | Bräuchte selbst einen Disk → dasselbe Problem, plus Spin-Down. Netto schlechter als 1. |
| 3 | Render Managed Postgres (Free) | ✓ | ✗ | $0 | **Läuft 30 Tage nach Erstellung ab**, 14 Tage Grace, dann Löschung · 1 GB · kein Connection Pooling · nur 1 DB pro Workspace |
| 4 | Neon Free (Frankfurt/EU) | ✓ | ~ (6 h PITR) | $0 | Permanent, kein Ablaufdatum · 0.5 GB/Projekt · 100 CU-h/Projekt · Scale-to-Zero nach 5 Min (nur Latenz, kein Datenverlust) |
| 5 | Supabase Free | ✓ | ✗ | $0 | 500 MB · **Projekt pausiert nach 1 Woche Inaktivität** · 2 aktive Projekte/Org |
| 6a | Render Paid Instance + Disk | ✓ | ✓ 24 h-Snapshots, ≥7 Tage | $7.25 | Kein Spin-Down, kein 750 h-Deckel · kein Zero-Downtime-Deploy · keine Multi-Instanz · behält `hibernate-community-dialects` |
| 6b | 6a + Pro-Workspace | ✓ | ✓ 24 h-Snapshots, ≥7 Tage | $32.25 | wie 6a, zusätzlich alle Devs administrationsfähig (heute erlaubt der Hobby-Workspace genau 1 Team-Member) |

Anmerkungen zur Bewertung:

- **Nur 6a/6b bieten echte Backups.** Render Free Postgres *"don't support any form of backups"*; Supabase Free ebenso wenig.
- **Instance-Type und Workspace-Plan sind entkoppelt** — ein bezahlter Instance-Type läuft im gratis Hobby-Workspace. Der Mehr-Admin-Zugriff (6b) ist eine eigenständige Entscheidung und liesse sich auch mit Variante 4 kombinieren.
- Die Disk-Nachteile (kein Zero-Downtime-Deploy, keine Multi-Instanz) wiegen hier gering: Horizontal Scaling ist unter SQLite ohnehin ausgeschlossen (siehe *Negative* oben).

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
