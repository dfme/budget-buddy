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
 * Einstieg über `authenticatedPage`/`authenticatedContext`: `/fixkosten` liegt hinter `authGuard`
 * UND `onboardingGuard`, die Fixture erledigt beides über die API (siehe
 * `fixtures/auth.fixture.ts`). Die Abo-Übersicht ist seit FE-FC-05 (#338) der Abschnitt
 * «Erkannte Abos» auf dieser Seite; `/abos` leitet nur noch dorthin um.
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

  /**
   * Zwei fiktive Empfänger («STREAMBOX.CH ABO» 15.90, «CLOUDBOX.CH ABO» 9.90) in Juni und Juli
   * 2025 — ein Import, der zwei Abos auf einmal erkennt. Grundlage für FE-NOTIF-04 (#336): eine
   * Benachrichtigung pro Import statt eine pro Abo.
   */
  const FIXTURE_BUNDLE = join(__dirname, '..', 'fixtures', 'pdf', 'kontoauszug-abo-buendel.pdf');

  /** Empfängertext ohne Ziffern — überlebt `ExpenseHistoryService.normalise` unverändert. */
  const PAYEE = 'STREAMBOX.CH ABO';
  const PAYEE_2 = 'CLOUDBOX.CH ABO';

  test('Happy Path: gleicher Empfänger/Betrag in 2 Folgemonaten erscheint mit «Neu»-Label', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    await importFixture(authenticatedContext.request, FIXTURE_DETECTION);

    await page.goto('/fixkosten');
    await expect(page.getByRole('heading', { level: 2, name: 'Erkannte Abos' })).toBeVisible();

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

  /**
   * Die Glocke ist zweimal im DOM (mobile Topbar, Desktop-Sidebar — `shell.html`), sichtbar ist
   * je Viewport nur eine. `filter({ visible: true })` statt `first()`: welche der beiden das
   * ist, hängt am Projekt-Viewport und soll den Test nicht interessieren.
   */
  function bell(page: import('@playwright/test').Page) {
    return page.getByRole('button', { name: 'Benachrichtigungen' }).filter({ visible: true });
  }

  // FE-NOTIF-04 (#336), AC 1: nach einem Import mit N erkannten Abos genügt eine Aktion.
  test('Bündel: zwei Abos in einem Import ergeben eine Benachrichtigung, ein Klick nimmt beide «Neu»', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    await importFixture(authenticatedContext.request, FIXTURE_BUNDLE);

    await page.goto('/fixkosten');
    await expect(page.locator('li.expense')).toHaveCount(2);
    await expect(page.locator('li.expense .expense__new')).toHaveCount(2);

    // Eine Abo-Benachrichtigung, nicht zwei — sie zählt Läufe, nicht Abos. Über den Text
    // gefiltert statt alle Einträge gezählt: der Import selbst darf daneben eigene
    // Benachrichtigungen erzeugen (BE-PDF-15, #337), die dieser Test nicht mitzählen soll.
    await expect(bell(page).locator('.bell__badge')).toHaveCount(1);
    await bell(page).click();
    const aboItems = page.locator('.bell-list__item:visible').filter({ hasText: /Abos? erkannt/ });
    await expect(aboItems).toHaveCount(1);
    await expect(aboItems.first()).toHaveClass(/bell-list__item--unread/);
    await expect(aboItems.first()).toContainText(`2 neue Abos erkannt: ${PAYEE_2}, ${PAYEE}`);

    // AC 2 (FE-NOTIF-01, unverändert): der Einzelklick liest die Benachrichtigung und führt
    // nach /fixkosten (FE-FC-05). Die Abo-Benachrichtigung ist danach gelesen …
    await aboItems.first().click();
    await expect(page).toHaveURL(/\/fixkosten$/);
    await bell(page).click();
    await expect(
      page.locator('.bell-list__item:visible').filter({ hasText: /Abos? erkannt/ }),
    ).toHaveClass(/bell-list__item--read/);
    await page.keyboard.press('Escape');

    // … und nach einem frischen GET tragen beide Einträge kein «Neu» mehr (BE-REC-02: das Label
    // hängt am Gelesen-Zustand der einen Bündel-Benachrichtigung).
    await page.reload();
    await expect(page.locator('li.expense')).toHaveCount(2);
    await expect(page.locator('li.expense .expense__new')).toHaveCount(0);
  });

  // FE-NOTIF-04 (#336): mehrere Importe hinterlassen mehrere Bündel — «Alle als gelesen
  // markieren» nimmt sie in einer Aktion.
  test('«Alle als gelesen markieren» bringt das Badge über mehrere Importe hinweg auf 0', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    // Erster Import erkennt STREAMBOX; der zweite bringt CLOUDBOX dazu (STREAMBOX ist dann
    // bereits bekannt und wird übersprungen) — zwei Läufe mit je einem Treffer, zwei Bündel.
    await importFixture(authenticatedContext.request, FIXTURE_DETECTION);
    await importFixture(authenticatedContext.request, FIXTURE_BUNDLE);

    await page.goto('/fixkosten');
    await expect(page.locator('li.expense .expense__new')).toHaveCount(2);
    await expect(bell(page).locator('.bell__badge')).toHaveCount(1);

    await bell(page).click();
    // Zwei Abo-Benachrichtigungen, je eine pro Lauf — gefiltert wie im Bündel-Test, damit
    // Import-Benachrichtigungen (BE-PDF-15) den Zähler nicht verfälschen.
    await expect(
      page.locator('.bell-list__item:visible').filter({ hasText: /Abos? erkannt/ }),
    ).toHaveCount(2);
    await page.getByRole('button', { name: 'Alle als gelesen markieren' }).click();

    // Ohne Navigation: das Dropdown bleibt offen, der Button verschwindet, das Badge auch.
    await expect(page.getByRole('button', { name: 'Alle als gelesen markieren' })).toHaveCount(0);
    await expect(page.locator('.bell-list:visible')).toHaveCount(1);
    await expect(bell(page).locator('.bell__badge')).toHaveCount(0);

    await page.reload();
    await expect(page.locator('li.expense')).toHaveCount(2);
    await expect(page.locator('li.expense .expense__new')).toHaveCount(0);
  });

  test('Alt-Pfad: «Kein Abo» entfernt den Eintrag dauerhaft, auch nach einem weiteren Import', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    await importFixture(authenticatedContext.request, FIXTURE_DETECTION);

    await page.goto('/fixkosten');
    const row = page.locator('li.expense').filter({ hasText: PAYEE });
    await expect(row).toHaveCount(1);

    // aria-label statt sichtbarem Text: der Button zeigt «Wird entfernt …», solange der Request
    // dieser Zeile läuft (`recurring-expense-list.html`), das aria-label bleibt stabil.
    await page.getByRole('button', { name: `Kein Abo: ${PAYEE}` }).click();

    // Kein Reload nötig: `dismiss` ersetzt den Eintrag lokal im State
    // (`recurring-expense.service.ts`). Die Zeile verschwindet sofort aus der Abo-Liste, die ist
    // danach leer — nur dieser eine Empfänger wurde importiert. Auf den Abo-Abschnitt
    // eingeschränkt: seit FE-FC-05 hat die Seite daneben den Leerzustand der Fixkosten-Tabelle,
    // und der Test-User hat keine Positionen erfasst.
    await expect(row).toHaveCount(0);
    const aboEmptyState = page.locator('app-recurring-expense-list p.status.empty');
    await expect(aboEmptyState).toBeVisible();

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
    await expect(aboEmptyState).toBeVisible();
    // … und unter «Kein Abo» steht er nach dem frischen GET weiterhin (status=DISMISSED).
    await expect(page.locator('li.dismissed-expense').filter({ hasText: PAYEE })).toHaveCount(1);
  });

  // FE-FC-05 (#338), AC 4: der alte Pfad leitet auf die zusammengeführte Seite um — Bookmarks
  // und ältere Links landen im Abo-Abschnitt, nicht über den Catch-all auf dem Dashboard.
  test('/abos leitet auf /fixkosten um', async ({ authenticatedPage: page }) => {
    await page.goto('/abos');

    await expect(page).toHaveURL(/\/fixkosten$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Fixkosten' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: 'Erkannte Abos' })).toBeVisible();
  });
});
