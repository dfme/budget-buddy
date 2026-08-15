# [FE-CAT-05] Pagination der Transaktionsliste (20 + «Weitere laden»)

- **Issue:** [#153](https://github.com/dfme/budget-buddy/issues/153)
- **Task-ID:** `FE-CAT-05`
- **Branch:** `feature/FE-CAT-05-transaktions-pagination`
- **Story:** US-13 — Einzeltransaktionen pro Kategorie einsehen
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-15

## Ausgangslage

`GET /transactions?month=YYYY-MM&category=<Label>` liefert seit FE-CAT-03 (#32) **alle** Buchungen
des Monats auf einmal. Die zweite Acceptance Criteria von US-13 schliesst genau das aus („kein
ungepaginierter Vollload"). Dieser Task ergänzt die Begrenzung im Backend und den
«Weitere laden»-Button im Frontend; damit ist US-13 vollständig.

## Entscheide

### 1. Paging in der Datenbank, nicht im Service

Die Begrenzung greift über `Slice<Transaction>` + `Pageable` in der Query, nicht als Slice auf einer
bereits geladenen Liste.

Ein In-Memory-Slice wäre auf einem Cluster **korrekt** — es überlebt kein Zustand im Speicher, der
Offset kommt bei jedem Request vom Client, zwei Instanzen liefern für denselben Request dieselbe
Seite. Er ist aber nicht nachhaltig: die Last pro Request wüchse mit der Monatsgrösse statt mit der
Seitengrösse, und zwar bei jedem «Weitere laden»-Klick erneut, auf einem Cluster zusätzlich
multipliziert mit der Instanzenzahl über denselben Neon-Free-Connection-Pool. Die ADR-3-Kritik
(`docs/prompts/03_01_prompt_adr_kritik_sergio.md`) nennt genau das als offenen Punkt; eine
Pagination, die den Vollload beibehält, würde ihn nur verstecken.

`Slice` statt `Page`: Spring Data holt intern `size + 1` Zeilen, `hasNext()` fällt daraus ab — die
Count-Query von `Page` wird nicht gebraucht, weil die Antwort keine Gesamtzahl enthält.

**Preis:** die Regel „`category IS NULL` zählt als *Sonstiges*" steht danach an zwei Stellen — im
JPQL-Filter und in `TransactionListService.labelOf()` für die Antwort. Das Label selbst kommt an
beiden Stellen aus `Category.SONSTIGES.getLabel()`, es gibt also keinen zweiten String. Beide
Stellen werden mit Verweis aufeinander kommentiert.

### 2. `page`/`size` statt `limit`/`offset`

Folgt aus Entscheid 1: `PageRequest.of(page, size, sort)` ist nativ, ein beliebiger Offset bräuchte
ein selbstgebautes `Pageable`. AC 1 lässt beide Formen ausdrücklich zu. Jeder benötigte Request ist
damit ausdrückbar, weil jedes Fenster entweder bei `page=0` beginnt oder an einer Vielfachen-von-20-
Grenze (siehe Entscheid 5).

### 3. Antwort als Wrapper-DTO

`TransactionListResponse { transactions, hasMore }` statt eines nackten Arrays. In Swagger
selbsterklärend, im Frontend typsicher. Es ist ein Breaking Change am Antwort-Body; einziger
Consumer ist `TransactionService.list()`, kein E2E-Test hängt daran.

### 4. Defaults und Grenzen

`page=0`, `size=20` — ein Aufruf ohne Parameter verhält sich damit exakt wie der erste
Seitenaufruf (AC 3), eine Zahl statt zweier. `size` gilt von 1 bis 100, `page` ab 0; alles andere
ist ein 400 über den bestehenden `TransactionExceptionHandler`. Die Obergrenze verhindert, dass
`size=100000` den Endpoint zum Vollload zurückdreht.

### 5. Nach einer Korrektur wird das geladene **Fenster** neu geladen

`refreshAfterCategoryChange()` ruft heute `loadDrilldown()`, und das **ersetzt** die Liste durch das
Ergebnis des Requests. Mit Pagination wäre das Seite 0, also 20 Einträge: wer dreimal «Weitere
laden» geklickt hat und dann korrigiert, sähe die Liste auf 20 Einträge zurückspringen.

**Verworfene Variante:** die korrigierte Zeile nur lokal entfernen und gar nicht nachladen. Das
erzeugt einen Offset-Versatz, der Buchungen unerreichbar macht. Bei 21 Buchungen sind 20 geladen
(`hasMore = true`); wird die sechste korrigiert, hat der Server noch 20 und die 21. rutscht auf
Index 19 — in den bereits geladenen Bereich. Der Client zählt aber weiter „Offset 0–19 gesehen" und
fragt beim nächsten Klick `page=1` an: leer. Die Buchung ist ohne Monats-Reload nicht mehr
erreichbar. Eine sichtbare Unschönheit gegen einen unsichtbaren Fehler getauscht — und AC 7 meint
genau diese Stelle.

**Gewählt:** die Komponente merkt sich `pagesLoaded`; das Fenster ist immer „die ersten
`pagesLoaded × 20` Einträge". Der Refresh fragt in **einem** Request `page=0&size=pagesLoaded*20` an.
Korrekt, weil es nur zwei Fälle gibt:

- Die Menge ist noch mindestens so gross wie das Fenster → der Refresh liefert das volle Fenster,
  die Offsets der Folgeseiten stimmen unverändert.
- Die Menge ist ins Fenster geschrumpft → der Refresh liefert alles Verbleibende und `hasMore` wird
  `false`. Es gibt keine Folgeseite, die etwas überspringen könnte.

Im Beispiel oben liefert der Refresh mit `size=20` alle 20 verbliebenen Einträge inklusive der
ursprünglich 21., `hasMore: false`, Button weg.

**Bekannte Grenze:** ab 6 geladenen Seiten überschreitet `pagesLoaded × 20` die Obergrenze von 100.
Der Refresh lädt dann 100 statt 120 Einträge, `pagesLoaded` fällt auf 5 zurück und der Button
erscheint wieder. Kein Datenverlust und keine übersprungene Buchung — die Liste ist nur kürzer als
vorher. Für eine einzelne Kategorie mit über 100 Buchungen in einem Monat liegt das ausserhalb
dessen, was die Personas erzeugen.

Das **Summary** wird weiterhin serverseitig neu geladen: Summen, Anteile und Donut brauchen echte
Serverwahrheit.

## Betroffene Files

### Backend — geändert

- `backend/src/main/java/com/budgetbuddy/transaction/TransactionRepository.java` — zwei neue
  Methoden (ungefiltert als `Pageable`-Overload, gefiltert als `@Query` mit `coalesce`); die
  bestehende 3-Argument-Variante bleibt unangetastet, `TransactionSummaryService` nutzt sie weiter
- `backend/src/main/java/com/budgetbuddy/transaction/TransactionListService.java` —
  `list(userId, month, categoryLabel, page, size)`, Sortierung als `Sort`-Konstante, In-Memory-
  Filter und -Sortierung entfallen
- `backend/src/main/java/com/budgetbuddy/transaction/TransactionListController.java` — zwei
  `@RequestParam` mit `defaultValue` + `@Parameter`-Doku, geänderter Rückgabetyp
- `backend/src/main/java/com/budgetbuddy/transaction/TransactionExceptionHandler.java` —
  `InvalidPaginationException` → 400, ohne Body wie die Nachbarn

### Backend — neu

- `backend/src/main/java/com/budgetbuddy/transaction/dto/TransactionListResponse.java`
- `backend/src/main/java/com/budgetbuddy/transaction/InvalidPaginationException.java`

### Frontend — geändert

- `frontend/src/app/transactions/transaction.model.ts` — `TransactionPage`-Interface
- `frontend/src/app/transactions/transaction.service.ts` — `list(month, category?, page?, size?)`
  → `Observable<TransactionPage>`
- `frontend/src/app/transactions/category-overview.ts` — `Drilldown` um `pagesLoaded`, `hasMore`,
  `loadingMore`; neues `loadMore()`; `loadDrilldown()` hängt an statt zu ersetzen;
  `refreshAfterCategoryChange()` nach Entscheid 5
- `frontend/src/app/transactions/category-overview.html` — «Weitere laden» als
  `<button appButton variant="ghost">` unter der Buchungsliste
- `frontend/src/app/transactions/category-overview.scss` — Platzierung des Buttons

## Implementierungsschritte

1. `TransactionListResponse` und `InvalidPaginationException` anlegen, Handler-Eintrag ergänzen
2. Repository: `Slice`-Methoden mit `Pageable` (ungefiltert + `coalesce`-gefiltert)
3. Service: Parameter validieren, `PageRequest.of(page, size, SORT)` bauen, Slice → DTO mappen,
   `hasNext()` durchreichen
4. Controller: Parameter, Defaults, Swagger-Beschreibung inkl. Default- und Maximalwert
5. Backend-Tests, `mvn verify`
6. Frontend: Modell + Service, dann Komponente (`loadMore`, Anhängen,
   `refreshAfterCategoryChange`), dann Template + SCSS
7. Frontend-Tests, `npm test`, `ng build`

## Test-Strategie

### Unit — `TransactionListServiceTest` (JUnit 5 + Mockito)

- Default `page=0, size=20` erzeugt das erwartete `PageRequest` inkl. Sortierung
  `buchungsdatum DESC, id DESC`
- gefilterter vs. ungefilterter Pfad ruft die jeweils richtige Repository-Methode
- `hasNext()` des Slice landet unverändert in `hasMore`
- `size=0`, `size=101`, `page=-1` → `InvalidPaginationException`
- nicht kategorisierte Buchung kommt weiterhin als `Sonstiges` in der Antwort an

### Integration — `TransactionListControllerIntegrationTest` (Testcontainers PostgreSQL)

- bestehende jsonPath-Ausdrücke auf `$.transactions[...]` umstellen; alle heutigen Fälle bleiben
  erhalten, inklusive `doesNotLeakTransactionsOfAnotherUser`
- 21 Buchungen einer Kategorie: ohne Parameter 20 Einträge + `hasMore: true`; `page=1` liefert die
  21. + `hasMore: false`
- Seitengrenze überlappt nicht und lässt nichts aus (IDs beider Seiten disjunkt und vollständig)
- `size=101` und `page=-1` → 400; ohne JWT → 401
- Mandantentrennung mit Pagination: Marc fragt `page=0` derselben Kategorie ab und sieht nur seine
  Buchung — der neue Query-Pfad ist der eigentliche Risikoort dieses PRs

### Frontend — Vitest / Angular TestBed

- `transaction.service.spec.ts`: `page`/`size` landen als Parameter, Defaults 0/20, Antwortobjekt
  wird durchgereicht
- `category-overview.spec.ts`: initial 20 Einträge + sichtbarer Button; Klick fordert `page=1` an
  und hängt an, ohne die ersten 20 erneut zu laden; Button verschwindet bei `hasMore: false`;
  Korrektur auf einem nachgeladenen Eintrag entfernt die Zeile und lädt Summary + Fenster neu;
  21 Buchungen, 20 geladen, Korrektur → Refresh fragt `page=0&size=20` an und die 21. Buchung steht
  danach in der Liste (der Fall, den die verworfene Variante verloren hätte)

### E2E

Keiner. `e2e/tests/` fasst `GET /transactions` heute nicht an, und US-13 ist eine *Should*-Story —
die E2E-Regel in CLAUDE.md deckt die Must-Haves US-03/04/05/06 ab.

## Acceptance Criteria (aus #153)

- [ ] `GET /transactions` nimmt eine Begrenzung entgegen und liefert nur den angeforderten
      Ausschnitt → `page`/`size`
- [ ] Die Antwort lässt erkennen, ob weitere Einträge folgen → `hasMore`
- [ ] Ein Aufruf ohne Begrenzung liefert weiterhin eine definierte, dokumentierte Menge → Default
      `size=20`, in Swagger dokumentiert
- [ ] Die aufgeklappte Kategorie zeigt initial 20 Buchungen
- [ ] Ein «Weitere laden»-Button hängt die nächsten Einträge an, ohne die bereits sichtbaren neu zu
      laden
- [ ] Der Button verschwindet, wenn keine weiteren Buchungen folgen
- [ ] Die Kategorie-Korrektur (FE-CAT-03) funktioniert auch auf nachgeladenen Einträgen
- [ ] Neuer/geänderter Parameter ist in Swagger UI dokumentiert
