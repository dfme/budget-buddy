Erstelle ein C4 System Context Diagram für unser Projekt beschrieben im CLAUDE.md

Zeige:
- Unser System (als Hauptelement in der Mitte)
- Alle Nutzertypen die damit interagieren
- Alle externen Systeme (E-Mail, Payment, Auth, APIs...)
- Wie sie interagieren (kurze Beschriftung der Pfeile)

Beschreibe das Diagram als Text mit klaren Boxes und Pfeilen.
Dann: Was haben wir vergessen?



-----

⏺ Ich lese das CLAUDE.md nochmal genau durch, um alle Elemente zu identifizieren.

  Read 1 file (ctrl+o to expand)

⏺ Hier ist das C4 System Context Diagram für BudgetBuddy:

  ---
  C4 System Context Diagram — BudgetBuddy

  ╔══════════════════════════════════════════════════════════════════════════════╗
  ║                        C4 System Context: BudgetBuddy                       ║
  ╚══════════════════════════════════════════════════════════════════════════════╝


    ┌─────────────────┐                                   ┌──────────────────────┐
    │   [Person]      │                                   │  [External System]   │
    │                 │                                   │                      │
    │  Lara / Marc    │                                   │   Claude API         │
    │  ─────────────  │                                   │   (Anthropic)        │
    │  Student /      │                                   │   ────────────────   │
    │  Berufsein-     │                                   │   Haiku: Tx-         │
    │  steiger        │                                   │   Kategorisierung    │
    │  (Schweiz)      │                                   │   Sonnet: Monats-    │
    └────────┬────────┘                                   │   bericht            │
             │                                            └──────────┬───────────┘
             │ Lädt PDF hoch,                                        │
             │ verwaltet Konto,       ╔══════════════════╗           │
  Kategorisiert
             │ liest Safe-to-Spend    ║                  ║           │
  Transaktionen,
             │ (HTTPS, Browser)       ║   BudgetBuddy    ║◄──────────┘ generiert
  Bericht
             └───────────────────────►║                  ║           (HTTPS/REST)
                                      ║  Angular SPA +   ║
             ┌───────────────────────►║  Spring Boot     ║
             │ Exportiert PDF-        ║  REST API +      ║
             │ Kontoauszug manuell    ║  SQLite DB       ║
             │ aus E-Banking          ╚══════════════════╝
             │ (kein direkter
             │  API-Aufruf)
    ┌────────┴────────┐
    │ [External       │
    │  System]        │
    │                 │
    │  Schweizer      │
    │  Banken         │
    │  ─────────────  │
    │  UBS            │
    │  Raiffeisen     │
    │  PostFinance    │
    └─────────────────┘

  Legende der Beziehungen

  ┌──────────────┬─────────────┬───────────────────────────────────────────────┐
  │     Von      │     Zu      │                 Beschreibung                  │
  ├──────────────┼─────────────┼───────────────────────────────────────────────┤
  │ Lara / Marc  │ BudgetBuddy │ PDF-Upload, Konto-Login, Safe-to-Spend lesen, │
  │              │             │  Kategorien korrigieren (HTTPS)               │
  ├──────────────┼─────────────┼───────────────────────────────────────────────┤
  │              │             │ Unbekannte Tx zur Kategorisierung senden      │
  │ BudgetBuddy  │ Claude API  │ (~20–30%), Monatsbericht generieren           │
  │              │             │ (HTTPS/REST)                                  │
  ├──────────────┼─────────────┼───────────────────────────────────────────────┤
  │ Claude API   │ BudgetBuddy │ Kategoriename zurück (Haiku),                 │
  │              │             │ Markdown-Bericht zurück (Sonnet)              │
  ├──────────────┼─────────────┼───────────────────────────────────────────────┤
  │ Schweizer    │ Lara / Marc │ User lädt PDF manuell aus E-Banking herunter  │
  │ Banken       │             │ (kein direkter System-zu-System-Aufruf)       │
  └──────────────┴─────────────┴───────────────────────────────────────────────┘

  ---
  Was haben wir vergessen?
  
  1. E-Mail-Service (fehlt komplett)
  - Passwort-Reset (US-14) braucht einen SMTP/Transactional-Mail-Dienst (SendGrid,
  Postmark, etc.)
  - Konto-Bestätigungs-E-Mail bei Registrierung (US-01)
  - Ohne diesen Baustein gibt es keine sichere Passwort-Wiederherstellung
  
  2. OpenBanking-API (bewusst ausgelassen, aber im Diagram fehlt der Hinweis)
  - US-11 ist "Could" — SIX Open Banking / Blink by Six oder ähnliche CH-Anbieter
  - Sollte zumindest als "planned external system" im Diagram erscheinen, damit der
   Kontext sichtbar ist

  3. Hosting-Infrastruktur / Deployment-Plattform
  - Wo läuft der Spring Boot JAR? Lokal, VPS, Render, Railway, Hetzner?
  - Für ein C4-Diagram auf Kurs-Niveau reicht "Web Server / Cloud Host" als Box
  
  4. Browser als Laufzeitkontext
  - Die Angular SPA läuft im Browser des Users — das ist technisch ein eigener
  "Container", fehlt aber im System Context
  
  5. Datenschutz-Behörde / nDSG
  - US-02 verlangt Konto-Löschung (Right to Deletion nach nDSG). Der
  Datenschutzkontext (Eidg. Datenschutzbeauftragter) ist kein System, aber ein
  wichtiger Compliance-Kontext — fehlt in der Dokumentation als explizite
  Abhängigkeit
  
  Kritischster fehlender Baustein: E-Mail-Service — ohne den ist US-01/US-14
  (Passwort-Reset) nicht umsetzbar und das Diagram gibt ein falsches Bild des
  Systems.