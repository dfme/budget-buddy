import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { Client } from 'pg';

import { expect, test } from '../fixtures/auth.fixture';
import { DB_HOST, DB_NAME, DB_PASSWORD, DB_PORT, DB_USER } from '../support/database';

/**
 * E2E-Abdeckung der Must-Have-Story US-05 «Transaktionen kategorisieren» (E2E-CAT-01).
 *
 * Ein Happy Path und ein Fehlerpfad — die in CLAUDE.md («Testing: Frameworks») vorgeschriebene
 * Menge, und zwar pro Story, nicht pro Issue. US-05 besteht aus mehreren Feature-Issues; die
 * beiden Fälle gehören deshalb hierher und nicht in einen Feature-PR.
 *
 * Einstieg über `authenticatedPage`: `/categories` liegt hinter `authGuard` UND `onboardingGuard`,
 * die Fixture erledigt beides über die API (siehe `fixtures/auth.fixture.ts`).
 */
test.describe('Transaktionen kategorisieren', () => {
  /** Derselbe synthetische Auszug, den E2E-PDF-01 benutzt — fünf Buchungen aus Juni 2025. */
  const FIXTURE_PDF = join(__dirname, '..', 'fixtures', 'pdf', 'kontoauszug-synthetisch.pdf');

  /** Anzahl Buchungen in der Fixture, als Gegenprobe an der Vorbedingung. */
  const FIXTURE_TRANSACTION_COUNT = 5;

  /** Monat der Fixture-Buchungen — der Default der Übersicht ist der laufende Monat. */
  const FIXTURE_MONTH = '2025-06';

  /**
   * Obergrenze für den Kategorisierungs-Job. Seit ADR-14 (BE-PDF-09) läuft er asynchron; in der
   * Testinstanz ohne `ANTHROPIC_API_KEY` dauert er Millisekunden, der serverseitige Watchdog
   * steht aber auf 300s. Grosszügig, damit ein legitim langsamer Job nicht als Testfehler
   * erscheint, während das Backend noch innerhalb seiner eigenen Grenze arbeitet.
   */
  const IMPORT_TIMEOUT_MS = 60_000;

  /**
   * Buchungstexte, für die dieser Lauf ein Lookup-Pattern gelernt hat — Eingabe für das Cleanup
   * in {@link test.afterEach}.
   *
   * <p>Modulweiter Zustand ist hier unbedenklich: `playwright.config.ts` fährt `workers: 1` und
   * `fullyParallel: false`, die Tests laufen also nacheinander in einem Prozess.
   */
  const learnedPatterns: string[] = [];

  /**
   * Löscht die in diesem Lauf gelernten Lookup-Patterns wieder.
   *
   * <p><strong>Warum das nötig ist.</strong> `PUT /api/transactions/{id}/category` lernt den
   * Buchungstext als Lookup-Pattern (`TransactionCategoryService:57`), und `category_lookup` ist
   * global — `empfaenger_pattern` ist der Primary Key, es gibt keine `user_id` (Migration V04).
   * Genau diese Tabelle nimmt {@link resetDatabase} bewusst aus, weil ihr Inhalt «aus Migration
   * V04 stammt und Teil des Schemas ist, nicht Zustand eines Laufs» (`support/database.ts`).
   *
   * <p>Ohne dieses Cleanup bliebe nach dem ersten Lauf z. B. `MIGROS BERN BAHNHOF → Wohnen`
   * dauerhaft stehen. `CategoryLookupRepository.findMatching` sortiert nach Pattern-Länge
   * absteigend — 19 Zeichen schlagen das Seed `MIGROS` mit 6 —, die Buchung käme ab dem zweiten
   * lokalen Lauf also in einer anderen Kategorie an, und die dokumentierte Invariante wäre
   * verletzt. In CI fällt das nicht auf: dort ist die Datenbank jedes Mal frisch.
   *
   * <p>Bewusst hier statt in {@link resetDatabase}: das ist gemeinsamer Harness-Code, an dem
   * jeder andere Spec hängt. Für einen PR, der einen Test hinzufügt, wäre das der falsche Blast
   * Radius.
   */
  test.afterEach(async () => {
    if (learnedPatterns.length === 0) {
      return;
    }

    const patterns = learnedPatterns.map((text) => text.toUpperCase());
    learnedPatterns.length = 0;

    const client = new Client({
      host: DB_HOST,
      port: DB_PORT,
      database: DB_NAME,
      user: DB_USER,
      password: DB_PASSWORD,
    });
    await client.connect();
    try {
      // upper() auf beiden Seiten, wie im Produktionscode: CategoryLearningService normalisiert
      // vor dem Speichern auf Grossschreibung (Begründung in V04), und dieselbe Regel muss hier
      // gelten, sonst räumt das DELETE nichts weg und schweigt dabei.
      await client.query('DELETE FROM category_lookup WHERE upper(empfaenger_pattern) = ANY($1)', [
        patterns,
      ]);
    } finally {
      await client.end();
    }
  });

  /**
   * Importiert die Fixture über die API und wartet, bis der Kategorisierungs-Job durch ist.
   *
   * <p>Bewusst nicht durch die Upload-UI: der Import ist Vorbedingung dieses Tests, nicht sein
   * Gegenstand. Ihn durchzuklicken würde US-05 an US-04 aufhängen — ein Bug im Upload-UI liesse
   * dann auch diese beiden Fälle rot werden, ohne Hinweis auf die eigentliche Ursache. Dieselbe
   * Begründung, mit der die Auth-Fixture über die API registriert statt durchs Login-Formular.
   *
   * <p>`context.request` teilt den Cookie-Jar mit dem BrowserContext, der Upload läuft also unter
   * demselben eingeloggten User wie die Seite danach.
   */
  async function importFixtureStatement(request: APIRequestContext): Promise<void> {
    const upload = await request.post('/api/import/pdf', {
      multipart: {
        file: {
          name: 'kontoauszug-synthetisch.pdf',
          mimeType: 'application/pdf',
          buffer: readFileSync(FIXTURE_PDF),
        },
      },
    });
    expect(upload.status(), 'Vorbedingung: POST /api/import/pdf').toBe(202);

    const { jobId, total } = (await upload.json()) as { jobId: number; total: number };
    expect(total, 'Vorbedingung: Anzahl geparster Buchungen').toBe(FIXTURE_TRANSACTION_COUNT);

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

    // DONE statt bloss «nicht mehr RUNNING»: ein FAILED-Job würde sonst als erfüllte Vorbedingung
    // durchgehen, und der Test scheiterte danach an einer leeren Übersicht — mit einer Meldung,
    // die auf die Kategorisierung zeigt statt auf den Import.
    expect(status, `Import-Job ${jobId} endete nicht erfolgreich`).toBe('DONE');
  }

  /**
   * Der Aufklapp-Button einer Kategoriezeile.
   *
   * <p>Adressiert über `.badge__label` und exakten Text, nicht über den Text des Buttons: das
   * Badge rendert daneben einen `.badge__dot` (`badge.html`), und ein Icon-Element im
   * `textContent` des Elternteils hat in FE-UI-07 schon einmal drei Assertions gekostet. Exakt
   * statt Teilstring, damit `Sonstiges` nicht versehentlich eine andere Zeile trifft.
   */
  function categoryToggle(page: Page, label: string): Locator {
    return page.locator('button.drilldown-toggle', {
      has: page.getByText(label, { exact: true }),
    });
  }

  /** Die erste aufgeklappte Buchung — Datum, Text, Betrag und das Kategorie-Select. */
  function firstTransaction(page: Page): Locator {
    return page.locator('tr.drilldown li.transaction').first();
  }

  test('Happy Path: manuelle Korrektur überlebt einen Reload', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    await importFixtureStatement(authenticatedContext.request);

    await page.goto(`/categories?month=${FIXTURE_MONTH}`);
    await expect(page.getByRole('heading', { name: 'Kategorie-Übersicht' })).toBeVisible();

    // AC 1, erster Teil: die Übersicht zeigt die Kategorien der importierten Buchungen. Eine
    // sichtbare Zeile ist der ganze Beweis — `loading`, `errorMessage`, `isEmpty` und `summary`
    // liegen in gegenseitig ausschliessenden @else-if-Zweigen (`category-overview.html`).
    const firstToggle = page.locator('button.drilldown-toggle').first();
    await expect(firstToggle).toBeVisible();
    await firstToggle.click();

    const transaction = firstTransaction(page);
    await expect(transaction).toBeVisible();

    // textContent statt innerText: die Interpolation `{{ tx.buchungstext }}` steht ohne
    // umgebenden Whitespace im Span, der Wert kommt also exakt heraus. innerText würde
    // Mehrfach-Leerzeichen zusammenziehen — und das Cleanup unten braucht den genauen Text.
    const buchungstext = (await transaction.locator('.transaction__text').textContent())?.trim();
    expect(buchungstext, 'Buchungstext der ersten Buchung').toBeTruthy();

    const select = transaction.locator('select');
    const previous = await select.inputValue();

    // Das Ziel kommt aus den echten Optionen, nicht aus einer im Test gespiegelten Liste: eine
    // Kopie von CATEGORIES würde beim nächsten neuen Label auseinanderlaufen, und der Test
    // prüfte dann eine Kategorie, die die UI gar nicht anbietet.
    const options = await select
      .locator('option')
      .evaluateAll((elements) => elements.map((element) => (element as HTMLOptionElement).value));
    const target = options.find((option) => option !== previous);
    expect(target, `Kategorie ungleich "${previous}" in den Optionen`).toBeTruthy();

    // Ab hier hat der Server gelernt — der Eintrag muss weg, auch wenn der Test danach scheitert.
    learnedPatterns.push(buchungstext!);

    await select.selectOption(target!);

    // AC 1, zweiter Teil: kein Fehlerbanner. `.save-notice` ist der Abdruck von
    // `saveErrorMessage` (`category-overview.html`); wäre es da, hätte der PUT nicht geklappt und
    // die Persistenz-Assertion unten prüfte einen optimistischen Zwischenstand.
    await expect(page.locator('app-notice.save-notice')).toHaveCount(0);

    // Der Reload ist die eigentliche Aussage: er wirft den Client-State weg, die Kategorie kommt
    // danach zwangsläufig aus der Datenbank statt aus dem optimistischen Update.
    await page.reload();

    // Nach der Korrektur steht die Buchung in der Zielkategorie, nicht mehr in der alten.
    const targetToggle = categoryToggle(page, target!);
    await expect(targetToggle).toBeVisible();
    await targetToggle.click();

    const moved = page
      .locator('tr.drilldown li.transaction')
      .filter({ hasText: buchungstext! });
    await expect(moved).toHaveCount(1);
    await expect(moved.locator('select')).toHaveValue(target!);
  });

  test('Fehlerpfad: gescheiterte Korrektur meldet den Fehler und nimmt die Anzeige zurück', async ({
    authenticatedContext,
    authenticatedPage: page,
  }) => {
    await importFixtureStatement(authenticatedContext.request);

    await page.goto(`/categories?month=${FIXTURE_MONTH}`);
    await page.locator('button.drilldown-toggle').first().click();

    const transaction = firstTransaction(page);
    await expect(transaction).toBeVisible();

    const select = transaction.locator('select');
    const previous = await select.inputValue();
    const options = await select
      .locator('option')
      .evaluateAll((elements) => elements.map((element) => (element as HTMLOptionElement).value));
    const target = options.find((option) => option !== previous);
    expect(target, `Kategorie ungleich "${previous}" in den Optionen`).toBeTruthy();

    // Nur der Korrektur-PUT scheitert. Das Muster trifft ausschliesslich
    // `PUT /api/transactions/{id}/category` — die Liste hängt an `/api/transactions` ohne Suffix
    // und lädt weiter normal, sonst prüfte der Test einen kaputten Screen statt eines
    // fehlgeschlagenen Speicherns.
    await page.route('**/api/transactions/*/category', (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
    );

    // Kein learnedPatterns-Eintrag: der PUT erreicht das Backend nie, es wird also nichts
    // gelernt und es gibt nichts aufzuräumen.
    await select.selectOption(target!);

    // `variant="error"` ist ein Angular-Input und im DOM unsichtbar; sein Abdruck ist
    // role="alert" aus den Host-Bindings von `notice.ts`.
    const failure = page.locator('app-notice.save-notice[role="alert"]');
    await expect(failure).toBeVisible();
    // Der Text hängt am `.notice__body`, nicht am Host: app-notice rendert seit FE-UI-07 ein
    // eigenes Icon, das in den textContent des Hosts mit einflösse.
    await expect(failure.locator('.notice__body')).toHaveText(
      'Die Kategorie konnte nicht gespeichert werden.',
    );

    // Der Rollback ist die eigentliche Aussage des Fehlerpfads: die Anzeige darf keinen Stand
    // behaupten, den der Server nicht hat (`applyCategory(…, previous)` im error-Callback).
    await expect(select).toHaveValue(previous);
  });
});
