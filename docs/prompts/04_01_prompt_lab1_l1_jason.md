## Prompt

Hier sind alle unsere Must-have User Stories: US-03, US-04, US-05, US-06 @CLAUDE.md
Unser Tech-Stack: C2 Container Diagram aus @CLAUDE.md
Unsere Definition of Done unter Antwort im @docs/prompts/04_01_prompt_uebung_dod_jason.md

Erstelle ein vollständiges Backlog:
- Zerlege jede Story in Tasks (max 4h)
- Story Points pro Task (1,2,3,5,8)
- Bereich-Label (Backend/Frontend/DB/DevOps)
- Abhängigkeiten zwischen Tasks
- Sortiere nach: was muss zuerst gebaut werden?

Gib mir am Ende: Gesamt-Story-Points und grobe Reihenfolge.


## Antwort

## Vollständiges Backlog — BudgetBuddy Must-Haves

### Legende

SP = Story Points | **Dep** = hängt von Task-IDs ab

---

## Wave 0 — Infrastruktur (Voraussetzung für alles)

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| INFRA-01 | Spring Boot Skeleton: pom.xml mit allen Dependencies (sqlite-jdbc, PDFBox 3.x, Anthropic SDK, JJWT, Springdoc, Flyway), Application.properties, Package-Struktur anlegen | 3 | Backend/DevOps | — |
| INFRA-02 | Angular Skeleton: Angular 21 CLI, ng2-charts, HttpClient mit `withCredentials:true`, OnPush global, Routing-Grundstruktur | 2 | Frontend | — |
| INFRA-03 | GitHub Actions CI: `mvn test` + `ng build` bei jedem PR | 2 | DevOps | INFRA-01, INFRA-02 |
| INFRA-04 | `render.yaml` + `application-prod.properties`: Render Web Service konfigurieren (Build-Command `mvn package -DskipTests`, Start-Command `java -jar target/*.jar`), SQLite-Pfad auf persistentem Render Disk, Env-Var-Liste dokumentieren (`ANTHROPIC_API_KEY`, `JWT_SECRET`) | 2 | DevOps | INFRA-01 |
| INFRA-05 | Angular-Build in Spring Boot JAR bundeln: `frontend-maven-plugin` in `pom.xml` einbinden, `ng build --output-path=src/main/resources/static` — Angular-Assets werden beim `mvn package` automatisch eingebettet | 3 | DevOps/Frontend | INFRA-01, INFRA-02 |
| INFRA-06 | GitHub Actions CD: Render Deploy Hook nach erfolgreichem Build auf `main` auslösen; Smoke-Test gegen `/actuator/health` nach Deploy | 1 | DevOps | INFRA-03, INFRA-04 |

**Wave-0-Total: 13 SP**

---

## Wave 1 — Datenbankschema (alle Migrationen zuerst)

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| DB-01 | Flyway V1: `users` (id, email, password_hash, monthly_income DECIMAL(10,2), onboarding_completed BOOLEAN) | 1 | DB | INFRA-01 |
| DB-02 | Flyway V2: `transactions` (id, user_id, buchungsdatum DATE, text VARCHAR, betrag DECIMAL(10,2), is_income BOOLEAN, category VARCHAR, pdf_sha256 VARCHAR) | 2 | DB | DB-01 |
| DB-03 | Flyway V3: `fixed_costs` (id, user_id, bezeichnung VARCHAR, betrag DECIMAL(10,2), intervall VARCHAR) | 1 | DB | DB-01 |
| DB-04 | Flyway V4: `category_lookup` (empfaenger_pattern VARCHAR PK, category VARCHAR) — Seed-Daten: Migros, Coop, SBB, Sunrise, etc. | 2 | DB | INFRA-01 |

**Wave-1-Total: 6 SP**

---

## Wave 2 — Backend-Kern (parallele Streams möglich)

### Stream A: Auth/User-Basis (US-03 Voraussetzung)

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| BE-AUTH-01 | JWT HS256 Filter: Token aus httpOnly Cookie lesen, SecurityContext befüllen | 3 | Backend | DB-01 |
| BE-AUTH-02 | `GET /users/me` (Profil inkl. `onboarding_completed`, `monthly_income`) + `PUT /users/me/income` | 2 | Backend | BE-AUTH-01 |

### Stream B: Fixkosten (US-03)

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| BE-FC-01 | `FixedCost` Entity + Repository (JPA, BigDecimal) | 2 | Backend | DB-03 |
| BE-FC-02 | `FixedCostService`: CRUD + monatliche Normalisierung (quartalsweise ÷ 3, jährlich ÷ 12) + Warnung wenn Fixkosten ≥ Einkommen | 3 | Backend | BE-FC-01, BE-AUTH-02 |
| BE-FC-03 | REST: `POST/GET/PUT/DELETE /fixed-costs` + `POST /users/me/onboarding-complete` — OpenAPI-annotiert | 2 | Backend | BE-FC-02 |

### Stream C: PDF-Import (US-04)

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| BE-PDF-01 | PDFBox-Parser: Textextraktion Schweizer Bank-PDFs; Datum `dd.MM.yyyy`, Betrag `1'234.56` → `replace("'","")` + `new BigDecimal(...)`, Passwort-Detection | 8 | Backend | DB-02 |
| BE-PDF-02 | `PdfImportService`: SHA-256-Hash → Duplikatcheck → Parse → Kategorisierung → Save; 30s Timeout + Fehler-Response; PDF nicht persistieren | 5 | Backend | BE-PDF-01, BE-CAT-03 |
| BE-PDF-03 | `POST /import/pdf` (multipart/form-data, 10 MB limit); Responses: 200 `{count}`, 409 Duplikat, 400 Passwort/Format, 408 Timeout — OpenAPI-annotiert | 2 | Backend | BE-PDF-02 |

### Stream D: Kategorisierung (US-05)

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| BE-CAT-01 | `CategorizationPort` Interface + `LookupTableService`: DB-Lookup gegen `category_lookup` | 2 | Backend | DB-04 |
| BE-CAT-02 | `ClaudeCategorizationService`: Anthropic Java SDK, Haiku-Modell, Timeout 10s, `AnthropicException` → `"Sonstiges"` | 5 | Backend | BE-CAT-01 |
| BE-CAT-03 | `HybridCategorizationService`: Lookup first, Claude nur für unbekannte Transaktionen | 2 | Backend | BE-CAT-02 |
| BE-CAT-04 | `PUT /transactions/{id}/category`: Kategorie-Änderung persistieren + Eintrag in `category_lookup` schreiben (Lerneffekt) | 2 | Backend | DB-02, DB-04 |
| BE-CAT-05 | `GET /transactions/summary?month=YYYY-MM`: CHF-Summe, Anzahl, Prozent pro Kategorie (BigDecimal, auf Rappen genau) | 3 | Backend | DB-02 |

### Stream E: Safe-to-Spend (US-06)

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| BE-STS-01 | `SafeToSpendService`: Formel `(Einkommen − Fixkosten − Ausgaben) ÷ verbleibende Wochen`; Divisor min. 1; negativ-Flag; kein Einkommen → Error-Flag | 5 | Backend | BE-FC-02, DB-02 |
| BE-STS-02 | Einkommens-Heuristik: wiederkehrende Gutschrift ±5%, ≥ 2 Monate → Vorschlag | 3 | Backend | DB-02 |
| BE-STS-03 | `GET /budget/safe-to-spend`: Response mit `amount`, `weeksLeft`, `isNegative`, `noIncome`, `incomeSuggestion` — OpenAPI-annotiert | 2 | Backend | BE-STS-01, BE-STS-02 |

**Wave-2-Total: 51 SP**

---

## Wave 3 — Frontend (parallel zum Backend möglich, sobald API-Kontrakt via Swagger steht)

### US-03: Fixkosten-Wizard

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| FE-FC-01 | Wizard-Component: Reactive Form (Bezeichnung, Betrag, Intervall-Select), feldspezifische Validierung (`required`, `betrag > 0`) | 5 | Frontend | INFRA-02 |
| FE-FC-02 | Route Guard: Wizard erzwingen bis Onboarding abgeschlossen; "Keine Fixkosten"-Bestätigung | 2 | Frontend | FE-FC-01 |
| FE-FC-03 | Fixkosten-Liste: Einträge anzeigen, bearbeiten, löschen; Warnung "Fixkosten ≥ Einkommen" | 3 | Frontend | BE-FC-03 |

### US-04: PDF-Upload

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| FE-PDF-01 | Upload-Component: Drag-and-Drop + File-Picker, 10 MB Client-Validierung, Lade-Spinner | 3 | Frontend | INFRA-02 |
| FE-PDF-02 | Ergebnis-Anzeige: "42 Transaktionen erkannt"; Fehler-Meldungen (Passwort, Format, Timeout) | 2 | Frontend | FE-PDF-01, BE-PDF-03 |
| FE-PDF-03 | Duplikat-Dialog: "Trotzdem importieren" / "Abbrechen" (Modal-Component) | 2 | Frontend | FE-PDF-02 |

### US-05: Kategorisierung

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| FE-CAT-01 | Kategorie-Übersicht: Liste mit CHF-Summe, Anzahl, Prozent pro Kategorie | 3 | Frontend | BE-CAT-05 |
| FE-CAT-02 | Pie-Chart (ng2-charts/Chart.js): Ausgaben nach Kategorie | 3 | Frontend | FE-CAT-01 |
| FE-CAT-03 | Manuelles Korrigieren: Dropdown mit 13 Kategorien, Save → optimistic UI-Update | 2 | Frontend | BE-CAT-04 |

### US-06: Safe-to-Spend

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| FE-STS-01 | Dashboard-Widget: Safe-to-Spend Betrag (gross, prominent), Wochen-Label | 2 | Frontend | BE-STS-03 |
| FE-STS-02 | Negativ-Banner: rot hinterlegt, "Budget überzogen" | 1 | Frontend | FE-STS-01 |
| FE-STS-03 | No-Income State + Auto-Einkommens-Vorschlag-Banner | 2 | Frontend | FE-STS-01 |

**Wave-3-Total: 30 SP**

---

## Gesamtübersicht

| Bereich | Tasks | Story Points |
|---------|-------|-------------|
| DevOps/Infra | 6 | 13 |
| DB | 4 | 6 |
| Backend | 16 | 51 |
| Frontend | 12 | 30 |
| **Total** | **37** | **100 SP** |

---

## Grobe Reihenfolge (Build Order)

```
[Wave 0] INFRA-01, INFRA-02 (parallel)
    │       + INFRA-04 (render.yaml)
    │       + INFRA-05 (Angular in JAR)
    │
[Wave 1] DB-01 → DB-02, DB-03, DB-04 (parallel)
    │                           + INFRA-03 (CI) → INFRA-06 (CD)
[Wave 2A] BE-AUTH-01 → BE-AUTH-02
[Wave 2B] BE-FC-01 → BE-FC-02 → BE-FC-03         ← US-03 Backend ready
[Wave 2C] BE-CAT-01 → BE-CAT-02 → BE-CAT-03      ← muss vor PDF-Import fertig sein
          → BE-PDF-01 → BE-PDF-02 → BE-PDF-03    ← US-04 Backend ready
          + BE-CAT-04, BE-CAT-05                  ← US-05 Backend ready
[Wave 2E] BE-STS-01 → BE-STS-02 → BE-STS-03      ← US-06 Backend ready
    │
[Wave 3] FE parallel sobald Swagger-Spec steht:
    FE-FC-01/02/03 | FE-PDF-01/02/03 | FE-CAT-01/02/03 | FE-STS-01/02/03
```

**Kritischer Pfad:** `DB-02 → BE-CAT-01 → BE-CAT-02 → BE-CAT-03 → BE-PDF-01 → BE-PDF-02` — der PDF-Parser mit Claude-Integration ist der längste und risikoreichste Strang (BE-PDF-01 mit 8 SP).

**Empfehlung:** BE-PDF-01 als ersten "Sprint 1"-Task starten — frühzeitiger Proof-of-Concept mit einem echten Raiffeisen-PDF verhindert, dass der gesamte Import-Flow kurz vor Demo scheitert.
