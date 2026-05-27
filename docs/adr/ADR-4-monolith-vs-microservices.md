# ADR-4: Monolith vs. Microservices Architektur

**Status:** Accepted (Monolith)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Backend-Deployment-Strategie

---

## Context

BudgetBuddy Backend braucht eine Architektur zur Organisation von:

- User Authentication & Authorization
- PDF-Verarbeitung (Kontoauszug-Parsing)
- Transaktionen-Kategorisierung (LLM + Lookup-Tabelle)
- Dashboard & Berechnungen (Safe-to-Spend, Kategorien-Summierung)
- KI-Reports (Claude API)
- Reporting & Analytics

### Optionen

1. **Monolith** — Alles in einer Spring Boot JAR; single database
2. **Microservices** — Services für PDF, Kategorization, Reports, Auth; separate deployments
3. **Serverless** — Lambda/Cloud Functions für einzelne Operationen
4. **Hybrid** — Monolith mit async Jobs (e.g., Kafka für Report-Generation)

---

## Decision

**Monolith (Single Spring Boot JAR)**

```
budget-buddy-backend.jar
├── api.controllers
│   ├── AuthController
│   ├── TransactionController
│   ├── UserController
│   ├── ReportController
│   └── ...
├── service
│   ├── AuthService
│   ├── PdfImportService
│   ├── CategorizationService
│   ├── ReportService
│   └── ...
├── repository (Spring Data JPA)
├── model (JPA Entities)
└── config (Security, DB, API Docs)
```

- **Database:** Shared SQLite (single file: `budget-buddy.db`)
- **Deployment:** Single JAR → Docker → Cloud Run / App Engine / VPS
- **Scaling:** Vertical (RAM/CPU) initially; horizontal later with read replicas

---

## Rationale

| Kriterium | Monolith | Microservices | Serverless | Hybrid |
|-----------|----------|---------------|-----------|--------|
| **Simplicity** | ✅✅ Eine Codebase | ❌ Komplexes Setup | ⚠️ Fragmented | ⚠️ Hybrid-Komplexität |
| **Development Speed** | ✅✅ Schnell | ❌ Viel Boilerplate | ⚠️ Slow (cold starts) | ⚠️ Mittelmäßig |
| **Deployment** | ✅ Einfach | ❌ Orchestration (K8s) | ✅ Platform-managed | ⚠️ Docker + Message Queue |
| **Testing** | ✅✅ Integration Tests einfach | ⚠️ Contract Testing nötig | ⚠️ Nur Unit-Tests | ⚠️ Komplex |
| **Data Consistency** | ✅✅ ACID Transactions | ❌ Saga Pattern, eventually consistent | ⚠️ Lambda Limits | ⚠️ Mittelmäßig |
| **Debugging** | ✅✅ Single process | ❌ Distributed Tracing nötig | ❌ Logs verteilt | ⚠️ Mittelmäßig |
| **Database** | ✅ Shared Schema | ❌ Eigene DB pro Service | ❌ Jede Lambda braucht Zugriff | ⚠️ Komplexer |
| **Scalability** | ⚠️ Vertikal leicht, Horizontal später | ✅ Horizontal pro Service | ✅ Auto-scaling | ⚠️ Uneben |
| **DevOps Overhead** | ✅ Minimal | ❌ Hoch (K8s, Logging, Monitoring) | ✅ Minimal | ⚠️ Hoch |
| **Team Size** | ✅ Gut für <10 Entwickler | ❌ Besser für >20 Entwickler | ⚠️ Immer zu teuer | ⚠️ Mittelmäßig |

**Konkrete Vorteile für BudgetBuddy:**

1. **MVP-Speed:** Eine Codebase, ein Deployment, ein Database-Schema
2. **Einfache Transaktionen:** User erstellt Fixkosten → Safe-to-Spend neuberechnet (single DB-transaction)
3. **Shared State:** Alle Features greifen auf gleiche Tabellen zu (keine Service-Orchestrierung nötig)
4. **Testing:** Integration Tests sind einfach (Spring TestContainers + SQLite)
5. **Deployment:** Single JAR → Docker → Cloud Run / VPS
6. **Team:** 2–3 Entwickler können alles deployen (nicht separates Ops-Team nötig)

**Wann ist die Grenzenergie bei einem Monolith erreicht?**

- **User Base:** >10.000 gleichzeitige Nutzer
- **Traffic:** >1.000 req/s
- **Team:** >10 Entwickler (sonst Merge-Konflikte)
- **Features:** > 50 Microfeatures (schwieriges Deployment von Features)

→ BudgetBuddy hat keine dieser Anforderungen; MVP wird nie diesen Punkt erreichen

---

## Consequences

### ✅ Positive

- **Entwicklung:** Schnell; Single Codebase; einfaches Refactoring
- **Testing:** Integration Tests mit echten Datenbankabfragen; keine Mocks nötig
- **Debugging:** Stack Traces enthalten gesamten Call Stack; einfach zu debuggen
- **Transaktionen:** ACID-Transaktionen über Schicht hinweg (Auth → PDF → Kategorization → DB)
- **Deployment:** `java -jar app.jar` — fertig
- **Monitoring:** Single Application → einfach, ein Dashboard genug

### ⚠️ Negative

- **Scaling:** Horizontal Scaling erfordert Load Balancer + Read Replicas (später)
- **Abhängigkeitsauflösung:** Große Abhängigkeitsgraphen (Spring Boot Boot-Zeit)
- **Deployment-Risiko:** Bug in Feature X = gesamte Applikation unten (aber: mit guten Tests mitigiert)
- **Language/Tech Lock-in:** Wenn später Python-Service nötig (z.B. ML-Modell) ist schwieriger
- **Database Bottleneck:** Shared SQLite = single point of failure (aber MVP ist ok damit)

### 🔄 Mitigations

- **Horizontal Scaling:** Später zu PostgreSQL + read replicas
- **Modularisierung:** Klare Package Structure (api, service, repository, model)
- **Testing:** Integration Tests + E2E Tests sichern ab
- **Monitoring:** Spring Boot Actuator + Prometheus/Grafana später

---

## Alternatives Considered

### ❌ Option 1: Microservices (K8s)

**Entscheidung:** Abgelehnt (für MVP)

**Begründung:**

- **Infrastruktur-Overhead:**
  ```
  - Kubernetes Cluster (mindestens 3 Nodes)
  - Service Mesh (Istio) für inter-service communication
  - Logging (ELK Stack) für zentrale Log-Aggregation
  - Distributed Tracing (Jaeger) für Debugging
  - Message Queue (Kafka) für async communication
  ```
  → Minimum 2-3 DevOps Engineers, um dies zu betreiben

- **Komplexität:** BudgetBuddy hat nur ~5 logische Services:
  - PDF Import Service
  - Categorization Service
  - Report Service
  - Dashboard Service
  - Auth Service

  Jede als separate Deployment? Overkill. Eine JAR kann alles.

- **Data Consistency:** Transaktionen über Service-Grenzen (z.B. User erstellt Fixkost → Safe-to-Spend neuberechnet) erfordert:
  - Saga Pattern (kompliziert)
  - oder monolithische DB (dann nicht wirklich Microservices)

- **Testing:** Integration Tests werden schwieriger (mussten Container orchestrieren)

- **Team:** 2–3 Entwickler + 2–3 DevOps = Team zu groß für Kurs-Projekt

**Aber:** Wenn später BudgetBuddy >10.000 User hat + mehrere Teams (Mobile, Web, Data) = Microservices sinnvoll

### ⚠️ Option 2: Serverless (Google Cloud Run / AWS Lambda)

**Entscheidung:** Abgelehnt

**Begründung:**
- **Cold Starts:** Lambda hat ~3s Startup Zeit (Java + JVM)
  - User wartet 3s auf Login-Response → schlecht UX
- **Cost:** Pay-per-invocation ist bei häufigen Requests teuer
- **PDF Processing:** Timeout limits (Lambda = 15 min max, Cloud Run = 3600 min)
- **Database:** SQLite nicht geeignet für concurrent Serverless Invocations
  - Würde zu PostgreSQL müssen (mehr Infrastruktur)
- **Debugging:** Distributed Logs, schwer zu debuggen

**Aber:** Könnte für spezifische async Jobs sinnvoll sein (z.B. Report-Generation per Cron)

### ⚠️ Option 3: Hybrid (Monolith + Async Jobs)

**Entscheidung:** Akzeptiert (als zukünftige Optimierung)

**Begründung:**
- **Jetzt:** Alles im Monolith (schnell)
- **Später:** Long-running Tasks (KI-Reports, Bulk-Kategorisierung) → async Jobs (Quartz/Kafka)
  - Backend erzeugt Job-Message
  - Separate Worker-Service (oder Lambda) verarbeitet asynchron
  - User erhält Notification wenn fertig

Dies ist ein natürlicher Evolutionspfad:
```
Monolith (Phase 1)
  ↓
Monolith + Async Workers (Phase 2)
  ↓
Microservices (Phase 3 — nur wenn nötig)
```

---

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (Monolith = Backend-Teil)
- **ADR-1:** Java + Spring Boot (Monolith-friendly)
- **ADR-5:** SQLite (Shared Database, kein Service-Isolation)

---

## Future Evolution

**Wenn in 12 Monaten User > 5.000:**
1. Measure Performance (Prometheus metrics)
2. Identify Bottleneck (PDF service? Categorization? Reports?)
3. Extract as Microservice (if needed)
4. Migrate Database (SQLite → PostgreSQL + read replicas)

**Warnsignale für Microservices:**
- ❌ Zwei Teams kämpfen um Code-Ownership
- ❌ PDF-Parser häufig deployed, andere Services in Production kaputt
- ❌ Categorization API braucht eigene Skalierung
- ❌ Report-Generation blockiert andere API-Requests

---

## References

- [Microservices.io — When to Use Microservices](https://microservices.io/articles/when-should-you-use-microservices.html)
- [Martin Fowler — Monolith First](https://martinfowler.com/bliki/MonolithFirst.html)
- [Kubernetes Complexity — Too Much For Small Teams](https://kwm.dev/blog/our-kubernetes-stack-is-overkill-for-our-team/)
- CLAUDE.md — Architecture Notes
