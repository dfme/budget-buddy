# ADR-5: SQLite für MVP-Datenbank

**Status:** Accepted (with migration path)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Datenspeicherung, Deployment

---

## Context

BudgetBuddy benötigt eine Datenbank zur Speicherung von:

- User (Email, bcrypt-Password, Settings)
- Transaktionen (Datum, Betrag, Empfänger, Kategorie)
- Fixkosten (Betrag, Intervall, User-zugeordnet)
- Sparziele (Betrag, Zieldatum)
- KI-Berichte (Monat, Inhalte)
- Kategorie-Mappings (Empfänger → Kategorie Lookups)

**Anforderungen:**
- Einfach zu deployen (keine separate DB-Server-Infrastruktur)
- ACID-Transaktionen für Finanz-Daten
- Full-Text-Search optional (für Transaktions-Suche)
- Skalierbar auf MVP-Größe (~1.000 User × ~1.000 Transaktionen = 1M rows)

### Optionen

1. **SQLite 3.x** — Single-file, serverless, ACID
2. **PostgreSQL** — Production-ready, aber braucht separaten Server
3. **MySQL** — Ähnlich PostgreSQL, aber weniger geeignet für MVP
4. **MongoDB** — NoSQL, flexibel Schema, aber komplexere Konsistenz
5. **H2 In-Memory** — Nur für Tests, nicht Production-geeignet

---

## Decision

**SQLite 3.x für MVP**

- **Driver:** `org.xerial:sqlite-jdbc:3.49.x`
- **Dialect:** `org.hibernate.orm:hibernate-community-dialects` (provides `SQLiteDialect`)
- **File:** `budget-buddy.db` (single file im App-Verzeichnis)
- **Migrations:** Flyway 10.x
- **Connection Pool:** HikariCP (Standard in Spring Boot)

### Schema (Beispiele)

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,  -- bcrypt
    monthly_income DECIMAL(10,2),
    created_at TIMESTAMP,
    ...
);

CREATE TABLE transactions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    date DATE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    recipient TEXT,
    category TEXT,
    ...
);

CREATE TABLE fixed_costs (
    id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    description TEXT,
    amount DECIMAL(10,2),
    interval ENUM('MONTHLY', 'QUARTERLY', 'YEARLY'),
    ...
);
```

---

## Rationale

| Kriterium | SQLite | PostgreSQL | MySQL | MongoDB |
|-----------|--------|-----------|-------|---------|
| **Setup** | ✅✅ Keine Infrastruktur | ❌ Separater Server | ❌ Separater Server | ⚠️ Docker nötig |
| **Deployment** | ✅✅ Single JAR + File | ❌ Server + DB + Network | ❌ Server + DB + Network | ⚠️ Docker Container |
| **ACID Transactions** | ✅ Vollständig | ✅ Vollständig | ✅ Vollständig | ⚠️ Multi-doc (Replica Set) |
| **Concurrent Writes** | ⚠️ SQLite locks Datei | ✅ Row-level locking | ✅ Row-level locking | ✅ Document-level |
| **Scaling (Horizontal)** | ❌ Single-Datei | ✅ Read replicas + Sharding | ✅ Replication | ✅ Sharding |
| **Scaling (Vertikal)** | ⚠️ File-Size Limits | ✅ TB-Größen ok | ✅ TB-Größen ok | ✅ Beliebig |
| **Performance** | ✅ Schnell für SELECTs | ✅ Schnell | ✅ Schnell | ⚠️ Langsamer für Joins |
| **Backup** | ✅ Einfach (Datei kopieren) | ⚠️ Dump-basiert | ⚠️ Dump-basiert | ⚠️ Snapshot-basiert |
| **Full-Text Search** | ⚠️ Basic FTS5 | ✅ Mächtig (trigram, etc.) | ⚠️ Gut | ⚠️ Atlas Search |
| **Cloud-ready** | ⚠️ File auf Filesystem | ✅ Cloud SQL, RDS, etc. | ✅ Cloud SQL, RDS, etc. | ✅ MongoDB Atlas |
| **Cost** | ✅ $0 (Open Source) | ✅ $0 (Open Source) | ✅ $0 (Open Source) | ⚠️ $57+ (Atlas) |

**Konkrete Vorteile für BudgetBuddy MVP:**

1. **Zero Infrastructure:** Alles in einer JAR — keine Docker Compose für DB nötig
2. **Offline Development:** Lokal entwickeln ohne Internet / DB-Server
3. **Easy Backup:** `budget-buddy.db` als Single File → Copy-and-Paste Backup
4. **Simple Deployment:** 
   ```
   docker run -v /data:/app -p 8080:8080 budget-buddy:latest
   # → DB.sqlite ist automatisch in /data persistiert
   ```
5. **Flyway Migrations:** Automatisches Schema-Update beim Start
6. **Hibernate Dialect:** Spring Boot + JPA funktioniert out-of-the-box
7. **Testing:** Tests können `jdbc:sqlite::memory:` nutzen (keine Infrastruktur)

**Skalierungs-Limits:**
- **User:** ~10.000 (concurrent) ok
- **Transactions:** ~1M Rows ok (mit Indexes)
- **Concurrent Writes:** ~50 pro Sekunde (SQLite wartet auf Lock)

→ BudgetBuddy MVP wird diese Limits nicht erreichen (~100 User × 100 req/s = 10k req/s, aber meist reads)

---

## Consequences

### ✅ Positive

- **Einfachheit:** No DevOps needed; Entwickler können lokal arbeiten
- **Schnelligkeit:** MVP ist in 2-3 Sprints deployable
- **Testbarkeit:** Tests brauchen keine Docker-Infrastruktur
- **Backup:** Einfach die Datei auf USB kopieren
- **Zero Kosten:** Keine Cloud-DB-Gebühren

### ⚠️ Negative (und Mitigations)

| Problem | Mitigation |
|---------|-----------|
| **Concurrent Writes** | Mostly reads (Transaktions-Import ist batch). Wenn bottleneck: Add Queue (Kafka) + async workers |
| **No Horizontal Scaling** | Für MVP nicht nötig. Später: PostgreSQL + read replicas |
| **File-based (No RAID)** | Backups auf Cloud-Storage (GCS, S3). Automatisch täglich. |
| **No Built-in Replication** | Single Server ok für MVP. Later: Standby-Server mit WAL-basiertem Backup. |
| **Max File Size ~140 TB** | MVP wird max ~100 MB erreichen |
| **Locking on Writes** | Most queries sind READ-only. Write-heavy jobs werden async (Report-Generation). |

---

## Migration Path (wenn Limits erreicht)

**Wenn in 6-12 Monaten concurrent writes > 100/s:**

1. **Add Message Queue:** Async Jobs für Report-Generation, Bulk-Categorization
2. **Monitor:** Measure database performance (query time, lock contention)
3. **Migrate:** Create PostgreSQL database in parallel
   ```sql
   -- Create PostgreSQL schema
   CREATE TABLE users (...);
   CREATE TABLE transactions (...);
   
   -- Flyway automatically matches DDL
   ```
4. **Dual-Write:** Spring Data JPA + Hibernate-Dialect Switch
   ```properties
   # Old: sqlite
   spring.jpa.database=SQLITE
   
   # New: postgresql
   spring.jpa.database=POSTGRESQL
   spring.datasource.url=jdbc:postgresql://...
   ```
5. **Validate:** Run tests against both databases
6. **Cutover:** Switch production connection string → PostgreSQL
7. **Cleanup:** Delete SQLite file (keep as backup)

**Effort:** ~1-2 sprints wenn planned correctly

---

## Consequences During Migration

| Phase | Database | Bottleneck | Status |
|-------|----------|-----------|--------|
| **MVP (now)** | SQLite | None | ✅ Deploy immediately |
| **6 months** | SQLite | Concurrent writes at 50/s | ✅ Still ok (maybe with queue) |
| **12 months** | SQLite → PostgreSQL | Write locks | 🟡 Bottleneck starts |
| **18+ months** | PostgreSQL + Read replicas | Sharding (if >1M users) | 🟢 Scales horizontally |

---

## Alternatives Considered

### ⚠️ Option 1: PostgreSQL from Day 1

**Entscheidung:** Abgelehnt (MVP-Tempo)

**Begründung:**
- Extra Komplexität: Docker Compose setup
- Deployment schwieriger (muss DB-Server provisioner)
- Overkill für MVP (nicht nötig)
- Aber: Wenn Team PostgreSQL-Erfahrung hat + Docker-Setup schon da → gute Option

### ❌ Option 2: MongoDB

**Entscheidung:** Abgelehnt

**Begründung:**
- Finanz-Daten = struktur + Relations
  - User → [Transactions], Transactions → Category, FixedCosts → User
  - Relational Schema ist natural
- NoSQL wäre Document-Duplication (z.B. Transactions mit User-Data embedded)
- Joins würden auf Applikation-Layer sein (komplizierter)
- Multi-document ACID ist kompliziert (braucht Replica Set)

### ✅ Option 3: H2 In-Memory

**Entscheidung:** Nur für Tests

**Begründung:**
- H2 ist schnell, aber: In-Memory = Datenverlust beim Shutdown
- Für Production = nicht brauchbar
- Nur für Integration Tests sinnvoll (mit `jdbc:h2:mem:test`)

---

## JDBC Driver & Dialect

**Important:** SQLite braucht spezifische Spring Boot Configuration:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.49.0</version>
</dependency>

<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-community-dialects</artifactId>
    <version>6.5.3</version>
</dependency>
```

```properties
# application.properties
spring.datasource.url=jdbc:sqlite:budget-buddy.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=validate  # Flyway manages schema
```

---

## Backup Strategy

```bash
# Daily backup to Google Cloud Storage
gsutil -m cp -r budget-buddy.db gs://budget-buddy-backups/$(date +%Y-%m-%d).db

# Restore if needed
gsutil cp gs://budget-buddy-backups/2026-05-27.db ./budget-buddy.db
```

---

## Related Decisions

- **ADR-1:** Java + Spring Boot (JPA + Hibernate)
- **ADR-4:** Monolith (single shared database)

---

## References

- [SQLite Official Docs](https://www.sqlite.org/index.html)
- [SQLite JDBC Driver (Xerial)](https://github.com/xerial/sqlite-jdbc)
- [Hibernate SQLite Dialect](https://hibernate.org/orm/documentation/6.5/)
- [SQLite Limitations & Concurrent Writes](https://www.sqlite.org/differences.html)
- CLAUDE.md — Technology Stack
