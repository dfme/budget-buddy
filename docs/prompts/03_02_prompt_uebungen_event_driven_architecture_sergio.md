Model: Sonnet 4.6 with high effort

##########
# Prompt #
##########
Welche unserer User Stories würden von
Event-Driven profitieren?
Was wären die Events?
Lohnt sich der Aufwand für unser MVP?
User Stories:
| [US-01](docs/requirements/US-01-konto-login.md) | Konto erstellen und einloggen | Lara | Should |
| [US-02](docs/requirements/US-02-datenschutz-consent.md) | Datenschutz-Consent + Konto löschen (nDSG) | Marc | Should |
| [US-03](docs/requirements/US-03-fixkosten-wizard.md) | Fixkosten erfassen (Onboarding-Wizard) | Lara | Must |
| [US-04](docs/requirements/US-04-pdf-upload.md) | Kontoauszug als PDF hochladen | Lara | Must |
| [US-05](docs/requirements/US-05-transaktionen-kategorisieren.md) | Transaktionen kategorisieren (Auto + manuell) | Lara | Must |
| [US-06](docs/requirements/US-06-safe-to-spend.md) | Wöchentlicher Safe-to-Spend-Betrag | Lara | Must |
| [US-07](docs/requirements/US-07-sparziel.md) | Sparziel definieren und verfolgen | Marc | Could |
| [US-08](docs/requirements/US-08-wiederkehrende-ausgaben.md) | Wiederkehrende Ausgaben (Abos) erkennen | Marc | Should |
| [US-09](docs/requirements/US-09-ki-monatsbericht.md) | KI-generierter Monatsbericht | Lara | Should |
| [US-10](docs/requirements/US-10-monatsvergleich.md) | Monatsvergleich (aktuell vs. Vormonat) | Lara | Could |
| [US-11](docs/requirements/US-11-openbanking.md) | OpenBanking-Anbindung | Lara | Could |
| [US-12](docs/requirements/US-12-monatswechsel.md) | Zwischen Monaten wechseln | Lara | Should |
| [US-13](docs/requirements/US-13-transaktionen-pro-kategorie.md) | Einzeltransaktionen pro Kategorie einsehen | Lara | Should |
| [US-14](docs/requirements/US-14-einstellungen.md) | Passwort und Einkommen in Einstellungen ändern | Marc | Should |



###########
# Antwort #
###########
● Event-Driven Architecture — BudgetBuddy Analyse

  1. Welche User Stories profitieren von EDA?

  ┌────────────────────────────┬─────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────┐
  │             US             │   Nutzen    │                                              Warum                                              │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-04 PDF-Upload           │ ⭐⭐⭐ Hoch │ Parsing + KI-Kategorisierung sind langsame, asynchrone Schritte — klassischer Pipeline-Kandidat │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-05 Kategorisierung      │ ⭐⭐⭐ Hoch │ Lookup → Claude API → manuelle Korrektur ist eine natürliche Event-Kette                        │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-09 KI-Monatsbericht     │ ⭐⭐⭐ Hoch │ Claude Sonnet-Call dauert Sekunden — blockierender HTTP-Call ist inakzeptabel                   │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-06 Safe-to-Spend        │ ⭐⭐ Mittel │ Neuberechnung sollte reaktiv auf andere Events folgen, nicht gepullt werden                     │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-02 Konto löschen (nDSG) │ ⭐⭐ Mittel │ Kaskadierende Datenlöschung über Module hinweg — Events sichern Vollständigkeit                 │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-08 Abo-Erkennung        │ ⭐⭐ Mittel │ Kann async nach dem Import laufen, ohne den User zu blockieren                                  │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-11 OpenBanking          │ ⭐⭐⭐ Hoch │ Webhooks sind Events — aber das ist Nice-to-Have                                                │
  ├────────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ US-01, 03, 07, 10, 12–14   │ ⭐ Gering   │ Request/Response reicht völlig aus                                                              │
  └────────────────────────────┴─────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────┘

  ---
  2. Was wären die Events?

  Der natürliche Event-Flow entlang eures Kernprozesses:

  ┌─────────────────────────────────────────────────────────────────┐
  │                    PDF IMPORT PIPELINE                          │
  │                                                                 │
  │  User lädt PDF hoch                                             │
  │       │                                                         │
  │       ▼                                                         │
  │  PdfUploadedEvent ──────► PDFBox parst Text                     │
  │       │                                                         │
  │       ▼                                                         │
  │  TransactionsExtractedEvent ──► Lookup-Tabelle prüft jede Tx    │
  │       │                              │                          │
  │       │                    bekannt? ─┤─ unbekannt?              │
  │       │                         │   │       │                   │
  │       ▼                         ▼   │       ▼                   │
  │  TransactionCategorizedEvent ◄──┘   └─► Claude API              │
  │       │                                     │                   │
  │       ▼                                     ▼                   │
  │  SafeToSpendRecalculatedEvent ◄─── TransactionCategorizedEvent  │
  │                                                                 │
  │  (monatlich oder on-demand):                                    │
  │  MonthClosedEvent ──────────► AiReportGeneratedEvent            │
  └─────────────────────────────────────────────────────────────────┘

  Event-Übersicht:

  ┌────────────────────────────────┬─────────────────────────┬──────────────────────────────────────────────────┐
  │             Event              │        Publisher        │                     Consumer                     │
  ├────────────────────────────────┼─────────────────────────┼──────────────────────────────────────────────────┤
  │ PdfUploadedEvent               │ PdfImportController     │ PdfParsingService                                │
  ├────────────────────────────────┼─────────────────────────┼──────────────────────────────────────────────────┤
  │ TransactionsExtractedEvent     │ PdfParsingService       │ CategorizationService                            │
  ├────────────────────────────────┼─────────────────────────┼──────────────────────────────────────────────────┤
  │ TransactionCategorizedEvent    │ CategorizationService   │ SafeToSpendService, SubscriptionDetectionService │
  ├────────────────────────────────┼─────────────────────────┼──────────────────────────────────────────────────┤
  │ CategoryManuallyCorrectedEvent │ TransactionController   │ LookupTableService (lernt!), SafeToSpendService  │
  ├────────────────────────────────┼─────────────────────────┼──────────────────────────────────────────────────┤
  │ SafeToSpendRecalculatedEvent   │ SafeToSpendService      │ Frontend via SSE/Polling                         │
  ├────────────────────────────────┼─────────────────────────┼──────────────────────────────────────────────────┤
  │ MonthClosedEvent               │ Scheduler / User-Action │ AiReportService                                  │
  ├────────────────────────────────┼─────────────────────────┼──────────────────────────────────────────────────┤
  │ AccountDeletionRequestedEvent  │ AuthController          │ transaction/, budget/, report/ Module            │
  └────────────────────────────────┴─────────────────────────┴──────────────────────────────────────────────────┘

  ---
  3. Lohnt sich der Aufwand fürs MVP?

  Kurze Antwort: Ja — aber mit Spring-internen Events, kein Kafka.

  Das Kernproblem ohne EDA

  // ❌ Ohne EDA: Alles synchron im Controller — Lara wartet 8 Sekunden
  @PostMapping("/import")
  public ResponseEntity<?> upload(@RequestParam MultipartFile file) {
      var transactions = pdfService.parse(file);        // 2s
      var categorized  = claudeService.categorize(all); // 5s  ← blockiert!
      var budget       = budgetService.recalculate();   // 1s
      return ResponseEntity.ok(budget);                 // Lara hat längst abgebrochen
  }

  Die pragmatische Lösung: Spring Application Events

  Spring Boot hat EDA eingebaut — kein Kafka, kein RabbitMQ, kein zusätzlicher Infrastruktur-Overhead:

  // Publisher (in PdfParsingService)
  applicationEventPublisher.publishEvent(
      new TransactionsExtractedEvent(this, transactions)
  );

  // Consumer (in CategorizationService)
  @EventListener
  @Async  // ← läuft in Thread-Pool, blockiert nicht
  public void onTransactionsExtracted(TransactionsExtractedEvent event) {
      event.getTransactions().forEach(tx -> {
          var category = lookupTable.find(tx.getText())
              .orElseGet(() -> claudeApi.categorize(tx.getText())); // Fallback
          tx.setCategory(category);
          applicationEventPublisher.publishEvent(new TransactionCategorizedEvent(tx));
      });
  }

  Fazit

  ┌──────────────────────────┬────────────────────────────────────────────────────────────┐
  │        Kriterium         │                         Bewertung                          │
  ├──────────────────────────┼────────────────────────────────────────────────────────────┤
  │ Nutzen für US-04/05/09   │ ✅ Echtes Problem gelöst (Blocking-Calls)                  │
  ├──────────────────────────┼────────────────────────────────────────────────────────────┤
  │ Implementierungsaufwand  │ ✅ Spring @EventListener + @Async — ~2h Setup              │
  ├──────────────────────────┼────────────────────────────────────────────────────────────┤
  │ Infrastruktur-Overhead   │ ✅ Kein Broker nötig                                       │
  ├──────────────────────────┼────────────────────────────────────────────────────────────┤
  │ Verständlichkeit im Team │ ⚠️ Pattern muss einmal erklärt werden                      │
  ├──────────────────────────┼────────────────────────────────────────────────────────────┤
  │ Testbarkeit              │ ✅ Events sind leicht zu mocken/verifizieren               │
  ├──────────────────────────┼────────────────────────────────────────────────────────────┤
  │ Overkill-Risiko          │ ❌ Kafka/RabbitMQ wären Overkill — das würde ich weglassen │
  └──────────────────────────┴────────────────────────────────────────────────────────────┘

  Empfehlung: Spring Application Events für die Import-Pipeline einsetzen. Der größte Gewinn: Claude API-Calls blockieren nie mehr den HTTP-Request, und das Modul-Design wird sauberer (kein direkter
  Service-zu-Service-Aufruf quer durch die Codebase).