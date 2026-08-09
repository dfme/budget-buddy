# Implementierungspläne

Pro umgesetztem Issue ein Plan — abgelegt von [`/implement-issue`](../../.claude/skills/implement-issue/SKILL.md)
nach der Bestätigung durch den User, bevor der Branch erstellt wird.

Die Ablage ist bewusst **flach**. Ein Verzeichnis pro Sprint wäre die naheliegende Gliederung,
schreibt aber eine Dimension fest: Sprint-Zugehörigkeit ist eine Eigenschaft des Boards und
ändert sich bei Carryover — #13 und #16 wurden in Sprint 2 geplant und erst in Sprint 3 fertig.
Ordner hätten diese Dateien umziehen lassen und die Historie gebrochen. Der Index bildet
Bereich, Story und Sprint stattdessen gleichzeitig als Spalten ab.

**Status und Story Points stehen hier nicht.** Die ändern sich laufend und gehören ins
[Sprint Board](https://github.com/users/dfme/projects/4) — eine Kopie
davon wäre ab ihrer Erzeugung veraltet. Die Spalte *Sprint* meint den Sprint, in dem der Plan
**geschrieben** wurde, nicht den, in dem das Issue fertig wurde.

Neue Zeilen hängt `/implement-issue` beim Ablegen des Plans selbst an. Bei Lücken oder
Handarbeit im Index baut `scripts/plans-index.sh` ihn vollständig neu auf.

| Task-ID | Plan | Issue | Story | Sprint |
| ------- | ---- | ----- | ----- | ------ |
| `BE-AUTH-01` | [BE-AUTH-01 — JWT HS256 Filter implementieren](BE-AUTH-01-jwt-filter.md) | [#8](https://github.com/dfme/budget-buddy/issues/8) | — | Sprint 1 |
| `BE-AUTH-02` | [BE-AUTH-02 — GET /users/me und PUT /users/me/income](BE-AUTH-02-users-me.md) | [#9](https://github.com/dfme/budget-buddy/issues/9) | — | Sprint 1 |
| `BE-AUTH-03` | [Register-, Login- und Logout-Endpoints](BE-AUTH-03-auth-endpoints.md) | [#46](https://github.com/dfme/budget-buddy/issues/46) | — | Sprint 1 |
| `BE-AUTH-04` | [BE-AUTH-04 — ADR-Auth-Doku korrigieren (httpOnly-Cookie statt Bearer/Interceptor)](BE-AUTH-04-adr-auth-doku.md) | [#103](https://github.com/dfme/budget-buddy/issues/103) | — | Sprint 3 |
| `BE-AUTH-06` | [BE-AUTH-06 — «Kein manueller Interceptor»-Vereinfachung präzisieren](BE-AUTH-06-interceptor-doku-praezisieren.md) | [#115](https://github.com/dfme/budget-buddy/issues/115) | — | Sprint 3 |
| `BE-AUTH-07` | [BE-AUTH-07 — Interceptor-Beschreibung nur in ADR-2 führen](BE-AUTH-07-interceptor-single-source.md) | [#117](https://github.com/dfme/budget-buddy/issues/117) | — | Sprint 3 |
| `BE-CAT-01` | [CategorizationPort Interface und LookupTableService](BE-CAT-01-categorization-port.md) | [#14](https://github.com/dfme/budget-buddy/issues/14) | US-05 | Sprint 2 |
| `BE-CAT-02` | [ClaudeCategorizationService](BE-CAT-02-claude-categorization.md) | [#15](https://github.com/dfme/budget-buddy/issues/15) | US-05 | Sprint 2 |
| `BE-CAT-03` | [HybridCategorizationService](BE-CAT-03-hybrid-categorization.md) | [#16](https://github.com/dfme/budget-buddy/issues/16) | US-05 | Sprint 3 |
| `BE-CAT-04` | [PUT /transactions/{id}/category](BE-CAT-04-update-transaction-category.md) | [#19](https://github.com/dfme/budget-buddy/issues/19) | US-05 | Sprint 3 |
| `BE-CAT-05` | [GET /transactions/summary](BE-CAT-05-transactions-summary.md) | [#20](https://github.com/dfme/budget-buddy/issues/20) | US-05 | Sprint 3 |
| `BE-FC-01` | [FixedCost Entity und Repository](BE-FC-01-fixedcost-entity.md) | [#10](https://github.com/dfme/budget-buddy/issues/10) | US-03 | Sprint 4 |
| `BE-FC-02` | [FixedCostService: CRUD und Normalisierung](BE-FC-02-fixedcost-service.md) | [#11](https://github.com/dfme/budget-buddy/issues/11) | US-03 | Sprint 4 |
| `BE-PDF-01` | [PDFBox-Parser für Schweizer Bank-PDFs](BE-PDF-01-swiss-bank-parser.md) | [#13](https://github.com/dfme/budget-buddy/issues/13) | US-04 | Sprint 3 |
| `BE-PDF-02` | [PdfImportService](BE-PDF-02-pdf-import-service.md) | [#17](https://github.com/dfme/budget-buddy/issues/17) | US-04 | Sprint 3 |
| `BE-PDF-03` | [POST /import/pdf Endpoint](BE-PDF-03-pdf-upload-endpoint.md) | [#18](https://github.com/dfme/budget-buddy/issues/18) | US-04 | Sprint 3 |
| `BE-PDF-04` | [Parser wirft keine Exception bei 0 Transaktionen](BE-PDF-04-empty-parse-exception.md) | [#83](https://github.com/dfme/budget-buddy/issues/83) | — | Sprint 3 |
| `BE-PDF-05` | [BE-PDF-05 — Gültiger Auszug mit 0 Buchungen wirft fälschlich UnsupportedStatementFormatException](BE-PDF-05-empty-statement-zero-bookings.md) | [#95](https://github.com/dfme/budget-buddy/issues/95) | — | Sprint 3 |
| `DB-01` | [DB-01 — Flyway V1: users-Tabelle](DB-01-flyway-users-table.md) | [#4](https://github.com/dfme/budget-buddy/issues/4) | — | Sprint 1 |
| `DB-02` | [DB-02 — Flyway V2: transactions-Tabelle](DB-02-transactions-table.md) | [#5](https://github.com/dfme/budget-buddy/issues/5) | US-04, US-05, US-06 | Sprint 1 |
| `DB-03` | [DB-03 — Flyway V3: fixed_costs-Tabelle](DB-03-fixed-costs-table.md) | [#6](https://github.com/dfme/budget-buddy/issues/6) | US-03 | Sprint 1 |
| `DB-04` | [Flyway V4: category_lookup-Tabelle mit Seed-Daten](DB-04-category-lookup-seed.md) | [#7](https://github.com/dfme/budget-buddy/issues/7) | US-05 | Sprint 2 |
| `DB-05` | [Migration von SQLite auf Neon Postgres (inkl. Entscheid-ADR)](DB-05-neon-postgres-migration.md) | [#89](https://github.com/dfme/budget-buddy/issues/89) | — | Sprint 4 |
| `FE-AUTH-01` | [FE-AUTH-01 — AuthService (Signal-State + /auth-Calls)](FE-AUTH-01-auth-service.md) | [#53](https://github.com/dfme/budget-buddy/issues/53) | US-01 | Sprint 2 |
| `FE-AUTH-02` | [Login-Component (Reactive Form)](FE-AUTH-02-login-component.md) | [#54](https://github.com/dfme/budget-buddy/issues/54) | US-01 | Sprint 2 |
| `FE-AUTH-03` | [FE-AUTH-03 — Register-Component (Reactive Form)](FE-AUTH-03-register-component.md) | [#55](https://github.com/dfme/budget-buddy/issues/55) | US-01 | Sprint 2 |
| `FE-AUTH-04` | [FE-AUTH-04 — authGuard + 401-Redirect für geschützte Routes](FE-AUTH-04-authguard-401-redirect.md) | [#56](https://github.com/dfme/budget-buddy/issues/56) | US-01 | Sprint 2 |
| `FE-AUTH-05` | [Logout-Button + Nav-Anbindung](FE-AUTH-05-logout-button.md) | [#57](https://github.com/dfme/budget-buddy/issues/57) | US-01 | Sprint 2 |
| `FE-CAT-01` | [FE-CAT-01 — Kategorie-Übersicht](FE-CAT-01-kategorie-uebersicht.md) | [#30](https://github.com/dfme/budget-buddy/issues/30) | US-05 | Sprint 3 |
| `FE-CAT-02` | [Pie-Chart Ausgaben nach Kategorie](FE-CAT-02-kategorie-donut-chart.md) | [#31](https://github.com/dfme/budget-buddy/issues/31) | US-05 | Sprint 4 |
| `FE-FC-01` | [Fixkosten-Wizard Component](FE-FC-01-fixkosten-wizard.md) | [#24](https://github.com/dfme/budget-buddy/issues/24) | US-03 | Sprint 4 |
| `FE-PDF-01` | [Plan: FE-PDF-01 — PDF-Upload Component](FE-PDF-01-pdf-upload-component.md) | [#27](https://github.com/dfme/budget-buddy/issues/27) | US-04 | Sprint 3 |
| `FE-PDF-02` | [FE-PDF-02 — Ergebnis-Anzeige nach PDF-Import](FE-PDF-02-import-ergebnis-anzeige.md) | [#28](https://github.com/dfme/budget-buddy/issues/28) | US-04 | Sprint 3 |
| `FE-UI-01` | [UI-Design definieren: 3 klickbare Varianten](FE-UI-01-design-varianten.md) | [#80](https://github.com/dfme/budget-buddy/issues/80) | US-05, US-06 | Sprint 3 |
| `FE-UI-02` | [Design-Token-Fundament (Variante A, theme-fähig)](FE-UI-02-design-token-fundament.md) | [#99](https://github.com/dfme/budget-buddy/issues/99) | — | Sprint 3 |
| `FE-UI-03` | [Shared-Basiskomponenten (Variante A)](FE-UI-03-shared-basiskomponenten.md) | [#100](https://github.com/dfme/budget-buddy/issues/100) | — | Sprint 3 |
| `FE-UI-04` | [App-Shell: Navigation, Topbar/Sidebar, Konto/Logout (Variante A)](FE-UI-04-app-shell.md) | [#101](https://github.com/dfme/budget-buddy/issues/101) | — | Sprint 3 |
| `FE-UI-05` | [Chart-Integration: ng2-charts + Donut/Bar-Basiskomponenten (Variante A)](FE-UI-05-chart-integration.md) | [#102](https://github.com/dfme/budget-buddy/issues/102) | US-06 | Sprint 3 |
| `FE-UI-06` | [Bestehende Screens auf Variante-A-Fundament migrieren](FE-UI-06-variante-a-migration.md) | [#104](https://github.com/dfme/budget-buddy/issues/104) | US-01, US-05 | Sprint 3 |
| `INFRA-01` | [Plan: [INFRA-01] Spring Boot Skeleton anlegen](INFRA-01-spring-boot-skeleton.md) | [#1](https://github.com/dfme/budget-buddy/issues/1) | — | Sprint 1 |
| `INFRA-02` | [Angular Skeleton anlegen](INFRA-02-angular-skeleton.md) | [#2](https://github.com/dfme/budget-buddy/issues/2) | — | Sprint 1 |
| `INFRA-03` | [INFRA-03 — GitHub Actions CI einrichten](INFRA-03-github-actions-ci.md) | [#3](https://github.com/dfme/budget-buddy/issues/3) | — | Sprint 1 |
| `INFRA-04` | [INFRA-04 — render.yaml und application-prod.properties anlegen](INFRA-04-render-deployment.md) | [#37](https://github.com/dfme/budget-buddy/issues/37) | — | Sprint 1 |
| `INFRA-05` | [INFRA-05 — Angular-SPA aus dem Spring-Boot-JAR ausliefern](INFRA-05-serve-spa.md) | [#38](https://github.com/dfme/budget-buddy/issues/38) | — | Sprint 1 |
| `INFRA-06` | [INFRA-06 — GitHub Actions CD: Render Deploy Hook](INFRA-06-render-deploy-hook.md) | [#39](https://github.com/dfme/budget-buddy/issues/39) | — | Sprint 2 |
| `INFRA-07` | [INFRA-07 — Angular Dev-Proxy für Backend-Calls](INFRA-07-dev-proxy.md) | [#60](https://github.com/dfme/budget-buddy/issues/60) | — | Sprint 2 |
| `INFRA-08` | [Smoke-Test verifiziert nicht die neue Version](INFRA-08-deploy-version-check.md) | [#68](https://github.com/dfme/budget-buddy/issues/68) | — | Sprint 2 |
| `INFRA-09` | [Smoke-Test-Timeout begrenzt Versuche statt Zeit](INFRA-09-smoke-test-deadline.md) | [#71](https://github.com/dfme/budget-buddy/issues/71) | — | Sprint 2 |
| `INFRA-10` | [Deployment-URL in der Erfolgsmeldung des CD-Jobs anzeigen](INFRA-10-deployment-url-im-log.md) | [#73](https://github.com/dfme/budget-buddy/issues/73) | — | Sprint 2 |
| `INFRA-11` | [ANTHROPIC_API_KEY: Verifikation dokumentieren + automatischer Startup-Check](INFRA-11-verifikation-doku.md) | [#76](https://github.com/dfme/budget-buddy/issues/76) | US-05 | Sprint 3 |
| `INFRA-12` | [INFRA-12 — Dokumentierte SQLite-Persistenz-Mitigation korrigieren](INFRA-12-persistenz-doku-korrigieren.md) | [#78](https://github.com/dfme/budget-buddy/issues/78) | — | Sprint 3 |
| `INFRA-14` | [Playwright-E2E-Setup aufsetzen](INFRA-14-playwright-e2e-setup.md) | [#91](https://github.com/dfme/budget-buddy/issues/91) | — | Sprint 3 |
| `INFRA-18` | [Sprint-Planung als /plan-sprint-Skill + Index für docs/plans/](INFRA-18-plan-sprint-skill.md) | [#127](https://github.com/dfme/budget-buddy/issues/127) | — | Sprint 3 |
