Erstelle ein C4 System Context Diagram für unser Projekt beschrieben im CLAUDE.md

Zeige:
- Unser System (als Hauptelement in der Mitte)
- Alle Nutzertypen die damit interagieren
- Alle externen Systeme (E-Mail, Payment, Auth, APIs...)
- Wie sie interagieren (kurze Beschriftung der Pfeile)

Beschreibe das Diagram als Text mit klaren Boxes und Pfeilen.
Dann: Was haben wir vergessen?



-----

## C4 System Context Diagram — BudgetBuddy

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  SYSTEM CONTEXT                                                              ║
╚══════════════════════════════════════════════════════════════════════════════╝

  [Person]                                              [Person]
 ┌──────────────────────┐                          ┌──────────────────────┐
 │  Lara                │                          │  Marc                │
 │  Studentin, 22       │                          │  Berufseinsteiger,25 │
 │  Bern                │                          │  Zürich              │
 └──────────┬───────────┘                          └──────────┬───────────┘
            │  lädt PDF hoch,                                 │  setzt Sparziel,
            │  liest Safe-to-Spend                            │  prüft Abos
            │                                                 │
            └────────────────────┐   ┌────────────────────────┘
                                 ▼   ▼
                    ╔══════════════════════════════╗
                    ║         BudgetBuddy          ║
                    ║  ─────────────────────────── ║
                    ║  Web-App für Schweizer        ║
                    ║  Studenten & Berufseinsteiger ║
                    ║                               ║
                    ║  Liest Kontoauszüge (PDF),    ║
                    ║  kategorisiert Transaktionen, ║
                    ║  zeigt Safe-to-Spend          ║
                    ╚═══════════╤══════════════════╝
                                │
              ┌─────────────────┼──────────────────┐
              │                 │                  │
              ▼                 ▼                  ▼

  [External System]    [External System]    [External System]
 ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────┐
 │  Anthropic       │  │  Schweizer      │  │  Swiss Bank          │
 │  Claude API      │  │  Banken         │  │  (UBS / Raiffeisen / │
 │  ─────────────── │  │  (PDF-Quelle)   │  │   PostFinance)       │
 │  Kategorisierung │  │  ─────────────  │  │  ──────────────────  │
 │  unbekannter Tx  │  │  Nutzer lädt    │  │  [Nice-to-Have]      │
 │  + KI-Monats-    │  │  PDF manuell    │  │  OpenBanking API     │
 │  bericht         │  │  herunter       │  │  (nicht MVP)         │
 └──────────────────┘  └─────────────────┘  └──────────────────────┘

       ▲                       ▲                      ▲
       │ sendet Tx-Text,       │ PDF-Datei             │ [Zukunft]
       │ empfängt Kategorie    │ (offline, via User)   │ direkter API-Zugriff
       │ / Bericht             │                       │
       └───────────────────────┴───────────────────────┘
                    (alle Pfeile von BudgetBuddy aus)
```

**Datenfluss zusammengefasst:**

```
Nutzer ──[PDF upload]──► BudgetBuddy ──[Tx-Text (20-30%)]──► Claude API
                                     ◄──[Kategorie / Bericht]──

Nutzer ──[lädt bei Bank herunter]──► PDF-Datei ──[Upload]──► BudgetBuddy
```

---

## Was haben wir vergessen?

**Wahrscheinlich relevant, aber nicht modelliert:**

| # | Fehlendes Element | Warum es fehlt / Risiko |
|---|---|---|
| 1 | **E-Mail-Dienst** (z.B. SendGrid, SMTP) | Passwort-Reset und Registrierungsbestätigung brauchen E-Mail — sonst kein Recovery-Pfad |
| 2 | **Hosting-Infrastruktur** (z.B. Render, Railway, eigener Server) | Wo läuft das Ding? Kein Deployment = kein System Context |
| 3 | **Backup-System für SQLite** | SQLite ist eine Datei — kein automatisches Backup = existenzielles Risiko bei Ausfall |
| 4 | **Browser / Web-Client** | Technisch kein externes System, aber für C4 L1 oft explizit als "User's Browser" modelliert |
| 5 | **nDSG-Compliance / Datenlöschung** | Marc's Persona hat Datenschutz-Skepsis — "Konto löschen" (US-02) impliziert einen Löschprozess, der evtl. geloggt werden muss |
| 6 | **Logging / Monitoring** | Kein Sentry, kein Prometheus — bei Fehlern im Prod hat man keine Sicht |

**Bewusst ausgelassen (korrekt so):**

- OpenBanking API: als "Nice-to-Have" richtig gestrichelt / ausgegraut
- Interne Komponenten (Angular, Spring Boot, SQLite): gehören in C4 Level 2 (Container Diagram), nicht in Level 1

Die grösste Lücke ist **E-Mail**: ohne Passwort-Reset ist US-01 (Login) für den MVP unvollständig.