Model: Sonnet 4.6 with high effort

##########
# Prompt #
##########
Habt ihr Endpoints die 100x häufiger
gelesen als geschrieben werden?
Braucht ihr CQRS jetzt — oder später?
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
● CQRS bei BudgetBuddy — Braucht ihr das?

  Read/Write-Ratio der User Stories

  ┌────────────────────────────────────┬──────────────────────┬─────────────────────────────────────────┬────────────────────┐
  │          Endpoint (Read)           │       Frequenz       │            Endpoint (Write)             │      Frequenz      │
  ├────────────────────────────────────┼──────────────────────┼─────────────────────────────────────────┼────────────────────┤
  │ GET /budget/safe-to-spend (US-06)  │ Jeder Dashboard-Load │ POST /import/pdf (US-04)                │ 1–2×/Monat/User    │
  ├────────────────────────────────────┼──────────────────────┼─────────────────────────────────────────┼────────────────────┤
  │ GET /transactions?month=… (US-13)  │ Jede Navigation      │ PUT /transactions/{id}/category (US-05) │ Gelegentlich       │
  ├────────────────────────────────────┼──────────────────────┼─────────────────────────────────────────┼────────────────────┤
  │ GET /budget/summary (US-10, US-12) │ Jeder Monatswechsel  │ POST /fixkosten (US-03)                 │ 1× beim Onboarding │
  ├────────────────────────────────────┼──────────────────────┼─────────────────────────────────────────┼────────────────────┤
  │ GET /reports/monthly (US-09)       │ 1×/Monat gelesen     │ POST /auth/register (US-01)             │ 1× ever            │
  └────────────────────────────────────┴──────────────────────┴─────────────────────────────────────────┴────────────────────┘

  Fazit: Reads schlagen Writes locker 10–50:1, nicht 100:1 — und das nur bei aktiven Usern.

  ---
  Braucht ihr CQRS jetzt?

  Nein. Für euer MVP gibt es drei konkrete Gegenargumente:

  1. SQLite ist kein Bottleneck bei dieser Last. CQRS macht bei SQLite keinen Sinn — der Gewinn kommt erst bei separater Read-DB (z.B. Read-Replika bei PostgreSQL). Euer Stack hat keine Read-Replika.
  2. Safe-to-Spend ist kein Hot Path. Die Berechnung passiert pro User, nicht global. Kein Caching-Druck, keine Race Conditions.
  3. Komplexitätskosten > Nutzen. Separate Command/Query-Handler, zwei Repository-Hierarchien, Event-Bus — das ist overhead für ein 2-3-Personen-Kursprojekt.

  ---
  Was ihr stattdessen tun solltet (CQRS-lite)

  Spring Data JPA gibt euch CQRS-ähnliche Vorteile ohne den Overhead:

  // Read-only Projection für Dashboard — kein volles Entity laden
  public interface TransactionSummary {
      BigDecimal getAmount();
      String getCategory();
      LocalDate getBookingDate();
  }

  // Im Repository
  List<TransactionSummary> findByUserIdAndMonthOrderByBookingDateDesc(
      Long userId, YearMonth month
  );

  Und für die Safe-to-Spend-Berechnung reicht eine @Query-Aggregation — kein separates Read-Model nötig.

  ---
  Wann würde CQRS Sinn machen?

  SQLite → PostgreSQL  +  Read-Replika  +  >1000 User gleichzeitig
           ↓
  Dann: Read-Model auf Replika, Write-Model auf Primary

  Kurze Antwort für euren Kurs: CQRS ist ein valides Muster, aber für BudgetBuddy MVP eine Over-Engineering-Falle. Modularer Monolith + Spring Data Projections + Separation in Service-Layer reicht vollständig.