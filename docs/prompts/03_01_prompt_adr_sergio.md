Prompt 1:
Identifiziere die wichtigsten ADRs


Antwort 1:
Basierend auf der CLAUDE.md sind hier die wichtigsten Architecture Decision Records (ADRs) für BudgetBuddy:

  🎯 Kritische ADRs

  ┌────────┬─────────────────────────────────────┬────────────────────────────────────────────────────────┬────────┐
  │   #    │            Entscheidung             │                       Begründung                       │ Status │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │        │ JWT (Stateless, HS256) statt        │ Reduziert SQLite-Schreibzugriffe; saubere              │ ✅     │
  │ ADR-1  │ Session-basiert                     │ Angular-SPA-Integration; Logout durch                  │ Locked │
  │        │                                     │ Client-Token-Löschung (MVP-akzeptabel)                 │        │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-2  │ SQLite für MVP mit Migrationspfad   │ Einfach, kein separater DB-Server; Bottleneck bei      │ ✅     │
  │        │ zu PostgreSQL                       │ concurrent writes erst später relevant                 │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │        │ Hybrid-Kategorisierung:             │ Lookup deckt ~70–80% ab (schnell, kostenlos); nur      │ ✅     │
  │ ADR-3  │ Lookup-Tabelle + Claude API         │ unbekannte Transaktionen an LLM; User-Korrektionen     │ Locked │
  │        │                                     │ trainieren das System                                  │        │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-4  │ Claude Haiku (Kategorisierung) +    │ Haiku: schnell (~200ms), günstig; Sonnet: 1x/Monat für │ ✅     │
  │        │ Claude Sonnet (Monatsbericht)       │  reichhaltigere Berichte                               │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-5  │ Apache PDFBox 3.x für               │ Text-Layer-Extraktion (Swiss bank PDFs haben Text,     │ ✅     │
  │        │ PDF-Textextraktion                  │ nicht nur Scans); 3.x wg. neuerer API                  │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-6  │ Angular Signals + Services (kein    │ MVP-Scope zu klein für NgRx-Overhead; Signals native   │ ✅     │
  │        │ NgRx)                               │ in Angular 19                                          │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-7  │ Reactive Forms (FormGroup) für      │ Stabil; Signal Forms noch experimental                 │ ✅     │
  │        │ Formulare                           │                                                        │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-6  │ Angular Signals + Services (kein    │ MVP-Scope zu klein für NgRx-Overhead; Signals native   │ ✅     │
  │        │ NgRx)                               │ in Angular 19                                          │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-7  │ Reactive Forms (FormGroup) für      │ Stabil; Signal Forms noch experimental                 │ ✅     │
  │        │ Formulare                           │                                                        │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-8  │ Spring Data JPA + Hibernate mit     │ ORM reduziert SQL-Boilerplate; Community-Dialect für   │ ✅     │
  │        │ SQLiteDialect                       │ SQLite nötig                                           │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-9  │ Flyway für DB-Migrations            │ Team-Schema-Sync; SQLite-getestet                      │ ✅     │
  │        │                                     │                                                        │ Locked │
  ├────────┼─────────────────────────────────────┼────────────────────────────────────────────────────────┼────────┤
  │ ADR-10 │ BigDecimal für Geldbeträge (nicht   │ Binäre Fließkomma kann CHF-Beträge nicht exakt         │ ✅     │
  │        │ double/float)                       │ darstellen                                             │ Locked │
  └────────┴─────────────────────────────────────┴────────────────────────────────────────────────────────┴────────┘

  ⚠️ Nicht verwendete Technologien (Ablehnung mit Begründung)

  - ❌ Spring Boot 4 — nur Milestones, nicht produktionsreif
  - ❌ Spring WebFlux — SQLite JDBC ist blocking; Reactive wrapping komplex ohne Nutzen
  - ❌ iText 7 — AGPL-Lizenz erfordert Opensource oder kommerzielles Upgrade
  - ❌ NgRx — Overengineering für 2–3 Personen-Projekt
  - ❌ Redis + Spring Session — stateless JWT braucht keine Session-Infrastruktur
  - ❌ H2 in-memory (Tests) — Dialect-Mismatch; stattdessen jdbc:sqlite::memory:

  ---
  Nächste Schritte: Falls noch ADRs ausstehen (z.B. zu API-Pagination, Fehlerbehandlung, Deployment), sollten diese
  dokumentiert werden. Möchtest du eine ADR vertiefen oder neue hinzufügen?
  
  
Prompt 2:
Sollten grundsätzliche Entscheidungen wie Trennung Frontend und Backend sowie die verwendeten Technologien nicht auch in einem ADR erwähnt werden?


Antwort 2:
Du hast absolut recht! Das sind fundamentale Architektur-Entscheidungen, die in formalen ADRs gehören. Die CLAUDE.md
  dokumentiert die Begründungen, aber es fehlen strukturierte ADR-Dateien mit dem Standard-Format (Context → Decision →
  Consequences → Alternatives).

  🏗️ Fehlende high-level ADRs

  ┌────────┬──────────────────────────────────────────────────────────────────────┬─────────────┐
  │   #    │                              ADR-Titel                               │  Priorität  │
  ├────────┼──────────────────────────────────────────────────────────────────────┼─────────────┤
  │ ADR-0  │ Frontend-Backend-Trennung (SPA + REST API) vs. Server-Side Rendering │ 🔴 Kritisch │
  ├────────┼──────────────────────────────────────────────────────────────────────┼─────────────┤
  │ ADR-00 │ Warum Spring Boot + Java statt Node.js, Go, Python FastAPI, .NET     │ 🔴 Kritisch │
  ├────────┼──────────────────────────────────────────────────────────────────────┼─────────────┤
  │ ADR-01 │ Warum Angular statt React, Vue, Svelte                               │ 🔴 Kritisch │
  ├────────┼──────────────────────────────────────────────────────────────────────┼─────────────┤
  │ ADR-02 │ REST API vs. GraphQL vs. gRPC                                        │ 🟡 Wichtig  │
  ├────────┼──────────────────────────────────────────────────────────────────────┼─────────────┤
  │ ADR-03 │ Monolith vs. Microservices-Architektur                               │ 🟡 Wichtig  │
  ├────────┼──────────────────────────────────────────────────────────────────────┼─────────────┤
  │ ADR-04 │ OpenAPI 3 + Springdoc für API-Dokumentation und Contract             │ 🟡 Wichtig  │
  └────────┴──────────────────────────────────────────────────────────────────────┴─────────────┘

  ---
  📝 Beispiel: ADR-0 (Frontend-Backend-Trennung)

  # ADR-0: Frontend-Backend-Trennung (SPA + REST API)

  ## Status
  Accepted

  ## Context
  BudgetBuddy ist eine Web-App für Studenten/Berufseinsteiger mit sensiblen Finanzdaten.
  Architektur-Optionen:
  - SPA (Single Page App) + REST Backend (Frontend in Browser, Backend auf Server)
  - Server-Side Rendering (Express, Next.js, Django Templates)
  - Monolith mit integriertem Frontend

  ## Decision
  **SPA + REST API**: Angular frontend (TypeScript) lädt im Browser; Java/Spring Boot backend
  exposiert REST API über OpenAPI 3.

  ## Rationale
  - **Separation of Concerns**: Frontend-Entwicklung (UI/UX) von Backend (Geschäftslogik/Sicherheit) trennbar
  - **Caching**: Browser-seitiges Caching (Service Worker) möglich; reduziert Backend-Last
  - **Mobile-Readiness**: Wenn später mobile App (React Native) hinzukommt, kann selbe API genutzt werden
  - **Frontend-Agility**: Frontend-Änderungen ohne Backend-Deployment

  ## Consequences
  - **Pro**: Modularer, unabhängig deploybar
  - **Con**: CORS-Konfiguration nötig; zusätzliche HTTP-Requests (vs. SSR)
  - **Con**: Browser zeigt API-Endpoints sichtbar (aber: Daten hinter JWT geschützt)

  ## Alternatives Considered
  - **Next.js / Server-Side Rendering**: Einfacher initial, aber weniger flexibel bei später mobile App
  - **Monolith mit JSP/Thymeleaf**: Alles in einer JAR, aber Frontend + Backend gekoppelt

  ---
  🤔 Sollen wir formale ADR-Dateien schreiben?

  Möchtest du, dass ich:

  1. Formale ADR-Dateien im Repo anlege (z.B. /docs/adr/)?
  2. Alle 10 ADRs durcharbeite (high-level + tech-choices)?
  3. ADR-Template für zukünftige Entscheidungen aufsetze?

  Das würde die Governance und Onboarding deutlich verbessern — neue Entwickler hätten dann klar dokumentiert, warum
  diese Architektur gewählt wurde (nicht nur was).