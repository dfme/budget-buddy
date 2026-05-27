# C4 Container Diagram — BudgetBuddy (Level 2)

**Projekt:** BudgetBuddy
**Level:** C4 Level 2 — Container
**Stand:** 2026-05-27
**Team:** 2–3 Entwickler · 3 Monate · gemischter Background

---

## Prompt

> Erstelle jetzt ein C4 Container Diagram für unser Projekt.
>
> Basierend auf unseren Quality Attributes aus Übung 1:
> - **Security** schützt davor, dass uns die Zielgruppe (Marc) vertraut.
> - **Usability** sorgt dafür, dass die Zielgruppe (Lara) überhaupt zum Vertrauenstest kommt.
> - **Reliability** sorgt dafür, dass das Vertrauen über die Zeit hält.
>
> Und unserer Systemübersicht aus C1: `c1_system_context_iteration2_daniel.md`
>
> Fragen:
> 1. Welche Container brauchen wir? (Web App, API, DB, Cache?)
> 2. Welche Technologien passen zu unserem Team und QAs?
> 3. Wie kommunizieren die Container miteinander?
> 4. Begründe jeden Container in einem Satz.
>
> Unser Team: 2–3 Devs, 3 Monate, gemischter Background.

---

## Resultat

### Leitprinzip für ein 2–3-Dev-Team über 3 Monate

**Monolith schlägt Microservices.** Jeder zusätzliche Container kostet Deployment-, Netzwerk- und Debugging-Komplexität — Aufwand, den wir besser in den Aha-Moment für Lara stecken. Die folgenden 3 Container sind das **Minimum**, das die drei Quality Attributes ehrlich erfüllt; alles darüber müsste seinen Mehrwert beweisen.

---

### 1. Welche Container brauchen wir?

| #  | Container             | Technologie                          | Verantwortung                                                            | Pflicht? |
| -- | --------------------- | ------------------------------------ | ------------------------------------------------------------------------ | :------: |
| C1 | **Web SPA**           | Angular 21 (TypeScript)              | UI im Browser: Onboarding, PDF-Upload, Dashboard, Safe-to-Spend, Korrekturen | Ja  |
| C2 | **API Application**   | Spring Boot 3.5 (Java 21), Single-JAR| REST-API, Auth, PDF-Parsing, Kategorisierung, Safe-to-Spend-Berechnung, KI-Bericht | Ja |
| C3 | **Database**          | SQLite 3.x (Datei) + Flyway          | Persistente Speicherung Users, Transaktionen, Fixkosten, Sparziele, Lookup-Tabelle | Ja |
| —  | ~~Cache~~             | (nicht eigener Container)            | In-Process LRU/Caffeine im API-Container reicht — siehe „Was wir bewusst weglassen" | Nein |
| —  | ~~Message Queue~~     | (nicht eigener Container)            | Keine Async-Workflows im MVP                                              | Nein |
| —  | ~~Eigener KI-Worker~~ | (nicht eigener Container)            | Claude-Calls laufen synchron aus C2 — kein eigener Worker nötig          | Nein |

**Externe Systeme** (aus C1 übernommen, kein eigener Container, aber als Außenkante relevant):
**Anthropic Claude API** · **Schweizer Banken** (indirekt via PDF-Export) · **OpenBanking API** (Post-MVP, gestrichelt).

---

### 2. Technologien — Begründung pro Schicht

Die Tech-Stack-Entscheidungen sind durch ADR-001/002/003 bereits getroffen. Hier die **container-spezifische** Begründung im Licht der drei QAs:

| Container        | Technologie               | Security                                                                    | Usability                                                                  | Reliability                                                              |
| ---------------- | ------------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Web SPA          | Angular 19 + Signals      | Functional HTTP-Interceptor hängt JWT zuverlässig an jeden Request          | Reactive Forms + Signals → schneller, formularstarker Onboarding-Wizard    | OnPush-Change-Detection → kein UI-Flackern, klares Verhalten unter Last  |
| API Application  | Spring Boot 3.5 + Java 21 | Spring Security `oauth2ResourceServer().jwt()`, bcrypt — Industriestandard  | Springdoc OpenAPI → SPA-Team kann gegen klaren Contract bauen              | LTS-Plattform, breites Ökosystem, deterministisches Verhalten            |
| Database         | SQLite + Flyway           | Single-File-DB ist leicht zu löschen (nDSG); kein Netzwerk-Angriffsvektor   | Setup-Zeit Null → Devs bauen Features statt DB zu konfigurieren           | Flyway-Migrationen garantieren konsistentes Schema über alle Umgebungen  |

**Innerhalb des API-Containers** (logische Module, kommen erst in C3 als Components zum Tragen):
Auth-Modul (JJWT + bcrypt) · PDF-Parser (Apache PDFBox 3.x) · Categorizer (Lookup-Tabelle + Anthropic Java SDK) · Safe-to-Spend-Calculator · Monthly-Report-Generator.

---

### 3. Wie kommunizieren die Container?

```
   Browser (Lara, Marc)
        │
        │ HTTPS · JSON · JWT Bearer (Authorization-Header)
        │ statische Assets (HTML/JS/CSS) via gleichem Host
        ▼
   ┌─────────────────────┐                          ┌────────────────────┐
   │     Web SPA         │                          │  Anthropic Claude  │
   │   [Container C1]    │                          │   [Ext. System]    │
   │   Angular 19        │                          └─────────▲──────────┘
   │   Browser-seitig    │                                    │
   └──────────┬──────────┘                                    │ HTTPS / API
              │                                                │ Anthropic Java SDK
              │ REST: HTTPS / JSON                             │ (synchron, vom User-Call getrieben)
              │ Auth: JWT HS256 im Authorization-Header        │
              │                                                │
              ▼                                                │
   ┌─────────────────────────────────────────────┐             │
   │             API Application                 │─────────────┘
   │              [Container C2]                 │
   │           Spring Boot 3.5 / Java 21         │
   │           Single-JAR, synchron (MVC)        │
   │                                             │──────────►  Schweizer Banken
   │  Auth · PDF-Parser · Categorizer ·          │             (indirekt: PDF aus
   │  Safe-to-Spend · Monthly-Report             │              E-Banking, User-getrieben)
   └──────────┬──────────────────────────────────┘
              │
              │ JDBC (xerial sqlite-jdbc)
              │ JPA / Hibernate · BigDecimal für Geld
              │ in-process · lokale Datei
              ▼
   ┌─────────────────────┐
   │     Database        │
   │   [Container C3]    │
   │   SQLite 3.x        │
   │   + Flyway          │
   └─────────────────────┘
```

### Kommunikations-Matrix

| Von             | Nach             | Protokoll/Kanal                | Datenformat | QA-Treiber                            |
| --------------- | ---------------- | ------------------------------ | ----------- | ------------------------------------- |
| Browser         | Web SPA          | HTTPS (statische Assets)       | HTML/JS/CSS | Security (TLS)                        |
| Web SPA         | API Application  | HTTPS REST + JWT Bearer        | JSON        | Security (Auth) · Usability (latenz-arm) |
| API Application | Database         | JDBC (in-process)              | SQL         | Reliability (Transaktionen, ACID)     |
| API Application | Claude API       | HTTPS (Anthropic Java SDK)     | JSON        | Usability (Auto-Kategorisierung)      |
| API Application | E-Mail-Provider  | SMTP oder REST (Post-MVP)      | JSON/MIME   | Usability (Benachrichtigungen)        |

---

### 4. Begründung pro Container — je ein Satz

1. **Web SPA (Angular):** Wir trennen UI von Backend, weil Lara einen formularreichen, latenz-armen Onboarding- und Korrektur-Flow braucht, den ein klassisches Server-Rendering nicht in der Qualität liefert — Angular's Signals + Reactive Forms zahlen direkt auf Usability ein.

2. **API Application (Spring Boot Monolith):** Wir bündeln alle Backend-Verantwortlichkeiten in **einem** deployable JAR, weil ein 2–3-Personen-Team in 3 Monaten weder Microservice-Infrastruktur noch verteiltes Debugging stemmen kann — Spring Security + bcrypt + JJWT geben Marc den Security-Standard, ohne den er die App nicht anfasst.

3. **Database (SQLite + Flyway):** Wir wählen SQLite, weil die einzelne Datei nDSG-Löschungen trivial macht (Security) und das Setup-Null-Overhead dem Team Zeit für Features verschafft (Usability) — Flyway sorgt für reproduzierbares Schema-Verhalten über alle Umgebungen (Reliability).

---

### Mapping Container → Quality Attribute

| Container       | Security                                                         | Usability                                                        | Reliability                                                       |
| --------------- | ---------------------------------------------------------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------- |
| Web SPA         | JWT-Interceptor, CSP, kein Token in localStorage (HttpOnly)      | Reactive Forms, Signals, schneller First-Paint                   | OnPush, kein Memory-Leak-anfälliges Two-Way-Binding                |
| API Application | Spring Security, bcrypt(≥12), Input-Validation, Rate-Limit-Bucket| OpenAPI-Contract, klare Fehlermeldungen pro Feld                 | Idempotente Endpoints, Transaktionen, Health-Endpoint              |
| Database        | Single-File, leicht zu hard-deleten, kein Netzwerk-Vektor        | Null-Setup-Zeit für Devs                                         | Flyway-Migrationen, ACID, BigDecimal für Geld                      |

---

### Was wir bewusst weglassen — und warum

Diese Liste ist genauso wichtig wie die Container-Liste, weil sie zeigt, dass wir die Komplexität **bewusst** klein halten.

| Nicht-Container         | Warum nicht?                                                                                                            | Wann reinnehmen?                                                  |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| **Redis / Cache**       | Lookup-Tabelle und JWT-Validierung laufen in-process (Caffeine LRU); ein separater Cache ist Infrastruktur ohne MVP-Nutzen | Erst wenn SQLite-Reads zum Hotspot werden (klar messbar)        |
| **Message Queue**       | Keine asynchrone Workflows im MVP — Claude-Calls laufen synchron im Request-Thread                                       | Wenn der Monatsbericht > 30 s dauert oder Batches nötig werden    |
| **Eigener KI-Worker**   | Synchrone Calls in C2 reichen; eine Worker-Instanz wäre Overengineering                                                  | Wenn API-Latenz unter Last leidet oder Retries komplexer werden   |
| **CDN / Static Hosting**| Angular-Build wird vom Spring-Boot-Container als statische Assets ausgeliefert — eine Origin spart CORS-Konfig            | Wenn echte Skalierung oder geografisch verteilte User auftauchen  |
| **Microservices**       | Verteilte Auth-, Transaktions- und Kategorisierungs-Services würden 80 % der Projektzeit in DevOps versenken              | Frühestens nach erfolgreichem MVP **und** echtem Skalierungsdruck |
| **Separate Auth-Server**| JJWT-Signing läuft in C2; ein dedizierter Identity-Provider (Keycloak/Auth0) ist nicht nötig                              | Wenn SSO mit Google/Apple kommt oder mehrere Apps auf denselben User-Pool zugreifen |
| **Separate Read-Replica**| Bei MVP-Load völlig unnötig; SQLite serialisiert sowieso                                                                | Bei Migration auf PostgreSQL, falls Schreiblast steigt           |

---

### Risiken am Container-Schnitt

| Risiko                                                                                                | Mitigation                                                                                                  |
| ----------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| API-Container hat zu viele Verantwortlichkeiten (Auth + Parsing + KI + Calc + Report) — wird zur Big-Ball-of-Mud | Klarer **Package-by-Feature** Schnitt; jedes Modul (auth, transactions, categorization, …) ist ein eigenes Spring-Boot-Package mit eigenem Service-Interface — vorbereitet für späteren Extract |
| Synchroner Claude-Call blockiert Request-Thread bei API-Timeout                                       | Pro Call konfiguriertes Timeout (≤ 5 s) + Fallback `Sonstiges`; Threadpool-Größe bewusst dimensionieren     |
| SQLite-Writer-Lock unter parallelen PDF-Uploads                                                       | Im API-Container Uploads serialisieren (Semaphore pro User) — kein zweiter Container nötig                  |
| SPA + API auf demselben Host → kein klares CORS, dafür dichte Kopplung beim Deployment                | Für MVP gewollt (weniger CORS-Fehler); bei späterer Trennung dokumentierter Cut-over-Plan                   |

---

### Entscheidungs-Zusammenfassung

| Frage aus dem Prompt                              | Antwort                                                                                                              |
| ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Welche Container brauchen wir?                    | **3:** Web SPA, API Application (Monolith), SQLite-Datenbank. Kein Cache, keine Queue, keine Microservices im MVP.   |
| Welche Technologien passen zu Team und QAs?       | Angular 19, Spring Boot 3.5 + Java 21, SQLite + Flyway — alle LTS/Standard, breite Doku-Basis für gemischtes Team.   |
| Wie kommunizieren die Container?                  | SPA↔API via HTTPS REST + JWT Bearer · API↔DB via JDBC in-process · API↔Claude via HTTPS Anthropic SDK.               |
| Pro Container 1 Satz Begründung                   | Siehe Abschnitt „Begründung pro Container — je ein Satz" oben.                                                       |
