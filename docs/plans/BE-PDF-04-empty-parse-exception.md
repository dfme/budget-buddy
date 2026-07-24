# [BE-PDF-04] Parser wirft keine Exception bei 0 Transaktionen

- **Issue:** #83 (Label: `bug`, Milestone: Sprint 3)
- **Task-ID:** BE-PDF-04
- **Branch:** `fix/BE-PDF-04-empty-parse-exception`
- **Basis:** `origin/main` (BE-PDF-02 ist gemergt)

## Problem

`SwissBankStatementParser.parse()` gibt bei einem nicht-leeren PDF, aus dem keine
Buchungszeile erkannt wurde, still eine leere Liste zurück. Für die Nutzerin sieht das
aus wie „Upload erfolgreich, 0 Transaktionen" — Churn-Risiko #1 aus CLAUDE.md.

## Entscheidungen

- **Zwei neue Exceptions als Subklassen von `PdfParseException`:**
  - `UnsupportedStatementFormatException` — PDF hat Text, aber keine Buchung erkannt.
  - `MissingTextLayerException` — PDF ohne Textlayer (gescanntes PDF).
  - Begründung: Bestehende Aufrufer und `@throws PdfParseException`-Verträge bleiben
    gültig; der spätere Upload-Endpoint (BE-PDF-03, #18) kann per Subtyp differenzierte
    Nutzermeldungen liefern (Scan: „bitte aus dem E-Banking herunterladen statt scannen").
- **Prüfreihenfolge in `parse()`:** zuerst Textlayer-Check (alle Seiten ohne Textzeile →
  `MissingTextLayerException`), dann Format-Parsing, danach Leerheits-Check
  (`UnsupportedStatementFormatException`).
- **`PdfImportService` bleibt logisch unverändert** — die Exception propagiert aus dem
  Parser; nur Javadoc wird aktualisiert (der Hinweis „0, wenn das PDF keine enthält —
  Fehlerverhalten dafür ist BE-PDF-04" ist mit diesem Fix obsolet).

## Betroffene Files

| Datei | Änderung |
|---|---|
| `backend/src/main/java/com/budgetbuddy/transaction/UnsupportedStatementFormatException.java` | neu |
| `backend/src/main/java/com/budgetbuddy/transaction/MissingTextLayerException.java` | neu |
| `backend/src/main/java/com/budgetbuddy/transaction/PdfParseException.java` | Konstruktor `(String message)` ergänzen |
| `backend/src/main/java/com/budgetbuddy/transaction/SwissBankStatementParser.java` | Checks in `parse()`, Javadoc |
| `backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java` | Javadoc |
| `backend/src/test/java/com/budgetbuddy/transaction/SwissBankStatementParserTest.java` | neue Testfälle |
| `backend/src/test/java/com/budgetbuddy/transaction/PdfImportServiceTest.java` | obsoleten Test `emptyParseResult_returnsZeroCountWithoutCategorizing` entfernen |

## Implementierungsschritte

1. `PdfParseException`: Konstruktor `(String message)` ergänzen.
2. Neue Exceptions `UnsupportedStatementFormatException` und `MissingTextLayerException`
   (beide `extends PdfParseException`) mit Javadoc zur nutzerseitigen Übersetzung.
3. `SwissBankStatementParser.parse()`: Textlayer-Check nach `extractPages`,
   Leerheits-Check nach dem Format-Switch; Javadoc (`@throws`) aktualisieren.
4. `PdfImportService`: Javadoc von `importPdf` aktualisieren.
5. Tests schreiben/anpassen, voller Testlauf `./mvnw test`.

## Test-Strategie

- Unit (`SwissBankStatementParserTest`):
  - PDF mit Text, aber ohne erkennbare Buchungszeile → `UnsupportedStatementFormatException`
  - PDF mit leerer Seite (kein Textlayer) → `MissingTextLayerException`
  - Beide Exceptions sind Subtypen von `PdfParseException`
- Bestehende Formate (Viseca, PostFinance, UBS, generisch) werfen unverändert nicht —
  abgedeckt durch bestehende `SwissBankStatementParserTest`- und
  `SwissBankStatementParserFixtureTest`-Fälle, die grün bleiben müssen.

## Acceptance Criteria (aus Issue #83)

- [ ] Nicht-leeres PDF ohne erkannte Buchung → definierte Exception statt leerer Liste
- [ ] PDF ohne Textlayer wird davon unterschieden (eigene Meldung)
- [ ] Bestehende Formate (Viseca, PostFinance, UBS, generisch) werfen unverändert nicht
- [ ] Unit-Test je Fall
