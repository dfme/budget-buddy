## Prompt

Wir sind ein Team von 3 Personen für das Projekt BudgetBuddy aus @CLAUDE.md.
Tech-Stack: Lese den TechStack aus @CLAUDE.md

Für jede dieser Rollen — wo kann Claude Code uns konkret helfen?
- Tech Lead (Architektur, Reviews)
- Developer (Implementation, Tests)
- QA/DevOps (Testing, CI/CD, Deployment)

Gib konkrete Beispiele — keine allgemeinen Floskeln.
Was sollte der Mensch behalten, was kann AI übernehmen?


## Antwort
  Basierend auf eurem Stack (Angular 21 / Spring Boot 3.5 + Java 21 / SQLite + Flyway / Claude API / PDFBox 3.x) und euren bereits getroffenen Entscheiden
  (ADRs, Modular Monolith, Hybrid-Kategorisierung) — hier konkrete Ansatzpunkte pro Rolle.

  ---
  🏗️ Tech Lead (Architektur, Reviews)

  Wofür Claude Code konkret einsetzen:

  ┌──────────────────────────┬────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
  │         Aufgabe          │                                                   Konkretes Beispiel                                                   │
  ├──────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ ADRs schreiben           │ Neue Entscheidung taucht auf (z.B. "Caching für Kategorisierung?") → Claude entwirft ADR-11 im Format eurer            │
  │                          │ bestehenden ADR-0…ADR-10, inkl. abgelehnter Alternativen                                                               │
  ├──────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │                          │ /code-review auf jedem PR: prüft gezielt auf double/float für CHF-Beträge (Verstoss gegen ADR-9), fehlende             │
  │ PR-Reviews               │ Timeout+Fallback bei Claude-API-Calls (Churn-Risiko #1), Modul-Grenzverletzungen (z.B. budget/ greift direkt auf       │
  │                          │ TransactionRepository aus transaction/ zu statt über Interface)                                                        │
  ├──────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Security-Review vor      │ /security-review prüft JWT-Cookie-Konfiguration (httpOnly, SameSite=Strict gemäss ADR-7), bcrypt-Einsatz,              │
  │ Release                  │ versehentlich eingecheckte Secrets                                                                                     │
  ├──────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Release-Review           │ /code-review ultra vor grösseren Merges in main — Multi-Agent-Review in der Cloud für tiefere Abdeckung als ein        │
  │                          │ einzelner Reviewer leisten kann                                                                                        │
  ├──────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Architektur-Drift        │ Claude als Explore-Agent durchsuchen lassen: "Gibt es Stellen, wo categorization/ direkt von transaction/-Entities     │
  │ erkennen                 │ importiert statt über CategorizationPort?"                                                                             │
  ├──────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ OpenAPI-Spec-Konsistenz  │ Prüfen, ob Springdoc-generierte Doku zu den tatsächlichen Controllern passt, fehlende @Operation-Annotationen ergänzen │
  └──────────────────────────┴────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

  Was der Mensch behält:
  - Strategische Trade-offs mit Geschäftsrisiko (z.B. "Render Frankfurt trotz nDSG-Risiko" — ADR-10 wurde bewusst von Menschen entschieden, Claude kann
  nur Optionen aufzeigen)
  - Verhindern von Overengineering: die "What NOT to Use"-Tabelle (NgRx, Redis, Microservices) ist eine bewusste Entscheidung — bei jedem AI-Vorschlag
  gegenprüfen, ob er diese Grenze wieder einreissen will
  - Compliance-/Haftungsentscheide (nDSG-Breach-Bewertung) bleiben menschlich, auch wenn Claude die technische Analyse liefert

  ---
  💻 Developer (Implementation, Tests)

  Wofür Claude Code konkret einsetzen:

  ┌────────────────────────────────────┬───────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
  │              Aufgabe               │                                              Konkretes Beispiel                                               │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Modul-Scaffolding                  │ Neues Modul budget/ nach dem Muster von transaction/ aufbauen: BudgetController, SafeToSpendService,          │
  │                                    │ SavingsGoalService, Entity, Repository                                                                        │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Swiss-PDF-Parsing                  │ PDFBox-Logik für Spaltenformat Buchungsdatum | Valuta | Text | Belastungen | Gutschriften | Saldo, inkl.      │
  │                                    │ Multiline-Text-Handling (Saldo-Spalte als Row-Anchor) und '-Trennzeichen-Bereinigung vor BigDecimal-Parsing   │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ CategorizationPort-Implementierung │ Claude-API-Client mit Timeout + catch(AnthropicException) → Fallback "Sonstiges", wie in CLAUDE.md            │
  │                                    │ spezifiziert                                                                                                  │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Flyway-Migrationen                 │ V3__add_savings_goals.sql mit DECIMAL(10,2) für Geldspalten, konsistent zu ADR-9                              │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Angular-Formulare                  │ Onboarding-Wizard (US-03) als Reactive Form mit mehreren Steps, Validierung der Fixkosten-Eingaben            │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ JWT/Spring-Security-Config         │ Stateless Resource-Server-Setup, Cookie-Filter liest Token statt Authorization-Header                         │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ User Story → erster Entwurf        │ Aus US-06 (Safe-to-Spend) inkl. Acceptance Criteria einen ersten Implementierungsentwurf generieren lassen,   │
  │                                    │ den der Dev dann verfeinert                                                                                   │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Tests                              │ Unit-Tests für SafeToSpendService (Rundungsfälle, negative Salden), Mock von CategorizationPort (genau dafür  │
  │                                    │ wurde das Interface gebaut), Test-Fixtures mit synthetischem PDF-Text für UBS/Raiffeisen/PostFinance-Layouts  │
  ├────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Manuelle Verifikation              │ /verify nach Implementierung: App starten, PDF hochladen, prüfen ob Safe-to-Spend korrekt berechnet wird      │
  └────────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

  Was der Mensch behält:
  - Fachliche Korrektheit der Business-Logik (z.B. wie genau Fixkosten in die wöchentliche Safe-to-Spend-Zahl einfliessen) — muss gegen die
  Persona-Bedürfnisse (Lara, Marc) validiert werden, nicht nur gegen den Code
  - Sicherheitskritischer Code (Auth, JWT) wird von Claude geschrieben, aber vor Merge von einem Menschen verstanden und gegengelesen — Risiko #2
  (Liability) verträgt kein "ungelesen gemergt"
  - Tests mit echten/realistischen Bank-PDFs (Edge Cases, die in keinem Trainingsdatensatz vorkommen)

  ---
  🔧 QA/DevOps (Testing, CI/CD, Deployment)

  Wofür Claude Code konkret einsetzen:

  ┌──────────────────────┬────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
  │       Aufgabe        │                                                     Konkretes Beispiel                                                     │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ CI-Pipeline          │ GitHub-Actions-Workflow: Spring-Boot-Build + Angular-Build → Angular-Assets nach BOOT-INF/static/ kopieren → Single JAR    │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Migration-Check in   │ Schritt, der Flyway-Migrationen gegen frische SQLite-DB laufen lässt, bevor gemerged wird                                  │
  │ CI                   │                                                                                                                            │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Integrationstests    │ @SpringBootTest mit jdbc:sqlite::memory: (nicht H2 — Dialekt-Mismatch ist in CLAUDE.md explizit vermerkt)                  │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Fallback-Tests       │ Szenario simulieren: Claude API down → Assert, dass Import trotzdem durchläuft und Kategorie "Sonstiges" gesetzt wird      │
  │                      │ (Test für Churn-Risiko-Mitigation)                                                                                         │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Concurrency-Test     │ Skript, das parallele Schreibzugriffe auf SQLite simuliert — liefert die Datenbasis für die "wann zu Postgres              │
  │                      │ migrieren"-Entscheidung aus ADR-5                                                                                          │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Secret-Scanning      │ CI-Step, der prüft, dass ANTHROPIC_API_KEY/JWT-Secret nie in application.properties oder Git landen                        │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ Deployment-Skript    │ Render-Deployment-Konfiguration (Frankfurt/EU), Health-Check-Endpunkt                                                      │
  ├──────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ nDSG-Testpfad        │ Testfall für "Konto löschen" (US-02) — verifizieren, dass wirklich alle personenbezogenen Daten entfernt werden            │
  └──────────────────────┴────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

  Was der Mensch behält:
  - Den finalen Deploy-Trigger auf Produktion — bei sensiblen Finanzdaten kein vollautomatisches Deploy ohne menschliches Review
  - Beurteilung, ob generierte Tests echte Edge Cases abdecken oder nur Coverage-Zahlen aufblähen
  - Die SQLite→Postgres-Migrationsentscheidung selbst (Claude liefert Lastdaten, das Team entscheidet den Aufwand/Nutzen-Trade-off)
  - Incident-Response bei einem echten Datenleck (rechtliche Meldepflicht nach nDSG ist Teamsache, nicht AI-Sache)

  ---
  Faustregel fürs Team

  AI übernimmt: Boilerplate, Wiederholung von etablierten Mustern, Testfälle generieren, Reviews als zweite Meinung, Doku/ADRs entwerfen.

  Mensch behält: Jede Entscheidung mit Geld-, Sicherheits- oder Rechtsfolge; jede Entscheidung, die eure bewusst gewählte Einfachheit (SQLite, kein NgRx,
  kein Microservice) wieder aufweichen könnte; und das Verständnis warum der Code funktioniert — nicht nur dass er es tut.