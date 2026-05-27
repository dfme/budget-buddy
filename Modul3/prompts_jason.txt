Hier sind unsere User Stories und Requirements: CLAUDE.md                        
  Analysiere welche Quality Attributes für unser Projekt                           
  am kritischsten sind.                                                            
                                                                                   
  Für die Top 3 QAs:                                                               
  1. Warum ist dieser QA für unser Projekt besonders wichtig?                      
  2. Welche konkreten architektonischen Massnahmen empfiehlst du?                  
  3. Was passiert wenn wir diesen QA ignorieren?                                   
                                                                                   
  Sei konkret — keine generischen Antworten.                                       
  Beziehe dich auf unser spezifisches Projekt.                                     

⏺ 
Ich lese das CLAUDE.md durch, um die Analyse auf konkreten Projektfakten
  aufzubauen.

  Read 1 file (ctrl+o to expand)

⏺ ---
  Top 3 Quality Attributes für BudgetBuddy

  ---
  QA 1 — Sicherheit & Datenschutz (Security / Privacy)

  Warum besonders wichtig?

  Das ist euer existenzielles Risiko, das ihr selbst in CLAUDE.md benannt habt:
  "ein Datenleck ist existenzbedrohend." Lara gibt euch ihr vollständiges
  Finanzbild — jeden Kaffee, jede Arztrechnung, jeden Lohn. Marc vertraut euch nur
  überhaupt, wenn er Sicherheit spürt — seine Hürde ist explizit
  Datenschutz-Skepsis. Dazu kommt nDSG (Schweizer Datenschutzgesetz) mit echtem
  Recht auf Löschung. Ein Versagen hier ist kein Bug, sondern der Tod des Projekts.

  Konkrete architektonische Massnahmen:

  ┌──────────────────────┬─────────────────────────────┬──────────────────────┐
  │      Massnahme       │             Wo              │    Warum konkret     │
  ├──────────────────────┼─────────────────────────────┼──────────────────────┤
  │ Passwörter nur als   │ Spring Security             │ US-1 AC verlangt es  │
  │ bcrypt (Work Factor  │ PasswordEncoder             │ explizit             │
  │ ≥ 12)                │                             │                      │
  ├──────────────────────┼─────────────────────────────┼──────────────────────┤
  │ PDF nie auf Disk     │                             │ US-4 AC:             │
  │ speichern — nur im   │ PdfImportService, kein      │ "Datenbankadmin      │
  │ RAM parsen           │ MultipartFile.transferTo()  │ findet keine         │
  │                      │                             │ PDF-Dateien"         │
  ├──────────────────────┼─────────────────────────────┼──────────────────────┤
  │ User-ID als          │                             │ Verhindert, dass     │
  │ Row-Level Filter auf │ JPA Repository-Layer        │ User A die           │
  │  ALLEN               │                             │ Transaktionen von    │
  │ Datenbankabfragen    │                             │ User B sieht         │
  ├──────────────────────┼─────────────────────────────┼──────────────────────┤
  │ Kaskadierendes       │ Flyway-Migration, ON DELETE │ US-2 AC: alle        │
  │ DELETE bei           │  CASCADE                    │ personenbezogenen    │
  │ Kontolöschung        │                             │ Daten weg, sofort    │
  ├──────────────────────┼─────────────────────────────┼──────────────────────┤
  │                      │                             │ Transaktionsdaten    │
  │ HTTPS only (kein     │ Spring Security             │ dürfen nie im        │
  │ HTTP Fallback)       │ requiresChannel()           │ Klartext übertragen  │
  │                      │                             │ werden               │
  ├──────────────────────┼─────────────────────────────┼──────────────────────┤
  │ JWT-Secret als       │ application.yml mit         │ Kein Secret in       │
  │ Umgebungsvariable,   │ ${JWT_SECRET}               │ Git-History          │
  │ nie im Code          │                             │                      │
  └──────────────────────┴─────────────────────────────┴──────────────────────┘
  
  Was passiert wenn ihr das ignoriert?

  Ein einziger SQL-Injection-Bug oder eine falsche Repository-Query ohne
  User-Filter legt die Finanzdaten aller User offen. Bei einem Kurs-Projekt mag das
   abstrakt klingen — aber ihr verarbeitet echte Bankdaten echter Menschen. Das
  nDSG sieht Bussen vor, und Marc ist sofort weg. Das Vertrauen, das ihr monatelang
   aufbaut, ist in 5 Minuten zerstört.

  ---
  QA 2 — Usability (Gebrauchstauglichkeit / First-Run-Experience)

  Warum besonders wichtig?

  Euer grösstes Risiko Nr. 1 ist die Churn-Falle. Laras Persönlichkeit ist präzise
  definiert: "wenn der erste PDF-Upload zu kompliziert ist, bricht sie sofort ab."
  Das ist kein UX-Nice-to-Have — das ist der einzige Moment, in dem ihr sie
  gewinnen oder verlieren könnt. Der Onboarding-Funnel ist: Registrierung → Consent
   → Fixkosten-Wizard → PDF-Upload → Dashboard mit Safe-to-Spend. Jeder Schritt,
  der Lara aufhält, kostet euch den User.

  Konkrete architektonische Massnahmen:

  Massnahme: Progress-Indikator beim PDF-Upload (Spinner + "42 Transaktionen
    erkannt") 
  Wo: Angular Upload-Component
  Warum konkret: US-4 AC fordert es explizit — Lara muss sehen, dass etwas passiert
  ────────────────────────────────────────
  Massnahme: Inline-Fehlermeldungen mit konkreten Hinweisen
  Wo: Reactive Forms + Angular Validators              
  Warum konkret: Nicht "Fehler" sondern "Das PDF ist passwortgeschützt — bitte
    entferne den Schutz"
  ────────────────────────────────────────
  Massnahme: Fixkosten-Wizard mit "Keine Fixkosten"-Escape
  Wo: Eigene Onboarding-Route, Guards
  Warum konkret: US-3: Wizard blockiert, bis Entscheidung getroffen — aber Escape
    verhindert Frustration
  ────────────────────────────────────────
  Massnahme: Safe-to-Spend sofort sichtbar nach Upload
  Wo: Dashboard lädt reaktiv mit Signals
  Warum konkret: Der Aha-Moment muss nach dem ersten PDF kommen, nicht nach dem
    dritten
  ────────────────────────────────────────
  Massnahme: 30-Sekunden-Timeout mit klarer Meldung
  Wo: Backend @Async + Frontend Polling
  Warum konkret: US-4: Lara soll nicht unbegrenzt auf einen hängenden Upload warten
  ────────────────────────────────────────
  Massnahme: Automatische Einkommens-Schätzung
  Wo: Backend erkennt regelmässige Gutschrift ±5%
  Warum konkret: US-6: Lara muss nicht alles manuell erfassen — Magie erhöht die
    Conversion

  Was passiert wenn ihr das ignoriert?

  Ein PDF-Upload ohne Fortschrittsanzeige fühlt sich nach Absturz an. Ein Wizard
  ohne Escape erzeugt Frustration. Lara gibt auf, erzählt niemandem davon, und ihr
  Conversion-Funnel ist 0%. Technisch korrekte Software, die niemand benutzt, ist
  wertlos — gerade bei einer Zielgruppe, die bereits schlechte Erfahrungen mit
  "mühsamen Excel-Tabellen" hat.

  ---
  QA 3 — Korrektheit (Correctness / Numerische Genauigkeit)

  Warum besonders wichtig?

  BudgetBuddy's Core Value ist "A weekly Safe-to-Spend number users can trust." Das
   Wort trust ist entscheidend. Wenn Lara dem System vertraut und 200 CHF ausgibt —
   aber die Berechnung hatte einen Rundungsfehler von 50 CHF — dann hat sie Miete
  riskiert. Das ist nicht ein Bug, das ist Vertrauensbruch. Eure User Stories
  verlangen "auf den Rappen genau" an fünf verschiedenen Stellen. Das ist kein
  Zufall.

  Konkrete architektonische Massnahmen:

  Massnahme: BigDecimal überall für CHF-Beträge, niemals double/float
  Wo: Entities, DTOs, Berechnungen   
  Warum konkret: 0.1 + 0.2 = 0.30000000000000004 in double — in der DB als
    NUMERIC(15,2) speichern 
  ────────────────────────────────────────
  Massnahme: Apostrophe-Parsing für Schweizer Zahlenformat 
  Wo: PdfParser: replace("'", "") vor BigDecimal 
  Warum konkret: 1'234.56 ist kein gültiger Java-BigDecimal — muss bereinigt werden
  ────────────────────────────────────────     
  Massnahme: Saldo-Anker beim PDF-Parsing
  Wo: PdfParser Zeilenlogik 
  Warum konkret: Text-Felder können umbrechen — Saldo-Spalte als Anker verhindert
    falsch gesplittete Transaktionen           
  ────────────────────────────────────────     
  Massnahme: Divisor mindestens 1 in Safe-to-Spend
  Wo: SafeToSpendService    
  Warum konkret: US-6 AC: Division durch 0 am Monatsende muss explizit abgefangen
    werden
  ────────────────────────────────────────
  Massnahme: Quartals/Jahres-Normalisierung bei Fixkosten
  Wo: FixedCostService: ÷ 3 bzw. ÷ 12
  Warum konkret: US-3 AC: 300 CHF quartalsweise = 100 CHF/Monat — Fehler hier
    verfälscht jede Berechnung
  ────────────────────────────────────────
  Massnahme: Kategorie-Summen als DB-Aggregation
  Wo: JPA @Query SUM(amount)
  Warum konkret: Client-seitige Summierung über gepaginierte Listen produziert
    falsche Totals

  Was passiert wenn ihr das ignoriert?

  Laras Safe-to-Spend zeigt 180 CHF statt 200 CHF — wegen Gleitkomma-Akkumulation
  über 40 Transaktionen. Oder der Apostrophe-Parser crasht bei 1'200.00 und
  importiert 0 CHF. Oder der Divisor-Bug zeigt an Silvester 800 CHF/Woche statt 200
   CHF. Jeder dieser Fehler zerstört das einzige Versprechen, das ihr Lara gemacht
  habt: dass die Zahl stimmt. Einmal gebrochen, kommt das Vertrauen nicht zurück.

  ---
  Zusammenfassung:
  
  ┌───────────┬────────────────────────┬─────────────────┬─────────────────────┐
  │ Priorität │           QA           │   Kernrisiko    │  Sofort-Massnahme   │
  ├───────────┼────────────────────────┼─────────────────┼─────────────────────┤
  │           │                        │ Datenleck =     │ Row-Level-Filter +  │
  │ 1         │ Sicherheit/Datenschutz │ Projektende +   │ kein PDF-Storage    │
  │           │                        │ nDSG            │                     │
  ├───────────┼────────────────────────┼─────────────────┼─────────────────────┤
  │ 2         │ Usability              │ Churn nach      │ Fortschrittsanzeige │
  │           │                        │ erstem Upload   │  + Timeout-Handling │
  ├───────────┼────────────────────────┼─────────────────┼─────────────────────┤
  │           │                        │ Falsche Zahlen  │ BigDecimal +        │
  │ 3         │ Korrektheit            │ =               │ Apostrophe-Parser + │
  │           │                        │ Vertrauensbruch │  Divisor-Guard      │
  └───────────┴────────────────────────┴─────────────────┴─────────────────────┘
