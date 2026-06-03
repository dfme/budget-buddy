# ADR-4: Monolith statt Microservices

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy Backend muss folgende Funktionen bereitstellen:

- User Authentication & Authorization
- PDF-Verarbeitung (Bank Statement Parsing)
- Transaktions-Kategorisierung (Lookup + LLM)
- Dashboard & Berechnungen (Safe-to-Spend, Summierungen)
- KI-Reports (Claude API)

**Architektur-Optionen:** Monolith, Microservices (K8s), Serverless, Hybrid

## Decision

Wir bauen einen **Modular Monolith (Spring Boot JAR)** — ein einzelnes Deployment, aber mit expliziten Modulgrenzen nach Domänen:

```
com.budgetbuddy
  ├── auth/           (AuthController, AuthService, User-Entity, JWT-Config)
  ├── transaction/    (TransactionController, PdfImportService, Transaction-Entity)
  ├── categorization/ (CategorizationService, LookupTable, CategorizationPort-Interface)
  ├── budget/         (BudgetController, SafeToSpendService, SavingsGoalService)
  └── report/         (ReportController, AiReportService)
```

**Regel:** Kein direkter Zugriff auf Repositories oder Services eines anderen Moduls. Cross-Modul-Kommunikation nur über definierte Interfaces (z.B. `CategorizationPort`).

**Async Import Flow:** PDF-Uploads laufen asynchron. Der Endpoint gibt sofort eine Job-ID zurück; der eigentliche Import (PDFBox + Claude API Calls) läuft im Hintergrund via Spring `@Async`. Status-Polling über `GET /import/{jobId}/status`.

- **Database:** Shared SQLite (`budget-buddy.db`), WAL-Modus, HikariCP max 1 Writer
- **Deployment:** Single JAR → Docker → Cloud Run / VPS
- **Scaling:** Vertikal initial; später Horizontal mit Read Replicas

## Consequences

### Positive

- **Development Speed:** Eine Codebase, ein Deployment, ein Database-Schema
- **Simple Transactions:** ACID-Transaktionen über Schichten (Auth → PDF → Kategorization → DB)
- **Testing:** Integration Tests sind einfach (Spring TestContainers + SQLite)
- **Debugging:** Single Process → Stack Traces enthalten ganzen Call Stack
- **Deployment:** `java -jar app.jar` — fertig
- **Team:** 2-3 Entwickler können alles deployen (kein separates Ops-Team)

### Negative

- **Horizontal Scaling:** Später schwieriger (braucht Load Balancer + Read Replicas)
- **Deployment Risk:** Bug in Feature X = ganze App offline (mitigiert durch Tests)
- **Large Dependency Graphs:** Größere Spring Boot Boot-Zeit
- **Database Bottleneck:** Shared SQLite ist Single Point of Failure (ok für MVP)
- **Moduldisziplin:** Ohne konsequente Durchsetzung der Modulgrenzen entsteht ein Big Ball of Mud — muss im Code-Review aktiv geprüft werden

## Alternatives

### Microservices (Kubernetes)

**Rejected.** Technisch sauber, aber:
- **Infrastructure Overhead:** K8s Cluster, Service Mesh, Logging, Distributed Tracing
- **Operational Complexity:** Braucht 2-3 DevOps Engineers
- **Overkill für BudgetBuddy:** Nur ~5 logische Services, alles könnte in einer JAR laufen
- **Data Consistency:** Transaktionen über Service-Grenzen sind kompliziert (Saga Pattern)
- **Team Size:** 2-3 Entwickler + 2-3 DevOps = zu großes Team für Kurs-Projekt

**Future Option:** Wenn >10.000 User und mehrere Teams = Microservices sinnvoll

### Serverless (Lambda / Cloud Run)

**Rejected.** Managed, aber:
- **Cold Starts:** Java JVM braucht ~3 Sekunden (schlecht für Login UX)
- **Cost:** Pay-per-invocation teuer bei häufigen Requests
- **Timeout Limits:** Lambda max 15 min (PDF-Verarbeitung ok)
- **Database:** SQLite nicht für Concurrent Serverless Invocations geeignet

**Future Option:** Könnte für Async Jobs (Report Generation per Cron) sinnvoll sein

### Hybrid (Monolith + Async Workers)

**Accepted as Future Optimization:**
- Phase 1: Monolith (alles in einer JAR)
- Phase 2: + Async Workers (Quartz/Kafka für Long-Running Tasks)
- Phase 3: Microservices (nur wenn nötig)

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung
- **ADR-1:** Java + Spring Boot
- **ADR-5:** SQLite (Shared Database)
- **ADR-3:** REST API (Monolith-freundlich)
