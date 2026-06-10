## Prompt

Wir sollen unser Sprint 1 präsentieren. Dazu gehört:
- Unser Sprint-1-Ziel in einem Satz.
- Board zeigen: Backlog-Grösse, Sprint-1-Tasks, wie wir priorisiert haben.
- Unser riskantester Task — und unser Plan damit umzugehen.
- Was wir bewusst aus Sprint 1 rausgelassen haben — und warum?

## Antwort

## Sprint 1 Präsentation — BudgetBuddy

---

### Sprint-Ziel

> **"Am Ende von Sprint 1 läuft die CI-Pipeline grün, das Datenbankschema ist deployed, und ein authentifizierter API-Call gegen `GET /users/me` liefert eine valide JWT-Response — Sprint 2 kann sofort Features bauen, ohne Infrastruktur-Schulden."**

---

### Board-Übersicht

**Gesamtes Backlog:** 35 Tasks · 94 Story Points

| Status | Tasks | Story Points |
|--------|-------|-------------|
| **Todo (Sprint 1)** | 8 | 16 SP |
| Backlog (Sprint 2+) | 27 | 78 SP |

**Sprint-1-Tasks im Detail:**

| # | Task | SP | Area | Begründung |
|---|------|----|------|-----------|
| #1 | INFRA-01: Spring Boot Skeleton | 3 | DevOps | Fundament für alle Backend-Tasks |
| #2 | INFRA-02: Angular Skeleton | 2 | Frontend | Fundament für alle Frontend-Tasks |
| #3 | INFRA-03: GitHub Actions CI | 2 | DevOps | Gebrochene Builds blockieren das ganze Team |
| #4 | DB-01: Flyway V1 users | 1 | DB | Voraussetzung für Auth und alle anderen Tabellen |
| #5 | DB-02: Flyway V2 transactions | 2 | DB | Kerntabelle für Import, Kategorisierung, Safe-to-Spend |
| #6 | DB-03: Flyway V3 fixed_costs | 1 | DB | Voraussetzung für Fixkosten-Feature (Sprint 2) |
| #8 | BE-AUTH-01: JWT HS256 Filter | 3 | Backend | Alle Endpoints brauchen Auth — muss zuerst stehen |
| #9 | BE-AUTH-02: GET/PUT /users/me | 2 | Backend | Onboarding-Status und Einkommen — Basis für US-03/06 |

**Priorisierungsprinzip:** "Was blockiert am meisten andere Tasks?" — reine Infrastruktur und Auth zuerst, kein UI-Feature bevor das Fundament steht.

---

### Riskantester Task — und unser Plan

**BE-PDF-01: PDFBox-Parser für Schweizer Bank-PDFs (8 SP)**

Das ist der grösste und unberechenbarste Task im gesamten Backlog. Warum riskant:

- Jede Schweizer Bank hat ein leicht anderes PDF-Layout (Raiffeisen ≠ PostFinance ≠ UBS)
- Sonderzeichen im Betragsformat (`1'234.56`) und mehrzeilige Transaktions-Texte können den Parser brechen
- 8 SP in einem 2-Wochen-Sprint ist fast ein ganzer Entwickler-Sprint alleine

**Unser Mitigationsplan:**
1. **Früh starten:** BE-PDF-01 ist der erste Task in Sprint 2 — kein Warten bis Woche 3
2. **Fixture-First:** Echtes Raiffeisen-PDF als Testdatei in `src/test/resources/` von Tag 1
3. **Timebox:** Nach 6h ohne stabiles Ergebnis → Spike abschliessen und pragmatisch entscheiden (Parser auf eine Bank beschränken für MVP)
4. **Passwort-PDFs:** Als separate Exception behandeln — nie den ganzen Flow blockieren

---

### Bewusst aus Sprint 1 rausgelassen

| Task | Warum rausgelassen |
|------|--------------------|
| **DB-04: category_lookup Seed-Daten (2 SP)** | Wird erst in Sprint 2/3 bei Kategorisierung gebraucht — zu früh geschriebene Seed-Daten veralten |
| **BE-PDF-01: PDFBox-Parser (8 SP)** | Risikoreichster Task — braucht stabiles DB-Schema und Auth als Voraussetzung |
| **BE-CAT-02: Claude API (5 SP)** | Externe Abhängigkeit; API-Key und Prompt-Design erst sinnvoll wenn Parser steht |
| **Alle Frontend-Features (FE-*)** | Angular-Skeleton reicht; vollständige UI-Komponenten brauchen fertige REST-Endpoints |
| **Safe-to-Spend (BE-STS-*, 10 SP)** | Core-Feature — aber abhängig von Transaktionen + Fixkosten, frühestens Sprint 3 |

**Leitprinzip:** Alles was kein Fundament ist, gehört nicht in Sprint 1. Ein roter CI-Build in Woche 1 lähmt das gesamte Team für den Rest des Projekts.

