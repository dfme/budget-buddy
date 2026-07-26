# BE-PDF-05 — Gültiger Auszug mit 0 Buchungen wirft fälschlich UnsupportedStatementFormatException

- **Issue:** [#95](https://github.com/dfme/budget-buddy/issues/95) `[BE-PDF-05]` (Label: `bug`)
- **Branch:** `fix/BE-PDF-05-empty-statement-zero-bookings`
- **User Story:** US-04 (PDF-Upload)
- **Abhängigkeit:** #83 (BE-PDF-04, gemergt via #94)

## Problem

Seit BE-PDF-04 wirft `SwissBankStatementParser.parse()` bei einer leeren
Transaktionsliste immer `UnsupportedStatementFormatException` — auch wenn das Format
erkannt wurde und das Konto schlicht keine Bewegung hatte. Die Meldung „Format wird
nicht unterstützt" ist dann inhaltlich falsch.

## Entscheid (Team-Entscheid aus AC 2)

**Erfolgreicher Import mit 0 Transaktionen** (statt eigener Fehlermeldung):

- `parse()` liefert eine leere Liste, wenn eine Format-Signatur erkannt wurde
  (Kopfzeilen-Schlüsselwort Viseca/PostFinance/UBS **oder** beim generischen Layout
  eine `Saldovortrag`-/`Anfangssaldo`-Zeile), aber 0 Buchungen extrahiert wurden.
- Der Upload-Endpoint antwortet `200` mit `{"count": 0}` — das Frontend kann daraus
  „keine Buchungen im gewählten Zeitraum" rendern (`count === 0`).
- Begründung: Es ist faktisch ein Erfolg; kein neuer Fehlerpfad nötig; passt zum
  bestehenden Body-losen Fehler-Handling (Status-only, Meldungen formuliert das
  Frontend, FE-PDF-02).
- Verworfen: eigene `EmptyStatementException` mit eigenem HTTP-Status (z. B. 422) —
  hätte die Invariante „nie 0 Transaktionen" erhalten, erzeugt aber einen neuen
  Fehlerpfad für einen Nicht-Fehler.

## Betroffene Files

| File | Änderung |
|------|----------|
| `backend/src/main/java/com/budgetbuddy/transaction/SwissBankStatementParser.java` | Format-Erkennung als positives Signal auswerten; leere Liste nur ohne Signatur → Exception; Javadoc |
| `backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java` | Javadoc-Vertrag anpassen (count kann 0 sein) |
| `backend/src/main/java/com/budgetbuddy/transaction/UnsupportedStatementFormatException.java` | Javadoc präzisieren |
| `backend/src/test/java/com/budgetbuddy/transaction/SwissBankStatementParserTest.java` | Neue Unit-Tests je AC-Fall |
| `backend/src/test/java/com/budgetbuddy/transaction/PdfImportControllerIntegrationTest.java` | Integrationstest: 200 mit `count: 0` |

## Implementierungsschritte

1. `detectFormat`-Resultat in `parse()` in Variable halten.
2. Positives Signatur-Kriterium: Format ≠ `GENERIC` **oder** (`GENERIC` und
   `Saldovortrag`-/`Anfangssaldo`-Zeile vorhanden).
3. Bei leerem Parse-Resultat: nur ohne positives Signal
   `UnsupportedStatementFormatException` werfen, sonst leere Liste zurückgeben.
4. Javadoc-Verträge (`parse()`, `importPdf()`) aktualisieren.
5. Tests (siehe Test-Strategie), `mvn test` grün.

## Test-Strategie

- **Unit (`SwissBankStatementParserTest`):**
  - Generisches Layout, nur `Saldovortrag`-Zeile, 0 Buchungen → leere Liste, keine Exception
  - Kopfzeilen-Signatur (PostFinance), 0 Buchungen → leere Liste, keine Exception
  - Regression #83: Text ohne jede Signatur → weiterhin `UnsupportedStatementFormatException`
- **Integration (`PdfImportControllerIntegrationTest`):**
  - Upload eines erkannten Auszugs ohne Buchungen → `200`, `{"count": 0}`, keine Persistierung

## Acceptance Criteria (aus dem Issue)

- [ ] Erkanntes Format + 0 Buchungen → keine `UnsupportedStatementFormatException`
- [ ] Erfolgreicher Import mit 0 Transaktionen (Team-Entscheid: Variante „Erfolg mit count=0")
- [ ] Text ohne Format-Signatur → weiterhin `UnsupportedStatementFormatException` (Regression #83)
- [ ] Unit-Test je Fall
