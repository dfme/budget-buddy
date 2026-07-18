# [BE-PDF-02] PdfImportService

| Feld | Wert |
|------|------|
| Issue | [#17](https://github.com/dfme/budget-buddy/issues/17) |
| Task-ID | BE-PDF-02 |
| Branch | `feature/BE-PDF-02-pdf-import-service` |
| Basis | `main` (enthält BE-PDF-01 seit PR #81) + Merge von #79 (BE-CAT-03) und #82 (BE-CAT-05) |
| Depends on | #13 (BE-PDF-01, gemergt), #16 (BE-CAT-03, PR #79 offen), #20 (BE-CAT-05, PR #82 offen — liefert Transaction-Entity) |

## Ziel

Orchestriert den vollständigen Import-Flow: SHA-256-Hash → Duplikatcheck → PDFBox-Parse →
Kategorisierung → Persistierung. 30s-Timeout. PDF-Binärdaten werden nicht in der DB gespeichert
(nur der Hash für den Duplikatcheck).

## Entscheide

1. **Stacked auf die offenen Dependency-PRs**: Branch von `main`, Merges von
   `feature/BE-CAT-03-hybrid-categorization` und `feature/BE-CAT-05-transactions-summary`.
   Der PR-Diff zeigt bis zu deren Merge auch diese Änderungen — im PR-Text dokumentiert.
   (Ursprünglich als Stack auf #81 geplant; #81 wurde während der Umsetzung gemergt.)
2. **Kooperatives Deadline statt Thread-Timeout**: injizierte `Clock`, Property
   `budgetbuddy.import.timeout-seconds` (Default 30). Check vor jedem Kategorisierungs-Call;
   überschritten → `PdfImportTimeoutException`. Einzelne hängende Claude-Calls begrenzt bereits
   der SDK-Timeout + Circuit Breaker (BE-CAT-02). Kein Extra-Thread, kein Interrupt.
3. **409/408 via `@ResponseStatus` auf den Exceptions** — der Endpoint (BE-PDF-03, #18) erbt
   das Mapping; die ACs 1/3 sind auf Service-Ebene als Exceptions testbar.
4. **Duplikatcheck pro User** (`userId` + `pdfSha256`): dasselbe PDF bei einem anderen User ist
   erlaubt (zwei Nutzer können denselben Gemeinschaftskonto-Auszug importieren).
5. **Kategorisierungs-Input ist `ParsedTransaction.fullText()`** (Buchungstext + Detailzeilen)
   — bei Überweisungen steht der Empfänger in den Details (siehe a5e5622).
6. **Persistierung erst nach vollständiger Kategorisierung** (`saveAll`) in `@Transactional`
   → bei Timeout/Fehler kein Partial-Import.
7. **`Optional.empty()` vom Port → `Sonstiges`**: AC 5 verlangt eine Kategorie für jede
   importierte Transaktion.
8. 0 geparste Transaktionen → `ImportResult` mit Count 0; Exception-Verhalten ist BE-PDF-04
   (#83) und hier bewusst out of scope.

## Files

### Neu
- `transaction/PdfImportService.java` — Orchestrierung, `@Transactional`
- `transaction/ImportResult.java` — Record (`pdfSha256`, `transactionCount`)
- `transaction/DuplicatePdfImportException.java` — `@ResponseStatus(CONFLICT)`
- `transaction/PdfImportTimeoutException.java` — `@ResponseStatus(REQUEST_TIMEOUT)`
- `transaction/PdfImportServiceTest.java` — Unit (Mockito, steuerbare Clock)
- `transaction/PdfImportServiceIntegrationTest.java` — Integration (SQLite + Flyway +
  UBS-Fixture-PDF, `CategorizationPort` als `@MockitoBean`)

### Geändert
- `transaction/TransactionRepository.java` — + `existsByUserIdAndPdfSha256(Long, String)`

## Implementierungsschritte

1. SHA-256 der PDF-Bytes (Hex, `MessageDigest` + `HexFormat`).
2. Duplikatcheck → `DuplicatePdfImportException` (AC 1).
3. Parse via `SwissBankStatementParser`; `PasswordProtectedPdfException`/`PdfParseException`
   propagieren unverändert.
4. Pro Transaktion: Deadline-Check → `CategorizationPort.categorize(fullText())`,
   `orElse(SONSTIGES)` (AC 5).
5. `saveAll` der `Transaction`-Entities mit Kategorie-Label + `pdfSha256`; die PDF-Bytes
   werden nie persistiert (AC 4).

## Test-Strategie

- **Unit**: Happy Path (Kategorie + Hash gespeichert), Duplikat (Parser/Port nie aufgerufen),
  Timeout beim 2. Call (`saveAll` nie aufgerufen), `Optional.empty()` → Sonstiges,
  Kategorisierung nutzt `fullText()`, 0 Transaktionen → Count 0, Parser-Exceptions propagieren.
- **Integration**: UBS-Fixture (28 Transaktionen) → alle persistiert mit Kategorie + Hash,
  Beträge BigDecimal-exakt (Umsatztotal-Kreuzprobe); zweiter Import derselben Bytes → 409-Exception;
  kein PDF-Blob in der DB.

## Acceptance Criteria (aus Issue #17)

- [ ] Duplikat (gleicher SHA-256) → 409-Response
- [ ] Import-Flow läuft innerhalb von 30s durch
- [ ] Timeout → 408-Response
- [ ] PDF-Binärdaten werden nicht in der DB gespeichert
- [ ] Alle importierten Transaktionen erhalten eine Kategorie
