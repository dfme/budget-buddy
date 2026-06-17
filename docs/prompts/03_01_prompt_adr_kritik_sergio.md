# Kritische Analyse der Architecture Decision Records (ADRs)

**Datum:** 2026-05-27  
**Reviewer:** Claude Code  
**Scope:** Alle 10 ADRs (ADR-0 bis ADR-9)

---

## Zusammenfassung: Top 3 Architektur-Risiken

| Rang | Risiko | Impact | Wahrscheinlichkeit |
|------|--------|--------|-------------------|
| 🔴 **#1** | **XSS → JWT Theft** (ADR-7) | Vollkompromittierung aller Finanzdaten | Mittel (ein Bug in ng2-charts reicht) |
| 🔴 **#2** | **Thread Pool Blocking + PDF Latency** (ADR-4, ADR-6) | Import-Feature wird unbenutzbar (60+ Sekunden) | Hoch (tritt bei jedem größeren PDF auf) |
| 🔴 **#3** | **SQLite Concurrency + Corruption** (ADR-5) | Datenverlust, Service-Ausfälle | Mittel-Hoch (bei 100+ Usern oder Crash) |

---

## Detaillierte Kritik pro ADR

### ADR-0: Frontend-Backend-Trennung (SPA + REST)

#### ⚠️ Nicht bedacht:
- **CSRF Protection:** Bei SPA + REST sind CSRF-Tokens nötig, besonders wenn später Cookies hinzukommen
- **Fehlerbehandlung:** Wenn Backend down ist, was zeigt der User? Blackscreen oder Offline UI?
- **SEO:** Dashboard-Daten sind für SEO relevant (finanzielle Aggregationen), aber SPA indexiert nicht gut
- **Progressive Enhancement:** Falls JavaScript fehlschlägt, ist die App completely broken

#### ❌ Fehlende Alternative:
- **Hybrid Rendering (Remix, Astro):** Schnellere Initial Loads als SPA ohne SSR-Komplexität

#### 🔴 Größtes Risiko:
**Waterfall-Rendering:** Browser lädt JS → parst Bundle → erst dann API-Calls. Bei langsamen Netzwerken (Studierende mit 4G) könnte Seite 5-10 Sekunden leer sein.

---

### ADR-1: Java 25 + Spring Boot 3.5.x

#### ⚠️ Nicht bedacht:
- **Java 25 ist NICHT LTS** — LTS-Releases sind 17, 21. Java 25 (Sept 2024) hat nur ~6 Monate Support. Sollte **Java 21 LTS** sein
- **Dependency Supply Chain:** Keine Strategie für Security Patches (jährlich >100 CVEs in Spring Ecosystem)
- **Spring Boot 4 Migration:** Roadmap nicht beachtet — könnte disruptiv sein
- **Actuator/Monitoring:** Für Produktion (Uptime, Memory, Requests) nicht erwähnt

#### ❌ Fehlende Alternativen:
- **Quarkus:** 10x schnellere Startzeit + niedrigerer Memory (für Serverless später relevant)

#### 🔴 Größtes Risiko:
**Versioning Lock:** Ein Kurs-Projekt mit fest versioniertem Spring Boot 3.5.3 wird in 18 Monaten EOL sein. Updates werden teuer.

**Empfehlung:** Java 21 LTS verwenden statt 25

---

### ADR-2: Angular 19.x

#### ⚠️ Nicht bedacht:
- **Angular 19 Support:** Nur ~6 Monate, dann eol. Sollte auf Long-term Support warten (Angular 18 LTS verfügbar)
- **Accessibility (A11Y):** Nicht erwähnt — finanzielle App sollte WCAG 2.1 AA sein
- **Offline-Fähigkeit:** PWA nicht erwähnt (aber Student mit unstabiler Verbindung könnte PWA nutzen)
- **Performance Budgets:** Keine Metriken (LCP, FID, CLS) definiert

#### ❌ Fehlende Alternative:
- **Solid.js / Qwik:** Schneller bei interaktiven Dashboards

#### 🔴 Größtes Risiko:
**Signal Forms noch experimental:** Angular 19 Signals sind neu, Signal Forms noch nicht stabil. Fallback auf RxJS bedeutet mehr Boilerplate.

---

### ADR-3: REST API statt GraphQL

#### ⚠️ Nicht bedacht:
- **Pagination:** GET `/transactions` → 10.000+ Rows? Keine Strategie erwähnt (könnte Memory-Crash am Frontend)
- **Rate Limiting:** Für Finanz-App essentiell (API-Abuse könnte zu DoS führen)
- **Caching Headers:** ETag/Cache-Control nicht erwähnt (könnte Mobile-Datenverbrauch reduzieren)
- **API Versioning Gotchas:** `/api/v1/` ist gut, aber wie werden v1-Clients depreciert?

#### ❌ Fehlende Alternative:
- **JSON:API Standard:** Bessere Patterns für Filtering/Sorting/Pagination

#### 🔴 Größtes Risiko:
**Breaking Changes:** Wenn Backend eine Feld-Struktur ändert, brechen alle Clients. Keine Deprecation-Strategie.

**Empfehlung:** Pagination mit Cursor oder Offset implementieren; Rate Limiting (z.B. 100 req/min) definieren

---

### ADR-4: Monolith statt Microservices

#### ⚠️ Nicht bedacht:
- **Thread Pool Exhaustion:** PDF-Parsing (30s) + Claude LLM Calls (300-500ms) blockieren Worker-Threads
  - HikariCP Default: 10 Connections
  - Wenn 5 Users parallel PDFs uploaden = alle Threads busy = neue Requests queued
- **LLM Rate Limiting:** Claude API hat ~60 requests/minute. Bei 100 Users × 1000 tx/Monat könnte Rate Limit getroffen werden
- **Long-Running Operations:** Keine async/messaging erwähnt (Quartz, Spring Events, Message Queue)

#### Konsequenzen **NICHT realistisch:**
- "Horizontal Scaling mit Read Replicas" funktioniert NICHT mit SQLite (file-based, kann nicht über Server distribuiert werden)
- Migration Path zu Microservices ist vage

#### 🔴 Größtes Risiko:
**Blocking Bottleneck:** Ein Benutzer mit großem PDF blockiert den ganzen Server für 30 Sekunden. Weitere Requests müssen warten.

**Empfehlung:** Async Job Queue für PDF + LLM Calls implementieren (Spring @Async oder Quartz Scheduler)

---

### ADR-5: SQLite als MVP-Datenbank

#### ⚠️ Nicht bedacht:
- **WAL Mode nicht erwähnt:** Mit `PRAGMA journal_mode=WAL`, SQLite kann gleichzeitige Reads + Writes unterstützen
- **Encryption nicht erwähnt:** `sqlcipher` wäre sinnvoll (sensible Finanzdaten am Disk)
- **Backup Strategy:** Single-file Backup ist nicht sicher (DB könnte mid-backup korrupt werden)
- **Integrity Constraints:** Foreign Keys müssen `PRAGMA foreign_keys=ON` sein (default off in SQLite!)

#### Konsequenzen **NICHT vollständig:**
- "50 Schreibzugriffe/s max" ist optimistisch (Reads blockieren Writers ohne WAL!)
- Migration zu PostgreSQL: Daten sind nicht einfach migrierbar wenn Constraints fehlschlagen

#### 🔴 Größtes Risiko:
**Datenverlust + Corruption:** SQLite-Datei könnte bei ungraceful Shutdown korrupt werden. Kein Redundancy/Replica.

**Empfehlung:** 
- WAL Mode enablen: `PRAGMA journal_mode=WAL`
- Foreign Keys enablen: `PRAGMA foreign_keys=ON`
- Backup-Strategie definieren (daily snapshots)

---

### ADR-6: Hybrid-Kategorisierung (Lookup + Claude API)

#### ⚠️ Nicht bedacht:
- **Latency-Problem:** 300 unbekannte Transaktionen × 200-300ms pro API Call = **90-150 Sekunden Wartezeit** für User!
- **Concurrent Requests:** Claude API hat Rate Limits (~60 req/min), bei 100 Users könnte Bottleneck entstehen
- **Prompt Injection:** User könnte böse Transaktion-Namen senden (z.B. `"; DROP TABLE users;`)
- **Accuracy Reality:** 85-90% = auf 1000 tx sind 100-150 falsch. Das ist **nicht gut genug** ohne Review-UI

#### Konsequenzen **NICHT realistisch:**
- Cost: Nicht alle 20-30% sind unbekannt. Bei 100 Users könnte es schnell >$1000/Monat werden
- Learning Loop: "User-Korrektionen → Lookup-Eintrag" ist nicht implementiert (wo wird Lookup gespeichert? Pro-User Overrides?)

#### 🔴 Größtes Risiko:
**Import Latency = Churn:** 60+ Sekunden Wartezeit bei größeren PDFs = Nutzer bricht ab (Churn-Falle aus CLAUDE.md!)

**Empfehlung:**
- Async Job Queue für LLM Calls (nicht blocking)
- Batch-Requests to Claude API statt einzeln
- Prompt Injection Prevention (sanitize input)
- User-Feedback-UI zeigen während Kategorisierung läuft

---

### ADR-7: JWT (Stateless) Authentication mit HS256

#### ⚠️ Nicht bedacht:
- **Token Refresh:** 1-Stunde Expiry = User wird alle Stunde ausgeloggt → schlechte UX
  - Sollte Refresh Token Pattern sein (kurzes Access Token + langes Refresh Token)
- **XSS ist KRITISCH:** localStorage kann von JavaScript kompromittiert werden
  - `localStorage.getItem('jwt')` in Browser-Console sichtbar für jeden Hacker in Café
  - Sollte **sofort** httpOnly Cookies sein (nicht Phase 2)
- **Token Revocation:** Kein Weg, gehacktes Token zu invalidieren (Blacklist in Phase 2?)
- **Audit Logging:** Keine Login/Logout Logs erwähnt (regulatorisch für Finanz-Apps wichtig)

#### Konsequenzen **FALSCH:**
- "Short Expiry + Client-seitiges Löschen = sofort logged out" ist NICHT sicher
  - Client löscht localStorage, aber Backend akzeptiert Token noch 1 Stunde
  - Jemand mit gehacktem Token kann es verwenden

#### 🔴 Größtes Risiko:
**XSS → Vollkompromittierung:** Ein XSS-Bug (z.B. in Chart.js) = Hacker stiehlt JWT = volle Kontrolle über Finanzdaten aller User!

**Empfehlung:**
- httpOnly Cookies JETZT (nicht Phase 2)
- Refresh Token Pattern implementieren (short-lived Access Token, long-lived Refresh Token)
- Token Blacklist/Revocation implementieren
- Audit Logging für alle Auth-Events

---

### ADR-8: Apache PDFBox 3.x für PDF-Verarbeitung

#### ⚠️ Nicht bedacht:
- **OCR nicht möglich:** Wenn Bank gescannte PDF sendet = Parsing-Fehler → Manual Entry nötig
- **Memory:** PDFBox lädt ganzes PDF in RAM. 50-Seiten × mehrere User gleichzeitig = OOM?
- **Regex Fragility:** Schweizer Banks ändern PDF-Layout alle 2-3 Jahre → Regex bricht
- **PDF Corruption:** Keine Fehlerbehandlung wenn PDF malformed

#### Konsequenzen **optimistisch:**
- <5 Sekunden ist gut, aber hängt von PDF-Struktur ab
- Keine Fallback bei Parsing-Fehler erwähnt (sollte "Manual entry required" zeigen)

#### 🔴 Größtes Risiko:
**Format Brittleness:** UBS/Raiffeisen ändern PDF-Layout → alle Imports brechen bis Regex updated wird.

**Empfehlung:**
- Regex Patterns in Konfigurationsdatei (nicht hardcoded)
- Fallback-UI für fehlgeschlagene Parsing
- Memory Limits für PDF Processing (max 50 MB)

---

### ADR-9: BigDecimal für Geldbeträge

#### ⚠️ Nicht bedacht:
- **JSON Serialization:** Wie wird `new BigDecimal("100.50")` zu JSON? Als String `"100.50"` oder Float `100.5`? (Precision Loss!)
- **Keine Utility Methods:** `amount.add(new BigDecimal("10"))` ist verbose, aber keine Lösung erwähnt
- **Developer Error:** Wer nutzt `double` statt `BigDecimal`? Keine IDE-Warning, Runtime-Bug erst in Produktion sichtbar
- **DECIMAL(10,2) Limit:** Max 99.999.999,99 CHF (100 Millionen) — ok, aber nicht erwähnt

#### Konsequenzen **NICHT realistisch:**
- Verbosity ist erwähnt, aber keine Lösung (Kotlin Extension Functions? Utility Klasse?)

#### 🔴 Größtes Risiko:
**Serialization Bug:** Frontend erwartet `100.50`, Backend sendet `100.5` (JSON Float) → UI zeigt `100.5` statt `100.50` CHF.

**Empfehlung:**
- Jackson Custom Serializer für BigDecimal (force String in JSON)
- Money Utility Klasse bauen für häufige Operationen
- Code Review Checklist: "BigDecimal statt double?"

---

## ✅ Sofortmaßnahmen (nicht warten)

| Priorität | ADR | Aktion | Timeline |
|-----------|-----|--------|----------|
| 🔴 KRITISCH | ADR-7 | httpOnly Cookies implementieren statt localStorage | Vor Login-Feature |
| 🔴 KRITISCH | ADR-5 | WAL Mode + Foreign Keys enablen in SQLite | Vor Datenbank-Setup |
| 🔴 KRITISCH | ADR-4 | Async Job Queue für PDF + LLM Calls | Vor PDF-Import-Feature |
| 🟠 HOCH | ADR-1 | Java 21 LTS statt 25 verwenden | Projekt-Setup |
| 🟠 HOCH | ADR-9 | Jackson BigDecimal Serializer konfigurieren | Before API Documentation |
| 🟠 HOCH | ADR-6 | Batch-Requests + Prompt Injection Prevention | Vor Kategorisierung |
| 🟡 MITTEL | ADR-3 | Pagination + Rate Limiting definieren | REST API Design |
| 🟡 MITTEL | ADR-5 | Backup Strategy dokumentieren | Deployment Planning |

---

## Allgemeine Beobachtungen

### Was gut ist:
✅ Technologie-Auswahl ist für MVP sinnvoll (Spring Boot, Angular, REST)  
✅ ADRs sind strukturiert und nachvollziehbar geschrieben  
✅ Alternative werden erwogen (auch wenn teilweise zu schnell abgelehnt)  
✅ Konsequenzen sind teils realistisch (SQLite Skalierungsgrenzen erkannt)

### Was verbessert werden sollte:
❌ Security-Risiken (XSS, Encryption) werden bagatellisiert ("Phase 2")  
❌ Performance-Bottlenecks (Thread Pool, PDF Latency) nicht adressiert  
❌ Versionierung nicht nachhaltig (Java 25, Angular 19 sind keine LTS)  
❌ Operational Details (Backup, Monitoring, Logging) fehlen  
❌ Cost-Estimationen sind rough (LLM Calls können überraschend teuer werden)

---

## Fazit

Die ADRs zeigen solide technische Grundlagen, **aber 3 kritische Lücken** müssen vor Implementierung geschlossen werden:

1. **Security:** XSS + JWT in localStorage ist unakzeptabel für Finanzen → httpOnly Cookies
2. **Performance:** Thread Pool Blocking durch PDF/LLM + Latency-Churn → Async Job Queue
3. **Database:** SQLite ohne WAL/FK/Backup ist riskant → WAL enablen + Backup-Plan

Mit diesen Fixes: ✅ Architektur ist solid für MVP.
