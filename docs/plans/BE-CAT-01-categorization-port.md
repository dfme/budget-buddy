# [BE-CAT-01] CategorizationPort Interface und LookupTableService

- **Issue:** #14
- **Task-ID:** BE-CAT-01
- **Branch:** `feature/BE-CAT-01-categorization-port`
- **Depends on:** #7 (DB-04, `category_lookup`-Tabelle inkl. Seed — bereits gemergt)

## Entscheide

- **Port-Signatur:** `Optional<Category> categorize(String transactionText)`. Die AC verlangt
  `Optional.empty()` für unbekannte Händler — das ersetzt das illustrative
  `Category categorize(...)`-Snippet aus CLAUDE.md.
- **Category als Java-`enum`** mit deutschem Anzeige-Label + `fromLabel(String)` für das
  Mapping DB-String → Enum. Type-safe, mockbar, verhindert ungültige Kategorien.
- **Substring-/Contains-Matching, case-insensitiv:** Ein Händler matcht, wenn sein Pattern
  (case-insensitiv) im Transaktionstext enthalten ist. Deckt reale PDF-Texte wie
  `"MIGROS BERN 044..."` ab und entspricht dem CLAUDE.md-Beispiel
  `categorize("DIGITEC GALAXUS AG 044 913 2323")`. JPA-`@Query` mit
  `upper(:text) LIKE '%'||upper(pattern)||'%'`, deterministische Sortierung nach Pattern-Länge
  absteigend (längstes/spezifischstes Pattern gewinnt).
- **DB-Zugriff via JPA-Entity + Repository** (analog `User`/`UserRepository`).
- **Kein REST-Endpoint** in diesem Task → DoD-Zeile "neue API-Endpoints in Swagger UI" ist N/A
  (nur Service + Interface).

## Betroffene Files

Neu:

- `backend/src/main/java/com/budgetbuddy/categorization/Category.java`
- `backend/src/main/java/com/budgetbuddy/categorization/CategorizationPort.java`
- `backend/src/main/java/com/budgetbuddy/categorization/CategoryLookup.java`
- `backend/src/main/java/com/budgetbuddy/categorization/CategoryLookupRepository.java`
- `backend/src/main/java/com/budgetbuddy/categorization/LookupTableService.java`
- `backend/src/test/java/com/budgetbuddy/categorization/LookupTableServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/categorization/LookupTableServiceIT.java`

Geändert: keine (`package-info.java` existiert bereits).

## Implementierungsschritte

1. `Category`-Enum (13 Werte, Label-Feld, `fromLabel` mit klarer Exception bei unbekanntem Label).
2. `CategorizationPort`-Interface (`Optional<Category> categorize(String)`).
3. `CategoryLookup`-Entity + `CategoryLookupRepository` mit Substring-Match-`@Query`.
4. `LookupTableService`: null/blank → `Optional.empty()`; Query; ersten Treffer-String → `Category` mappen.
5. Unit-Test (Mockito) + Integrationstest (Temp-File-DB + Flyway-Seed).
6. `mvn package`; lokaler Review.

## Test-Strategie

- **Unit** (`LookupTableServiceTest`, Mockito): bekannt→`Optional.of`, unbekannt→`empty`,
  String→Enum-Mapping, blank/null→`empty`.
- **Integration** (`LookupTableServiceIT`, echtes SQLite + Flyway-Seed): `"MIGROS BERN 044..."`→
  Lebensmittel, lowercase `"digitec galaxus"`→Shopping, unbekannt→`empty`.

## Acceptance Criteria (aus #14)

- [ ] Interface `CategorizationPort` mit Methode `categorize(String): Category` existiert
- [ ] LookupTableService findet bekannte Händler case-insensitiv
- [ ] Unbekannte Händler geben `Optional.empty()` zurück
- [ ] Service ist via Interface austauschbar (für Tests mockbar)
