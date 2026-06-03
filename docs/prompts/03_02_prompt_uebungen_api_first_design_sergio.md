Model: Sonnet 4.6 with high effort

##########
# Prompt #
##########
Hier ist unser Projekt: Unser Kontext: **BudgetBuddy** ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die ihnen durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben gibt. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an — damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können. Durch gezielte, lebensnahe Sparvorschläge hilft BudgetBuddy jungen Menschen, finanzielle Kontrolle zu gewinnen und erste Rücklagen aufzubauen.
Hier sind unsere Must-have User Stories:
| [US-03](docs/requirements/US-03-fixkosten-wizard.md) | Fixkosten erfassen (Onboarding-Wizard) | Lara | Must |
| [US-04](docs/requirements/US-04-pdf-upload.md) | Kontoauszug als PDF hochladen | Lara | Must |
| [US-05](docs/requirements/US-05-transaktionen-kategorisieren.md) | Transaktionen kategorisieren (Auto + manuell) | Lara | Must |
| [US-06](docs/requirements/US-06-safe-to-spend.md) | Wöchentlicher Safe-to-Spend-Betrag | Lara | Must 

Welche 5 API-Endpoints sind für unseren MVP am wichtigsten?
Für jeden Endpoint:
- HTTP Methode (GET/POST/PUT/DELETE)
- Path (/api/v1/...)
- Was macht er?
- Wer ruft ihn auf?



###########
# Antwort #
###########
● Die 5 wichtigsten MVP-Endpoints

  ┌─────┬─────────┬────────────────────────────────────┬────────────┐
  │  #  │ Methode │                Path                │ User Story │
  ├─────┼─────────┼────────────────────────────────────┼────────────┤
  │ 1   │ POST    │ /api/v1/statements/upload          │ US-04      │
  ├─────┼─────────┼────────────────────────────────────┼────────────┤
  │ 2   │ GET     │ /api/v1/transactions               │ US-05      │
  ├─────┼─────────┼────────────────────────────────────┼────────────┤
  │ 3   │ PATCH   │ /api/v1/transactions/{id}/category │ US-05      │
  ├─────┼─────────┼────────────────────────────────────┼────────────┤
  │ 4   │ POST    │ /api/v1/fixed-costs                │ US-03      │
  ├─────┼─────────┼────────────────────────────────────┼────────────┤
  │ 5   │ GET     │ /api/v1/safe-to-spend              │ US-06      │
  └─────┴─────────┴────────────────────────────────────┴────────────┘

  ---
  1 — POST /api/v1/statements/upload

  Was: Nimmt ein PDF-Kontoauszug entgegen, parst es mit PDFBox, kategorisiert jede Transaktion (Lookup-Tabelle → Claude API als Fallback) und speichert alles in der DB.
  Wer: Angular-Frontend, Datei-Upload-Komponente (multipart/form-data)

  ---
  2 — GET /api/v1/transactions

  Was: Gibt alle Transaktionen des eingeloggten Users zurück — gefiltert nach Monat (?month=2026-05). Jede Transaktion enthält Datum, Betrag, Beschreibung und Kategorie.
  Wer: Angular-Frontend, Übersichts-/Dashboard-Komponente

  ---
  3 — PATCH /api/v1/transactions/{id}/category

  Was: Korrigiert die Kategorie einer einzelnen Transaktion manuell. Schreibt die Korrektur zusätzlich in die Lookup-Tabelle → nächste ähnliche Transaktion wird automatisch korrekt kategorisiert.
  Wer: Angular-Frontend, wenn User auf eine falsch kategorisierte Transaktion klickt

  ---
  4 — POST /api/v1/fixed-costs

  Was: Speichert die Fixkosten des Users (Miete, Krankenkasse, Abo, etc.) aus dem Onboarding-Wizard. Daten fliessen direkt in die Safe-to-Spend-Berechnung.
  Wer: Angular-Frontend, Onboarding-Wizard (Schritt „Deine Fixkosten")

  ---
  5 — GET /api/v1/safe-to-spend

  Was: Berechnet den wöchentlichen Safe-to-Spend-Betrag: (Monatseinkommen − Fixkosten − bereits ausgegebene variable Kosten) / verbleibende Wochen im Monat. Gibt Betrag + Restwochen zurück.
  Wer: Angular-Frontend, Dashboard — die Kernzahl der gesamten App

  ---

  ▎ Warum genau diese 5?
  ▎ Sie bilden den kompletten Datenfluss ab: PDF rein → Transaktionen speichern → anzeigen & korrigieren → Fixkosten hinterlegen → Safe-to-Spend ausgeben. Alle 4 Must-Haves sind abgedeckt, ohne Endpoints für
  ▎ Nice-to-Haves (Monatsberichte, Sparziels etc.).
