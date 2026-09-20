import { join } from 'node:path';

import { expect, test } from '../fixtures/auth.fixture';
import { importFixture } from '../support/import';

/**
 * E2E-Abdeckung der Should-Have-Story US-08 «Wiederkehrende Ausgaben (Abos) erkennen»
 * (E2E-REC-01).
 *
 * Ein Happy Path und ein Alt-Pfad, analog zur Must-Have-Regel in `docs/CONVENTIONS.md`, obwohl
 * US-08 als Should-Have nicht darunter fällt. Erkennung (`BE-REC-01`), REST-Endpoints
 * (`BE-REC-02`) und der Screen (`FE-REC-01`) existieren bereits; dieser Task liefert nur die
 * Playwright-Abdeckung.
 *
 * Einstieg über `authenticatedPage`/`authenticatedContext`: `/abos` liegt hinter `authGuard` UND
 * `onboardingGuard`, die Fixture erledigt beides über die API (siehe `fixtures/auth.fixture.ts`).
 *
 * <p>Die Abo-Erkennung läuft synchron am Ende desselben Import-Jobs
 * (`ImportJobRunner.detectRecurringExpenses`, vor `finishSuccessfully`), ein separates Warten
 * auf die Erkennung ist deshalb in keinem der beiden Tests nötig — sobald `importFixture` `DONE`
 * meldet, ist `GET /api/recurring-expenses` bereits aktuell.
 */
test.describe('Abo-Erkennung', () => {
  /**
   * Zwei Buchungen desselben fiktiven Empfängers «STREAMBOX.CH ABO» zu je CHF 15.90 in Juni und
   * Juli 2025 — generisches Layout wie `kontoauszug-synthetisch.pdf`. Die Kopfzeile mit dem
   * Auszugszeitraum ist rein dekorativ und wird vom Parser nie geprüft
   * (`SwissBankStatementParser.parseGeneric`); eine einzelne Fixture kann deshalb Buchungen über
   * zwei Kalendermonate tragen. Grundlage für den Happy Path UND den ersten Teil des Alt-Pfads.
   */
  const FIXTURE_DETECTION = join(
    __dirname,
    '..',
    'fixtures',
    'pdf',
    'kontoauszug-abo-erkennung.pdf',
  );

  /**
   * Dritte Monatsbuchung (August 2025) desselben Empfängers/Betrags — Nachweis, dass die
   * Erkennung nach «Kein Abo» nicht erneut anspringt (`RecurringExpenseService`: ein bereits
   * bekannter Empfänger wird übersprungen, unabhängig vom Status).
   */
  const FIXTURE_PERSISTENCE = join(
    __dirname,
    '..',
    'fixtures',
    'pdf',
    'kontoauszug-abo-persistenz.pdf',
  );

  /** Empfängertext ohne Ziffern — überlebt `ExpenseHistoryService.normalise` unverändert. */
  const PAYEE = 'STREAMBOX.CH ABO';

  test('Happy Path: gleicher Empfänger/Betrag in 2 Folgemonaten erscheint mit «Neu»-Label', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    await importFixture(authenticatedContext.request, FIXTURE_DETECTION);

    await page.goto('/abos');
    await expect(page.getByRole('heading', { name: 'Abos' })).toBeVisible();

    // AC 1: die Zeile der erkannten Gruppe — Empfänger und «seit»-Label (erster Monat der Reihe).
    const row = page.locator('li.expense').filter({ hasText: PAYEE });
    await expect(row).toHaveCount(1);
    await expect(row.locator('.expense__since')).toHaveText(/^seit\s+Juni\s+2025$/);

    // AC 2: neu erkannt trägt das «Neu»-Label — Text, nicht nur Farbe (`recurring-expense-list.html`).
    await expect(row.locator('.expense__new')).toHaveText('Neu');

    // Betrag der jüngsten Belastung (Juli), ohne Vorzeichen (`hidePositiveSign`) und ohne
    // «CHF»-Präfix (`showCurrency` ist hier nicht gesetzt) — `formatSwissAmount`.
    await expect(row.locator('.expense__amount')).toHaveText('15.90');
  });

  test('Alt-Pfad: «Kein Abo» entfernt den Eintrag dauerhaft, auch nach einem weiteren Import', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    await importFixture(authenticatedContext.request, FIXTURE_DETECTION);

    await page.goto('/abos');
    const row = page.locator('li.expense').filter({ hasText: PAYEE });
    await expect(row).toHaveCount(1);

    // aria-label statt sichtbarem Text: der Button zeigt «Wird entfernt …», solange der Request
    // dieser Zeile läuft (`recurring-expense-list.html`), das aria-label bleibt stabil.
    await page.getByRole('button', { name: `Kein Abo: ${PAYEE}` }).click();

    // Kein Reload nötig: `dismiss` ersetzt den Eintrag lokal im State
    // (`recurring-expense.service.ts`). Die Zeile verschwindet sofort aus der Abo-Liste, die ist
    // danach leer — nur dieser eine Empfänger wurde importiert.
    await expect(row).toHaveCount(0);
    await expect(page.locator('p.status.empty')).toBeVisible();

    // FE-NOTIF-03 (#333): Der Eintrag verlässt die Seite nicht, er wechselt in den Abschnitt
    // «Kein Abo» — damit der Klick auf die Benachrichtigung in der Glocke weiterhin ein Ziel
    // hat. Eigene Klasse, nicht `li.expense`: die Abo-Liste oben bleibt genau die Abos.
    const dismissedRow = page.locator('li.dismissed-expense').filter({ hasText: PAYEE });
    await expect(dismissedRow).toHaveCount(1);
    await expect(dismissedRow.locator('.expense__dismiss')).toHaveCount(0);

    // Zweiter Import: eine dritte Monatsbuchung desselben Empfängers/Betrags. Für sich genommen
    // qualifiziert das Paar Juli/August erneut — die Erkennung überspringt den Empfänger aber,
    // weil er bereits (als DISMISSED) bekannt ist.
    await importFixture(authenticatedContext.request, FIXTURE_PERSISTENCE);

    // Neu laden statt nur den State zu prüfen: erst ein frischer `GET /api/recurring-expenses`
    // beweist, dass der Empfänger dauerhaft ausgeschlossen bleibt, nicht nur, dass der
    // Dismiss-Request ihn einmalig aus der Anzeige entfernt hat.
    await page.reload();
    await expect(page.locator('li.expense').filter({ hasText: PAYEE })).toHaveCount(0);
    await expect(page.locator('p.status.empty')).toBeVisible();
    // … und unter «Kein Abo» steht er nach dem frischen GET weiterhin (status=DISMISSED).
    await expect(page.locator('li.dismissed-expense').filter({ hasText: PAYEE })).toHaveCount(1);
  });
});
