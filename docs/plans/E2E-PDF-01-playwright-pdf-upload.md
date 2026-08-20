# [E2E-PDF-01] Playwright: PDF-Upload (Happy Path + Fehlerpfad)

- **Issue:** [#122](https://github.com/dfme/budget-buddy/issues/122)
- **Task-ID:** `E2E-PDF-01`
- **Branch:** `feature/E2E-PDF-01-playwright-pdf-upload`
- **Story:** US-04 — Kontoauszug als PDF hochladen
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-20

## Ziel

Die in CLAUDE.md („Testing: Frameworks") vorgeschriebene E2E-Abdeckung für die Must-Have-Story
US-04: je ein Happy Path und ein Fehlerpfad **pro Story**, nicht pro Issue. US-04 besteht aus acht
Issues (#13, #17, #18, #27, #28, #29, #83, #95); die zwei Testfälle gehören deshalb in einen
eigenen Task und nicht rückwirkend in einen Feature-PR.

Aufgesetzt wird auf der Harness aus [INFRA-14](INFRA-14-playwright-e2e-setup.md) (#91): Config,
CI-Job und `authenticatedPage`-Fixture stehen, das Gerüst wird nicht dupliziert.

## Entscheide

### 1. Fixture handgeschrieben statt PDFBox-generiert

Das PDF wird als unkomprimiertes Text-Layer-PDF von Hand geschrieben (Content-Stream ohne
`FlateDecode`, `Helvetica`/`WinAnsiEncoding`, korrekt berechnete xref-Offsets). Es ist damit reines
ASCII von ~1.2 KB und steht als Klartext im Diff — ein Reviewer sieht ohne Werkzeug, dass keine
echten Kontodaten enthalten sind. Ein von PDFBox erzeugtes PDF läge als Blob im Repo, und genau
das ist bei einer Fixture, deren Kerneigenschaft „enthält keine echten Kontodaten" ist, die
falsche Ablageform.

Kein Generator-Skript im Repo: die Datei ist das Artefakt, sie ist lesbar, und ein Skript wäre
zusätzliche Oberfläche für etwas, das genau einmal erzeugt wird.

Inhalt: Bank `Testbank Musterhausen AG`, kein IBAN, fünf frei erfundene Buchungen aus Juni 2025,
`Saldovortrag`- und `Schlusssaldo`-Zeile. Die Formaterkennung von `SwissBankStatementParser` hängt
an der Struktur (`Saldovortrag` plus `dd.MM.yyyy`-Zeilen mit Betrag und Saldo → generisches
Layout), nicht am Banknamen — ein Fantasiename ändert am Parse-Ergebnis nichts.

Vorab gegen den echten Parser verifiziert (nicht gegen eine Annahme): fünf Transaktionen, Richtung
je korrekt, `LOHN JUNI` als `isIncome=true` über das Saldo-Delta.

### 2. Fehlerpfad über das Backend, nicht über die Client-Validierung

Die AC lässt beides zu („nicht lesbares/kein PDF"). Eine `.txt`-Datei würde aber schon
`PdfUpload.isPdf()` im Browser abweisen (`pdf-upload.ts:129-132`) und das Backend nie erreichen —
und genau dieser Fall ist als Vitest-Unit-Test bereits abgedeckt (`pdf-upload.spec.ts:111-117`).
Ein E2E-Test, der dasselbe noch einmal prüft, kostet eine JVM und beweist nichts Zusätzliches.

Der E2E-Fall lädt deshalb Müll-Bytes unter dem Namen `kaputt.pdf` mit `application/pdf` hoch: durch
die Client-Validierung hindurch, `Loader.loadPDF()` scheitert, `PdfParseException` →
`PdfImportExceptionHandler` → 400 mit `reason: UNSUPPORTED_FORMAT` → `PdfUpload.importErrorMessage`
formuliert die Meldung. Das ist die Kette, die nur ein E2E-Test durchläuft.

### 3. Selektoren über Klasse UND role

Die AC nennt `app-notice variant="error"` und `role="alert"`. `variant` ist ein Angular-Input und
im DOM nicht sichtbar; seine beiden Abdrücke sind die Host-Bindings in `notice.ts:15-19`:
`role="alert"` und die Klasse `notice--error`. Der Test assertet beide — über `role` allein wäre er
unscharf, weil die Dropzone während des Uploads selbst ein `role="status"` trägt
(`pdf-upload.html:18`).

### 4. Keine Änderung am CI-Job

`npm test` in `e2e/` ist `playwright test` über `testDir: './tests'` — eine neue Spec-Datei wird
ohne Konfigurationsänderung mitgenommen. Der Job `E2E (Playwright)` in `build.yml` bleibt
unangetastet.

## Betroffene Files

| Datei | Art |
| --- | --- |
| `e2e/tests/pdf-import.spec.ts` | neu — die zwei Testfälle |
| `e2e/fixtures/pdf/kontoauszug-synthetisch.pdf` | neu — synthetischer Auszug, ASCII-lesbar |
| `e2e/README.md` | ändern — Aufbau-Tabelle und Scope-Absatz |
| `docs/plans/E2E-PDF-01-playwright-pdf-upload.md` | neu — dieser Plan |
| `docs/plans/README.md` | ändern — eine Index-Zeile |

Nicht angefasst: `playwright.config.ts`, `fixtures/auth.fixture.ts`, `.github/workflows/build.yml`.

## Implementierungsschritte

1. Fixture `e2e/fixtures/pdf/kontoauszug-synthetisch.pdf` anlegen — fünf Buchungen Juni 2025,
   `SYNTHETISCH`-Vermerk sowohl als PDF-Kommentar (`%`) wie im sichtbaren Text-Layer.
2. Fixture gegen den echten `SwissBankStatementParser` gegenprüfen, bevor der Test geschrieben wird.
3. `e2e/tests/pdf-import.spec.ts` mit den zwei Fällen schreiben.
4. `e2e/README.md` nachziehen: Zeile für die neue Spec und den Fixture-Pfad in die Aufbau-Tabelle,
   Scope-Absatz von „die acht Must-Have-Fälle sind Folgearbeit" auf den tatsächlichen Stand
   (US-04 abgedeckt, US-03/05/06 offen) korrigieren.
5. Lokal grün fahren: `./mvnw -Pprod -DskipTests package` im Backend, dann `npm run typecheck` und
   `npm test` in `e2e/`.

## Test-Strategie

Der Task **ist** der Test — es kommen keine Unit-Tests dazu.

| Fall | Ablauf | Assertion |
| --- | --- | --- |
| Happy Path | `authenticatedPage` → `/import` → `setInputFiles(fixture)` | `app-notice.notice--info[role=status]` mit `5 Transaktionen erkannt.`; anschliessend Quergegenprobe auf `/categories?month=2025-06`: die Tabelle hat Zeilen und zeigt **nicht** „Keine Ausgaben in diesem Monat." |
| Fehlerpfad | `authenticatedPage` → `/import` → `setInputFiles({name:'kaputt.pdf', mimeType:'application/pdf', buffer})` | `app-notice.notice--error[role=alert]` mit der Format-Meldung; `.notice--info` nicht vorhanden |

Die Quergegenprobe im Happy Path ist bewusst Teil des Falls: die Erfolgsmeldung trägt nur die Zahl
aus der HTTP-Response. Dass die Buchungen den Import überlebt haben und über einen zweiten
Endpoint wieder herauskommen, zeigt erst der Blick auf `/categories`. Sie assertet keine konkrete
Kategorie — ohne `ANTHROPIC_API_KEY` in der Testinstanz fällt alles Unbekannte auf `Sonstiges`
zurück, und welche Händler die Lookup-Tabelle aus V04 kennt, ist nicht Gegenstand dieses Tests.

## Acceptance Criteria (aus dem Issue)

- [ ] Happy Path: eingeloggt auf `/import` → PDF-Fixture hochladen → Erfolgsmeldung mit Anzahl
      erkannter Transaktionen sichtbar
- [ ] Fehlerpfad: nicht lesbares/kein PDF hochladen → Fehlermeldung sichtbar
      (`app-notice variant="error"`, `role="alert"`), kein Erfolgszustand
- [ ] Der Test nutzt die `authenticatedPage`-Fixture aus #91 als Vorbedingung — kein Durchklicken
      durchs Login-Formular
- [ ] PDF-Fixture liegt versioniert im Repo und enthält **keine** echten Kontodaten (synthetischer
      Auszug)
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npm test` in `e2e/`
- [ ] Der Test läuft im bestehenden CI-Job `E2E (Playwright)` mit — kein neuer Job nötig

## Nachtrag: Scope-Erweiterung (nach der Plan-Bestätigung)

Beim Betrachten der Testläufe fiel auf, dass Playwright für die neuen Tests **keine Videos**
aufzeichnet, für die meisten Tests in `auth.spec.ts` dagegen schon. Der Befund gehört nicht zu
US-04, sondern zur Harness aus #91 — auf ausdrückliche Ansage im selben PR mitgenommen statt als
Folge-Issue.

**Ursache:** `video`, `trace`, `screenshot` und `baseURL` werden nicht vom Browser aus der Config
gelesen, sondern von Playwrights `_contextFactory`-Fixture beim Erzeugen des Contexts
zusammengebaut (`node_modules/playwright/lib/index.js`): sie schreibt `recordVideo` in die
Context-Optionen und speichert das Video beim Schliessen an den Ort, den der HTML-Report erwartet.
`authenticatedContext` erzeugte seinen Context aber selbst über `browser.newContext({ baseURL })`
und ging an dieser Maschinerie vorbei — jeder Test auf dieser Fixture blieb stumm ohne Artefakte.
Sichtbar war das als „5 Videos bei 6 Tests" in `auth.spec.ts`: ohne Video blieb genau der Test,
der `authenticatedPage` benutzt.

`contextOptions` als Fixture zu nehmen hätte nicht geholfen — `recordVideo` steht dort nicht drin,
es entsteht erst in der `_contextFactory`.

**Fix:** `authenticatedContext` setzt auf Playwrights eingebauter `context`-Fixture auf. Damit
greifen alle `use`-Optionen automatisch; das explizite `baseURL`-Argument und das manuelle
`context.close()` entfallen.

**Video-Modus:** `retain-on-failure` statt des zum Debuggen gesetzten `on`. Aufgezeichnet wird
immer, behalten nur beim Fehlschlag — konsistent mit `screenshot: 'only-on-failure'` und mit dem
bestehenden Grundsatz, dass ein grüner Lauf keine Artefakte hinterlässt. `on` schriebe bei jedem
grünen CI-Lauf ein Video pro Test.

`spa-routing.spec.ts` bleibt bewusst ohne Video: diese Tests benutzen nur die `request`-Fixture
(`APIRequestContext`) und öffnen nie eine Browser-Seite — dort gibt es nichts aufzunehmen.

## Offener Punkt (nicht blockierend)

`Area` ist für #122 im Sprint Board leer (Story Points 3 und Sprint 5 stehen). Gemeldet, nicht
gesetzt — Board-Metadaten sind eine Team-Entscheidung.
