# Architecture Decision Records (ADRs)

Diese Dokumentation hält die **wichtigsten Architektur-Entscheidungen** für BudgetBuddy fest. Jedes ADR erklärt:

- **Context:** Welches Problem musste gelöst werden?
- **Decision:** Welche Lösung wurde gewählt?
- **Rationale:** Warum diese Lösung?
- **Consequences:** Welche Trade-offs ergeben sich?
- **Alternatives:** Welche anderen Optionen wurden in Betracht gezogen?

---

## ADR Index

| # | Titel | Status | Auswirkung |
|---|-------|--------|-----------|
| **ADR-0** | [Frontend-Backend-Trennung (SPA + REST API)](ADR-0-frontend-backend-separation.md) | ✅ Accepted | Gesamte Systemarchitektur |
| **ADR-1** | [Java 21 + Spring Boot 3.5.x Backend](ADR-1-java-spring-boot-backend.md) | ✅ Accepted | Backend Tech Stack |
| **ADR-2** | [Angular 19.x Frontend](ADR-2-angular-frontend.md) | ✅ Accepted | Frontend Tech Stack |
| **ADR-3** | [REST API vs. GraphQL](ADR-3-rest-vs-graphql.md) | ✅ Accepted | API-Design |
| **ADR-4** | [Monolith vs. Microservices](ADR-4-monolith-vs-microservices.md) | ✅ Accepted | Backend-Deployment |
| **ADR-5** | [SQLite für MVP-Datenbank](ADR-5-sqlite-mvp-database.md) | ✅ Accepted | Datenspeicherung |
| **ADR-6** | [Hybrid-Kategorisierung (Lookup + Claude API)](ADR-6-hybrid-categorization.md) | ✅ Accepted | Transaction Categorization |
| **ADR-7** | [JWT (Stateless) Authentication](ADR-7-jwt-authentication.md) | ✅ Accepted | User Auth & Session Mgmt |
| **ADR-8** | [Apache PDFBox 3.x für PDF-Verarbeitung](ADR-8-apache-pdfbox.md) | ✅ Accepted | PDF-Import |
| **ADR-9** | [BigDecimal für Geldbeträge](ADR-9-bigdecimal-money.md) | ✅ Accepted | Money Calculations |

---

## Übersicht nach Kategorie

### Fundamentale Architektur-Entscheidungen
- **ADR-0:** Frontend-Backend-Trennung → SPA + REST API
- **ADR-4:** Monolith statt Microservices (für MVP)

### Technology Stack
- **ADR-1:** Java 21 + Spring Boot (Backend)
- **ADR-2:** Angular 19 (Frontend)
- **ADR-5:** SQLite (Datenbank)
- **ADR-7:** JWT (Authentication)
- **ADR-8:** Apache PDFBox (PDF-Verarbeitung)
- **ADR-9:** BigDecimal (Geldbeträge)

### Feature-Spezifische Entscheidungen
- **ADR-3:** REST API (statt GraphQL)
- **ADR-6:** Hybrid Kategorisierung (Lookup + Claude API)

---

## Entscheidungs-Prinzipien

### MVP-First
Alle Entscheidungen optimieren für schnelle MVP-Auslieferung, nicht für infinite Skalierbarkeit:
- Monolith statt Microservices
- SQLite statt PostgreSQL (Migration Path vorhanden)
- JWT statt Session-Store (keine extra Infrastruktur)

### Pragmatische Tech-Wahl
Gewählte Technologien sind:
- **Reif & bewährt** (nicht experimental)
- **Gut dokumentiert** (Team kann lernen)
- **Open Source freundlich** (keine Licensing-Probleme)
- **Team-familiar** (Studierende kennen Java/Angular)

### Fallback-Sicherheit
Kritische Systeme haben Fallbacks:
- PDF-Import mit Fehlerbehandlung (nie die Flow blockieren)
- Claude API mit Fallback zu "Sonstiges" (wenn API down)
- User-Korrektionen trainieren das System (Feeedback-Loop)

### Zukünftige Skalierbarkeit
Architektur ermöglicht Upgrades **ohne Rewrite**:
- SQLite → PostgreSQL (1-2 Sprints)
- Monolith → Async Workers (bei Report-Generation)
- JWT → Refresh Token Rotation (bei Logout-Anforderung)
- Lookup-Only → ML-Klassifier (bei mehr Daten)

---

## Verwendung dieser ADRs

### Für neue Entwickler
1. Lese **ADR-0** für System-Überblick
2. Lese **ADR-1, ADR-2** für Tech-Stack
3. Lese feature-spezifische ADR wenn du diese Feature arbeitest

### Für Code-Reviews
- Verlinkung zu relevantem ADR in PR-Comments
- Beispiel: "Diese Änderung trifft ADR-9 (BigDecimal). Bitte sicherstellen, dass alle Geldbeträge BigDecimal verwenden."

### Für Future Refactorings
- **Migration zu PostgreSQL?** → Siehe ADR-5 Migration Path
- **Adding Microservices?** → Siehe ADR-4 Warnsignale
- **Instant Logout?** → Siehe ADR-7 Consequences

### Für neue Entscheidungen
- Folge dem ADR-Template (Context → Decision → Rationale → Consequences)
- Nummeriere in Reihenfolge (ADR-10, ADR-11, ...)
- Verlinke auf verwandte ADRs

---

## Zusammenhänge (Graph)

```
ADR-0: Frontend-Backend-Trennung
├── ADR-1: Java + Spring Boot (Backend-Implementierung)
│   ├── ADR-5: SQLite (Datenbank)
│   ├── ADR-7: JWT (Authentication)
│   ├── ADR-8: Apache PDFBox (PDF)
│   └── ADR-9: BigDecimal (Money)
├── ADR-2: Angular 19 (Frontend-Implementierung)
│   └── ADR-7: JWT (Token Management)
├── ADR-3: REST API (Frontend ↔ Backend Kommunikation)
│   └── ADR-1: Spring Boot REST-Framework
└── ADR-4: Monolith (Deployment-Architektur)
    ├── ADR-1: Single Spring Boot JAR
    ├── ADR-5: Shared SQLite Database
    └── ADR-6: Hybrid Kategorisierung (in Monolith)
```

---

## ADR-Template (für zukünftige Entscheidungen)

```markdown
# ADR-X: [Entscheidungs-Titel]

**Status:** Proposed / Accepted / Deprecated  
**Entscheidung vom:** YYYY-MM-DD  
**Betroffen:** [Komponenten/Systeme]

---

## Context
[Problem-Beschreibung, Anforderungen, Constraints]

### Optionen
1. **Option A** — [Beschreibung]
2. **Option B** — [Beschreibung]
3. **Option C** — [Beschreibung]

---

## Decision
[Gewählte Lösung]

---

## Rationale
[Warum diese Lösung besser ist als Alternativen]

| Kriterium | Option A | Option B | Option C |
|-----------|----------|----------|----------|
| ... | ... | ... | ... |

---

## Consequences

### ✅ Positive
- [Vorteil 1]
- [Vorteil 2]

### ⚠️ Negative
- [Nachteil 1]
- [Nachteil 2]

### 🔄 Mitigations
| Problem | Mitigation |
|---------|-----------|
| [Problem 1] | [Lösung 1] |

---

## Alternatives Considered
### ❌ Option A
[Warum abgelehnt]

### ⚠️ Option B
[Warum abgelehnt oder später relevant]

---

## Related Decisions
- **ADR-X:** [Related ADR Title]

---

## References
- [Link 1]
- [Link 2]
```

---

## Status-Legende

- ✅ **Accepted** — Entscheidung ist final, im Einsatz
- 🟡 **Proposed** — Kandidat, aber nicht finalisiert
- ⚠️ **Deprecated** — War gut, ist aber überholt
- ❌ **Rejected** — War Option, wurde abgelehnt

---

## Kontakt & Fragen

Falls Fragen zu einer Entscheidung oder Anpassungsbedarf:

1. Öffne ein GitHub Issue mit Label `adr`
2. Referenziere die ADR (z.B. "Question about ADR-5")
3. Erkläre das Problem
4. Falls sinnvoll: Aktualisiere das ADR oder erstelle neues ADR-X

---

**Zuletzt aktualisiert:** 2026-05-27  
**Maintainer:** BudgetBuddy Team
