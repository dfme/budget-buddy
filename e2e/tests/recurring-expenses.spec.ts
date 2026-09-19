import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { type APIRequestContext } from '@playwright/test';

import { expect, test } from '../fixtures/auth.fixture';

/**
 * E2E-Abdeckung der Should-Have-Story US-08 «Wiederkehrende Ausgaben (Abos) erkennen»
 * (E2E-REC-01).
 *
 * Ein Happy Path und ein Alt-Pfad — die in CLAUDE.md («Testing: Frameworks») vorgeschriebene
 * Menge pro Story. Erkennung (`BE-REC-01`), REST-Endpoints (`BE-REC-02`) und der Screen
 * (`FE-REC-01`) existieren bereits; dieser Task liefert nur die Playwright-Abdeckung.
 *
 * Einstieg über `authenticatedPage`/`authenticatedContext`: `/abos` liegt hinter `authGuard` UND
 * `onboardingGuard`, die Fixture erledigt beides über die API (siehe `fixtures/auth.fixture.ts`).
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

  /**
   * Obergrenze für den Import-Job. Die Abo-Erkennung läuft synchron am Ende desselben Jobs
   * (`ImportJobRunner.detectRecurringExpenses`, vor `finishSuccessfully`), ein separates Warten
   * auf die Erkennung ist deshalb nicht nötig — sobald der Poll `DONE` meldet, ist
   * `GET /api/recurring-expenses` bereits aktuell. Grosszügig wie in `categorization.spec.ts`:
   * der Watchdog steht auf 300s, in der Testinstanz ohne `ANTHROPIC_API_KEY` dauert der Job aber
   * Millisekunden.
   */
  const IMPORT_TIMEOUT_MS = 60_000;

  /**
   * Importiert eine Fixture über die API und wartet, bis der Job einen Endzustand erreicht hat.
   *
   * <p>Bewusst nicht durch die Upload-UI: der Import ist Vorbedingung dieses Tests, nicht sein
   * Gegenstand — dieselbe Begründung wie in `categorization.spec.ts`.
   */
  async function importFixture(request: APIRequestContext, fixturePath: string): Promise<void> {
    const upload = await request.post('/api/import/pdf', {
      multipart: {
        file: {
          name: fixturePath.split(/[/\\]/).pop()!,
          mimeType: 'application/pdf',
          buffer: readFileSync(fixturePath),
        },
      },
    });
    expect(upload.status(), 'Vorbedingung: POST /api/import/pdf').toBe(202);

    const { jobId } = (await upload.json()) as { jobId: number };

    let status = 'RUNNING';
    await expect
      .poll(
        async () => {
          const response = await request.get(`/api/import/${jobId}/status`);
          expect(response.status(), `GET /api/import/${jobId}/status`).toBe(200);
          ({ status } = (await response.json()) as { status: string });
          return status;
        },
        {
          timeout: IMPORT_TIMEOUT_MS,
          message: `Import-Job ${jobId} hat keinen Endzustand erreicht`,
        },
      )
      .not.toBe('RUNNING');

    expect(status, `Import-Job ${jobId} endete nicht erfolgreich`).toBe('DONE');
  }

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

    // Kein Reload nötig: `dismiss` nimmt den Eintrag lokal aus dem State
    // (`recurring-expense.service.ts`). Die Zeile verschwindet sofort, die Übersicht ist danach
    // leer — nur dieser eine Empfänger wurde importiert.
    await expect(row).toHaveCount(0);
    await expect(page.locator('p.status.empty')).toBeVisible();

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
  });
});
