# [INFRA-38] Testdaten für Präsentation: Demo-Logins + Kontoauszüge für Lara und Marc

- **Issue:** [#258](https://github.com/dfme/budget-buddy/issues/258)
- **Task-ID:** `INFRA-38`
- **Branch:** `feature/INFRA-38-demo-testdaten`
- **Story:** — (kein `us-*`-Label)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-10

## Entscheide

### 1. Monate relativ zu «heute», nicht fest verdrahtet

`SafeToSpendService` rechnet ausschliesslich für den laufenden Monat — ein vergangener liefert
den `CLOSED`-Marker (`backend/src/main/java/com/budgetbuddy/budget/SafeToSpendService.java:56`).
Ein Satz fest datierter Demo-PDFs ist damit ab dem nächsten Monatswechsel für genau das Feature
tot, das die Demo zeigen soll.

Der Generator erzeugt deshalb pro Persona **6 Auszüge**: die fünf letzten vollen Monate plus den
laufenden bis zum Stichtag. `--end-month` und `--months` sind überschreibbar, Default ist der
laufende Monat. Der
committete Snapshot ist auf 2026-09 datiert; verschiebt sich die Präsentation über einen
Monatswechsel, wird einmal neu generiert.

### 2. `generate_pdf_fixtures.py` wird nicht angefasst

Das bestehende Skript erzeugt Parser-Testfixtures mit fest verdrahteten Assertions in
`SwissBankStatementParserFixtureTest` und `PdfLookupCoverageIntegrationTest` — das Issue schliesst
eine Änderung für Demozwecke ausdrücklich aus.

Das neue Skript **importiert es als Modul** und nutzt seine Layout-Bausteine (`Page`,
`_post_row`, `_post_card`, `_post_lsv`, `_post_giro_in`, `_post_dauerauftrag`, `_post_write`,
`_swiss`, `footer_marker`) mit überschriebenen Modul-Globals (`HOLDER`, `OUT`,
`POST_IBAN_SPACED`, `POST_ACCOUNT_NO`). Damit ist das Satzbild bitgenau das, was
`SwissBankStatementParser` in den Fixtures bereits parst — statt einer Nachbildung, die still
danebenliegt. Der Raiffeisen-Zweig wird parametrisiert nachgebaut, weil `raiffeisen()` Rows, IBAN
und Periode hart verdrahtet und als Funktion nicht wiederverwendbar ist.

### 3. Keine neuen JUnit-Tests

Ein Test auf die committeten Demo-PDFs müsste nach jedem Regenerieren grün bleiben — und würde
ausgerechnet vor der Präsentation rot, wenn jemand den Snapshot auf den neuen Monat zieht. Der
Nachweis ist stattdessen der echte End-to-End-Lauf gegen die laufende App.

### 4. Passwörter: Pflicht-Umgebungsvariablen, nie im Git

Das Seed-Skript verlangt `DEMO_LARA_PASSWORD` und `DEMO_MARC_PASSWORD` aus der Umgebung und
bricht ab, wenn eines fehlt — geprüft wird beides, bevor der erste Account entsteht.

Ursprünglich war ein Zufallsgenerator mit Ablage in `.env.demo` geplant. Verworfen auf Zuruf des
Users: ein gewürfeltes Passwort ist auf jedem Rechner ein anderes, und der Demo-Account gehört
dann faktisch dem Laptop, auf dem er angelegt wurde. Das Team vereinbart stattdessen ein Passwort
je Persona. Nicht ins Repo — #258 verlangt die Login-Daten ausdrücklich ausserhalb, CLAUDE.md sagt
«Keine Secrets im Git». Wer mag, legt lokal eine eigene `.env.demo` an (greift über `.env.*` in
`.gitignore`) und lädt sie selbst; das Skript schreibt und liest sie nicht.

## Betroffene Dateien

### Neu

| Datei | Zweck |
| ----- | ----- |
| `backend/tools/generate_demo_statements.py` | Erzeugt 12 Auszüge (Lara PostFinance, Marc Raiffeisen), Monate relativ zu `--end-month` |
| `backend/tools/seed_demo_accounts.sh` | Fährt den echten API-Flow: Registrierung → Einkommen → Fixkosten → Onboarding → Uploads mit Status-Polling → Kontrollausgabe |
| `docs/demo/README.md` | Persona-Storys, Ablauf, Zahlenerwartung, Umgang mit den Passwörtern |
| `docs/demo/statements/*.pdf` | Committeter Snapshot, 12 Dateien |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `README.md` | Verweis auf `docs/demo/` |
| `docs/plans/README.md` | Index-Zeile für diesen Plan |

### Nicht im Git

Die Demo-Passwörter. Sie leben in der Umgebung bzw. im Passwortmanager des Teams.

## Persona-Daten

| | Lara — PostFinance | Marc — Raiffeisen |
| --- | --- | --- |
| Kontoinhaber | Lara Bieri, Länggassstrasse 42, 3012 Bern | Marc Steiner, Birmensdorferstrasse 118, 8003 Zürich |
| Monatseinkommen | 1'900.— (Barjob 20% unregelmässig + Elternbeitrag) | 4'200.— (Detailhandel, fix am 25.) |
| Fixkosten | WG-Zimmer 650 monatlich, CSS 180 monatlich, Salt 25 monatlich, Semestergebühr 1'500 jährlich | Miete 1'450 monatlich, Helsana 380 monatlich, Swisscom 79 monatlich, Fitness 89 monatlich, Hausrat 240 jährlich |
| Story im Auszug | wenige grosse Posten, Lebensmittel kleinteilig, Lohn schwankt (620–980) | Lohn stabil, aber viele Kleinbuchungen pro Monat: Coop Pronto, Take-away, Kiosk, Onlinekäufe, Abos |

Die Händler sind so gewählt, dass ein Teil über die Seeds aus
`backend/src/main/resources/db/migration/V04__create_category_lookup_table.sql` läuft (Migros,
Coop, SBB, CSS, Swisscom, Netflix, Zalando, digitec) und ein Teil bewusst **nicht** — damit die
Claude-Stufe und die manuelle Korrektur in der Demo überhaupt etwas zu zeigen haben.

## Implementierungsschritte

1. `generate_demo_statements.py`: Modul-Import und Global-Override, Monatsrechnung relativ zum
   Stichtag, Saldoketten pro Persona durchgehend über alle 6 Auszüge (Schlusssaldo eines Monats =
   Anfangssaldo des Folgemonats).
2. PostFinance-Zweig: Buchungen über die Detailblock-Builder, Händler in den Detailzeilen wie im
   realen Satzbild — nicht in der Buchungszeile.
3. Raiffeisen-Zweig: parametrisierter Renderer nach dem Muster von `raiffeisen()`, inkl.
   `ROW_FLOOR`-Assert gegen das Verschmelzen der letzten Zeile mit dem Fusszeilen-Marker.
4. `seed_demo_accounts.sh`: Pflichtprüfung der beiden Passwort-Variablen (Länge und bcrypt-Byte-
   Grenze), `curl` mit Cookie-Jar, Register mit Fallback auf Login bei 409, Upload
   je PDF, Polling bis `DONE`/`FAILED`, Abschluss-Report über `/api/transactions/summary` und
   `/api/budget/safe-to-spend`.
5. Passwortlogik wie unter Entscheid 4.
6. `docs/demo/README.md` schreiben, Verweis in `README.md` ergänzen.
7. Snapshot generieren und committen.

## Test-Strategie

- **Selbstprüfungen im Generator**, nach dem Vorbild des Fixture-Skripts: Saldokette konsistent,
  Buchungstext kollidiert nicht mit der Betragsspalte, keine Zeile unter `ROW_FLOOR`,
  Detailzeilen ≤ `MAX_DETAIL_LINES`.
- **Echter End-to-End-Lauf**: Docker Desktop und Postgres hoch, Backend lokal starten,
  `seed_demo_accounts.sh` durchlaufen lassen.
- Keine neuen JUnit-Tests (Entscheid 3).

## Acceptance Criteria (aus dem Issue)

- [ ] Beide Demo-Accounts sind einsatzbereit (eingeloggtes Dashboard zeigt Safe-to-Spend,
      Kategorien, mehrere Monate Verlauf)
- [ ] Login-Daten sind für die Präsentation griffbereit dokumentiert, nicht im Repo
- [ ] Je Persona sind mind. 3 Kontoauszüge des zugewiesenen Bank-Formats erfolgreich importiert
      (umgesetzt: 6)
