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
| `BE-AUTH-08` | [monthly_income auf Rappen und Kapazität prüfen](BE-AUTH-08-income-rappen-validierung.md) | [#148](https://github.com/dfme/budget-buddy/issues/148) | — | Sprint 5 |
| `BE-AUTH-09` | [Passwort-Änderung-Endpoint](BE-AUTH-09-password-change.md) | [#176](https://github.com/dfme/budget-buddy/issues/176) | US-14 | Sprint 5 |
| `BE-CAT-01` | [CategorizationPort Interface und LookupTableService](BE-CAT-01-categorization-port.md) | [#14](https://github.com/dfme/budget-buddy/issues/14) | US-05 | Sprint 2 |
| `BE-CAT-02` | [ClaudeCategorizationService](BE-CAT-02-claude-categorization.md) | [#15](https://github.com/dfme/budget-buddy/issues/15) | US-05 | Sprint 2 |
| `BE-CAT-03` | [HybridCategorizationService](BE-CAT-03-hybrid-categorization.md) | [#16](https://github.com/dfme/budget-buddy/issues/16) | US-05 | Sprint 3 |
| `BE-CAT-04` | [PUT /transactions/{id}/category](BE-CAT-04-update-transaction-category.md) | [#19](https://github.com/dfme/budget-buddy/issues/19) | US-05 | Sprint 3 |
| `BE-CAT-05` | [GET /transactions/summary](BE-CAT-05-transactions-summary.md) | [#20](https://github.com/dfme/budget-buddy/issues/20) | US-05 | Sprint 3 |
| `BE-CAT-07` | [AnthropicStartupHealthCheckTest ist ordnungsabhängig und macht einen echten Netzwerk-Call](BE-CAT-07-healthcheck-test-isolation.md) | [#162](https://github.com/dfme/budget-buddy/issues/162) | — | Sprint 5 |
| `BE-FC-01` | [FixedCost Entity und Repository](BE-FC-01-fixedcost-entity.md) | [#10](https://github.com/dfme/budget-buddy/issues/10) | US-03 | Sprint 4 |
| `BE-FC-02` | [FixedCostService: CRUD und Normalisierung](BE-FC-02-fixedcost-service.md) | [#11](https://github.com/dfme/budget-buddy/issues/11) | US-03 | Sprint 4 |
| `BE-FC-03` | [REST-Endpoints für Fixkosten](BE-FC-03-fixed-costs-endpoints.md) | [#12](https://github.com/dfme/budget-buddy/issues/12) | US-03 | Sprint 4 |
| `BE-PDF-01` | [PDFBox-Parser für Schweizer Bank-PDFs](BE-PDF-01-swiss-bank-parser.md) | [#13](https://github.com/dfme/budget-buddy/issues/13) | US-04 | Sprint 3 |
| `BE-PDF-02` | [PdfImportService](BE-PDF-02-pdf-import-service.md) | [#17](https://github.com/dfme/budget-buddy/issues/17) | US-04 | Sprint 3 |
| `BE-PDF-03` | [POST /import/pdf Endpoint](BE-PDF-03-pdf-upload-endpoint.md) | [#18](https://github.com/dfme/budget-buddy/issues/18) | US-04 | Sprint 3 |
| `BE-PDF-04` | [Parser wirft keine Exception bei 0 Transaktionen](BE-PDF-04-empty-parse-exception.md) | [#83](https://github.com/dfme/budget-buddy/issues/83) | — | Sprint 3 |
| `BE-PDF-05` | [BE-PDF-05 — Gültiger Auszug mit 0 Buchungen wirft fälschlich UnsupportedStatementFormatException](BE-PDF-05-empty-statement-zero-bookings.md) | [#95](https://github.com/dfme/budget-buddy/issues/95) | — | Sprint 3 |
| `BE-PDF-06` | [Import-Flow instrumentieren: Phasendauer und Lookup/Claude-Verhältnis loggen, Klartexte entfernen](BE-PDF-06-import-instrumentierung.md) | [#157](https://github.com/dfme/budget-buddy/issues/157) | — | Sprint 4 |
| `BE-PDF-07` | [Absender/Empfänger aus den Detailzeilen wird beim Import verworfen](BE-PDF-07-detailzeilen-persistieren.md) | [#159](https://github.com/dfme/budget-buddy/issues/159) | — | Sprint 5 |
| `BE-PDF-08` | [Import eines Kontoauszugs schlägt zuverlässig fehl](BE-PDF-08-fehlerdiagnose.md) | [#173](https://github.com/dfme/budget-buddy/issues/173) | — | Sprint 5 |
| `BE-PDF-09` | [PDF-Import läuft in Produktion ins 30s-Zeitbudget und verwirft den gesamten Import](BE-PDF-09-async-import-job.md) | [#192](https://github.com/dfme/budget-buddy/issues/192) | US-04 | Sprint 5 |
| `BE-STS-01` | [SafeToSpendService](BE-STS-01-safe-to-spend-service.md) | [#21](https://github.com/dfme/budget-buddy/issues/21) | US-06 | Sprint 4 |
| `BE-STS-02` | [Einkommens-Heuristik](BE-STS-02-einkommens-heuristik.md) | [#22](https://github.com/dfme/budget-buddy/issues/22) | US-06 | Sprint 4 |
| `BE-STS-03` | [GET /budget/safe-to-spend](BE-STS-03-safe-to-spend-endpoint.md) | [#23](https://github.com/dfme/budget-buddy/issues/23) | US-06 | Sprint 4 |
| `BE-STS-04` | [Fixkosten werden im Safe-to-Spend doppelt abgezogen](BE-STS-04-fixkosten-doppelabzug.md) | [#154](https://github.com/dfme/budget-buddy/issues/154) | US-06 | Sprint 5 |
| `DB-01` | [DB-01 — Flyway V1: users-Tabelle](DB-01-flyway-users-table.md) | [#4](https://github.com/dfme/budget-buddy/issues/4) | — | Sprint 1 |
| `DB-02` | [DB-02 — Flyway V2: transactions-Tabelle](DB-02-transactions-table.md) | [#5](https://github.com/dfme/budget-buddy/issues/5) | US-04, US-05, US-06 | Sprint 1 |
| `DB-03` | [DB-03 — Flyway V3: fixed_costs-Tabelle](DB-03-fixed-costs-table.md) | [#6](https://github.com/dfme/budget-buddy/issues/6) | US-03 | Sprint 1 |
| `DB-04` | [Flyway V4: category_lookup-Tabelle mit Seed-Daten](DB-04-category-lookup-seed.md) | [#7](https://github.com/dfme/budget-buddy/issues/7) | US-05 | Sprint 2 |
| `DB-05` | [Migration von SQLite auf Neon Postgres (inkl. Entscheid-ADR)](DB-05-neon-postgres-migration.md) | [#89](https://github.com/dfme/budget-buddy/issues/89) | — | Sprint 4 |
| `DB-07` | [DB-07 — Foreign Keys auf users ohne ON DELETE — Löschpfad für US-02](DB-07-user-loeschpfad.md) | [#142](https://github.com/dfme/budget-buddy/issues/142) | US-02 | Sprint 5 |
| `E2E-CAT-01` | [Playwright: Transaktionen kategorisieren (Happy Path + Fehlerpfad)](E2E-CAT-01-playwright-kategorisierung.md) | [#124](https://github.com/dfme/budget-buddy/issues/124) | US-05 | Sprint 5 |
| `E2E-FC-01` | [Playwright: Fixkosten-Wizard (Happy Path + Fehlerpfad)](E2E-FC-01-playwright-fixkosten-wizard.md) | [#123](https://github.com/dfme/budget-buddy/issues/123) | US-03 | Sprint 5 |
| `E2E-PDF-01` | [Playwright: PDF-Upload (Happy Path + Fehlerpfad)](E2E-PDF-01-playwright-pdf-upload.md) | [#122](https://github.com/dfme/budget-buddy/issues/122) | US-04 | Sprint 5 |
| `FE-AUTH-01` | [FE-AUTH-01 — AuthService (Signal-State + /auth-Calls)](FE-AUTH-01-auth-service.md) | [#53](https://github.com/dfme/budget-buddy/issues/53) | US-01 | Sprint 2 |
| `FE-AUTH-02` | [Login-Component (Reactive Form)](FE-AUTH-02-login-component.md) | [#54](https://github.com/dfme/budget-buddy/issues/54) | US-01 | Sprint 2 |
| `FE-AUTH-03` | [FE-AUTH-03 — Register-Component (Reactive Form)](FE-AUTH-03-register-component.md) | [#55](https://github.com/dfme/budget-buddy/issues/55) | US-01 | Sprint 2 |
| `FE-AUTH-04` | [FE-AUTH-04 — authGuard + 401-Redirect für geschützte Routes](FE-AUTH-04-authguard-401-redirect.md) | [#56](https://github.com/dfme/budget-buddy/issues/56) | US-01 | Sprint 2 |
| `FE-AUTH-05` | [Logout-Button + Nav-Anbindung](FE-AUTH-05-logout-button.md) | [#57](https://github.com/dfme/budget-buddy/issues/57) | US-01 | Sprint 2 |
| `FE-CAT-01` | [FE-CAT-01 — Kategorie-Übersicht](FE-CAT-01-kategorie-uebersicht.md) | [#30](https://github.com/dfme/budget-buddy/issues/30) | US-05 | Sprint 3 |
| `FE-CAT-02` | [Pie-Chart Ausgaben nach Kategorie](FE-CAT-02-kategorie-donut-chart.md) | [#31](https://github.com/dfme/budget-buddy/issues/31) | US-05 | Sprint 4 |
| `FE-CAT-03` | [Manuelles Korrigieren von Kategorien](FE-CAT-03-kategorie-korrektur.md) | [#32](https://github.com/dfme/budget-buddy/issues/32) | US-05 | Sprint 4 |
| `FE-CAT-04` | [Direktsprung zu einem Monat in der Kategorie-Übersicht](FE-CAT-04-monat-direktsprung.md) | [#144](https://github.com/dfme/budget-buddy/issues/144) | US-12 | Sprint 4 |
| `FE-CAT-05` | [Pagination der Transaktionsliste (20 + «Weitere laden»)](FE-CAT-05-transaktions-pagination.md) | [#153](https://github.com/dfme/budget-buddy/issues/153) | US-13 | Sprint 4 |
| `FE-FC-01` | [Fixkosten-Wizard Component](FE-FC-01-fixkosten-wizard.md) | [#24](https://github.com/dfme/budget-buddy/issues/24) | US-03 | Sprint 4 |
| `FE-FC-02` | [Route Guard für Onboarding](FE-FC-02-onboarding-guard.md) | [#25](https://github.com/dfme/budget-buddy/issues/25) | US-03 | Sprint 4 |
| `FE-FC-03` | [Fixkosten-Liste](FE-FC-03-fixkosten-liste.md) | [#26](https://github.com/dfme/budget-buddy/issues/26) | US-03 | Sprint 4 |
| `FE-FC-04` | [Fixkosten-Tabelle läuft auf schmalen Viewports über die Card hinaus](FE-FC-04-tabelle-overflow.md) | [#172](https://github.com/dfme/budget-buddy/issues/172) | — | Sprint 5 |
| `FE-PDF-01` | [Plan: FE-PDF-01 — PDF-Upload Component](FE-PDF-01-pdf-upload-component.md) | [#27](https://github.com/dfme/budget-buddy/issues/27) | US-04 | Sprint 3 |
| `FE-PDF-02` | [FE-PDF-02 — Ergebnis-Anzeige nach PDF-Import](FE-PDF-02-import-ergebnis-anzeige.md) | [#28](https://github.com/dfme/budget-buddy/issues/28) | US-04 | Sprint 3 |
| `FE-PDF-03` | [Duplikat-Dialog](FE-PDF-03-duplikat-dialog.md) | [#29](https://github.com/dfme/budget-buddy/issues/29) | US-04 | Sprint 4 |
| `FE-SET-01` | [Einstellungen-Screen: Route und Navigation](FE-SET-01-einstellungen-screen.md) | [#177](https://github.com/dfme/budget-buddy/issues/177) | US-14 | Sprint 5 |
| `FE-SET-04` | [Erscheinungsbild: Hell, Dunkel, System](FE-SET-04-erscheinungsbild.md) | [#180](https://github.com/dfme/budget-buddy/issues/180) | US-14 | Sprint 5 |
| `FE-STS-01` | [Safe-to-Spend Dashboard-Widget](FE-STS-01-safe-to-spend-widget.md) | [#33](https://github.com/dfme/budget-buddy/issues/33) | US-06 | Sprint 4 |
| `FE-STS-02` | [Negativ-Banner](FE-STS-02-negativ-banner.md) | [#34](https://github.com/dfme/budget-buddy/issues/34) | US-06 | Sprint 4 |
| `FE-STS-03` | [No-Income State und Einkommens-Vorschlag](FE-STS-03-no-income-state-and-suggestion.md) | [#35](https://github.com/dfme/budget-buddy/issues/35) | US-06 | Sprint 4 |
| `FE-UI-01` | [UI-Design definieren: 3 klickbare Varianten](FE-UI-01-design-varianten.md) | [#80](https://github.com/dfme/budget-buddy/issues/80) | US-05, US-06 | Sprint 3 |
| `FE-UI-02` | [Design-Token-Fundament (Variante A, theme-fähig)](FE-UI-02-design-token-fundament.md) | [#99](https://github.com/dfme/budget-buddy/issues/99) | — | Sprint 3 |
| `FE-UI-03` | [Shared-Basiskomponenten (Variante A)](FE-UI-03-shared-basiskomponenten.md) | [#100](https://github.com/dfme/budget-buddy/issues/100) | — | Sprint 3 |
| `FE-UI-04` | [App-Shell: Navigation, Topbar/Sidebar, Konto/Logout (Variante A)](FE-UI-04-app-shell.md) | [#101](https://github.com/dfme/budget-buddy/issues/101) | — | Sprint 3 |
| `FE-UI-05` | [Chart-Integration: ng2-charts + Donut/Bar-Basiskomponenten (Variante A)](FE-UI-05-chart-integration.md) | [#102](https://github.com/dfme/budget-buddy/issues/102) | US-06 | Sprint 3 |
| `FE-UI-06` | [Bestehende Screens auf Variante-A-Fundament migrieren](FE-UI-06-variante-a-migration.md) | [#104](https://github.com/dfme/budget-buddy/issues/104) | US-01, US-05 | Sprint 3 |
| `FE-UI-07` | [Notice-Komponente: Icon und optionaler Titel](FE-UI-07-notice-icon-titel.md) | [#181](https://github.com/dfme/budget-buddy/issues/181) | — | Sprint 5 |
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
| `INFRA-16` | [GitHub Actions auf Node-24-Runtime heben (Node-20-Deprecation)](INFRA-16-node24-actions.md) | [#121](https://github.com/dfme/budget-buddy/issues/121) | — | Sprint 5 |
| `INFRA-17` | [SPA-Routen /import, /categories, /register per Deep-Link nicht erreichbar](INFRA-17-api-prefix-spa-catchall.md) | [#126](https://github.com/dfme/budget-buddy/issues/126) | — | Sprint 5 |
| `INFRA-18` | [Sprint-Planung als /plan-sprint-Skill + Index für docs/plans/](INFRA-18-plan-sprint-skill.md) | [#127](https://github.com/dfme/budget-buddy/issues/127) | — | Sprint 3 |
| `INFRA-28` | [INFRA-28 — Neon-Compute-Verbrauch: Health-Check und Hikari-Pool halten die DB dauerhaft wach](INFRA-28-neon-compute-health-check.md) | — | — | — |
| `INFRA-31` | [Automatisches PR-Review via GitHub Action einrichten (Claude Code)](INFRA-31-claude-pr-review-action.md) | [#215](https://github.com/dfme/budget-buddy/issues/215) | — | Sprint 5 |
