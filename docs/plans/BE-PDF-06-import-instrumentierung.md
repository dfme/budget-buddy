# [BE-PDF-06] Import-Flow instrumentieren: Phasendauer und Lookup/Claude-Verhältnis loggen, Klartexte entfernen

- **Issue:** [#157](https://github.com/dfme/budget-buddy/issues/157)
- **Task-ID:** `BE-PDF-06`
- **Branch:** `feature/BE-PDF-06-import-instrumentierung`
- **Story:** — (kein us-*-Label; technische Nachbesserung an US-04/US-05)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-15

## Entscheidungen

- **Lookup/Claude-Verhältnis via Port-Erweiterung.** Das Verhältnis kennt nur
  `HybridCategorizationService`; der Import-Loop sieht durch `CategorizationPort` nur die
  Kategorie. Der Port (die definierte Cross-Modul-Schnittstelle) wird deshalb auf
  `Optional<CategorizationResult>` umgestellt — ein Record aus `Category` und
  `Source { LOOKUP, CLAUDE }`. `LookupTableService` liefert `LOOKUP`,
  `ClaudeCategorizationService` `CLAUDE` (auch bei Breaker-offen/Fallback — für die
  ADR-6-Trefferquote zählt „nicht via Lookup"), die Hybrid-Kette reicht durch.
- **Zeitquelle: injizierte `Clock`, nicht `System.nanoTime()`.** Für die Grössenordnung
  „1s oder 20s" genügt die Clock, und die Testbarkeit über die injizierte Clock bleibt
  erhalten. Wird als Kommentar im Code begründet (AC).
- **Redaktion statt Volltext: `<len=NN sha256=xxxxxxxx>`** (Länge + SHA-256-Präfix) — über
  Log-Zeilen hinweg korrelierbar, aber nicht rekonstruierbar. Kein Volltext auf keinem
  Level, auch nicht `TRACE` (der sicherste der im AC offen gelassenen Wege).
- **Claudes Antwort-Label bleibt im Log** (Zeile „unbekannte Kategorie"): Modell-Output,
  max. 20 Tokens, Diagnosezweck der Zeile. Redigiert wird der Transaktionstext.
- **Scope-Erweiterung (Team-Entscheid vom 2026-08-15):** Die breite Suche fand zwei
  Parser-WARNs, die CHF-Beträge loggen (`SwissBankStatementParser` Saldo-Delta,
  Zeilen 453/464). Werden mitgefixt (Betrag raus, Anzahl Buchungen bleibt) und im
  PR-Body deklariert.

## Betroffene Files

Ändern:

| File | Änderung |
|---|---|
| `categorization/CategorizationPort.java` | Rückgabetyp `Optional<CategorizationResult>` |
| `categorization/LookupTableService.java` | Source `LOOKUP` |
| `categorization/ClaudeCategorizationService.java` | Source `CLAUDE`; Log-Zeilen 91/102/154/162 redigiert |
| `categorization/HybridCategorizationService.java` | Result durchreichen; Log-Zeilen 48/68 redigiert |
| `categorization/CategoryLearningService.java` | Debug-Logs 39/48 redigiert (Händler-Pattern) |
| `transaction/PdfImportService.java` | Phasenmessung + Zähler + erweiterte INFO-Summary |
| `transaction/SwissBankStatementParser.java` | Saldo-Delta-Betrag aus WARNs 453/464 entfernen |

Neu:

- `categorization/CategorizationResult.java` (Record + Source-Enum)
- `categorization/LogRedaction.java` (Helfer `redact(String)`)
- `categorization/CategorizationLogRedactionTest.java`

## Implementierungsschritte

1. `CategorizationResult` + Port-Umstellung; alle drei Implementierungen und den Aufrufer
   in `PdfImportService` anpassen; bestehende Tests an die Signatur anpassen.
2. `LogRedaction`-Helfer, package-private im `categorization`-Package.
3. Klartext-Stellen umstellen (8 Stellen, siehe Tabelle).
4. `PdfImportService` instrumentieren: Parse- und Kategorisierungsdauer getrennt via Clock
   (mit Begründungskommentar), Zähler aus den `Source`-Werten, bestehende INFO-Zeile wird
   zur Summary: `PDF-Import für User 3: 12 Transaktion(en) importiert (Parse 180 ms,
   Kategorisierung 3450 ms; 9 via Lookup, 3 via Claude).` — eine Zeile pro Import, INFO,
   nichts pro Transaktion.
5. Parser-WARNs 453/464: `delta` aus den Platzhaltern entfernen.
6. Tests (unten).

## Test-Strategie

- **Neu `CategorizationLogRedactionTest`** (Unit, Logback `ListAppender`): löst jeden
  umgestellten Pfad aus (Claude-Fehler, leere Antwort, unbekannte Kategorie,
  Breaker-offen-DEBUG, Hybrid-Lookup-DEBUG, Hybrid-RuntimeException, Learning-DEBUG) und
  asserted, dass der Transaktionstext nirgends im Log-Output steht (AC-Regressionstest).
- **`PdfImportServiceTest` erweitern** (Unit, Happy Path fürs DoD): Summary-Zeile via
  `ListAppender` — Phasendauern vorhanden (deterministisch über injizierte Clock),
  Verhältnis korrekt gezählt.
- **Bestehende Tests** an die neue Port-Signatur anpassen — keine Verhaltensänderung.
- `mvn package` und `ng build` fehlerfrei (Frontend unberührt, läuft fürs DoD mit).

## Acceptance Criteria (aus dem Issue)

### Instrumentierung

- Phasendauer getrennt geloggt (Parse vs. Kategorisierung), nicht als Gesamtzahl
- Lookup-/Claude-Verhältnis pro Import geloggt
- Auf `INFO` (kommt mit `logging.level.com.budgetbuddy=INFO` in Prod an)
- Zeitquelle bewusst gewählt und im Code begründet
- Keine zusätzliche Log-Zeile pro Transaktion auf `INFO`

### Datenminimierung (Risiko #2, nDSG)

- Kein Transaktionstext im Klartext mehr in Produktions-Logs
  (`ClaudeCategorizationService` 102/154/162, `HybridCategorizationService` 68)
- `DEBUG`-Pfade mitgezogen (`ClaudeCategorizationService` 91, Hybrid-Lookup-Treffer,
  `CategoryLearningService`-Pattern)
- Diagnosefähigkeit bleibt erhalten (Länge/Hash-Präfix statt Volltext)
- Test sichert die Redaktion gegen Regression ab
