## Prompt

Kannst du aufgrund der @CLAUDE.md einen Vorschlag für die Projektstruktur machen?


## Antwort

● Hier ist ein Vorschlag für die Projektstruktur, abgeleitet aus den Konventionen in CLAUDE.md (Modular Monolith nach Domäne, Angular Feature-Folders, Flyway-Migrations, Testing-Pyramide). backend/ und frontend/ sind
  aktuell leer — das hier wäre der Zielzustand.

  Gesamtstruktur

  budget-buddy/
  ├── backend/            ← Spring Boot 3.5 / Java 25 / Maven
  ├── frontend/           ← Angular 21
  ├── e2e/                ← Playwright (testet beide Seiten end-to-end)
  ├── docs/               ← bereits vorhanden (ADRs, requirements, prompts)
  ├── .github/            ← bereits vorhanden
  ├── CLAUDE.md
  └── README.md

  Backend (backend/)

  Package-Struktur nach Domäne, nicht nach Schicht (genau wie im CLAUDE.md-Abschnitt "Package-Struktur" vorgegeben):

  backend/
  ├── pom.xml
  ├── mvnw, mvnw.cmd, .mvn/
  └── src/
      ├── main/
      │   ├── java/com/budgetbuddy/
      │   │   ├── BudgetBuddyApplication.java
      │   │   │
      │   │   ├── auth/
      │   │   │   ├── AuthController.java
      │   │   │   ├── AuthService.java
      │   │   │   ├── User.java                  (Entity)
      │   │   │   ├── UserRepository.java
      │   │   │   ├── SecurityConfig.java         (Spring Security, JWT-Filter)
      │   │   │   ├── JwtService.java             (HS256, httpOnly Cookie)
      │   │   │   └── dto/  (LoginRequest, RegisterRequest, ...)
      │   │   │
      │   │   ├── transaction/
      │   │   │   ├── TransactionController.java
      │   │   │   ├── PdfImportService.java       (PDFBox 3.x, Loader.loadPDF())
      │   │   │   ├── Transaction.java            (Entity, Beträge als BigDecimal)
      │   │   │   ├── TransactionRepository.java
      │   │   │   ├── ImportJob.java               (für Status-Polling-Upgrade-Pfad)
      │   │   │   ├── ImportJobRepository.java
      │   │   │   └── dto/
      │   │   │
      │   │   ├── categorization/
      │   │   │   ├── CategorizationService.java   (Hybrid: Lookup → Claude)
      │   │   │   ├── CategorizationPort.java       (Interface, ADR-6)
      │   │   │   ├── ClaudeCategorizationAdapter.java  (implements Port, Timeout+Fallback "Sonstiges")
      │   │   │   ├── CategoryLookup.java           (Entity: Händler → Kategorie)
      │   │   │   ├── CategoryLookupRepository.java
      │   │   │   └── Category.java                (Enum)
      │   │   │
      │   │   ├── budget/
      │   │   │   ├── BudgetController.java
      │   │   │   ├── SafeToSpendService.java
      │   │   │   ├── SavingsGoalService.java
      │   │   │   ├── FixedCost.java
      │   │   │   ├── FixedCostRepository.java
      │   │   │   ├── SavingsGoal.java
      │   │   │   └── SavingsGoalRepository.java
      │   │   │
      │   │   └── report/
      │   │       ├── ReportController.java
      │   │       ├── AiReportService.java         (Claude Sonnet, 1×/Monat)
      │   │       └── dto/
      │   │
      │   └── resources/
      │       ├── application.properties
      │       ├── application-dev.properties        (CORS für localhost:4200)
      │       ├── application-prod.properties
      │       └── db/migration/                      (Flyway)
      │           ├── V1__create_users.sql
      │           ├── V2__create_fixed_costs.sql
      │           ├── V3__create_transactions.sql
      │           ├── V4__create_category_lookup.sql
      │           ├── V5__create_savings_goals.sql
      │           └── V6__create_import_jobs.sql
      │
      └── test/
          └── java/com/budgetbuddy/
              ├── auth/            (Unit: JUnit5+Mockito; Integration: @WebMvcTest, @DataJpaTest)
              ├── transaction/
              ├── categorization/  (90%+ Coverage-Ziel — CategorizationPort gemockt)
              ├── budget/          (90%+ Coverage-Ziel)
              └── report/

  Begründung wichtiger Punkte:
  - Keine controller/, service/, repository/-Top-Level-Packages — das widerspräche der explizit vorgegebenen Modular-Monolith-Regel ("kein direkter Zugriff auf Repositories/Services eines anderen Moduls").
  - CategorizationPort liegt bewusst im categorization-Modul, nicht in auth o.ä. — Claude-API ist austauschbar/mockbar.
  - Flyway-Versionsnummern grob an User-Story-Reihenfolge angelehnt (US-01 → users, US-03 → fixed_costs, US-04/05 → transactions/category_lookup, US-07 → savings_goals).

  Frontend (frontend/)

  Feature-Folders, an die Domänen-Module des Backends sowie an die User Stories angelehnt:

  frontend/
  ├── package.json
  ├── angular.json
  ├── tsconfig.json
  └── src/
      ├── main.ts
      ├── index.html
      ├── styles.scss
      ├── environments/
      │   ├── environment.ts
      │   └── environment.prod.ts
      └── app/
          ├── app.config.ts          (providers, HttpClient mit withCredentials)
          ├── app.routes.ts
          ├── app.component.ts
          │
          ├── auth/                  (US-01: Login/Register)
          ├── onboarding/             (US-03: Fixkosten-Wizard)
          ├── transactions/           (US-04 Upload, US-05 Kategorisierung, US-13 pro Kategorie)
          ├── dashboard/              (US-06 Safe-to-Spend, US-10 Monatsvergleich, US-12 Monatswechsel)
          ├── savings/                (US-07: Sparziel)
          ├── reports/                (US-09: KI-Monatsbericht)
          ├── settings/               (US-02 Consent/Löschen, US-14 Passwort/Einkommen)
          │
          ├── shared/                 (Charts-Komponenten, Pipes, Formatierung CHF)
          └── core/                   (Guards, Auth-State-Service, HTTP-Error-Handling)

  Begründung:
  - Kein core/interceptors/ nötig — laut ADR-7 reicht withCredentials: true, kein manueller HttpInterceptor.
  - State via Signals direkt in den Feature-Services (kein NgRx, kein zentraler Store-Ordner).
  - shared/ nur für wirklich domänenübergreifende UI (Chart.js-Wrapper, CHF-Pipe).

  E2E-Tests (e2e/)

  Separates Verzeichnis, da Playwright gegen die laufende App (Frontend+Backend zusammen) testet:

  e2e/
  ├── package.json
  ├── playwright.config.ts
  └── tests/
      ├── us-03-fixkosten-wizard.spec.ts
      ├── us-04-pdf-upload.spec.ts
      ├── us-05-kategorisierung.spec.ts
      └── us-06-safe-to-spend.spec.ts

  Je 1 Happy Path + 1 Fehlerpfad pro Must-Have Story, wie in der Testing-Tabelle vorgegeben.

  ---
  Soll ich das Backend- und Frontend-Skeleton (Maven-pom.xml, Spring-Boot-Grundgerüst, Angular-CLI-Projekt, erste Flyway-Migration) tatsächlich anlegen, oder bleibt es vorerst bei diesem Vorschlag zur Diskussion?