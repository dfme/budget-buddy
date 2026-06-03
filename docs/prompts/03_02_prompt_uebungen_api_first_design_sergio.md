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



##########
# Prompt #
##########
Schreibe eine OpenAPI 3.0 Spec für den ersten Endpunkt POST /api/v1/statements/upload

Inkludiere:
- Path, Method, Summary, Description
- Request Body mit Beispiel (JSON Schema)
- Response 200 mit Beispiel
- Response 400, 401, 404, 422 mit Fehlerbeschreibungen
- Security: Bearer Token

Format: YAML



###########
# Antwort #
###########
● openapi: "3.0.3"

  info:
    title: BudgetBuddy API
    version: "1.0.0"
    description: >
      REST API für BudgetBuddy — automatische Kategorisierung von
      Banktransaktionen und wöchentliche Safe-to-Spend-Berechnung.
      Alle Beträge in CHF als String (BigDecimal-kompatibel).
    contact:
      name: BudgetBuddy Team

  servers:
    - url: http://localhost:8080
      description: Lokale Entwicklungsumgebung

  # ─────────────────────────────────────────────
  # Security-Schema
  # ─────────────────────────────────────────────
  components:
    securitySchemes:
      BearerAuth:
        type: http
        scheme: bearer
        bearerFormat: JWT
        description: >
          JWT-Token aus POST /api/v1/auth/login.
          Header-Format: `Authorization: Bearer <token>`

    # ── Wiederverwendbare Schemas ──────────────
    schemas:

      Transaction:
        type: object
        required: [id, date, description, debit, credit, balance, category, categorySource]
        properties:
          id:
            type: string
            format: uuid
            example: "a3f8c2d1-09eb-4b77-8a12-3e5f7a9c0d41"
          date:
            type: string
            format: date
            description: Buchungsdatum (ISO 8601)
            example: "2026-05-03"
          description:
            type: string
            description: Originaltext aus dem Kontoauszug
            example: "DIGITEC GALAXUS AG 044 913 2323"
          debit:
            type: string
            description: Belastung in CHF (leer wenn Gutschrift)
            example: "149.90"
          credit:
            type: string
            description: Gutschrift in CHF (leer wenn Belastung)
            example: ""
          balance:
            type: string
            description: Saldo nach Buchung in CHF
            example: "1'842.15"
          category:
            type: string
            description: Zugewiesene Kategorie
            enum:
              - Wohnen
              - Lebensmittel
              - Transport
              - Versicherung
              - Telekom
              - Gesundheit
              - Freizeit
              - Restaurant
              - Shopping
              - Bildung
              - Einkommen
              - Sparen
              - Sonstiges
            example: "Shopping"
          categorySource:
            type: string
            description: Wie die Kategorie bestimmt wurde
            enum:
              - LOOKUP   # Händler war in Lookup-Tabelle bekannt
              - AI       # Kategorisiert via Claude API
              - MANUAL   # Manuell vom User korrigiert
            example: "AI"

      ImportSummary:
        type: object
        required: [totalTransactions, totalDebit, totalCredit, lookupCategorized, aiCategorized]
        properties:
          totalTransactions:
            type: integer
            example: 34
          totalDebit:
            type: string
            description: Summe aller Belastungen in CHF
            example: "2'318.45"
          totalCredit:
            type: string
            description: Summe aller Gutschriften in CHF
            example: "3'200.00"
          lookupCategorized:
            type: integer
            description: Anzahl Transaktionen via Lookup-Tabelle kategorisiert
            example: 26
          aiCategorized:
            type: integer
            description: Anzahl Transaktionen via Claude API kategorisiert
            example: 8

      UploadResponse:
        type: object
        required: [statementId, month, detectedBank, importedAt, summary, transactions]
        properties:
          statementId:
            type: string
            format: uuid
            description: Eindeutige ID des importierten Kontoauszugs
            example: "f1e2d3c4-b5a6-7890-abcd-ef1234567890"
          month:
            type: string
            description: Abrechnungsmonat (YYYY-MM)
            example: "2026-05"
          detectedBank:
            type: string
            description: Erkannte Bank (aus PDF-Metadaten oder Header)
            example: "Raiffeisen"
          importedAt:
            type: string
            format: date-time
            example: "2026-06-03T14:22:00Z"
          summary:
            $ref: "#/components/schemas/ImportSummary"
          transactions:
            type: array
            items:
              $ref: "#/components/schemas/Transaction"

      ErrorResponse:
        type: object
        required: [status, error, message, timestamp]
        properties:
          status:
            type: integer
            example: 400
          error:
            type: string
            example: "Bad Request"
          message:
            type: string
            example: "Nur PDF-Dateien werden akzeptiert."
          timestamp:
            type: string
            format: date-time
            example: "2026-06-03T14:22:00Z"
          details:
            type: array
            description: Optionale Liste von Einzel-Fehlern (z. B. Validierung)
            items:
              type: string
            example: []

  # ─────────────────────────────────────────────
  # Globale Security (gilt für alle Endpoints)
  # ─────────────────────────────────────────────
  security:
    - BearerAuth: []

  # ─────────────────────────────────────────────
  # Endpoints
  # ─────────────────────────────────────────────
  paths:
    /api/v1/statements/upload:
      post:
        tags:
          - Statements
        operationId: uploadStatement
        summary: Kontoauszug als PDF hochladen
        description: >
          Nimmt einen Schweizer Bankkontoauszug (PDF) entgegen und verarbeitet ihn
          vollständig in einer einzigen Anfrage:

          1. **Validierung** — Dateityp (application/pdf), Grösse (max. 10 MB),
             Passwortstatus (verschlüsselte PDFs werden abgelehnt).

          2. **Parsing** — PDFBox 3.x extrahiert den Text-Layer.
             Spaltenformat: `Buchungsdatum | Valuta | Text | Belastungen CHF | Gutschriften CHF | Saldo CHF`.
             Beträge werden bereinigt (Apostroph-Tausendertrenner entfernt) und als `BigDecimal` gespeichert.

          3. **Kategorisierung (Hybrid)**
             - Lookup-Tabelle (bekannte Händler → Kategorie) — ~70–80 % der Transaktionen
             - Claude API (`claude-haiku-3-5-20241022`) für unbekannte Einträge
             - Fallback-Kategorie `Sonstiges` wenn Claude API nicht erreichbar

          4. **Persistierung** — Kontoauszug-Metadaten und alle Transaktionen werden in SQLite gespeichert.

          **Idempotenz:** Bereits importierte Kontoauszüge (gleicher User + gleicher Monat)
          werden mit HTTP 409 abgelehnt — kein Duplicate-Import möglich.
        security:
          - BearerAuth: []

        requestBody:
          required: true
          content:
            multipart/form-data:
              schema:
                type: object
                required: [file]
                properties:
                  file:
                    type: string
                    format: binary
                    description: >
                      PDF-Kontoauszug einer Schweizer Bank.
                      Max. 10 MB. Muss einen lesbaren Text-Layer enthalten
                      (keine gescannten Bilder). Darf nicht passwortgeschützt sein.
              encoding:
                file:
                  contentType: application/pdf

        responses:

          # ── 200 OK ──────────────────────────────
          "200":
            description: >
              PDF erfolgreich verarbeitet. Alle Transaktionen wurden kategorisiert
              und gespeichert. Die Antwort enthält den vollständigen Import.
            content:
              application/json:
                schema:
                  $ref: "#/components/schemas/UploadResponse"
                example:
                  statementId: "f1e2d3c4-b5a6-7890-abcd-ef1234567890"
                  month: "2026-05"
                  detectedBank: "Raiffeisen"
                  importedAt: "2026-06-03T14:22:00Z"
                  summary:
                    totalTransactions: 3
                    totalDebit: "527.40"
                    totalCredit: "3200.00"
                    lookupCategorized: 2
                    aiCategorized: 1
                  transactions:
                    - id: "a3f8c2d1-09eb-4b77-8a12-3e5f7a9c0d41"
                      date: "2026-05-03"
                      description: "DIGITEC GALAXUS AG 044 913 2323"
                      debit: "149.90"
                      credit: ""
                      balance: "3050.10"
                      category: "Shopping"
                      categorySource: "AI"
                    - id: "b9d1e2f3-1234-5678-9abc-def012345678"
                      date: "2026-05-05"
                      description: "MIGROS BERN BAHNHOF"
                      debit: "77.50"
                      credit: ""
                      balance: "2972.60"
                      category: "Lebensmittel"
                      categorySource: "LOOKUP"
                    - id: "c4e5f6a7-abcd-ef01-2345-678901234567"
                      date: "2026-05-01"
                      description: "LOHN MAI 2026 ARBEITGEBER AG"
                      debit: ""
                      credit: "3200.00"
                      balance: "3200.00"
                      category: "Einkommen"
                      categorySource: "LOOKUP"

          # ── 400 Bad Request ──────────────────────
          "400":
            description: >
              Ungültige Anfrage. Mögliche Ursachen:
              - Kein `file`-Feld im Multipart-Body
              - Dateiendung ist nicht `.pdf`
              - Content-Type ist nicht `application/pdf`
              - Datei überschreitet 10 MB
              - PDF ist passwortgeschützt
            content:
              application/json:
                schema:
                  $ref: "#/components/schemas/ErrorResponse"
                examples:
                  kein_file:
                    summary: Kein file-Feld
                    value:
                      status: 400
                      error: "Bad Request"
                      message: "Pflichtfeld 'file' fehlt im Request-Body."
                      timestamp: "2026-06-03T14:22:00Z"
                      details: []
                  falscher_typ:
                    summary: Falscher Dateityp
                    value:
                      status: 400
                      error: "Bad Request"
                      message: "Nur PDF-Dateien werden akzeptiert (erhalten: image/png)."
                      timestamp: "2026-06-03T14:22:00Z"
                      details: []
                  passwort_geschuetzt:
                    summary: PDF ist passwortgeschützt
                    value:
                      status: 400
                      error: "Bad Request"
                      message: "Das PDF ist passwortgeschützt und kann nicht verarbeitet werden. Bitte exportiere es ohne Passwortschutz."
                      timestamp: "2026-06-03T14:22:00Z"
                      details: []

          # ── 401 Unauthorized ────────────────────
          "401":
            description: >
              Kein oder ungültiger Bearer-Token im `Authorization`-Header.
              Token ist abgelaufen oder wurde manipuliert.
            content:
              application/json:
                schema:
                  $ref: "#/components/schemas/ErrorResponse"
                example:
                  status: 401
                  error: "Unauthorized"
                  message: "JWT-Token fehlt oder ist ungültig. Bitte erneut einloggen."
                  timestamp: "2026-06-03T14:22:00Z"
                  details: []

          # ── 404 Not Found ───────────────────────
          "404":
            description: >
              Der im JWT enthaltene User existiert nicht mehr in der Datenbank
              (z. B. Konto wurde gelöscht, Token aber noch nicht invalidiert).
            content:
              application/json:
                schema:
                  $ref: "#/components/schemas/ErrorResponse"
                example:
                  status: 404
                  error: "Not Found"
                  message: "User-Konto nicht gefunden. Bitte erneut registrieren."
                  timestamp: "2026-06-03T14:22:00Z"
                  details: []

          # ── 409 Conflict ────────────────────────
          "409":
            description: >
              Für diesen User und diesen Monat wurde bereits ein Kontoauszug importiert.
              Duplicate-Import ist nicht erlaubt.
            content:
              application/json:
                schema:
                  $ref: "#/components/schemas/ErrorResponse"
                example:
                  status: 409
                  error: "Conflict"
                  message: "Für den Monat 2026-05 wurde bereits ein Kontoauszug importiert."
                  timestamp: "2026-06-03T14:22:00Z"
                  details: []

          # ── 422 Unprocessable Entity ─────────────
          "422":
            description: >
              PDF wurde erfolgreich geöffnet, aber der Inhalt kann nicht verarbeitet werden.
              Mögliche Ursachen:
              - PDF enthält keinen Text-Layer (gescanntes Bild ohne OCR)
              - Unbekanntes Bankformat — Spaltenstruktur kann nicht erkannt werden
              - Keine Transaktionen im geparsten Text gefunden (leerer Auszug)
            content:
              application/json:
                schema:
                  $ref: "#/components/schemas/ErrorResponse"
                examples:
                  kein_text_layer:
                    summary: Kein Text-Layer (gescanntes PDF)
                    value:
                      status: 422
                      error: "Unprocessable Entity"
                      message: "Das PDF enthält keinen lesbaren Text-Layer. Bitte verwende einen Kontoauszug, der direkt aus dem E-Banking exportiert wurde (kein Scan)."
                      timestamp: "2026-06-03T14:22:00Z"
                      details: []
                  unbekanntes_format:
                    summary: Unbekanntes Bankformat
                    value:
                      status: 422
                      error: "Unprocessable Entity"
                      message: "Das Bankformat konnte nicht erkannt werden. Unterstützte Banken: Raiffeisen, UBS, PostFinance, ZKB."
                      timestamp: "2026-06-03T14:22:00Z"
                      details: ["Erkannte Spalten stimmen nicht mit bekannten Formaten überein."]
                  keine_transaktionen:
                    summary: Keine Transaktionen gefunden
                    value:
                      status: 422
                      error: "Unprocessable Entity"
                      message: "Im Kontoauszug wurden keine Transaktionen gefunden."
                      timestamp: "2026-06-03T14:22:00Z"
                      details: []

  ---
  Highlights der Spec

  ┌─────────────────────────────────┬───────────────────────────────────────────────────────────────────┐
  │            Entscheid            │                            Begründung                             │
  ├─────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
  │ multipart/form-data statt JSON  │ PDF ist Binärdaten — kein Base64-Encoding nötig                   │
  ├─────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
  │ Beträge als string nicht number │ BigDecimal-kompatibel — kein 1.1 + 2.2 = 3.30000001-Problem       │
  ├─────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
  │ 409 Conflict extra ergänzt      │ Fehlte in deiner Liste, ist aber kritisch gegen Duplicate-Imports │
  ├─────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
  │ categorySource im Response      │ Frontend kann zeigen welche Tx AI-kategorisiert sind → User-Trust │
  ├─────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
  │ 3 separate 422-Beispiele        │ Jeder Parse-Fehler braucht eine andere UX-Reaktion im Frontend    │
  └─────────────────────────────────┴───────────────────────────────────────────────────────────────────┘