# [BE-CAT-10] Vier neue Kategorien (Persönliches, Steuern, Bargeldbezug, Reisen) plus ein Icon je Kategorie

- **Issue:** [#266](https://github.com/dfme/budget-buddy/issues/266)
- **Task-ID:** `BE-CAT-10`
- **Branch:** `feature/BE-CAT-10-kategorien-icons`
- **Story:** US-05 — Transaktionen in Kategorien sehen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-18

## Entscheide

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Icon-Typ | Farb-Emoji wie im Issue vorgeschlagen | Für 17 unterscheidbare Kategorien gibt es kein brauchbares monochromes Vokabular. VS16 (`U+FE0F`) wird überall dort ergänzt, wo die Glyphe sonst als Textvariante kippt (🛡️ ⚕️ 🍽️ 🗂️ ✈️). |
| Badge-Layout | Icon ersetzt den Farbpunkt | Punkt + Icon + Label sind drei Signale in einer kleinen Pille. Die Kategoriefarbe bleibt über die Textfarbe (`:host` trägt bereits `color: $color`). Bei unbekanntem Slug bleibt der neutrale Punkt als Fallback. |
| Lookup-Seeds | keine in diesem Task | `PdfLookupCoverageIntegrationTest` pinnt 144 Treffer exakt und sein Javadoc sagt ausdrücklich, ein neuer Seed solle reissen und die Fixture sei neu zu erzeugen. Die 240er-Fixture enthält 3× `STEUERVERWALTUNG KT. BERN` (`generate_pdf_fixtures.py:701`) — ein `Steuern`-Seed verschiebt die Quote real. Eigenes Issue. |
| Scope-Erweiterung | 16 Code-/Kommentarstellen + `US-05` + `ADR-6` | Das AC nennt nur den Javadoc in `Category.java`. Eine breite Suche fand 16 weitere Stellen, fünf davon harte Testbrüche. `design/variant-a\|b` und `docs/plans` bleiben als historische Artefakte unangetastet. |

### Widersprüche im Issue, die hier aufgelöst werden

1. Die Begründung für Unicode-Glyphs lautet „sofort theme-fähig" und verweist auf Nav `≡` und
   Upload `↑` — monochrome Textglyphen, die `currentColor` erben. Die vorgeschlagene Tabelle ist
   dagegen durchgehend **Farb-Emoji**, die in beiden Themes dieselben festen Farben rendern.
   Theme-fähig sind sie gerade nicht. Entscheid: Emoji trotzdem, weil Erkennbarkeit über 17
   Kategorien schwerer wiegt; die Begründung im Issue ist damit hinfällig, nicht der Entscheid.
2. Das AC nennt `_tokens.scss` für `--cat-<slug>`. Die Farbwerte stehen dort nicht — sie liegen in
   `frontend/src/styles.scss:57–69` (hell) und `:101–113` (dunkel). `_tokens.scss` hält nur die
   `$categories`-Map mit `var()`-Verweisen. Beide Dateien werden angefasst.

## Enum-Reihenfolge und Slugs

Die vier neuen Konstanten werden **vor `SONSTIGES` angehängt**:

```
… BILDUNG, EINKOMMEN, SPAREN, PERSOENLICHES, STEUERN, BARGELDBEZUG, REISEN, SONSTIGES
```

Das hält den Fallback als letzten Eintrag, lässt den Diff der bestehenden 13 bei null und
entspricht der Reihenfolge der Icon-Tabelle im Issue. Enum-Ordinale werden nirgends persistiert
(geprüft: kein `ordinal()` und kein `values()[…]` im Backend) — die Reihenfolge ist reine Anzeige.

Slugs: `persoenliches` (ö→oe, wie der Enum-Name `PERSOENLICHES`), `steuern`, `bargeldbezug`,
`reisen`.

## Neue Farben

Gewählt entlang der vier grössten Farbton-Lücken der bestehenden 13, damit sie sich von allen
Nachbarn trennen.

| Slug | hell | dunkel | Ton |
| ---- | ---- | ------ | --- |
| `persoenliches` | `#5a5ba8` | `#9a9bdc` | Indigo (Lücke 220–268) |
| `steuern` | `#8a7526` | `#d6bb55` | Gold/Oliv (Lücke 30–88) |
| `bargeldbezug` | `#8f4a8f` | `#cb8bcb` | Magenta (Lücke 268–330) |
| `reisen` | `#b8434b` | `#eb8288` | Karmin (Lücke 347–13) |

## Icons

| Kategorie | Glyph | | Kategorie | Glyph |
| --- | --- | --- | --- | --- |
| Wohnen | 🏠 | | Einkommen | 💵 |
| Lebensmittel | 🛒 | | Sparen | 🐷 |
| Transport | 🚆 | | **Persönliches** | 👤 |
| Versicherung | 🛡️ | | **Steuern** | 🧾 |
| Telekom | 📱 | | **Bargeldbezug** | 🏧 |
| Gesundheit | ⚕️ | | **Reisen** | ✈️ |
| Freizeit | 🎭 | | Sonstiges | 🗂️ |
| Restaurant | 🍽️ | | | |
| Shopping | 🛍️ | | | |
| Bildung | 🎓 | | | |

## Betroffene Dateien

### Ändern — Backend

| Datei | Änderung |
| ----- | -------- |
| `backend/…/categorization/Category.java` | 4 Konstanten, Javadoc 13→17 |
| `backend/…/categorization/ClaudeCategorizationService.java:113` | „13 Kategorien" im Javadoc |
| `backend/…/categorization/CategorizationLogRedactionTest.java:117` | „13 Enum-Konstanten" |

### Ändern — Frontend

| Datei | Änderung |
| ----- | -------- |
| `frontend/src/app/shared/category.ts` | `CategoryMeta.icon`, 17 Einträge, `categoryIcon(slug)`-Helper, Javadoc |
| `frontend/src/app/shared/badge/badge.{html,scss,ts}` | Icon statt Punkt, Punkt als Fallback |
| `frontend/src/app/shared/chip/chip.{ts,scss}` | optionaler `category`-Input, Icon vor dem projizierten Inhalt |
| `frontend/src/styles.scss` | 4 `--cat-*` je im `:root`- und im `[data-theme='dark']`-Block |
| `frontend/src/styles/_tokens.scss` | 4 Einträge in `$categories`, Kommentar 13→17 |
| `frontend/src/app/shared/chart/chart-theme.ts:25` | Kommentar |
| `frontend/src/app/transactions/pdf-upload.ts:127` | Kommentar |
| `frontend/src/app/transactions/category-overview.ts:118` | Kommentar |
| `frontend/src/app/styleguide/styleguide.ts:52` | Kommentar |
| `frontend/src/app/styleguide/styleguide.html:45` | sichtbare Überschrift „Badge (13 Kategorien)" → 17 |

### Ändern — Tests

| Datei | Änderung |
| ----- | -------- |
| `ClaudeCategorizationServiceTest.java:264` | `hasSize(13)` → `17` |
| `badge.spec.ts` | Icon-Rendering, `aria-hidden`, Punkt-Fallback bei unbekanntem Slug |
| `chip.spec.ts` | Icon bei gesetztem `category`, kein Icon ohne |
| `styleguide.spec.ts:33` | `toBe(13)` → `17` |
| `category-overview.spec.ts:540` | `toHaveLength(13)` → `17` |
| `pdf-upload.spec.ts:620` | `toHaveLength(13)` → `17` |
| `chart-theme.spec.ts:38` | Testname |
| `e2e/tests/pdf-import.spec.ts:86` (+ Kommentar `:80`) | `toHaveCount(13)` → `17` |
| `e2e/tests/categorization.spec.ts:145` | Kommentar nennt `.badge__dot`, jetzt `.badge__icon` |

### Ändern — Doku

| Datei | Änderung |
| ----- | -------- |
| `docs/requirements/US-05-transaktionen-kategorisieren.md:11` | Taxonomie-Aufzählung im Akzeptanzkriterium auf 17 Namen |
| `docs/adr/ADR-6-hybrid-categorization.md:8` | „13 Kategorien" → 17 |

### Neu

| Datei | Zweck |
| ----- | ----- |
| `backend/…/categorization/CategoryTest.java` | `fromLabel` für die vier neuen Labels; existiert heute nicht |
| `frontend/src/app/shared/category.spec.ts` | 17 Einträge, Slugs und Icons paarweise eindeutig |
| `docs/plans/BE-CAT-10-kategorien-icons.md` | dieser Plan |

### Ausdrücklich nicht angefasst

- **Keine Flyway-Migration** — kein Schema-Change nötig, `category_lookup.category` und
  `transactions.category` sind `TEXT`.
- **Keine Lookup-Seeds**, `PdfLookupCoverageIntegrationTest` unverändert.
- `design/variant-a/styles.scss`, `design/variant-b/styles.scss`, `design/README.md` — der
  Prototyp ist ein eingefrorenes Artefakt des Design-Entscheids, kein Spiegel des Codes.
- `docs/plans/*` — historische Pläne werden nie nachgezogen.

## Implementierungsschritte

1. `Category.java`: 4 Konstanten vor `SONSTIGES`, Javadoc auf 17.
2. `category.ts`: `CategoryMeta` um `readonly icon: string`, alle 17 Einträge in der
   Enum-Reihenfolge, `CATEGORY_BY_SLUG`-Map und `categoryIcon(slug): string | undefined`.
3. `styles.scss`: 4 Tokens je Theme. `_tokens.scss`: 4 `$categories`-Einträge.
4. `badge`: Icon-Span aus `categoryIcon(category())`; ist der Slug unbekannt, bleibt der bisherige
   `.badge__dot`. `aria-hidden` auf dem Icon — das Label trägt die Bedeutung, ein Screenreader soll
   nicht „Einkaufswagen Lebensmittel" lesen.
5. `chip`: optionaler `category`-Input, gleicher Icon-Span vor `<ng-content />`; ohne Input
   verhält sich der Chip exakt wie heute.
6. Alle 16 Kommentar- und Zählstellen von 13 auf 17.
7. `US-05` und `ADR-6` nachziehen.
8. Build und Tests: `mvn package` (JDK 25 via `JAVA_HOME`), `ng build`, Vitest, Playwright.

## Test-Strategie

| Ebene | Was |
| ----- | --- |
| Unit BE | `ClaudeCategorizationServiceTest.schemaContainsAllCategories` — trägt AC 4: `Category.values()` hat 17 und jeder Name steht als `enum` im Structured-Output-Schema. Bereits gegen `values()` statt gegen eine Literal-Liste geschrieben, deckt die vier neuen also mit ab. |
| Unit BE | neuer `CategoryTest`: `fromLabel` für die vier neuen Labels, unbekanntes Label wirft |
| Unit FE | `badge.spec.ts`: Icon wird gerendert, trägt `aria-hidden`, Punkt-Fallback bei unbekanntem Slug |
| Unit FE | `chip.spec.ts`: Icon bei gesetztem `category`, unverändert ohne |
| Unit FE | neuer `category.spec.ts`: 17 Einträge, Slugs eindeutig, **Icons paarweise eindeutig** (AC 3) |
| Unit FE | `chart-theme.spec.ts`, `styleguide.spec.ts`, `category-overview.spec.ts`, `pdf-upload.spec.ts` auf 17 |
| Integration BE | `PdfLookupCoverageIntegrationTest` unverändert laufen lassen — Nachweis für AC 5 |
| E2E | `pdf-import.spec.ts`: Dropdown-Anzahl 17 |

**Nicht automatisch prüfbar und bewusst offen:** dass die 17 `--cat-*`-Farben paarweise
unterscheidbar *aussehen*. Ein Kontrast-Test dafür ist nicht Teil dieses Tasks.

## Acceptance Criteria

- [ ] `Category.java` enthält die vier neuen Konstanten mit deutschem Label; der „13 Kategorien"-Javadoc ist auf 17 gezogen.
- [ ] `category.ts` (`CATEGORIES`) und `_tokens.scss` (`--cat-<slug>` + `$categories`) spiegeln alle 17 Kategorien in gleicher Reihenfolge; Slugs = kleingeschriebene Enum-Namen.
- [ ] Jede der 17 Kategorien hat ein eindeutiges Unicode-Icon; `badge`/`chip` rendern es neben Farbe/Label.
- [ ] Der Claude-Structured-Output akzeptiert die vier neuen Kategorien (folgt automatisch aus dem Enum — durch einen Test belegt, dass das Schema alle 17 als `enum` führt).
- [ ] `PdfLookupCoverageIntegrationTest` bleibt grün (Band gehalten oder bewusst angepasst).
- [ ] Bestehende Kategorie-Spiegel-Tests decken die 17 Einträge ab.

## Nach dem PR

Folge-Issue `BE-CAT-13` für die Lookup-Seeds (`BANCOMAT`/`ATM` → Bargeldbezug,
`STEUERVERWALTUNG` → Steuern) samt Neugenerierung der 240er-Fixture und Nachziehen von
`EXPECTED_LOOKUP_HITS` in `PdfLookupCoverageIntegrationTest`.
