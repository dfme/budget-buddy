# [E2E-FC-01] Playwright: Fixkosten-Wizard (Happy Path + Fehlerpfad)

- **Issue:** [#123](https://github.com/dfme/budget-buddy/issues/123)
- **Task-ID:** `E2E-FC-01`
- **Branch:** `feature/E2E-FC-01-playwright-fixkosten-wizard`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-21

## Ziel

Die in CLAUDE.md („Testing: Frameworks") vorgeschriebene E2E-Abdeckung für die Must-Have-Story
US-03: je ein Happy Path und ein Fehlerpfad **pro Story**, nicht pro Issue. Der Test gehört
deshalb in einen eigenen Task und nicht rückwirkend in eines der Feature-Issues.

Aufgesetzt wird auf der Harness aus [INFRA-14](INFRA-14-playwright-e2e-setup.md) (#91): Config,
CI-Job und `authenticatedPage`-Fixture stehen, das Gerüst wird nicht dupliziert.

## Entscheide

### 1. AC-treu auf `authenticatedPage`, Wizard per Direktnavigation

Die Fixture schliesst das Onboarding per API ab (`auth.fixture.ts:84`,
`POST /users/me/onboarding-complete`). Beim Teststart ist `onboardingCompleted` also bereits
`true`. Das hat drei Folgen, die vor der Umsetzung geklärt wurden:

- Der Wizard wird per `page.goto('/onboarding')` erreicht, nicht über den erzwungenen Redirect des
  `onboardingGuard`. **Es geht dabei nichts verloren:** dieser Redirect ist in `auth.spec.ts:29`
  (Registrierung) und `auth.spec.ts:76` (Login) schon doppelt belegt.
- Erreichbar ist die Route überhaupt nur, weil `/onboarding` bewusst **ohne** `onboardingGuard`
  registriert ist (`app.routes.ts:40-43`: „das Ziel der Umleitung darf sich nicht selbst
  umleiten"). Ohne diese Eigenschaft wäre der Test so nicht baubar.
- „Kein Wizard-Abschluss" aus der Fehlerpfad-AC ist damit nicht als Flag-Übergang beobachtbar —
  das Flag steht schon. Belegt wird es stattdessen verhaltensmässig: die URL bleibt
  `/onboarding`, es findet keine Navigation aufs Dashboard statt. Das ist die für den Nutzer
  sichtbare Bedeutung der Aussage.

Bewusst **nicht** gemacht: `auth.fixture.ts` um einen un-onboardeten Einstieg erweitern. Das wäre
stärkere US-03-Abdeckung (Redirect, Abschluss per Button, Freischaltung des Dashboards), ändert
aber eine von allen E2E-Tests geteilte Datei und geht über den AC-Wortlaut hinaus. Der
Abschluss-Übergang `finishOnboarding()` → `POST` → `/dashboard` bleibt damit ungetestet und wäre
ein eigenes Ticket.

### 2. Eine Position, quartalsweise

Erfasst wird `Krankenkasse` / `1200.00` / `quartalsweise`. Das deckt in einem Fall zwei Dinge ab,
die „monatlich" nicht abdecken könnte:

- die **de-CH-Formatierung mit Tausendertrennung**, die die AC ausdrücklich nennt
  (`CHF 1’200.00`),
- die **Monatsbetrag-Normalisierung** `÷ 3` aus `FixedCostService.monatsbetrag`
  (`FixedCostService.java:181-188`) → `CHF 400.00`.

Bei „monatlich" wären `betrag` und `monatsbetrag` identisch; der Test könnte die beiden Spalten
nicht auseinanderhalten und die Normalisierung bliebe ungeprüft.

### 3. CHF-Formatierung: keine ASCII-Zeichen

Der `CurrencyPipe` liefert unter `de-CH` (`app.config.ts:18,24`) **nicht** ASCII. Ein hexdump von
`fixed-cost-list.spec.ts:124` — dem Unit-Test gegen genau dieses Template — zeigt:

```
CHF  c2 a0  1  e2 80 99  200.00
     NBSP      U+2019
```

Also ein **No-Break Space** nach `CHF` und ein **Right Single Quotation Mark** als
Tausendertrennung. Der Chart-Tooltip daneben benutzt ASCII-Space und `'` (`chart-options.spec.ts:20`)
— anderer Code-Pfad, kein Widerspruch. Ein naives `CHF 1'200.00` würde reissen.

Die erwarteten Zeichen stehen deshalb als `\u`-Escapes in der Spec, nicht als literale Zeichen:
so ist im Quelltext sichtbar, welches Zeichen gemeint ist, statt zwei fast identisch aussehende
Apostrophe zu verwechseln. Ob Playwrights `toHaveText` den NBSP auf ein normales Space
normalisiert, wird am Lauf festgestellt und nicht angenommen.

### 4. Selektoren über Klasse UND role

`variant` ist ein Angular-Input und im DOM nicht sichtbar; seine Abdrücke sind die Host-Bindings in
`notice.ts:16-19` (`role` plus `notice--info`/`notice--error`). Assertiert wird auf beiden, weil
`role="status"` allein unscharf wäre — auch andere Elemente tragen es.

Feld-Fehlermeldungen hängen als `<p class="field__error">` unter dem Feld (`field.html:3-5`).

### 5. Keine Änderung am CI-Job

`npm test` in `e2e/` ist `playwright test` über `testDir: './tests'` — eine neue Spec-Datei wird
ohne Konfigurationsänderung mitgenommen. Belegt daran, dass derselbe Job für
`pdf-import.spec.ts` (E2E-PDF-01) unverändert lief.

## Betroffene Files

| Datei | Art |
| --- | --- |
| `e2e/tests/fixed-cost-wizard.spec.ts` | neu — die zwei Testfälle |
| `e2e/README.md` | ändern — Aufbau-Tabelle und Scope-Absatz |
| `docs/plans/E2E-FC-01-playwright-fixkosten-wizard.md` | neu — dieser Plan |
| `docs/plans/README.md` | ändern — eine Index-Zeile |

Nicht angefasst: `fixtures/auth.fixture.ts`, `playwright.config.ts`,
`.github/workflows/build.yml`.

## Implementierungsschritte

1. `e2e/tests/fixed-cost-wizard.spec.ts` mit den zwei Fällen schreiben.
2. Die de-CH-Zeichen am ersten Lauf empirisch pinnen (NBSP-Normalisierung durch Playwright).
3. Jede Assertion einmal mutieren und sehen, dass sie reisst — sonst ist nicht belegt, dass sie
   etwas prüft.
4. `e2e/README.md` nachziehen: Zeile für die neue Spec in die Aufbau-Tabelle, Scope-Absatz von
   „US-04 abgedeckt" auf „US-03 und US-04 abgedeckt".
5. Ganze Suite grün fahren, nicht nur die neue Spec: die bestehenden 17 Tests müssen bleiben.

## Test-Strategie

Der Task **ist** der Test — es kommen keine Unit-Tests dazu.

| Fall | Ablauf | Assertion |
| --- | --- | --- |
| Happy Path | `authenticatedPage` → `/onboarding` → erfassen → speichern → `/fixkosten` | `app-notice.notice--info[role=status]` mit `«Krankenkasse» wurde gespeichert.`; Tabellenzeile mit `CHF 1’200.00`, `quartalsweise`, `CHF 400.00` |
| Fehlerpfad | `/onboarding` → leer absenden, danach Betrag `10.999` | `Bezeichnung ist erforderlich.` + `Betrag ist erforderlich.`, dann `Betrag darf höchstens zwei Nachkommastellen haben.`; kein `notice--info`; URL bleibt `/onboarding`; `/fixkosten` zeigt weiter `Noch keine Fixkosten erfasst.` |

Beide Validierungsvarianten aus dem AC-Wortlaut („Pflichtfeld leer **bzw.** ungültiger Betrag")
liegen in **einem** Fehlerpfad: CLAUDE.md verlangt je einen pro Story, nicht pro
Validierungsregel.

Die Quergegenprobe auf `/fixkosten` ist in beiden Fällen bewusst Teil des Falls. Die
Erfolgsmeldung des Wizards trägt nur die Bezeichnung aus der HTTP-Response; dass die Position
persistiert ist und über einen zweiten Endpoint wieder herauskommt, zeigt erst die Liste. Im
Fehlerpfad zeigt dieselbe Route umgekehrt, dass **nichts** geschrieben wurde — die Abwesenheit
einer Meldung im Formular allein würde das nicht belegen.

Die Einkommens-Warnung der Liste kann dabei nicht störend dazwischenfunken:
`exceedsIncome = monthlyIncome != null && …` (`FixedCostService.java:87`), und die Fixture setzt
kein Einkommen.

## Acceptance Criteria (aus dem Issue)

- [ ] Happy Path: eingeloggt in den Wizard → Fixkosten erfassen → gespeichert → Posten erscheint
      in der Liste mit korrektem Betrag (CHF-Formatierung, de-CH)
- [ ] Fehlerpfad: Pflichtfeld leer bzw. ungültiger Betrag → Validierungsmeldung, kein Speichern,
      kein Wizard-Abschluss
- [ ] Der Test nutzt die `authenticatedPage`-Fixture aus #91 als Vorbedingung — kein Durchklicken
      durchs Login-Formular
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npm test` in `e2e/`
- [ ] Der Test läuft im bestehenden CI-Job `E2E (Playwright)` mit — kein neuer Job nötig

## Offener Punkt (nicht blockierend)

`Area` ist für #123 im Sprint Board leer (Story Points 3 und Sprint 5 stehen). Gemeldet, nicht
gesetzt — Board-Metadaten sind eine Team-Entscheidung.
