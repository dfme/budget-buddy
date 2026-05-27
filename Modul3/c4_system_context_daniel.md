# C4 System Context Diagram — BudgetBuddy

**Projekt:** BudgetBuddy
**Level:** C4 Level 1 — System Context
**Stand:** 2026-05-27

---

## Projektbeschreibung

**BudgetBuddy** ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die ihnen durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben gibt. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an — damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können. Durch gezielte, lebensnahe Sparvorschläge hilft BudgetBuddy jungen Menschen, finanzielle Kontrolle zu gewinnen und erste Rücklagen aufzubauen.

---

## Diagramm (ASCII)

```
                                  ┌────────────────────┐
                                  │      Lara (22)     │
                                  │     [Person]       │
                                  │   Studentin BE     │
                                  └─────────┬──────────┘
                                            │
                                            │ Lädt Bank-PDF hoch,
                                            │ sieht Safe-to-Spend,
                                            │ korrigiert Kategorien
                                            │ (HTTPS, Browser)
                                            ▼
   ┌────────────────────┐         ┌────────────────────────────┐         ┌─────────────────────┐
   │    Marc (25)       │ Erfasst │                            │ Sendet  │  Anthropic          │
   │    [Person]        │ Sparziel│      BudgetBuddy           │ Kategor.│  Claude API         │
   │ Junior-Verkäufer ZH├────────►│      [Software System]     ├────────►│  [Ext. System]      │
   │                    │ liest   │                            │ Anfrage │  Haiku 3.5 (Cat.)   │
   └────────────────────┘ KI-Beri.│ Webapp: PDF-Import,        │         │  Sonnet 4 (Report)  │
                          (HTTPS) │ Auto-Kategorisierung,      │◄────────┤                     │
                                  │ Safe-to-Spend, Monatsber.  │ Liefert │                     │
                                  │                            │ Kategor.│                     │
                                  │ Angular SPA                │ + Bericht                     │
                                  │ Spring Boot 3.5            │         └─────────────────────┘
                                  │ SQLite + Flyway            │
                                  │ JWT (HS256) Auth           │
                                  └──┬────────────────┬────────┘
                                     │                │
                                     │ Versendet      │ Liest (extrahiert
                                     │ optionale      │  im Speicher,
                                     │ E-Mail-Benach- │  speichert nichts)
                                     │ richtigung     │
                                     │ (SMTP/API)     │ PDF-Datei (lokal
                                     ▼                │  vom User-Gerät)
                          ┌──────────────────────┐    │
                          │  E-Mail-Provider     │    │
                          │  [Ext. System]       │    │     ┌─────────────────────────┐
                          │  z. B. Mailgun /     │    └────►│  Schweizer Banken       │
                          │  Postmark / SMTP     │          │  [Ext. System – indirekt]│
                          │                      │          │  UBS, Raiffeisen,       │
                          │  Transaktionale      │          │  PostFinance            │
                          │  Mails (opt-in)      │          │                         │
                          └──────────────────────┘          │  Stellen Kontoauszüge   │
                                                            │  als PDF bereit (vom    │
                                                            │  User exportiert)       │
                                                            └─────────────────────────┘
```

---

## Legende

| Symbol               | Bedeutung                                                              |
| -------------------- | ---------------------------------------------------------------------- |
| `[Person]`           | Menschlicher Nutzer                                                    |
| `[Software System]`  | Eigenes System (BudgetBuddy)                                           |
| `[Ext. System]`      | Externes System, das wir nutzen oder mit dem wir indirekt interagieren |
| Pfeil                | Datenfluss; Beschriftung sagt **was** und **wie** (Protokoll/Kanal)    |

---

## Beziehungen kompakt

| Von                | Nach              | Beschreibung                                                   | Kanal         |
| ------------------ | ----------------- | -------------------------------------------------------------- | ------------- |
| Lara               | BudgetBuddy       | PDF-Upload, Dashboard, Kategorien-Korrektur                    | HTTPS/Browser |
| Marc               | BudgetBuddy       | Sparziel anlegen, KI-Monatsbericht lesen, Konto verwalten      | HTTPS/Browser |
| BudgetBuddy        | Anthropic Claude  | Sendet unbekannte Transaktionen / Monatsdaten zur Verarbeitung | HTTPS / API   |
| Anthropic Claude   | BudgetBuddy       | Liefert Kategorie bzw. generierten Monatsbericht               | HTTPS / API   |
| BudgetBuddy        | E-Mail-Provider   | Optionaler Versand Monatsbericht / nDSG-Lösch-Bestätigung      | SMTP / API    |
| Schweizer Banken   | Lara / Marc       | Stellen Kontoauszüge als PDF zum Download bereit (indirekt)    | außerhalb     |

---

## Akteure im Detail

### Personen

- **Lara (22), Studentin in Bern** — primärer Nutzer. Lädt Bank-PDFs hoch, sieht Safe-to-Spend, korrigiert Kategorien.
- **Marc (25), Junior-Verkäufer in Zürich** — primärer Nutzer. Legt Sparziel an, liest KI-Monatsbericht, datenschutzkritisch.

### Eigenes System

- **BudgetBuddy** — Web-App mit Angular SPA (Frontend), Spring Boot 3.5 (Backend), SQLite + Flyway (Persistenz), JWT HS256 (Auth). Verarbeitet Bank-PDFs im Speicher, persistiert nur extrahierte Transaktionen.

### Externe Systeme

- **Anthropic Claude API** — KI-Service für Transaktions-Kategorisierung (Haiku 3.5) und Monatsbericht (Sonnet 4). Wird nur bei Cache-Miss in der lokalen Lookup-Tabelle aufgerufen.
- **E-Mail-Provider** — optionaler Versand transaktionaler Mails (Monatsbericht, nDSG-Lösch-Bestätigung). User-Opt-in.
- **Schweizer Banken (UBS, Raiffeisen, PostFinance)** — indirekt: stellen Kontoauszüge als PDF bereit; der User exportiert sie und lädt sie in BudgetBuddy hoch. **Keine technische Integration im MVP.**

---

## Was haben wir vergessen? (Kritik-Pass)

Beim C4 Level 1 reicht „grob richtig" — aber für **Security/Reliability** sind ein paar Akteure relevant, die wir nicht eingezeichnet haben:

### Wahrscheinlich vergessen (sollten rein)

1. **Admin / Operator (Person):** Wer macht Backups, Logs ansehen, Account-Löschungen auf Wunsch durchführen, Incidents bearbeiten? In einer App mit Finanzdaten ist die Operator-Rolle ein eigener Akteur, weil sie privilegierten Zugriff hat — und damit auditpflichtig ist.
2. **Hosting-/Cloud-Plattform:** Wo läuft BudgetBuddy (z. B. Swisscom Application Cloud, Hetzner, AWS-Region Zürich)? Für die nDSG-Argumentation („Daten bleiben in der Schweiz") ist die Plattform Teil des Kontextes — sonst lässt sich Marcs Vertrauensfrage nicht beantworten.
3. **TLS-/Zertifikats-CA (z. B. Let's Encrypt):** Aus Security-Sicht ein externer Dienst, von dem wir abhängen — Ausfall = kein gültiges Cert = keine HTTPS-Session.
4. **Backup-Ziel (S3-kompatibel / Swiss-Backup):** Die DB-Datei muss irgendwohin gesichert werden; auch dafür gilt die 30-Tage-nDSG-Garantie aus ADR-006 — das Backup-Ziel ist also kein „Infra-Detail", sondern Teil der Vertrauenskette.
5. **Monitoring/Logging-Service (z. B. Sentry, Grafana Cloud, Loki):** Für die Reliability-Säule essenziell — wir merken sonst gar nicht, wenn Lara den Aha-Moment nicht erreicht hat.

### Bewusst weglassbar (für Level 1)

6. **OpenBanking API (z. B. SIX b.Link):** Steht in US-11 als **Could** und ist explizit Post-MVP — in der Kontextansicht für Phase 1 nicht nötig, aber als „Future External System" sinnvoll mit gestrichelter Linie.
7. **Payment-Provider:** Aktuell keine Monetarisierung im MVP — gehört nicht ins Diagramm. Wenn später Premium kommt, ja.
8. **Identity Provider (Google/Apple SSO):** US-1 löst Login mit E-Mail+Passwort; ein externer IdP ist nicht vorgesehen. Wenn das ein Vereinfachungs-Trick für Lara würde („1-Click-Login"), wäre es ein Kontext-Akteur.
9. **Push-Notification-Dienst:** US-9 spricht von „In-App-Benachrichtigung" — kein externer Push-Service nötig, solange wir kein PWA-Push machen.

### Fehlende Annotationen am Diagramm

10. **Datenklassifizierung an den Pfeilen:** Welcher Pfeil transportiert **personenbezogene Finanzdaten**? Konkret: BudgetBuddy → Claude API. Das ist die brisanteste Außenkante (Daten verlassen unsere Vertrauenszone). In einem ehrlichen C4-Context-Diagramm gehört das markiert — z. B. mit einem `[PII]`-Tag und einer Notiz, ob/wie wir Empfängernamen vor dem Senden pseudonymisieren.
11. **Richtung der Initiative:** Wer löst was aus? Der Claude-Pfeil ist nur „Request/Response" wenn der User uploadet — kein Background-Job ruft heute Claude an. Das sollte im Pfeillabel stehen, sonst denkt ein Leser an einen Cron-Worker.
12. **„Off-system" Kanal Bank → User:** Der Pfeil von „Schweizer Banken" zum User passiert komplett außerhalb von BudgetBuddy. Das ist korrekt zu zeigen (zeigt die Lara-Friction: sie muss erst exportieren), sollte aber als gestrichelte Linie / „manuell" gekennzeichnet sein, damit niemand eine technische Integration vermutet.

### Vorschlag für die nächste Iteration

Akteure ergänzen: **Admin/Operator**, **Hosting-Plattform (CH)**, **Backup-Ziel**, **Monitoring-Service**. Den **OpenBanking-Pfad** als gestrichelten „Future"-Akteur aufnehmen. Pfeil BudgetBuddy↔Claude API mit **[PII]**-Tag versehen und die Pseudonymisierungs-Annahme dokumentieren.
