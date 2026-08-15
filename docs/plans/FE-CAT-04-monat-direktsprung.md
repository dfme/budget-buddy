# [FE-CAT-04] Direktsprung zu einem Monat in der Kategorie-Übersicht

- **Issue:** [#144](https://github.com/dfme/budget-buddy/issues/144)
- **Task-ID:** `FE-CAT-04`
- **Branch:** `feature/FE-CAT-04-monat-direktsprung`
- **Story:** US-12 — Zwischen Monaten wechseln
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-15

## Ausgangslage

Der Monat-Selector der Kategorie-Übersicht ist ein reiner Prev/Next-Stepper. Wer einen Kontoauszug
vom Juni 2025 ansieht, klickt sich vom aktuellen Monat aus durch 14 Einzelschritte — und jeder löst
einen eigenen `GET /transactions/summary` aus. Dazu steht der Monat nicht in der URL: ein Reload
wirft die Navigation zurück, ein bestimmter Monat lässt sich weder verlinken noch als Lesezeichen
ablegen.

Der Stepper war in FE-CAT-01 eine bewusste Entscheidung (Review-Feedback aus PR #90: kein
Vorblättern in beliebig viele leere Zukunftsmonate). Dieser Task **ergänzt** ihn um einen
Direktsprung, er ersetzt ihn nicht.

**Basis:** Dieser Branch zweigt von `feature/FE-CAT-05-transaktions-pagination` ab, nicht von `main`
— FE-CAT-05 fasst dieselbe Komponente an (`loadDrilldown` heisst dort neu `loadPage`,
`refreshAfterCategoryChange` ist umgebaut). Der PR läuft entsprechend gegen diesen Branch; GitHub
zieht die Base auf `main` nach, sobald #170 gemergt ist.

## Entscheide

### 1. `GET /transactions/months` liefert die Monate mit Ausgaben

Das Issue lässt die Variante offen (Dropdown über Jahr + Monat, Monats-Picker, oder Backend-
Endpoint) und schreibt den Endpoint einem eigenen BE-Task zu. Entschieden wurde: Endpoint, und zwar
in diesem PR.

Grund ist ein konkreter Befund aus der Analyse: die Test-PDFs enthalten Buchungen aus **2019, 2021
und 2025**. Eine im Frontend festgelegte Jahresspanne träfe genau die Monate nicht, wegen denen der
Task existiert — zu kurz und alte Kontoauszüge sind unerreichbar, zu lang und die Liste besteht aus
leeren Jahren. Nur der Endpoint weiss, welche Monate überhaupt Daten haben.

Die Scope-Erweiterung über die Frontend-Area des Issues hinaus wird im PR-Body deklariert.

Ein nativer Monats-Picker (`input type="month"`) wurde verworfen: die Browser-Unterstützung ist
uneinheitlich, und fällt sie weg, steht ein Textfeld im Format `YYYY-MM` da — für Lara schlechter
als der heutige Stepper.

### 2. Der Monat steht als Query-Parameter in der URL: `/categories?month=2026-06`

Nicht als Pfad-Segment, und das ist keine Geschmacksfrage. Der `SpaForwardController` mappt
**exakte Pfade, kein `/**`** — sein Javadoc begründet das ausführlich: `/import` ist Frontend-Route
und API-Präfix zugleich, ein Wildcard-Pattern würde von `SecurityConfig.SPA_GET_PATHS` als
`permitAll` übernommen und einen API-Endpoint ohne Auth erreichbar machen (Risiko #2).

- `?month=2026-06` trifft den Server als `GET /categories`. Der Query-String ist nicht Teil des
  Pfads, das exakte Pattern greift unverändert. **Null Änderung an der Server-Seite.**
- `/categories/2026-06` hätte das exakte Pattern nicht getroffen → 404 beim Hard-Reload. Der Fix
  wäre `/categories/*` in `CLIENT_ROUTE_PATTERNS` gewesen — also die Doppelliste anfassen, deren
  Pflege [#126](https://github.com/dfme/budget-buddy/issues/126) als Fehlerquelle benennt, an der
  Stelle, an der das Javadoc vor Wildcards warnt.

Zum Stand von #126: das konkrete Symptom (Deep-Link auf `/categories` → 404/401) ist mit INFRA-14
behoben, `/categories` steht in beiden Listen und `e2e/tests/spa-routing.spec.ts` prüft es gegen das
echte JAR. Offen geblieben ist der strukturelle Teil des Issues (API unter `/api/**`). #144 ist
davon nicht blockiert.

**Zusicherung dieses Plans:** `SecurityConfig` und `SpaForwardController` werden nicht angefasst.
Passiert es doch, ist Entscheid 2 gebrochen.

### 3. Zweiweg-Kopplung mit Gleichheits-Wache

Stepper und Sprung setzen das Signal und laden **synchron** wie heute, und schieben die URL
hinterher. Die Subscription auf `queryParamMap` ignoriert alles, was dem angezeigten Monat schon
entspricht, und reagiert damit nur auf äussere Änderungen: Browser-Zurück/-Vorwärts, Deep-Link, von
Hand editierte Adresse.

```ts
private goTo(month: string): void {
  this.month.set(month);
  this.load();                       // synchron, wie heute
  void this.router.navigate([], {
    relativeTo: this.route,
    queryParams: { month },
    queryParamsHandling: 'merge',
  });
}

this.route.queryParamMap.subscribe((params) => {
  const month = params.get('month') ?? currentMonth();
  if (month === this.month()) return;   // <- die Wache
  this.month.set(month);
  this.load();
});
```

Verworfen wurden:

- **URL als alleinige Quelle** (Stepper navigiert nur, Neuladen hängt allein an der URL):
  konzeptionell sauberer, macht aber jeden Monatswechsel asynchron — die 8 Aufrufstellen von
  `previousMonth()`/`nextMonth()` in `category-overview.spec.ts` bekämen `await`, und die Tests
  prüften danach «Klick → Navigation → Request» statt «Klick → Request».
- **URL nur schreiben, einmal lesen**: kleinster Diff, aber Browser-Zurück ändert die Adresse und
  nicht die Anzeige — die Seite zeigt dann einen anderen Monat als ihre eigene URL. Deckt die ACs
  formal ab und wäre trotzdem als bekannte Lücke auszuweisen.
- **`withComponentInputBinding()`**: Angulars eigener Weg, ändert die 8 Testfälle aber genauso und
  kostet zusätzlich eine globale Zeile in `app.config.ts`, die alle Routen betrifft.

**Preis der gewählten Variante:** zwei Schreiber auf denselben Zustand, zusammengehalten von einer
Zeile Wache. Sie wird kommentiert und durch den Browser-Zurück-Test abgesichert, der bei halbierter
Kopplung rot wird.

### 4. Das Dropdown wohnt in `MonthNav`

Als optionaler `months`-Input mit `select`-Output. Stepper und Sprung sind ein Bedienelement und
gehören in eine Komponente, mit einer Spec und einer a11y-Prüfung. Ohne `months` rendert nichts —
der Styleguide, der `MonthNav` ebenfalls einbindet, bleibt unverändert.

### 5. Der angezeigte Monat ist immer wählbar

Auch wenn der Endpoint ihn nicht liefert. Sonst stünde das Dropdown bei leerem Konto oder einem per
Deep-Link geöffneten leeren Monat auf einem fremden Wert.

### 6. Zukunftsmonate sind auch im Dropdown gesperrt

AC 4 verlangt die Sperre am Stepper; ein Dropdown, das Zukunftsmonate anböte, hebelte sie aus.
Gefiltert wird mit derselben Regel wie `isCurrentMonth()`.

### 7. Fällt der Monats-Endpoint aus, degradiert das Dropdown still

Es zeigt dann nur den angezeigten Monat, ohne Fehlermeldung. Seite und Stepper funktionieren
weiter; eine rote Meldung für ein ausgefallenes Komfort-Element stünde in keinem Verhältnis. Wird im
PR-Body als bewusste Auslassung benannt.

## Betroffene Files

### Backend

- `backend/src/main/java/com/budgetbuddy/transaction/TransactionRepository.java` — Query auf
  `distinct year/month` der Ausgaben eines Users, absteigend
- `backend/src/main/java/com/budgetbuddy/transaction/TransactionListService.java` —
  `availableMonths(userId)`, formatiert die Tupel zu `YYYY-MM`
- `backend/src/main/java/com/budgetbuddy/transaction/TransactionListController.java` —
  `GET /transactions/months` inklusive `@Operation`
- `backend/src/test/java/com/budgetbuddy/transaction/TransactionListOpenApiTest.java` — neuer Pfad

### Frontend

- `frontend/src/app/transactions/transaction.service.ts` — `availableMonths()`
- `frontend/src/app/shared/month-nav/month-nav.ts` / `.html` / `.scss` — optionales Dropdown mit
  `<label>`, `select`-Output
- `frontend/src/app/transactions/category-overview.ts` — `Router`/`ActivatedRoute`, Wache,
  `selectMonth()`, Monatsliste laden
- `frontend/src/app/transactions/category-overview.html` — Monatsliste durchreichen

### E2E

- `e2e/tests/spa-routing.spec.ts` — ein Fall: `/categories?month=2025-06` liefert 200 mit
  `<app-root>`

### Ausdrücklich nicht

`SecurityConfig` und `SpaForwardController` (siehe Entscheid 2).

## Implementierungsschritte

1. Backend: Repository-Query, Service-Methode, Endpoint, Swagger
2. Backend-Tests inklusive Mandantentrennung, `mvn verify`
3. `MonthNav` um das Dropdown erweitern, Spec dazu
4. `CategoryOverview`: Router-Verdrahtung, Wache, `selectMonth()`, Monatsliste
5. Frontend-Tests ergänzen, `npm test`, `ng build`
6. E2E-Fall ergänzen und ausführen

## Test-Strategie

### Backend

- Unit (`TransactionListServiceTest`): Reihenfolge absteigend, Format `YYYY-MM`, nur Ausgaben,
  leere Liste ohne Transaktionen
- Integration (`TransactionListControllerIntegrationTest`, Testcontainers): Laras Monate ≠ Marcs
  Monate — der Endpoint gibt preis, *wann* jemand Geld ausgegeben hat, das ist hier der Risikoort;
  Gutschriften erscheinen nicht; ohne JWT 401
- OpenAPI (`TransactionListOpenApiTest`): `$.paths['/transactions/months'].get` dokumentiert

### Frontend

- `month-nav.spec.ts`: Dropdown nur bei übergebenen Monaten; Auswahl feuert `select`; `<label>`
  vorhanden (AC 5); Stepper unverändert bedienbar
- `category-overview.spec.ts`:
  - Deep-Link `?month=2025-06` lädt diesen Monat (AC 3)
  - Sprung von Juli 2026 auf Juni 2025 setzt **genau einen** Summary-Request ab (AC 2) —
    `httpMock.verify()` fällt um, sobald pro übersprungenem Monat einer rausginge
  - Browser-Zurück nach einem Sprung lädt den vorigen Monat — der Test, der bei halbierter
    Kopplung rot wird
  - Sprung und Stepper schreiben beide den Monat in die URL
  - Zukunftsmonate weder im Dropdown noch am Stepper (AC 4, AC 6)
  - Der angezeigte Monat steht im Dropdown, auch wenn der Endpoint ihn nicht liefert (Entscheid 5)
  - Fehlgeschlagener Monats-Request bricht die Seite nicht (Entscheid 7)

### E2E

Der Deep-Link mit Query-String gegen das echte `-Pprod`-JAR. Eine TestBed-Assertion belegt nur, dass
die Komponente den Parameter liest; dass der Server die URL ausliefert, belegt sie nicht — und genau
diese Lücke war der Inhalt von #126.

## Acceptance Criteria (aus #144)

- [ ] Ein beliebiger Monat ist ohne Zwischenklicks direkt erreichbar
- [ ] Beim Sprung wird genau ein Request abgesetzt, nicht einer pro übersprungenem Monat
- [ ] Der gewählte Monat steht in der URL — Reload und Deep-Link landen wieder im selben Monat
- [ ] Der bestehende Prev/Next-Stepper bleibt erhalten, inkl. der Sperre für Zukunftsmonate
- [ ] Der Direktsprung ist per Tastatur bedienbar und beschriftet (a11y, analog zu den bestehenden
      Basiskomponenten)
