# ADR-12: PostgreSQL bei Neon als Produktionsdatenbank

**Status:** Accepted
**Date:** 2026-08-09
**Supersedes:** [ADR-5](ADR-5-sqlite-mvp-database.md)

## Context

[ADR-5](ADR-5-sqlite-mvp-database.md) wählte SQLite als MVP-Datenbank und hielt unter *Offene
Frage: Persistenz in Produktion* fest, dass diese Wahl in Produktion nicht trägt: Auf dem Render
Free-Plan liegt die SQLite-Datei auf einem **ephemeren Filesystem**. Laut
[Render-Doku](https://render.com/docs/free) gehen Änderungen *"every time the service redeploys,
restarts, or spins down"* verloren — und ein Free-Service spinnt bereits nach 15 Minuten ohne
Traffic herunter. Faktisch überlebt die Datenbank keine Nacht.

Ein Persistent Disk ist auf dem Free-Plan nicht buchbar; er setzt ein Upgrade des Instance-Types
voraus. Acht Varianten wurden in ADR-5 mit Kosten, Backup-Verhalten und Migrationsaufwand
gegenübergestellt und in [Issue #78](https://github.com/dfme/budget-buddy/issues/78) zur
Abstimmung gestellt.

Der Zeitpunkt war bewusst gewählt: vier triviale Migrationen (V01–V04) und **keine zu erhaltenden
Daten**. Mit jedem weiteren Sprint — Fixkosten (BE-FC-\*), Safe-to-Spend (BE-STS-\*) — wären
weitere Tabellen und damit Migrationsaufwand hinzugekommen.

## Decision

Wir nutzen **PostgreSQL 18 bei [Neon](https://neon.com), Region Frankfurt/EU, Free-Plan**
([Abstimmungsergebnis zu Variante 4](https://github.com/dfme/budget-buddy/issues/78#issuecomment-5015065128)).
Der Render-Workspace bleibt auf Hobby. Der Web-Service lief zum Zeitpunkt dieses Entscheids auf
Free und wurde am 09.08.2026 auf Starter gewechselt (INFRA-24) — das ist Variante 8 der Analyse
und ändert an diesem Entscheid nichts, ausser dass der Cold Start des Web-Service entfällt.

Konkret:

- **JDBC-Treiber:** `org.postgresql:postgresql` (Spring-Boot-managed)
- **Hibernate-Dialect:** keiner konfiguriert — Hibernate erkennt PostgreSQL über die
  JDBC-Metadaten. `org.hibernate.orm:hibernate-community-dialects` entfällt ersatzlos.
- **Migrations:** Flyway 11.x, zusätzlich das Modul `org.flywaydb:flyway-database-postgresql`
  (seit Flyway 10 liegt der Datenbank-Support in eigenen Modulen)
- **Geldbeträge:** weiterhin `DECIMAL(10,2)` (ADR-9) — jetzt als echtes `numeric` statt als
  SQLite-Affinität
- **Verbindung in Produktion:** ausschliesslich über `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME` und `SPRING_DATASOURCE_PASSWORD` aus der Render-Umgebung; in
  `render.yaml` nur mit `sync: false` deklariert
- **Lokal:** `docker-compose.yml` im Repo-Root mit Postgres 18
- **Tests:** Testcontainers PostgreSQL 18 statt `jdbc:sqlite::memory:`

Postgres **18** ist der Default für neue Neon-Projekte (unterstützt werden 14–18, siehe
[Version Support Policy](https://neon.com/docs/postgresql/postgres-version-policy)). Lokal, in CI
und bei Neon läuft dieselbe Major-Version — andernfalls wäre der Dialekt-Mismatch nur verschoben.

## Rationale

- **Persistenz ist die Grundanforderung.** Alle Varianten mit externer Datenbank lösen sie; der
  Spin-Down des Web-Service kostet dann nur noch Latenz, keine Daten mehr.
- **Kein Ablaufdatum.** Render Managed Postgres Free läuft 30 Tage nach Erstellung ab, Supabase
  Free pausiert nach 7 Tagen Inaktivität und wird nach 90 Tagen gelöscht — beides derselbe
  Ausfallpfad, den die Migration beseitigen soll. Neon Free hat kein Ablaufdatum und weckt nach
  Scale-to-Zero (5 Min) automatisch wieder auf.
- **Frankfurt/EU** stützt dieselbe nDSG-Argumentation wie ADR-10 für den Web-Service.
- **Reines PostgreSQL.** Supabase bringt Auth, Storage und Realtime mit, was sich mit dem
  Spring-Boot-JWT aus ADR-7 überschneidet. Aus demselben Grund bleibt auch **Neon Auth** bewusst
  deaktiviert: es würde die bereits gebaute Auth-Schicht (BE-AUTH-01/02/03) ersetzen, bietet keine
  SDKs für Angular oder Java und würde die Nutzeridentität an Neon binden — der Anbieterwechsel
  wäre dann ein Auth-Neubau.
- **Kosten bleiben bei $0.**

## Consequences

### Positive

- **Daten überleben Redeploy, Restart und Spin-Down** — die eigentliche Motivation.
- **`hibernate-community-dialects` entfällt.** Ein Workaround weniger im Abhängigkeitsbaum.
- **ADR-9 gilt jetzt auch in der Datenbank.** Unter SQLite war `DECIMAL(10,2)` bloss eine
  *Affinität*: Werte lagen physisch als `REAL` in der Datei und liefen sehr wohl durch
  Binär-Gleitkomma ([#141](https://github.com/dfme/budget-buddy/issues/141)). PostgreSQL speichert
  `numeric(10,2)` exakt und erhält die Skala über den Round-Trip.
- **Tests laufen gegen dieselbe Engine wie Produktion.** Der Dialekt-Mismatch, vor dem `CLAUDE.md`
  unter *What NOT to Use* warnte, ist beseitigt statt verschoben.
- **Echte Parallelität möglich.** Der Single-Writer-Engpass von SQLite entfällt.

### Negative — was mit SQLite verloren geht

ADR-5 führte drei Vorteile an, die mit diesem Entscheid **alle drei entfallen**. Das ist der
bewusst akzeptierte Preis für Persistenz:

- **„Zero Infrastructure" ist weg.** Es gibt jetzt einen zweiten Dienst mit eigenem Dashboard,
  eigenem Zugang und eigenem Ausfallverhalten. Ein Dev muss Neon *und* Render kennen.
- **„Offline Development" ist weg.** Ohne laufenden Docker-Container startet das Backend lokal
  nicht mehr. Wer ohne Docker arbeiten muss, braucht einen eigenen Neon-Branch (Free-Plan erlaubt
  10 pro Projekt) — und damit eine Internetverbindung.
- **„Einfaches Deployment für Team" ist weg.** Die Datenbank ist keine Datei mehr, die man kopiert.
  Ein Dump ist ein eigener Arbeitsschritt mit eigenen Werkzeugen.

Dazu kommen:

- **Docker wird Pflicht** — lokal für `docker compose`, in CI für Testcontainers und den
  Service-Container des E2E-Jobs. Auf GitHub-hosted Runnern ist das gegeben.
- **Cold Start bleibt — seit dem Wechsel auf Starter nur noch einer.** Neon skaliert nach 5 Min
  auf null und wacht beim nächsten Zugriff automatisch auf; der erste Request danach ist
  entsprechend langsam. Der zweite Cold Start (Render-Spin-Down nach 15 Min) ist mit Variante 8
  am 09.08.2026 entfallen (INFRA-24). Bei Verfassen dieses ADR war er noch Teil der
  Konsequenzen.
- **Kein vollwertiges Backup.** Neon Free bietet ~6 h PITR. Für ein Kursprojekt akzeptiert; ein
  echtes Backup-Fenster kostet Geld (ADR-5, Variante 7).
- **Grenzen des Free-Plans:** 0,5 GB Storage und 100 CU-h pro Projekt. Der Storage ist für
  MVP-Scale (~1.000 User × ~1.000 Transaktionen) unkritisch — das Compute-Kontingent ist es
  nicht: 100 CU-h sind bei der Free-Computegrösse von 0,25 CU rund 400 Stunden und damit keine
  17 Tage Dauerbetrieb. Es reicht nur, solange Scale-to-Zero tatsächlich greift. Am 23.08.2026
  waren 80 % verbraucht, ohne dass ein Nutzer die App angefasst hätte: Renders Dauerping auf den
  DB-behafteten `/actuator/health` hielt den Compute seit INFRA-24 wach (abgestellt in
  INFRA-28).
- **`sslmode=require` verschlüsselt, authentifiziert den Server aber nicht.** Die Verbindung ist
  gegen Mitlesen geschützt, nicht gegen einen aktiven Man-in-the-Middle: pgjdbc prüft in diesem
  Modus weder Zertifikatskette noch Hostname. Dagegen hülfe nur `sslmode=verify-full`, und das ist
  spürbarer Aufwand — pgjdbc zieht dafür `sslrootcert` heran statt des JVM-Truststores, das
  Zertifikat müsste also ins Deployment. Für ein Kursprojekt mit Testdaten ist das Restrisiko
  akzeptiert; es steht hier, weil Risiko #2 aus `CLAUDE.md` (Datenleck, nDSG) es sonst
  stillschweigend unterschlüge. Vor echten Nutzerdaten ist `verify-full` nachzuholen.
- **`COLLATE NOCASE` hat keine Entsprechung.** Die case-insensitive Zuordnung in
  `category_lookup` liegt jetzt in der Anwendung: `CategoryLearningService` normalisiert Patterns
  auf Grossschreibung, `CategoryLookupRepository#findMatching` vergleicht über `upper()`.

### Neutral

- **Der Migrationsweg zu einem anderen Postgres bleibt offen.** Neon ist reines PostgreSQL, das
  Schema liegt in Flyway. Ein Wechsel zu Render Postgres oder einem Schweizer Anbieter ist ein
  Connection-String, kein Rewrite — vorausgesetzt, es kommen keine Neon-spezifischen Funktionen
  dazu.

## Alternatives

Die vollständige Gegenüberstellung aller acht Varianten mit Kosten, Backup-Verhalten und
Migrationsaufwand steht in [ADR-5, „Offene Frage: Persistenz in Produktion"](ADR-5-sqlite-mvp-database.md#offene-frage-persistenz-in-produktion)
und bleibt dort als Entscheidungsgrundlage erhalten. Die wesentlichen Ablehnungen:

| Variante | Warum nicht |
| -------- | ----------- |
| **1 — Status quo (SQLite, ephemer)** | Löst das Problem nicht. Datenverlust bei jedem Spin-Down. |
| **3 — Render Managed Postgres Free** | Läuft 30 Tage nach Erstellung ab, danach 14 Tage Grace, dann Löschung. |
| **5 — Supabase Free** | Pausiert nach 7 Tagen Inaktivität, manueller Restore, Löschung nach 90 Tagen. Überschneidet sich zudem mit ADR-7. |
| **6a/6b — Render Paid Instance + Disk** | Behält SQLite, spart aber keine Arbeit: ohne WAL, `busy_timeout` und Pool-Grösse 1 ist der always-on-Betrieb nicht tragfähig. Backup nur als Disk-Snapshot, den Render für Datenbanken ausdrücklich nicht empfiehlt. Kostet $7.25/Mt. |
| **7 — Render Postgres Basic-256MB** | Einziges echtes Backup (PITR 3 Tage), aber $6/Mt für ein Kursprojekt ohne Produktionsdaten. |
| **8 — Web-Service auf Starter + Neon** | Identisch zum gewählten Entscheid plus $7/Mt gegen den Cold Start. Jederzeit als Dropdown-Wechsel nachrüstbar, falls er beim Präsentieren stört. |

## Setup: Neon-Projekt und Render-Variablen

Einmalig manuell auszuführen; es gibt bewusst kein Skript dafür, weil dabei ein Credential
entsteht, das nie ins Repository gehört.

1. **Neon-Projekt anlegen** — [neon.tech](https://neon.com) → *New Project*
   - Name `budget-buddy`, **Region Europe (Frankfurt)** — nicht US, die EU-Region trägt die
     nDSG-Argumentation aus ADR-10
   - Postgres 18, Free-Plan
2. **Datenbank** `budgetbuddy` anlegen oder die Default-Datenbank verwenden.
3. **Connection-String holen** — *Connect* liefert eine URI der Form
   `postgresql://<user>:<password>@<host>.eu-central-1.aws.neon.tech/<db>?sslmode=require`.
   Ein Java-/JDBC-Snippet bietet Neon nicht an; die Umformung folgt in Schritt 5.
   `npx neonctl@latest init` ist dafür nicht nötig — es scaffoldet JS-Projekte und legt
   `.env`-Dateien an, die in einem Spring-Boot-Repo nichts zu suchen haben.
4. **Zweite Person einladen** — *Project settings → Members*. Der Render-Workspace bleibt laut
   Entscheid auf Hobby und erlaubt damit genau einen Admin; dieser Engpass soll sich nicht
   verdoppeln.
5. **Render-Variablen setzen** — *Service budgetbuddy → Environment*. Die URI aus Schritt 3 wird
   auf drei Variablen aufgeteilt. **An dieser Umformung sind beim ersten Deploy zwei Versuche
   hintereinander gescheitert**, deshalb hier ausgeschrieben statt beschrieben:

   ```
   Neon liefert:
   postgresql://<USER>:<PASSWORT>@<HOST>.eu-central-1.aws.neon.tech/<DB>?sslmode=require

   Daraus wird:
   SPRING_DATASOURCE_URL       jdbc:postgresql://<HOST>.eu-central-1.aws.neon.tech/<DB>?sslmode=require
   SPRING_DATASOURCE_USERNAME  <USER>
   SPRING_DATASOURCE_PASSWORD  <PASSWORT>
   ```

   Die Platzhalter stehen hier bewusst in spitzen Klammern statt als Beispielwerte: Ein
   realistisch aussehendes Passwort in der Doku wird irgendwann kopiert.

   Alles zwischen `//` und `@` — einschliesslich des `@` — wandert in die beiden anderen
   Variablen. Der Rest der URL bleibt unverändert. Der fehlende Port ist Absicht: pgjdbc nimmt
   dann 5432.

   Der Wert von `SPRING_DATASOURCE_URL` muss beide Bedingungen erfüllen: er **beginnt mit
   `jdbc:postgresql://`** und **enthält kein `@`**. Trifft eines nicht zu, startet der Container
   nicht:

   | Fehler | Meldung im Render-Log |
   | ------ | --------------------- |
   | `jdbc:`-Präfix fehlt | `'url' must start with "jdbc"` |
   | Zugangsdaten in der URL gelassen | `JDBC URL invalid port number: <PASSWORT>@<HOST>`, danach `Driver org.postgresql.Driver claims to not accept jdbcUrl, …` |
   | Variablen fehlen oder falsch benannt | `Unable to obtain connection from database: Connection to localhost:5432 refused` — der Start bricht ab |

   **Zum zweiten Fall:** pgjdbc scheitert bereits am Parsen der URL, nicht an der Namensauflösung.
   Alles nach dem ersten `:` im Autoritätsteil wird als Port gelesen — bei
   `//user:passwort@host/db` also `passwort@host`. Eine DNS-Auflösung findet nie statt.
   **Damit steht das Passwort im Klartext im Render-Log**, gleich zweimal: in der
   `invalid port number`-Warnung und in der vollständigen `jdbcUrl` der Folgemeldung. Zugangsdaten
   aus der URL herauszuhalten ist deshalb nicht nur eine Startbedingung, sondern verhindert ein
   Leck — Render-Logs unterliegen einer anderen Zugriffskontrolle als die Datenbank.

   **Zum dritten Fall:** Der Start bricht während der Context-Initialisierung ab, weil
   `spring.flyway.enabled=true` die Verbindung schon beim Hochfahren erzwingt. Es gibt kein
   Zeitfenster, in dem der Dienst läuft und erst später Fehler liefert; der Health-Check wird nie
   grün und Render meldet einen fehlgeschlagenen Deploy.

   Tückisch ist dieser Fall trotzdem, aber **lokal**: Auf einem Rechner mit laufendem Postgres auf
   `localhost:5432` greifen die Defaults aus `application.properties`, und die App läuft klaglos
   gegen die **falsche** Datenbank — ohne jede Meldung. Ein Fail-fast dagegen ist als
   [INFRA-25](https://github.com/dfme/budget-buddy/issues/150) erfasst.

   `?sslmode=require` bleibt stehen — Neon nimmt ausschliesslich TLS-Verbindungen an. Steht in
   der URI zusätzlich `&channelBinding=require`, kann das ebenfalls bleiben; pgjdbc 42.7 kennt
   den Parameter.

   Zwei Stolperer beim Kopieren: Neon bietet teils den **psql-Befehl** an (`psql 'postgresql://…'`)
   statt der nackten URI — dann kommen `psql ` und Anführungszeichen mit. Und die Änderung muss
   im Dashboard mit *Save changes* bestätigt werden; ein Deploy davor nimmt noch den alten Wert.

6. **Verifizieren** — nach dem Deploy einen User registrieren, *Manual Deploy → Clear build cache*
   auslösen und danach einloggen. Zweite Probe: 6 Minuten warten (Neon Scale-to-Zero) und erneut
   aufrufen. Die Daten müssen beide Male da sein.

## Related

- [ADR-5](ADR-5-sqlite-mvp-database.md) — superseded; enthält die vollständige Variantenanalyse
- [ADR-9](ADR-9-bigdecimal-money.md) — `BigDecimal`/`DECIMAL(10,2)` gilt unverändert und wird
  durch diesen Entscheid erstmals auch auf Datenbankebene eingelöst
- [ADR-10](ADR-10-hosting-plattform.md) — Hosting auf Render; die dort offene Persistenzfrage ist
  mit diesem ADR beantwortet
- [Issue #78](https://github.com/dfme/budget-buddy/issues/78) — Entscheidungsvorlage und Abstimmung
- [Issue #89](https://github.com/dfme/budget-buddy/issues/89) — Umsetzung (DB-05)
