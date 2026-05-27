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

## Migration Path to PostgreSQL

**Wenn concurrent writes > 100/s oder >2M Rows:**

1. Erstelle PostgreSQL Datenbank parallel
2. Flyway Schema automatisch migrierbar
3. Wechsel Hibernate Dialect: SQLite → PostgreSQL
4. Tests gegen beide DBs validieren
5. Production Connection String aktualisieren
6. Aufwand: ~1-2 Sprints

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
