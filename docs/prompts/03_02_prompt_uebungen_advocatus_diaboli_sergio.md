Model: Sonnet 4.6 with high effort

##########
# Prompt #
##########
Wir haben uns für einen Monolith entschieden.
Unser Kontext: **BudgetBuddy** ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die ihnen durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben gibt. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an — damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können. Durch gezielte, lebensnahe Sparvorschläge hilft BudgetBuddy jungen Menschen, finanzielle Kontrolle zu gewinnen und erste Rücklagen aufzubauen. Das Team besteht aus 3 Entwicklern. Der Zeitrahmen ist 3 Monate.

Spiele jetzt den Advocatus Diaboli:
1. Warum ist diese Entscheidung für uns FALSCH?
2. Was werden wir in 6 Monaten bereuen?
3. Welches Pattern wäre ehrlich gesagt besser — und warum?
4. Was müssen wir tun damit unsere Wahl wirklich funktioniert?



###########
# Antwort #
###########
● Advocatus Diaboli: Monolith bei BudgetBuddy

  ---
  1. Warum diese Entscheidung für euch FALSCH ist

  Ihr nennt es Monolith — aber ihr baut bereits ein verteiltes System.

  - Angular SPA + Spring Boot JAR + SQLite-Datei + Claude API = 4 Komponenten, die miteinander kommunizieren. Der
  "Monolith" beschreibt nur das Backend. Die eigentliche Frage habt ihr gar nicht gestellt: Wie isoliert ihr die drei
  gefährlichsten Teile voneinander?

  PDF-Parsing und KI-Calls blockieren euer ganzes Backend.

  Spring MVC ist synchron/blocking. Ein PDF-Upload startet PDFBox (CPU-intensiv), danach N × Claude API Calls
  (~200–500ms each). Während dieser Zeit hält jede Anfrage einen Thread. Mit 10 gleichzeitigen Uploads seid ihr am Limit
  des Standard-Tomcat-Thread-Pools (200 Threads). Ein lauter Tag = alle anderen Requests hängen.

  SQLite unter Last ist nicht "einfach" — es ist gefährlich.

  Auch im WAL-Modus: eine Schreibtransaktion sperrt die DB. PDF-Import + Kategorie-Update + Safe-to-Spend-Berechnung =
  viele Writes. Bei 3 gleichzeitigen Usern, die PDFs hochladen, bekommt ihr SQLITE_BUSY-Exceptions, die ihr heute noch
  nicht auf dem Radar habt.

  ---
  2. Was ihr in 6 Monaten bereuen werdet

  "Wir wollten einfach anfangen — jetzt ist alles verwoben."

  Ohne explizite Modulgrenze wird euer Paket-Baum so aussehen:

  com.budgetbuddy
    ├── controller/   (alle Endpoints)
    ├── service/      (alle Services — PDF, Auth, KI, Budget)
    ├── repository/   (alle Repos)
    └── model/        (alle Entities)

  3 Entwickler × 1 undifferenzierter Service-Layer = wöchentliche Merge-Konflikte in BudgetService.java.

  Ihr werdet timeouts haben, die ihr nicht reproduzieren könnt.

  Claude API antwortet manchmal in 800ms statt 200ms. Kein Retry, kein Timeout-Budget, kein User-Feedback. Der User
  sieht einen hängenden Upload-Spinner — und bricht ab. Genau die Churn-Falle, die ihr als Risiko #1 identifiziert habt.

  Ihr werdet die KI nicht ersetzen können, ohne alles anzufassen.

  Wenn Anthropic die API ändert oder ihr auf ein anderes Modell wechseln wollt, steckt die Kategorisierungslogik mitten
  in TransactionService, der auch Safe-to-Spend berechnet.

  ---
  3. Welches Pattern wäre ehrlich gesagt besser

  Modular Monolith — nicht Microservices, aber mit echten Grenzen.

  com.budgetbuddy
    ├── auth/           (eigenes Package, eigener Service, eigene Tests)
    ├── transaction/    (PDF-Import, Parsing, Speicherung)
    ├── categorization/ (Lookup-Tabelle + Claude-Adapter — austauschbar!)
    ├── budget/         (Safe-to-Spend, Sparzielverfolgung)
    └── report/         (KI-Monatsbericht — 1× pro User/Monat)

  Regel: Jedes Modul darf nur seine eigenen Repositories und Services kennen. Cross-Modul-Kommunikation nur über
  definierte Interfaces. Das kostet euch 2 Stunden Planung und spart euch 40 Stunden Refactoring.

  Dazu: Async für alles, was länger als 100ms dauert.

  // Schlecht: User wartet auf den ganzen Import
  @PostMapping("/upload")
  public ResponseEntity<?> upload(@RequestParam MultipartFile file) {
      importService.importAndCategorize(file); // blockiert 2–10 Sekunden
      return ResponseEntity.ok();
  }

  // Besser: Sofortige Job-ID, Status per Polling
  @PostMapping("/upload")
  public ResponseEntity<ImportJobResponse> upload(...) {
      String jobId = importService.startAsyncImport(file); // kehrt sofort zurück
      return ResponseEntity.accepted().body(new ImportJobResponse(jobId));
  }

  Spring @Async + ein einfaches ImportJob-Entity in SQLite reicht — kein Kafka, kein Redis.

  ---
  4. Was ihr tun müsst, damit eure Wahl wirklich funktioniert

  ┌─────┬───────────────────────────────────────────────────────────────┬───────────────────────────────────────────┐
  │  #  │                           Massnahme                           │              Warum kritisch               │
  ├─────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────┤
  │ 1   │ SQLite WAL-Modus + Connection Pool aktivieren (PRAGMA         │ Verhindert SQLITE_BUSY unter Last         │
      importService.importAndCategorize(file); // blockiert 2–10 Sekunden
      return ResponseEntity.ok();
  }

  // Besser: Sofortige Job-ID, Status per Polling
  @PostMapping("/upload")
  public ResponseEntity<ImportJobResponse> upload(...) {
      String jobId = importService.startAsyncImport(file); // kehrt sofort zurück
      return ResponseEntity.accepted().body(new ImportJobResponse(jobId));
  }

  Spring @Async + ein einfaches ImportJob-Entity in SQLite reicht — kein Kafka, kein Redis.

  ---
  4. Was ihr tun müsst, damit eure Wahl wirklich funktioniert

  ┌─────┬───────────────────────────────────────────────────────────────┬───────────────────────────────────────────┐
  │  #  │                           Massnahme                           │              Warum kritisch               │
  ├─────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────┤
  │ 1   │ SQLite WAL-Modus + Connection Pool aktivieren (PRAGMA         │ Verhindert SQLITE_BUSY unter Last         │
  │     │ journal_mode=WAL, HikariCP max 1 Writer)                      │                                           │
  ├─────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────┤
  │ 2   │ Paket-Struktur nach Domänen, nicht nach Schichten             │ Verhindert Big-Ball-of-Mud ab Tag 1       │
  ├─────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────┤
  │ 3   │ Claude-API hinter Interface (CategorizationPort)              │ Erlaubt Mock in Tests + Austausch ohne    │
  │     │                                                               │ Refactoring                               │
  ├─────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────┤
  │ 4   │ Timeout + Fallback für alle externen Calls (Claude,           │ Churn-Risiko #1 eliminieren               │
  │     │ PDF-Parser)                                                   │                                           │
  ├─────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────┤
  │ 5   │ Async Import-Flow von Anfang an                               │ Blockierende Uploads = abgebrochene User  │
  ├─────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────┤
  │ 6   │ Flyway ab Tag 1                                               │ 3 Devs + kein Schema-Migration-Tool =     │
  │     │                                                               │ Datenbankkatastrophe                      │
  └─────┴───────────────────────────────────────────────────────────────┴───────────────────────────────────────────┘

  ---
  Fazit: Der Monolith ist für euren Kontext vertretbar — aber nur, wenn ihr ihn als Modular Monolith baut. Was ihr nicht
  akzeptieren dürft, ist ein Monolith als Ausrede für fehlende Strukturdisziplin. Der eigentliche Fehler wäre nicht
  "wir haben einen Monolith gebaut" — sondern "wir haben 3 Monate lang in einem Monolith ohne Modulgrenzen gearbeitet
  und nennen es dann technische Schulden".