##########
# Prompt #
##########

Hier ist unser Architecture Draft:
- C2 Container Diagram befindet sich im CLAUDE.md
- Alles ADRs sind unter @docs/adr/README.md
- Pattern-Entscheidungen: Modularer Monolith (ADR-4)

Prüfe auf Konsistenz:
1. Stimmen ADRs mit dem C2-Diagram überein?
2. Gibt es Widersprüche zwischen den Entscheidungen?
3. Passen die gewählten Patterns zu unserem Team (3 Devs, 3 Monate)?
4. Was passt zusammen — was beisst sich?


###########
# Antwort #
###########

Architecture Consistency Check — BudgetBuddy
1. ADRs vs. C2 Diagram — Übersicht
Thema	C2 Diagram	ADR	Status
Auth-Mechanismus	httpOnly Cookie, SameSite=Strict, withCredentials:true	ADR-7: identisch	✅ Konsistent
Angular + SPA	Angular 21, withCredentials:true, kein HttpInterceptor	ADR-0 + ADR-2: identisch	✅ Konsistent
Backend JAR	Spring Boot 3.5 / Java 21, Single JAR	ADR-1 + ADR-4: identisch	✅ Konsistent
Deployment	Render Frankfurt/EU, SPA gebündelt im JAR	ADR-10: identisch	✅ Konsistent
Kategorisierung	Haiku für Kategorisierung, Sonnet für Monatsbericht	ADR-6: identisch	✅ Konsistent
Paketstruktur	auth/, transaction/, categorization/ etc.	ADR-4: identisch	✅ Konsistent
Import Flow	synchron, Timeout + Fallback "Sonstiges"	ADR-4 + ADR-6: identisch	✅ Konsistent
BigDecimal	implizit in C2	ADR-9: ✅	✅ Konsistent
Grundsätzlich: Die ADRs und das C2 Diagram sind gut synchronisiert. Drei Ausnahmen:

2. Gefundene Widersprüche
⚠️ Widerspruch 1 — "JWT in Header" in CLAUDE.md (veraltet)
In der ADR-Übersichtstabelle in CLAUDE.md steht:

ADR-0 | SPA + REST API (Angular ↔ Spring Boot, **JWT in Header**)

Das ist falsch — ADR-0 und ADR-7 sagen beide klar: httpOnly Cookie, kein Bearer-Header. Das "JWT in Header" ist vermutlich ein Überbleibsel eines früheren Entwurfs. Ein neuer Entwickler, der nur das CLAUDE.md liest, bekommt hier falschen Input.

Fix: In CLAUDE.md die Zeile auf JWT als httpOnly Cookie korrigieren.

⚠️ Widerspruch 2 — C2 zeigt GET /import/{jobId}/status, aber MVP ist synchron
Das C2 Diagram enthält:


ImportJob-Status: GET /import/{jobId}/status
Und die DB-Tabellen im C2 listen bereits import_jobs. ADR-4 ist aber klar: MVP ist synchron — kein Async Worker, kein Polling-Endpoint. Der Upgrade-Pfad auf Async ist explizit als Phase 2 beschrieben.

Das C2 "leakt" eine zukünftige Architektur in die aktuelle Darstellung. Wenn ein Dev diesen Endpoint implementiert und ein anderer davon ausgeht, er existiere, entsteht Verwirrung.

Fix: Im C2 den Endpoint als (Phase 2) markieren oder komplett entfernen bis zur Implementierung.

ℹ️ Inkonsistenz 3 — ADR-4 nennt "Cloud Run / VPS", ADR-10 sagt Render
ADR-4 beschreibt den Deployment-Artefakt als:

Single JAR → Docker → Cloud Run / VPS

ADR-10 hat die konkrete Entscheidung: Render (Frankfurt/EU). Kein echter Widerspruch, aber ADR-4 ist etwas veraltet im Deployment-Detail.

3. Was passt besonders gut zusammen
Auth-Kette ist kohärent end-to-end:


Spring setzt httpOnly Cookie → Browser speichert (kein JS-Zugriff)
→ Angular: withCredentials:true → Cookie automatisch mitgesendet
→ Spring Security: validiert JWT-Signature aus Cookie
→ SameSite=Strict: verhindert CSRF ohne extra Token
Das ist sauber — keine Lücke, kein doppelter Aufwand.

Monolith + SQLite + Render passt für 3 Devs:

Ein Repo, ein Deployment, eine DB-Datei
Kein Kubernetes, kein Redis, kein Message Queue
java -jar app.jar reicht für Render
Synchroner Import + Fallback-Kette ist richtig für MVP:

Claude-Fehler blockt nicht den Import (Churn-Risiko #1 explizit adressiert)
CategorizationPort-Interface erlaubt Mock in Tests ohne echte API
4. Was sich beissen könnte
SQLite WAL + 1 Writer ist ein unsichtbares Limit:
ADR-4 erwähnt HikariCP max 1 Writer — das steht aber nur dort, nicht im C2 und nicht in CLAUDE.md. Wenn zwei Requests gleichzeitig Transaktionen importieren (z.B. Marc und Lara gleichzeitig), serialisiert SQLite. Bei 5 Usern kein Problem. Bei 50+ gleichzeitig: Timeouts. Das Team muss das als bewusste MVP-Grenze kennen, nicht als Überraschung.

Moduldisziplin ist das fragile Glied:
ADR-4 fordert: kein direkter Zugriff auf Repositories anderer Module. Mit 3 Devs unter Zeitdruck ist das die Regel, die als erstes bricht. Ein @Autowired TransactionRepository in budget/ passiert schnell. Das CLAUDE.md hat das als Convention notiert — aber ohne automatischen Guard (ArchUnit-Test o.ä.) ist es nur ein Commitment.

nDSG-Compliance-Gap (ADR-6) als Deployment-Blocker:
ADR-6 akzeptiert explizit den PII-Transfer an Anthropic ohne DPA für das MVP. Das ist korrekt dokumentiert. Aber wenn das Projekt live geht (auch als Demo mit echten Daten), muss dieser Gap geschlossen sein — sonst ist das kein Architektur-Problem, sondern ein rechtliches.

5. Team-Fit Bewertung (3 Devs, 3 Monate)
Entscheidung	Fit	Begründung
Monolith	✅ Sehr gut	Ein Deploy, kein Ops-Overhead
SQLite	✅ Sehr gut	Kein DB-Server nötig
Synchroner Import	✅ Gut	Einfacher zu debuggen als Async
Angular Signals, kein NgRx	✅ Gut	Angemessene Komplexität
Flyway Migrationen	⚠️ Disziplin nötig	Alle 3 Devs müssen schema changes über Flyway laufen lassen — kein Schema-Edit direkt in DB
Modulgrenzen	⚠️ Risiko	Ohne ArchUnit-Tests oder striktes Review bricht das unter Zeitdruck
Zusammenfassung
3 Punkte zum Fixen:

CLAUDE.md: "JWT in Header" → "JWT als httpOnly Cookie" in ADR-0 Zeile
C2 Diagram: GET /import/{jobId}/status als (Phase 2) kennzeichnen oder entfernen
CLAUDE.md / C2: SQLite 1-Writer-Limit explizit sichtbar machen
Architektur-Urteil: Die Entscheidungen sind für euer Team, eure Timeline und euren MVP-Scope kohärent und begründet. Der einzige echte Risikofaktor ist Moduldisziplin — wer das von Anfang an im Code-Review aktiv durchsetzt, hat einen soliden Modular Monolith. Wer es nicht tut, hat in 6 Wochen einen Big Ball of Mud.