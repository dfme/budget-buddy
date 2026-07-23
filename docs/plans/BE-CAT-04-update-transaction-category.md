# [BE-CAT-04] PUT /transactions/{id}/category

- **Issue:** [#19](https://github.com/dfme/budget-buddy/issues/19)
- **Task-ID:** BE-CAT-04
- **Branch:** `feature/BE-CAT-04-update-transaction-category`
- **Story:** US-05 (Transaktionen kategorisieren — manuelle Korrektur)

## Ziel

Endpoint `PUT /transactions/{id}/category` — Kategorie einer Transaktion manuell
setzen. Schreibt (a) die neue Kategorie in `transactions` und (b) einen Lerneintrag
in `category_lookup`, sodass die nächste Transaktion desselben Händlers ohne
Claude-Call kategorisiert wird (Lerneffekt, ADR-6 Schritt 3).

## Entscheidungen

- **Lookup-Pattern = `buchungstext` verbatim** (getrimmt). Deterministisch, keine
  Fehlkategorisierung; matcht künftige Transaktionen nur, wenn der Text dieses
  Pattern als Substring enthält. Smartere Händler-Token-Extraktion ist ein Follow-up.
- **Modulgrenze:** Das `transaction`-Modul greift nicht direkt auf
  `CategoryLookupRepository` zu (CLAUDE.md). Neuer Schreib-Port
  `CategoryLearningPort` im `categorization`-Modul, analog zum Lese-`CategorizationPort`.
- **Category-Format im Request:** deutsches Label (z. B. `"Lebensmittel"`),
  konsistent zur Summary-API. Konvertierung via `Category.fromLabel`.
- **Ownership:** Fremde/nicht-existente Transaktion → 404 (nicht 403), gegen
  Enumeration.
- **Antwort:** 200 mit aktualisierter Transaktion (`TransactionResponse`),
  analog zu `PUT /users/me/income`.

## Neue Files

**categorization/**
1. `CategoryLearningPort.java` — Interface `void learn(String merchantPattern, Category category)`
2. `CategoryLearningService.java` — `@Service @Transactional`; Upsert via
   `repository.save(new CategoryLookup(pattern, category.getLabel()))`

**transaction/**
3. `dto/UpdateCategoryRequest.java` — record `@NotBlank String category`
4. `dto/TransactionResponse.java` — record (id, buchungsdatum, buchungstext, betrag, income, category)
5. `TransactionNotFoundException.java` — → 404
6. `InvalidCategoryException.java` — → 400
7. `TransactionCategoryService.java` — `@Transactional updateCategory(userId, txId, label)`
8. `TransactionCategoryController.java` — `PUT /transactions/{id}/category`, OpenAPI-Annotationen

## Geänderte Files

- `Transaction.java` — Setter `setCategory(String)`
- `TransactionExceptionHandler.java` — `assignableTypes` + `TransactionCategoryController`;
  Handler für `TransactionNotFoundException` (404) und `InvalidCategoryException` (400)

Keine Änderung an `SecurityConfig` — `anyRequest().authenticated()` deckt den Pfad ab.

## Test-Strategie

- **Unit** `TransactionCategoryServiceTest` (Mockito): Update + `learn`-Aufruf ·
  Tx nicht gefunden → 404 · fremder User → 404 · ungültiges Label → 400
- **Integration** `CategoryLearningServiceIntegrationTest` (SQLite+Flyway): `learn`
  legt Eintrag an; erneutes `learn` überschreibt; danach liefert
  `LookupTableService.categorize(...)` die gelernte Kategorie
- **Integration** `TransactionCategoryControllerIntegrationTest` (SQLite+Flyway,
  MockMvc): Happy Path (Tx aktualisiert + `category_lookup` geschrieben + danach
  Lookup ohne Claude) · 404 · 400 · 401

## Acceptance Criteria (aus Issue #19)

- [ ] Kategorie wird in transactions-Tabelle aktualisiert
- [ ] Händler-Pattern wird in category_lookup eingetragen
- [ ] Nächste Transaktion desselben Händlers wird ohne Claude-Call kategorisiert
- [ ] Endpoint in Swagger UI dokumentiert
