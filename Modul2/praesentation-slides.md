---
marp: true
theme: default
paginate: true
style: |
  section {
    font-family: 'Segoe UI', sans-serif;
    background: #ffffff;
  }
  section.title {
    background: #1a1a2e;
    color: #ffffff;
    text-align: center;
  }
  section.title h1 { font-size: 2.8rem; margin-bottom: 0.2em; }
  section.title p  { font-size: 1.1rem; color: #a0aec0; }
  h2 { color: #2d3748; border-bottom: 3px solid #4f7ef7; padding-bottom: 0.3em; }
  .highlight { background: #ebf4ff; border-left: 4px solid #4f7ef7; padding: 0.6em 1em; border-radius: 4px; margin: 0.5em 0; }
  .warn { background: #fff5f5; border-left: 4px solid #e53e3e; padding: 0.6em 1em; border-radius: 4px; }
  .formula { background: #f0fff4; border-left: 4px solid #38a169; padding: 0.8em 1.2em; border-radius: 4px; font-size: 1.15rem; font-weight: bold; }
  code { background: #edf2f7; padding: 0.1em 0.4em; border-radius: 3px; }
  footer { font-size: 0.75rem; color: #a0aec0; }
---

<!-- _class: title -->

# 💸 BudgetBuddy

### 5-Minuten-Pitch

**Sergio Klemensberger**
CAS Application Development with AI · BFH Biel · 2026

---

## Das Problem — ein Satz

<br>

> **"Lara verliert Mitte des Monats den Überblick und scrollt panisch durch ihre Banking-App."**

<br>

| Persona | Schmerz |
|---------|---------|
| **Lara**, 22, Studentin, Bern | Weiss nie, ob das Geld für Miete und Lebensmittel reicht |
| **Marc**, 25, Junior-Verkäufer, Zürich | 0 CHF übrig am Monatsende trotz Vollzeitjob |

<br>

_Keine Management-Sprache. Ein konkreter Mensch in einer konkreten Situation._

---

## Die ONE Number: Safe-to-Spend

<br>

<div class="formula">
(Einkommen − Fixkosten − bisherige Ausgaben im Monat) ÷ verbleibende Wochen
</div>

<br>

**Beispiel:** Einkommen 2 000 CHF · Fixkosten 800 CHF · Ausgaben Woche 1: 400 CHF

→ **200 CHF / Woche** für die verbleibenden 3 Wochen

<br>

<div class="highlight">
Das ist unsere Differenzierung gegenüber jedem Mint/YNAB-Klon — <strong>eine Zahl, der man vertrauen kann.</strong>
</div>

---

## MVP-Scope — 4 Must-Stories, bewusst gesetzt

```
Fixkosten erfassen  →  PDF hochladen  →  Kategorien  →  Safe-to-Spend
     (US-3)               (US-4)           (US-5)          (US-6)
```

<br>

**Auth (US-1) und Consent (US-2)** sind Must — aber kein User-Wert:
> _"Sie ermöglichen den Wert, liefern ihn nicht."_

**OpenBanking (US-11)** ist Won't — eine Entscheidung, keine Entschuldigung.

<br>

_Was fehlt, ist dokumentiert. Scope-Disziplin vor Feature-Liste._

---

## Hybrid-Kategorisierung — die spannendste Tech-Entscheidung

| Schritt | Methode | Abdeckung |
|---------|---------|-----------|
| 1. Bekannte Händler | Lookup-Tabelle | ~70–80 % |
| 2. Unbekannte Einträge | **Claude Haiku API** | ~20–30 % |
| 3. Manuelle Korrekturen | Lookup-Tabelle erweitern | Lerneffekt |

<br>

<div class="highlight">
<strong>Das System lernt ohne Retraining:</strong> Jede User-Korrektur wird zur Regel für zukünftige Transaktionen desselben Empfängers.
</div>

<br>

**Fallback:** `Sonstiges` — Claude-Ausfall blockiert nie den Import-Flow.

---

## Architektur — 5 Boxen

<br>

```
[Angular SPA]  ──JWT──▶  [Spring Boot 3.5]  ──▶  [SQLite]
                               │
                               ├──▶  [PDFBox 3.x]    (Text-Extraktion)
                               │
                               └──▶  [Lookup-Tabelle]
                                          │ fallback
                                          ▼
                                   [Claude Haiku API]
```

<br>

**SQLite statt Postgres?** MVP, kein Concurrent-Write-Druck, Migrationspfad dokumentiert.
**JWT statt Session?** Kein Session-Table-Write-Druck + saubere SPA-Integration.

---

## Drei dokumentierte Risiken — Reifezeichen

<div class="warn">
<strong>1. Churn-Falle</strong><br>
Manueller PDF-Import + Kategorisierung → Abbruch nach erstem Aha-Effekt wenn Onboarding reibt.
</div>

<div class="warn">
<strong>2. Liability & Compliance</strong><br>
Sensible Transaktionsdaten = Hacking-Ziel. nDSG-Recht auf Löschung implementiert: kein Eintrag mit gelöschter User-ID auffindbar.
</div>

<div class="warn">
<strong>3. Feature-Lücke der Banken</strong><br>
UBS, Raiffeisen bauen eigene PFM-Tools — Business Case kann über Nacht wegfallen.
</div>

<br>

> _"Wir haben diese Lücken **vor** der Implementierung gefunden, nicht im Bugfix."_

---

## Was Architekten fragen — und unsere Antworten

| Frage | Antwort |
|-------|---------|
| Warum SQLite? | MVP; kein Concurrent-Write-Druck; PostgreSQL-Migration dokumentiert |
| Warum JWT? | SQLite-Write-Pressure-Vermeidung; saubere SPA-Integration |
| Geld als `double`? | Nein — `BigDecimal`; Schweizer Apostroph (`1'234.56`) → `replace("'","")` |
| Claude API down? | Fallback `"Sonstiges"` — Import-Flow blockiert nicht |
| Wie misst ihr Kategorisierung? | ≥ 80 % auf Test-Set mit 200 gelabelten Transaktionen |
| nDSG-Löschung wirklich umgesetzt? | Ja — US-2 AC: DB-Admin findet keinen Eintrag mit gelöschter User-ID |

---

<!-- _class: title -->

# Fragen?

<br>

**Vollständige Dokumentation:** `CLAUDE.md` / User Stories mit Acceptance Criteria

**Tech Stack:** Angular 19 · Spring Boot 3.5 · SQLite · Claude API · PDFBox 3

<br>

_BudgetBuddy — eine Zahl, der man vertrauen kann._
