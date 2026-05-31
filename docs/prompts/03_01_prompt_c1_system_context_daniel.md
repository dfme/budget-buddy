# C4 System Context Diagram — BudgetBuddy (Iteration 2)

**Projekt:** BudgetBuddy
**Level:** C4 Level 1 — System Context
**Stand:** 2026-05-27

---

## Prompt

> Erstelle ein C4 System Context Diagram für unser Projekt.
>
> **Projektname:** BudgetBuddy
> **Beschreibung:**
> **BudgetBuddy** ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die ihnen durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben gibt. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an — damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können. Durch gezielte, lebensnahe Sparvorschläge hilft BudgetBuddy jungen Menschen, finanzielle Kontrolle zu gewinnen und erste Rücklagen aufzubauen.
>
> Zeige:
> - Unser System (als Hauptelement in der Mitte)
> - Alle Nutzertypen die damit interagieren
> - Alle externen Systeme (Auth, APIs, PDF einlesen, Bankverbindung)
> - Wie sie interagieren (kurze Beschriftung der Pfeile)
>
> Beschreibe das Diagram als Text mit klaren Boxes und Pfeilen.
> Dann: Was haben wir vergessen?

---

## Resultat

Diese Iteration zieht die externen Systeme, die im Prompt explizit genannt sind (**Auth, APIs, PDF einlesen, Bankverbindung**), klar als eigene Boxen heraus.

### Diagramm (ASCII)

```
   ┌─────────────────────┐                                      ┌─────────────────────┐
   │     Lara (22)       │                                      │      Marc (25)      │
   │     [Person]        │                                      │     [Person]        │
   │   Studentin BE      │                                      │  Junior-Verkäufer ZH│
   └──────────┬──────────┘                                      └──────────┬──────────┘
              │                                                            │
              │ PDF hochladen,                                Sparziel, KI-│
              │ Safe-to-Spend                                Bericht, Konto│
              │ ansehen, Kategorien                          verwalten     │
              │ korrigieren (HTTPS)                                (HTTPS) │
              ▼                                                            ▼
   ┌────────────────────────────────────────────────────────────────────────────────┐
   │                                                                                │
   │                              BudgetBuddy                                       │
   │                          [Software System – wir]                               │
   │                                                                                │
   │   Angular SPA · Spring Boot 3.5 · SQLite · JWT (HS256) ·                       │
   │   PDF-Parsing im Speicher · Hybride Kategorisierung · Safe-to-Spend            │
   │                                                                                │
   └──┬───────────────┬──────────────────┬───────────────────┬─────────────────────┘
      │               │                  │                   │
      │ verifiziert   │ sendet           │ liest PDF-        │ ruft Konto­
      │ Login / hasht │ unbekannte       │ Inhalt (im        │ bewegungen ab
      │ Passwort      │ Transaktionen,   │ Speicher,         │ (PSD2 / Swiss
      │ (bcrypt) /    │ generiert        │ wird nicht        │ Open Banking,
      │ JWT signieren │ Monatsbericht    │ persistiert)      │ Post-MVP)
      │ (HMAC)        │ (HTTPS / API)    │ (lokale Lib)      │ (mTLS / API)
      ▼               ▼                  ▼                   ▼
   ┌──────────────┐ ┌────────────────┐ ┌─────────────────┐ ┌─────────────────────┐
   │ Auth /       │ │ Anthropic      │ │ PDF-Engine      │ │ OpenBanking API     │
   │ Identity     │ │ Claude API     │ │ (Apache PDFBox  │ │ (z. B. SIX b.Link / │
   │ [intern,     │ │ [Ext. System]  │ │  3.x)           │ │ FinTech-S API)      │
   │  eigener     │ │                │ │ [Bibliothek,    │ │ [Ext. System]       │
   │  Service]    │ │ Haiku 3.5 für  │ │  Inproc]        │ │                     │
   │              │ │ Kategorien;    │ │                 │ │ Status: Could,      │
   │ E-Mail +     │ │ Sonnet 4 für   │ │ Extrahiert      │ │ Post-MVP            │
   │ bcrypt-Hash  │ │ Monatsbericht  │ │ Datum/Betrag/   │ │                     │
   │              │ │                │ │ Empfänger aus   │ │ Liefert Trans-      │
   │              │ │                │ │ Schweizer Bank- │ │ aktionen direkt     │
   │              │ │                │ │ PDFs            │ │ (kein PDF nötig)    │
   └──────────────┘ └────────────────┘ └────────┬────────┘ └──────────┬──────────┘
                                                │                     │
                                                │ braucht als         │ ruft direkt
                                                │ Eingabe das         │ ab bei
                                                │ exportierte PDF     │
                                                ▼                     ▼
                                       ┌────────────────────────────────────────┐
                                       │     Schweizer Banken                   │
                                       │     [Ext. System]                      │
                                       │     UBS · Raiffeisen · PostFinance     │
                                       │                                        │
                                       │  • PDF-Export via E-Banking            │
                                       │    (User macht das manuell — MVP)      │
                                       │  • OpenBanking-Endpoints               │
                                       │    (Direktanbindung — Post-MVP)        │
                                       └────────────────────────────────────────┘
```

### Legende

| Symbol               | Bedeutung                                                              |
| -------------------- | ---------------------------------------------------------------------- |
| `[Person]`           | Menschlicher Nutzer                                                    |
| `[Software System]`  | Eigenes System (BudgetBuddy)                                           |
| `[Ext. System]`      | Externes System, das wir nutzen                                        |
| `[Bibliothek]`       | Lokal eingebundene Komponente — kein Netzwerk-Call                     |
| Durchgezogener Pfeil | Aktiv im MVP                                                           |
| Gestrichelt          | Post-MVP (OpenBanking)                                                 |

### Beziehungen kompakt

| Von                 | Nach              | Was passiert                                                       | Kanal           | Phase    |
| ------------------- | ----------------- | ------------------------------------------------------------------ | --------------- | -------- |
| Lara                | BudgetBuddy       | Lädt PDF hoch, sieht Safe-to-Spend, korrigiert Kategorien          | HTTPS / Browser | MVP      |
| Marc                | BudgetBuddy       | Sparziel anlegen, KI-Bericht lesen, Datenschutz-Aktionen           | HTTPS / Browser | MVP      |
| BudgetBuddy         | Auth/Identity     | Passwort hashen (bcrypt), JWT signieren/validieren                 | in-process      | MVP      |
| BudgetBuddy         | Anthropic Claude  | Kategorisiert unbekannte Transaktionen / generiert Monatsbericht   | HTTPS / API     | MVP      |
| BudgetBuddy         | PDFBox (Lib)      | Liest hochgeladenes PDF im Speicher, extrahiert Transaktionen      | in-process      | MVP      |
| PDFBox              | Schweizer Banken  | Konsumiert das vom User aus dem E-Banking exportierte PDF          | außerhalb       | MVP      |
| BudgetBuddy         | OpenBanking API   | Holt Transaktionen direkt aus dem Bankkonto (statt PDF-Workflow)   | mTLS / API      | Post-MVP |
| OpenBanking API     | Schweizer Banken  | OAuth/Consent-Flow zum Konto des Users                             | TLS / Token     | Post-MVP |
| Schweizer Banken    | Lara / Marc       | Stellen Kontoauszüge im E-Banking als PDF zum Download bereit      | außerhalb       | MVP      |

### Akteure im Detail

#### Personen
- **Lara (22), Studentin Bern** — primäre Nutzerin; Friction: „erstes PDF darf nicht mühsam sein".
- **Marc (25), Junior-Verkäufer Zürich** — primärer Nutzer; Friction: „warum soll ich meine Finanzdaten einer Web-App geben?".

#### Eigenes System
- **BudgetBuddy** — Angular SPA + Spring Boot 3.5; PDF-Parsing im Speicher, hybride Kategorisierung, Safe-to-Spend-Berechnung.

#### Externe / interne Systeme rund herum
- **Auth/Identity** — bewusst als eigene Box dargestellt, auch wenn sie im MVP **intern** läuft (Spring Security + bcrypt + JJWT-Signing). So bleibt der Pfad sichtbar, an dem später ein externer IdP (Google/Apple SSO) andocken könnte.
- **Anthropic Claude API** — externes LLM für Kategorisierung (Haiku 3.5) und Monatsbericht (Sonnet 4); Fallback `Sonstiges` bei Ausfall.
- **PDFBox 3.x** — Bibliothek, kein Netzwerk-Call; im Diagramm trotzdem als Box, weil sie der einzige Pfad ist, wie Transaktionsdaten ins System kommen.
- **Schweizer Banken** — Quelle der Daten; im MVP nur indirekt (PDF-Export durch den User).
- **OpenBanking API** — Post-MVP-Pfad als gestrichelte Linie; macht die spätere Umstellung „PDF → direkte Anbindung" sichtbar, damit das Team weiß, wo der Wachstumspfad hingeht.

---

## Was haben wir vergessen?

### Akteure, die im Bild fehlen

1. **Admin / Operator** — wer macht Backups, Account-Löschungen, Incident-Response? Bei Finanzdaten ist privilegierter Zugriff selbst ein Akteur mit eigenen Vertrauenspflichten.
2. **Hosting-Plattform (CH-Region)** — für Marcs nDSG-Vertrauen relevant; „läuft in der Schweiz" ist Teil des Kontexts, nicht nur Infra-Detail.
3. **Backup-Ziel** — die SQLite-DB muss irgendwohin gesichert werden; die 30-Tage-Retention aus ADR-006 (nDSG-Löschung) wird hier eingelöst oder gebrochen.
4. **Monitoring / Logging** — Sentry/Grafana o. ä. Ohne das merken wir nicht, dass die PDF-Pipeline für Raiffeisen plötzlich kaputt ist.
5. **TLS-/Zertifikats-CA** (z. B. Let's Encrypt) — externer Dienst, der HTTPS überhaupt möglich macht.
6. **E-Mail-Provider** — für nDSG-Lösch-Bestätigung und optionalen Monatsbericht-Versand; in dieser Iteration herausgekürzt, gehört aber rein, sobald US-9 (E-Mail-Opt-in) aktiv ist.

### Was als „eigene Box" diskutabel ist

7. **Auth als eigene Box, obwohl intern** — Pro: macht zukünftige Externalisierung sichtbar (z. B. Keycloak/Cognito/SSO). Contra: streng nach C4-Definition gehört das in Level 2 (Container), nicht Level 1. Bewusst gelassen, weil im Prompt „Auth" als externes System verlangt wurde — würde bei einer formalen Architektur-Review wieder rausgenommen.
8. **PDFBox als eigene Box, obwohl Library** — selbe Logik: zeigt den Datenpfad „PDF rein", schafft aber den Eindruck eines eigenen Service. Korrekt wäre, PDFBox als Container im Level-2-Diagramm zu führen und auf Level 1 nur „Schweizer Banken (PDF)" zu zeigen.

### Fehlende Annotationen / Risiken

9. **Datenklassifizierung an Außenkanten** — der heißeste Pfeil ist BudgetBuddy → Claude API: hier verlassen Empfängernamen + Beträge unsere Vertrauenszone. Sollte mit `[PII]` markiert sein, plus Notiz „werden Empfänger vor dem Senden pseudonymisiert?" — heute ist die Antwort noch offen.
10. **Initiator pro Pfeil** — Claude-Calls passieren nur Request/Response getrieben durch User-Aktion; nirgends ein Cron-Job. Sollte am Pfeil stehen, damit niemand einen Background-Worker vermutet.
11. **Off-System-Flow Bank → User** — der manuelle PDF-Export ist eine User-Reibung (Laras Hürde!), aber im Diagramm aktuell nur als normaler Pfeil. Gestrichelt + „manuell, außerhalb von BudgetBuddy" macht ehrlich, woher die Churn-Gefahr aus dem Risikoregister kommt.
12. **OpenBanking-Consent-Pfad** — wenn OpenBanking aktiv wird, kommt eine OAuth-Redirect-Schleife (User → Bank → zurück zu BudgetBuddy) dazu. Das ist ein eigener Pfeil, der heute fehlt und in der nächsten Iteration eingezeichnet werden sollte.

### Vorschlag für die nächste Iteration

Hinzufügen: **Admin/Operator**, **Hosting-Plattform (CH)**, **Backup-Ziel**, **Monitoring**, **E-Mail-Provider**. Pfeil BudgetBuddy↔Claude mit `[PII]` taggen. Den OpenBanking-Consent-Flow als zweiten Pfeil sichtbar machen. **Auth** und **PDFBox** beim Wechsel auf C4 Level 2 in das Container-Diagramm verschieben.
