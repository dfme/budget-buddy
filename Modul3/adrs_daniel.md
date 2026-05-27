# Architecture Decision Records (ADRs) — BudgetBuddy

**Projekt:** BudgetBuddy
**Team:** 2–3 Entwickler
**Zeitrahmen:** 3 Monate (MVP)
**Stand:** 2026-05-27
**Format:** Michael Nygard

## Leitende Qualitätsattribute

| Attribut    | Persona | Bedeutung                                                                |
| ----------- | ------- | ------------------------------------------------------------------------ |
| Security    | Marc    | Schützt davor, dass uns die Zielgruppe Marc vertraut.                    |
| Usability   | Lara    | Sorgt dafür, dass die Zielgruppe Lara überhaupt zum Vertrauenstest kommt. |
| Reliability | beide   | Sorgt dafür, dass das Vertrauen über die Zeit hält.                      |

---

## ADR-001: Web-Stack — Angular (Frontend) + Spring Boot (Backend)

**Status:** Proposed

### Kontext

BudgetBuddy ist eine Web-App, die einen typsicheren Backend-Stack für Finanz-/Geldoperationen sowie eine reaktive, formularstarke UI braucht (PDF-Upload, Fixkosten-Wizard, Kategorien-Korrektur). Das Team besteht aus 2–3 Entwicklern mit Java-Hintergrund und hat 3 Monate für ein MVP. Die Plattform-Reife (LTS, Security-Patches, Ökosystem) ist für eine Finanz-App nicht verhandelbar, weil sie direkt auf das Vertrauen der Persona Marc einzahlt.

### Entscheidung

Wir bauen das Frontend mit **Angular 19** (Standalone Components, Signals, OnPush, Reactive Forms) und das Backend mit **Java 25 (LTS) + Spring Boot 3.5.x** als synchroner Spring MVC Stack. Wir verwenden **Springdoc OpenAPI 3** zur automatischen API-Dokumentation und vermeiden bewusst Spring Boot 4 (Milestone) sowie Spring WebFlux (passt nicht zum blockierenden SQLite-JDBC).

### Konsequenzen

**Positiv (+)**

- Industriestandard-Stack mit aktiver Sicherheits-Wartung — Security-Patches landen zeitnah, was direkt das Vertrauen von Marc absichert.
- Reactive Forms + Signals + Angular CLI ermöglichen schnelles Bauen formularlastiger UIs (Onboarding, Kategorien-Korrektur) — Lara kommt mit wenigen Klicks zum „Aha"-Moment.
- Spring Boot LTS und Java 25 LTS bieten eine planbare Lebensdauer über die Projektzeit hinaus — Reliability über Monate hinweg ohne erzwungene Migrationen.
- Springdoc generiert OpenAPI 3 automatisch — Frontend/Backend bleiben kontraktsynchron, weniger Integrations-Bugs.

**Negativ (−)**

- Angular hat eine steilere Lernkurve als z. B. React/Svelte; in einem 3-Monats-MVP zählt jede Stunde, und neue Teammitglieder brauchen Ramp-up.
- Der Spring-Stack erzwingt mehr Boilerplate (Configuration, Beans, Profiles) als ein leichtgewichtiges Framework wie z. B. Node/Express — höhere initiale Komplexität.
- Synchrones Spring MVC limitiert die Skalierung bei gleichzeitig vielen langlaufenden Calls (PDF-Parsing, Claude API) — wir müssen Threadpool-Größen bewusst dimensionieren.

---

## ADR-002: SQLite als MVP-Datenbank (statt PostgreSQL)

**Status:** Proposed

### Kontext

Im MVP sollen wenige Pilot-User (Lara, Marc und ihre Peers) die App testen; das Schreibvolumen ist niedrig (PDF-Uploads, Kategorien-Korrekturen). Ein dediziertes DB-Setup (Postgres-Container, Backups, Schema-Migrationen, Netzwerk) ist für ein 3-Personen-Team über 3 Monate ein erheblicher Overhead. Gleichzeitig müssen wir Geldbeträge präzise speichern (BigDecimal/NUMERIC) und Schema-Änderungen versioniert mitziehen können, weil Lara sonst beim nächsten Release in einer leeren oder defekten Datenbank steht.

### Entscheidung

Wir verwenden **SQLite 3.x** mit dem **xerial sqlite-jdbc**-Treiber, **Hibernate Community Dialect** (`SQLiteDialect`) und **Flyway 10.x** für versionierte Migrationen. Geldbeträge werden ausschließlich als `BigDecimal` mit `DECIMAL(19,2)` persistiert — niemals `double`/`float`. Ein Migrationspfad zu PostgreSQL bleibt durch JPA und Flyway offen.

### Konsequenzen

**Positiv (+)**

- Keine externe DB-Infrastruktur — Setup, lokale Entwicklung und CI laufen sofort, das Team kann Tag 1 produktiv coden statt DevOps zu konfigurieren.
- Eine einzelne Datei (`budgetbuddy.db`) erleichtert Backups, Wiederherstellung und nDSG-konforme Löschung im MVP-Betrieb.
- `BigDecimal` + Flyway garantieren, dass Beträge auf den Rappen genau gespeichert und Schemata über alle Umgebungen konsistent bleiben — kritisch für die Reliability der Safe-to-Spend-Berechnung.

**Negativ (−)**

- SQLite serialisiert Schreibzugriffe — bei vielen gleichzeitigen Uploads/User wird die DB zum Engpass; ein Wachstum über das MVP hinaus erzwingt eine Postgres-Migration.
- Kein eingebautes Rollen-/User-Konzept auf DB-Ebene und keine Replikation/HA — operatives Härten der Reliability ist nur eingeschränkt möglich.
- Die Hibernate-Unterstützung ist „Community", nicht „First-Party" — Edge-Cases (Locking, Concurrency, bestimmte JPA-Features) können überraschende Bugs erzeugen, die Debugging-Zeit kosten.

---

## ADR-003: Stateless Authentication mit JWT (HS256)

**Status:** Proposed

### Kontext

BudgetBuddy speichert sensible Finanzdaten — Authentication ist die erste Verteidigungslinie und der entscheidende Hebel für Marcs Datenschutz-Skepsis. Wir haben eine SPA (Angular) plus REST-Backend (Spring Boot) und müssen Schreibzugriffe auf SQLite minimieren, weil SQLite Writes serialisiert (siehe ADR-002). Ein klassisches serverseitiges Session-Management würde bei jedem Request schreiben und die DB zusätzlich belasten.

### Entscheidung

Wir implementieren **Stateless Authentication** auf Basis von **JWT mit HS256** (signiert) via **JJWT 0.12.x** und konfigurieren Spring Security als **OAuth2 Resource Server (`jwt()`)**. Tokens haben eine kurze Lebensdauer (≤ 60 Minuten); Logout erfolgt clientseitig durch Token-Löschung; Passwörter werden serverseitig mit **bcrypt** (Kostenfaktor ≥ 12) gehasht.

### Konsequenzen

**Positiv (+)**

- Keine Session-Tabelle und keine Schreibzugriffe pro Request — SQLite bleibt entlastet, was die Reliability unter Last hochhält.
- Bcrypt + signierte JWTs entsprechen Industriestandard für Web-Apps mit Finanzdaten — direkt einzahlbar auf Marcs Vertrauen.
- Spring Security bringt JWT-Resource-Server-Support „first-class" mit — wenig eigener Code, geringere Angriffsfläche durch Eigenimplementierung.

**Negativ (−)**

- Stateless heißt: ein einmal ausgegebenes Token bleibt bis Ablauf gültig — keine sofortige serverseitige Invalidierung bei Verdacht auf Kompromittierung (Mitigation: kurze TTL + späteres Refresh-/Blacklist-Konzept).
- HS256 nutzt ein geteiltes Secret — ein Leak des Secrets kompromittiert alle Tokens; das Secret muss strikt aus `.env`/Vault geladen werden, nie commitet.
- Der Client (Angular) muss Tokens sicher halten (HttpOnly-Cookie vs. Storage-Trade-offs) — XSS-Risiko erfordert eine konsequente Content-Security-Policy.

---

## ADR-004: PDF-Inhalte werden extrahiert, aber nicht persistiert

**Status:** Proposed

### Kontext

Persona Marc misstraut Web-Apps mit Finanzdaten — jeder unnötig gespeicherte Datensatz erhöht das Risiko eines Datenlecks und damit die Existenzbedrohung des Projekts. Schweizer Bank-PDFs (UBS, Raiffeisen, PostFinance) enthalten zusätzlich zur Transaktionsliste sensible Metadaten (Saldo, Kontonummer, Adresse). Für die Kernfunktion (Kategorisierung, Safe-to-Spend) benötigen wir ausschließlich die extrahierten Felder Datum, Betrag und Empfänger pro Transaktion — das Original-PDF wird danach nicht mehr gebraucht.

### Entscheidung

Wir verarbeiten hochgeladene PDFs **ausschließlich im Speicher** mit **Apache PDFBox 3.x** (`Loader.loadPDF()`), persistieren **nur die extrahierten Transaktionsfelder** (Datum, Betrag, Empfänger, Kategorie, SHA-256-Hash zur Duplikaterkennung) und **verwerfen das PDF-Original sofort nach erfolgreichem Parsing**. Passwortgeschützte PDFs werden explizit abgelehnt; die Upload-Größe ist auf 10 MB limitiert.

### Konsequenzen

**Positiv (+)**

- Minimaler Blast Radius bei einem Datenleck — Bank-Originale, Saldi und Kontonummern liegen erst gar nicht auf dem Server, was direkt Marcs Datenschutz-Bedenken adressiert.
- Klare, ehrliche Datenschutzerklärung möglich („Wir speichern keine Kontoauszüge") — stärkt das Vertrauen und vereinfacht die nDSG-konforme Löschung beim Account-Delete.
- Kein Storage-Wachstum durch PDFs — die DB bleibt klein, Backups bleiben schnell, Reliability bei Wiederherstellung bleibt hoch.

**Negativ (−)**

- Bei einem Parser-Bug können extrahierte Transaktionen unvollständig sein, ohne dass der User das Original-PDF zur Re-Verifikation hat — er muss erneut hochladen.
- Spätere Features (z. B. „Original-Beleg anzeigen", Audit-Log mit PDF-Referenz, OCR-Vergleich) sind im MVP nicht möglich, ohne die Entscheidung zurückzunehmen.
- Die Duplikaterkennung basiert auf dem SHA-256-Hash des PDFs vor dem Verwerfen — kleinste Änderungen am PDF (z. B. derselbe Auszug, zweimal exportiert) führen zu unterschiedlichen Hashes und damit potenziellen Dubletten.

---

## ADR-005: Hybride Transaktions-Kategorisierung (Lookup-Tabelle + Claude API)

**Status:** Proposed

### Kontext

Die Auto-Kategorisierung ist das zentrale Usability-Versprechen: Wenn Lara nach dem PDF-Upload eine korrekt vorsortierte Kategorienübersicht sieht, bleibt sie in der App; wenn sie 50 Einträge manuell labeln muss, ist sie weg. Gleichzeitig sind reine LLM-Calls pro Transaktion teuer (Kosten + Latenz) und schaffen eine harte Außenabhängigkeit, die Reliability gefährdet. Erfahrungswerte zeigen, dass ca. 70–80 % der Transaktionen über wiederkehrende Händlernamen abgebildet werden (Migros, Coop, SBB, Swisscom, …).

### Entscheidung

Wir kategorisieren in **drei Stufen**: (1) **Statische Lookup-Tabelle** Händlername → Kategorie als erster, deterministischer Pfad; (2) **Claude Haiku** (`claude-haiku-3-5-20241022`) als API-Fallback ausschließlich für nicht in der Lookup-Tabelle gefundene Transaktionen; (3) **Manuelle User-Korrekturen** schreiben den Empfänger zurück in die Lookup-Tabelle und wirken so als kontinuierliches Training ohne Modell-Retraining. Bei Claude-API-Fehlern wird die Transaktion mit der Default-Kategorie `Sonstiges` markiert.

### Konsequenzen

**Positiv (+)**

- Hohe Trefferquote ohne API-Kosten für die häufigen Fälle — Lara sieht beim ersten Upload sofort eine sinnvoll sortierte Übersicht, der Aha-Moment kommt schnell.
- Eingebauter Fallback auf `Sonstiges` bei API-Ausfall — der Import schlägt nie wegen Claude-Downtime fehl, was die Reliability hochhält.
- Selbstverbesserung durch User-Korrekturen — die Lookup-Tabelle wird mit jedem Nutzer genauer, ohne dass das Team aktiv „trainieren" muss.

**Negativ (−)**

- Zwei Code-Pfade (Lookup + LLM) erhöhen die Komplexität und damit die Testfläche — wir brauchen ein klares Test-Set (mind. 200 gelabelte Transaktionen, vgl. US-5).
- Die initiale Lookup-Tabelle muss vom Team manuell befüllt werden — Aufwand vor dem ersten Release; bei schlechter Befüllung verschiebt sich Last und Kosten zur Claude API.
- Eine Lookup-„Vergiftung" durch fehlerhafte User-Korrekturen kann sich auf andere User auswirken, wenn die Tabelle global geteilt ist — wir müssen entscheiden, ob die Tabelle user-lokal, global oder hybrid ist (Mitigation: im MVP user-lokal, später ggf. global mit Schwellenwerten).

---

## ADR-006: nDSG-konforme Account-Löschung als Hard Delete

**Status:** Proposed

### Kontext

Das Schweizer Datenschutzgesetz (nDSG) gibt Nutzern ein **Recht auf Löschung** — verpflichtend und nicht delegierbar. Persona Marc ist datenschutzkritisch und prüft genau, was beim Account-Delete passiert; eine reine „Soft-Delete"-Markierung (Flag `deleted=true`) genügt der gesetzlichen Anforderung nicht und wäre vertrauenszerstörend, sobald jemand das herausfindet. Gleichzeitig brauchen wir Backups, um operative Reliability zu gewährleisten — Backups dürfen aber nicht ewig leben, sonst läuft das Löschrecht ins Leere.

### Entscheidung

Beim Account-Delete führen wir einen **physischen Hard Delete** durch: Alle personenbezogenen Daten (Profil, Transaktionen, Fixkosten, Sparziele, Lookup-Korrekturen) werden aus der Produktionsdatenbank gelöscht. Ein erneuter Login mit denselben Zugangsdaten schlägt fehl; ein DB-Admin findet keinen Eintrag mit der gelöschten User-ID. **Backups werden gemäß Datenschutzerklärung nach spätestens 30 Tagen überschrieben.**

### Konsequenzen

**Positiv (+)**

- Klare, prüfbare Erfüllung des nDSG-Löschrechts — die Datenschutzerklärung kann diese Garantie explizit aussprechen und stärkt Marcs Vertrauen messbar.
- Datenminimierung als Default — gelöschte User-Daten können nicht versehentlich in Reports, Analytics oder Logs auftauchen.
- Einfaches mentales Modell für das Team — „Delete heißt weg" ist leichter korrekt umzusetzen als ein dauerhafter Soft-Delete-Pfad mit Filter-Logik in jeder Query.

**Negativ (−)**

- Versehentliche Löschungen sind unwiderruflich — wir brauchen eine zusätzliche Bestätigungsstufe in der UI (z. B. Passwort-Re-Entry) und im API (idempotenter, signierter Bestätigungs-Token).
- Audit-/Forensik-Spuren verschwinden mit — bei späteren Streitfällen (Betrug, Support-Eskalation) gibt es keinen Datensatz mehr; eine separate, anonymisierte Audit-Log-Spur muss bewusst geplant werden.
- Cascading Deletes über alle Tabellen sind fehleranfällig — vergessene Fremdschlüssel führen zu „Daten-Leichen", was die Compliance-Garantie technisch unterläuft (Mitigation: integrationstestbarer Delete-Job + DB-Admin-Stichprobe in CI).

---

## ADR-007: Spring Boot 3.5 LTS — kein Wechsel auf Spring Boot 4 im MVP

**Status:** Proposed

### Kontext

Spring Boot 4 ist zum Projektstart in Milestone-/RC-Releases verfügbar, nicht als stabile GA-Version. Ein 3-Personen-Team mit 3 Monaten Zeit kann sich keine instabilen Plattform-Komponenten leisten — jeder Plattform-Bug verbraucht Kapazität, die direkt vom Bauen der Features Lara/Marc abgezogen wird. Gleichzeitig hängen Drittbibliotheken (Springdoc 2.8.17, Spring Security 6.5, JPA-Treiber) in den ersten Wochen typischerweise hinter Major-Sprüngen her und werden bei einem Pre-GA-Sprung zur Risikoquelle.

### Entscheidung

Wir bleiben für das MVP auf **Spring Boot 3.5.x (LTS-Linie)** mit **Java 25 (LTS)** und ziehen Spring Boot 4 **nicht** vor das erste produktive Release. Updates innerhalb der 3.5-Linie (Patch-Releases mit Security-Fixes) werden zeitnah eingespielt. Ein Sprung auf 4.x wird als **eigenes Backlog-Item nach dem MVP** geplant.

### Konsequenzen

**Positiv (+)**

- Stabile, breit erprobte Plattform — keine Zeit für „Random Bugs aus dem Framework" zu verlieren; die Reliability des Builds bleibt hoch.
- Volle Kompatibilität mit allen geplanten Drittbibliotheken (Springdoc, JJWT, sqlite-jdbc, Anthropic SDK) — keine Wartezeiten, keine eigenen Patches.
- Security-Patches der 3.5-Linie sind über Monate garantiert — Marcs Sicherheitsversprechen bleibt einlösbar, ohne dass das Team auf eine neue Major-Version umsteigen muss.

**Negativ (−)**

- Wir verzichten temporär auf Features/Performance-Verbesserungen aus Spring Boot 4 — ein Catch-up-Cost entsteht später.
- Beim späteren Upgrade auf 4.x häufen sich Breaking Changes mehrerer Releases an — der Migrationsschritt wird größer, je länger wir warten.
- Externe Lerninhalte/Beispiele beziehen sich in 6–12 Monaten zunehmend auf 4.x — Onboarding neuer Devs wird leicht erschwert, weil die Doku „auseinanderdriftet".

---

## Zusammenfassung — Mapping Qualitätsattribut → ADR

| ADR     | Entscheidung                                  | Security | Usability | Reliability |
| ------- | --------------------------------------------- | :------: | :-------: | :---------: |
| ADR-001 | Angular + Spring Boot 3.5 + Java 25           |          |     X     |      X      |
| ADR-002 | SQLite (MVP) mit Flyway + BigDecimal          |          |           |      X      |
| ADR-003 | Stateless JWT (HS256) + bcrypt                |    X     |           |      X      |
| ADR-004 | PDFs nicht persistieren                       |    X     |           |             |
| ADR-005 | Hybrid-Kategorisierung Lookup + Claude API    |          |     X     |      X      |
| ADR-006 | nDSG-konforme Hard-Delete-Löschung            |    X     |           |             |
| ADR-007 | Spring Boot 3.5 LTS (kein Boot 4 im MVP)      |    X     |           |      X      |
