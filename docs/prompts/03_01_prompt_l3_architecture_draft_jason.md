Du bist ein erfahrener Software-Architekt.

Hier sind alle unsere heutigen Ergebnisse:
- Projekt: aus @CLAUDE.md
- Top-3 QAs: 
    - **Security** schützt davor, dass uns die Zielgruppe (Marc) vertraut.
    - **Usability** sorgt dafür, dass die Zielgruppe (Lara) überhaupt zum Vertrauenstest kommt.
    - **Reliability** sorgt dafür, dass das Vertrauen über die Zeit hält.
- C1 System Context: @03_01_prompt_c1_system_ctx_diagramm_jason.md
- C2 Container Diagram: @03_01_prompt_c2_container_diagramm_daniel.md
- ADRs: 
    - Technologie: ADR-1, ADR2 & ADR-5
    - Pattern: ADR-0

Erstelle daraus einen strukturierten Architecture Draft.
Identifiziere: Was ist noch unklar oder widersprüchlich?
Was sind die 3 wichtigsten Fragen die wir in Tag 2 klären müssen?

-----

## Resultat

# BudgetBuddy — Architecture Draft (Tag 1 Synthese)

**Stand:** 2026-05-27 | **Basis:** C1/C2 Diagramme, ADR-0/1/2/5, QA-Trias

---

## 1. Architektur-Überblick

### Entscheidungskern

Drei Container, ein Deploy-Artefakt — Komplexität bewusst klein:

```
Browser
  │  HTTPS + JWT Bearer
  ▼
┌─────────────────────────────────────────────────────┐
│  Web SPA  [Angular 21, TypeScript]                  │
│  Onboarding · PDF-Upload · Dashboard · Korrekturen  │
└──────────────────┬──────────────────────────────────┘
                   │ REST/JSON (gleicher Host → kein CORS-Problem im Prod)
                   ▼
┌─────────────────────────────────────────────────────┐
│  API Application  [Spring Boot 3.5 / Java 21, JAR]  │
│  Auth (JJWT/bcrypt) · PDF-Parser (PDFBox 3.x)       │
│  Categorizer (Lookup → Claude Haiku fallback)        │
│  Safe-to-Spend-Calc · Monthly-Report (Sonnet 4)     │
└──────────────────┬──────────────────────────────────┘
                   │ JDBC in-process
                   ▼
┌─────────────────────────────────────────────────────┐
│  Database  [SQLite 3.x + Flyway]                    │
│  Users · Transactions · FixedCosts ·                │
│  SavingsGoals · CategoryLookup                      │
└─────────────────────────────────────────────────────┘
                            │
                   HTTPS/Anthropic SDK
                            ▼
               [ Anthropic Claude API ]  ← externer Call
               Haiku: Kategorisierung
               Sonnet 4: Monatsbericht
```

### QA-Architektur-Mapping

| QA | Wo verankert | Konkrete Massnahme |
|---|---|---|
| **Security** (Marc vertraut) | API Container | Spring Security 6.5, bcrypt ≥12, JWT HS256, Input-Validation, Rate-Limiting |
| **Usability** (Lara kommt an) | Web SPA | Angular Signals + Reactive Forms, OnPush, Single-Page ohne Reload-Friction |
| **Reliability** (Vertrauen hält) | DB + API | Flyway-Migrationen, ACID-Transaktionen, BigDecimal, Claude-Fallback = `Sonstiges` |

---

## 2. Widersprüche und Unklarheiten

### Kritisch (blockiert Implementierung)

**W-1: JWT Token Storage — Widerspruch im C2-Dokument**

Das C2-Mapping sagt gleichzeitig:
- `"kein Token in localStorage (HttpOnly)"` → impliziert **httpOnly Cookie**
- `"JWT Bearer (Authorization-Header)"` → impliziert **JavaScript-Zugriff** auf Token

Das sind zwei inkompatible Ansätze. Ein httpOnly Cookie ist kein Bearer-Header. ADR-7 sagt "Logout = Client löscht Token" — was wiederum client-seitigen Zugriff impliziert (localStorage/sessionStorage).


### Strukturell (beeinflusst Tag-2-Entscheide)

**W-2: SPA-Hosting-Strategie — Dev vs. Prod divergieren still**

- ADR-0: CORS konfiguriert für `localhost:4200` (Dev-Separation)
- C2: SPA läuft vom gleichen Spring-Boot-Host (Prod-Bündelung, kein CORS)
- Das ist technisch konsistent, aber nirgends explizit als zwei Phasen dokumentiert. Führt sonst zu Verwirrung beim Deployment.

**W-3: Hosting-Plattform und nDSG-Konformität offen**

Git-History zeigt "Render" als Ziel-Plattform. Render hat keine CH-Region (nächste: Frankfurt/EU). Für Marcs Vertrauen ("warum meine Daten einer Web-App geben?") ist "läuft in der Schweiz" oder mindestens "DSGVO-konform in der EU" ein Trust-Signal — aber nirgends entschieden.

**W-4: PII-Transfer zu Anthropic API**

Transaktionstext wie `"DIGITEC GALAXUS AG 044 913 2323"` verlässt beim Claude-Call unsere Vertrauenszone. Das C1-Dokument stellt die Frage ("werden Empfänger pseudonymisiert?") — aber keine Antwort existiert. Für die nDSG-Compliance (Auftragsverarbeitung) braucht es mindestens einen Data Processing Agreement (DPA) mit Anthropic.

**W-5: Fehlende Akteure im C1 (noch nicht adressiert)**

Aus dem C1-"Was haben wir vergessen?"-Abschnitt ist noch kein Folge-Diagramm entstanden: Admin/Operator, Backup-Ziel, Monitoring/Logging, E-Mail-Provider. Das sind echte System-Komponenten mit Sicherheitsrelevanz.

---

## 3. Die 3 wichtigsten Fragen für Tag 2

### Frage 1: Wo lebt der JWT-Token? — Security-Fundament

**Warum kritisch:** Diese Entscheidung prägt den gesamten Auth-Flow, CORS-Config, Logout-Semantik und XSS-Risiko. Sie muss vor dem ersten Code-Commit stehen.

| Option | Mechanismus | Vorteil | Risiko |
|---|---|---|---|
| **A) httpOnly Cookie** | Browser hält Cookie; kein JS-Zugriff | XSS-sicher; Anthropic-Best-Practice | SameSite + CORS komplexer; CSRF-Schutz nötig |
| **B) sessionStorage** | JS schreibt/liest; Tab-lokal | Bearer-Header trivial; Tab-close = Logout | XSS exponiert Token; kurzlebiger als nötig |
| **C) localStorage** | JS schreibt/liest; persistent | Bearer-Header trivial | XSS exponiert Token dauerhaft — für Finanzdaten inakzeptabel |

**Empfehlung zur Diskussion:** Option A (httpOnly Cookie) ist für Finanzdaten die defensiv korrekte Wahl — auch wenn sie CSRF-Mitigation erfordert. Option B ist ein akzeptabler Kompromiss, wenn XSS durch strikte CSP mitigiert wird.

---

### Frage 2: Wo läuft BudgetBuddy, und gilt das für Marc als vertrauenswürdig? — Hosting + nDSG

**Warum kritisch:** Marcs Kernfriction ist Datenschutz-Skepsis. "Wir speichern in der Schweiz" oder "DSGVO-konform (EU)" ist ein konkretes Trust-Signal im Onboarding — wenn wir es nicht halten können, sollten wir es nicht versprechen.

Zu klären:
- Render Frankfurt → EU-DSGVO ✓, aber nicht nDSG-spezifisch (CH) — reicht das?
- Alternative: Exoscale/Nine.ch (CH-Region) — mehr Aufwand, stärkeres Trust-Signal
- Backup-Strategie für SQLite auf dem Hosting-Provider?
- Wer ist "Admin/Operator" mit privilegiertem Datenzugriff, und wie wird das nDSG-Lösch-Recht eingelöst?

---

### Frage 3: Was geht an Anthropic, und unter welchem Rechtsrahmen? — PII + DPA

**Warum kritisch:** Jeder Claude-Call mit Transaktionsdaten ist eine Datenübermittlung an einen US-Dienstleister. Das ist nDSG Art. 16 (Bekanntgabe ins Ausland). Ohne DPA ist das eine Compliance-Lücke — gerade für ein Produkt, das Marcs Vertrauen auf Datenschutz aufbaut.

Zu klären:
- Hat Anthropic ein DPA-Angebot für Business-Kunden? (Ja: "Anthropic Data Processing Agreement")
- Schicken wir Roh-Transaktionstext oder pseudonymisieren wir vorher (z.B. Betrag + normalisierter Händlertyp)?
- Was genau braucht Haiku 3.5 um zu kategorisieren — braucht es den vollen Originaltext oder reicht eine bereinigte Version?
- Consent in den App-Nutzungsbedingungen explizit erwähnen?

---

## 4. Empfohlene Tag-2-Agenda (30 min)

| Slot | Thema | Output |
|---|---|---|
| 10 min | JWT Token Storage — Entscheid A/B/C | ADR-7 vervollständigen |
| 10 min | Hosting-Plattform und nDSG-Position | ADR-10 (neu) Hosting-Entscheid |
| 10 min | PII-Policy für Claude-Calls + DPA-Vorgehen | Eintrag in ADR-6 (Kategorisierung) ergänzen |

---

**Was sich bestätigt und nicht nochmals diskutiert werden muss:** 3-Container-Monolith (kein Microservice), SQLite für MVP, PDFBox 3.x, BigDecimal für Geld, hybride Kategorisierung mit Fallback `Sonstiges` — diese Entscheide sind solide begründet und konsistent über alle Dokumente.

