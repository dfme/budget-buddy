import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { expect, test } from '../fixtures/auth.fixture';

/**
 * E2E-Abdeckung der Should-Have-Story US-12 «Zwischen Monaten wechseln» (E2E-STS-02).
 *
 * Ein Happy Path und ein Fehlerpfad — die in CLAUDE.md («Testing: Frameworks») vorgeschriebene
 * Menge, und zwar pro Story, nicht pro Issue. US-12 besteht aus mehreren Feature-Issues
 * (BE-STS-06/07, FE-CAT-08, FE-STS-04); die beiden Fälle gehören deshalb hierher und nicht in
 * einen Feature-PR.
 *
 * Einstieg über `authenticatedPage`: `/dashboard` und `/categories` liegen hinter `authGuard`
 * UND `onboardingGuard`, die Fixture erledigt beides über die API (siehe
 * `fixtures/auth.fixture.ts`).
 *
 * <p>Der Monat steht auf beiden Seiten im Query-Parameter `month` — das ist die ganze Mechanik
 * hinter «synchron» in AC 1. Deshalb prüft der Happy Path den Wechsel auf dem Dashboard und
 * folgt ihm anschliessend per Deep-Link auf die Kategorie-Übersicht, statt eine Kopplung zu
 * erwarten, die die Hauptnavigation gar nicht herstellt: ihre `routerLink`s tragen keine
 * Query-Parameter (`core/layout/shell.html`).
 */
test.describe('Monatswechsel', () => {
  /** Derselbe synthetische Auszug, den E2E-PDF-01 und E2E-CAT-01 benutzen. */
  const FIXTURE_PDF = join(__dirname, '..', 'fixtures', 'pdf', 'kontoauszug-synthetisch.pdf');

  /** Anzahl Buchungen in der Fixture, als Gegenprobe an der Vorbedingung. */
  const FIXTURE_TRANSACTION_COUNT = 5;

  /**
   * Monat der Fixture-Buchungen.
   *
   * <p>Juni 2025 ist der Grund, warum dieser Test ohne Zeitmanipulation auskommt: Der Monat ist
   * unwiderruflich vergangen, `GET /api/budget/safe-to-spend?month=2025-06` antwortet damit
   * zwangsläufig mit `status: 'CLOSED'` — und genau dieser Zustand ist der Gegenstand von AC 1.
   */
  const FIXTURE_MONTH = '2025-06';

  /**
   * Erwartetes Label des Fixture-Monats, bewusst als Literal.
   *
   * <p>Die laufzeitabhängigen Labels weiter unten entstehen über {@link monthLabel} und damit
   * über dieselbe `Intl`-Formatierung, die auch `shared/month.ts` benutzt — eine Kopie der
   * Produktionslogik, die nur sagt, dass beide Seiten dasselbe tun. Für den einen Monat, der
   * fest steht, steht hier deshalb der ausgeschriebene Erwartungswert: Er ist die einzige
   * Assertion im Spec, die eine falsche Locale (`June 2025`, `Juni 2025.`) überhaupt bemerken
   * könnte.
   */
  const FIXTURE_MONTH_LABEL = 'Juni 2025';

  /**
   * Obergrenze für den Kategorisierungs-Job. Seit ADR-14 (BE-PDF-09) läuft er asynchron; in der
   * Testinstanz ohne `ANTHROPIC_API_KEY` dauert er Millisekunden, der serverseitige Watchdog
   * steht aber auf 300s. Grosszügig, damit ein legitim langsamer Job nicht als Testfehler
   * erscheint, während das Backend noch innerhalb seiner eigenen Grenze arbeitet.
   */
  const IMPORT_TIMEOUT_MS = 60_000;

  /**
   * Importiert die Fixture über die API und wartet, bis der Kategorisierungs-Job durch ist.
   *
   * <p>Bewusst nicht durch die Upload-UI: der Import ist Vorbedingung dieses Tests, nicht sein
   * Gegenstand. Ihn durchzuklicken würde US-12 an US-04 aufhängen — ein Bug im Upload-UI liesse
   * dann auch diese beiden Fälle rot werden, ohne Hinweis auf die eigentliche Ursache. Dieselbe
   * Begründung, mit der die Auth-Fixture über die API registriert statt durchs Login-Formular.
   *
   * <p><strong>Auf `DONE` gewartet wird nicht aus Vorsicht.</strong> `Transaction.category`
   * bleibt bis zum Abschluss des Jobs `null` (`Transaction.java:67`), und die Kategorie-Übersicht
   * gruppiert danach. Ohne das Warten stünde der letzte Schritt des Happy Path vor einer leeren
   * Tabelle und meldete einen Fehler im Monatswechsel, wo in Wahrheit nur die Vorbedingung noch
   * lief.
   *
   * <p>Zweite Kopie neben `categorization.spec.ts:98` statt eines Helfers in `support/`: Das ist
   * gemeinsamer Harness-Code, an dem alle Specs hängen — für einen PR, der einen Test
   * hinzufügt, der falsche Blast Radius. Dieselbe Abwägung, mit der jener Spec sein Cleanup
   * lokal hält.
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

    const { jobId, total } = (await upload.json()) as {
      jobId: number;
      total: number;
    };
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
    // durchgehen, und der Test scheiterte danach an einer leeren Ansicht — mit einer Meldung, die
    // auf den Monatswechsel zeigt statt auf den Import.
    expect(status, `Import-Job ${jobId} endete nicht erfolgreich`).toBe('DONE');
  }

  /**
   * Der laufende Monat und sein Vormonat als `YYYY-MM`, aus einem einzigen `Date` abgeleitet.
   *
   * <p>Beides in einem Aufruf, weil der Test sonst zweimal «jetzt» läse: Ein Lauf, der exakt über
   * Mitternacht des Monatsletzten geht, bekäme dann einen Vormonat, der nicht zum vorher
   * gelesenen laufenden Monat gehört. Der Rest dieser Exposition bleibt bestehen — der Browser
   * liest seine eigene Uhr, siehe die Anmerkung in {@link monthLabel} — aber innerhalb des
   * Testprozesses ist der Bezug damit fest.
   */
  function currentAndPreviousMonth(): { current: string; previous: string } {
    const now = new Date();
    const toMonth = (date: Date): string =>
      `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
    return {
      current: toMonth(now),
      previous: toMonth(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
    };
  }

  /**
   * Das Label eines Monats, wie `shared/month.ts` es rendert.
   *
   * <p>Dieselbe `Intl`-Formatierung wie im Frontend, und das ist hier die schwächere Assertion:
   * Sie bestätigt nur, dass Testprozess und Browser dieselbe Regel anwenden. Für die Locale
   * selbst steht {@link FIXTURE_MONTH_LABEL} als ausgeschriebener Wert bereit. Nötig ist sie
   * trotzdem, weil laufender Monat und Vormonat vom Kalendertag des Laufs abhängen und ein
   * Literal dafür ab dem Folgemonat rot wäre.
   */
  function monthLabel(month: string): string {
    const [year, monthNumber] = month.split('-').map(Number);
    return new Intl.DateTimeFormat('de-CH', {
      month: 'long',
      year: 'numeric',
    }).format(new Date(year, monthNumber - 1, 1));
  }

  /** Das Label der Monatsnavigation — auf Dashboard und Kategorie-Übersicht dieselbe Komponente. */
  function monthNavLabel(page: Page): Locator {
    return page.locator('app-month-nav .month-nav__label');
  }

  test('Happy Path: Wechsel in einen vergangenen Monat zeigt «Abgeschlossen», Kategorien und Safe-to-Spend synchron', async ({
    authenticatedContext: context,
    authenticatedPage: page,
  }) => {
    await importFixtureStatement(context.request);

    const { current } = currentAndPreviousMonth();

    await page.goto('/dashboard');
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    // AC 1, erster Teil: ohne Parameter steht die Seite auf dem laufenden Monat. Belegt über
    // drei Dinge, die nur gemeinsam «aktueller Monat» heissen: das Label, der gesperrte
    // Vor-Pfeil (`isCurrentMonth`) und eine URL, die den Monat gar nicht erst nennt — das
    // Dashboard schreibt ihn erst beim Wechsel hinein.
    await expect(monthNavLabel(page)).toHaveText(monthLabel(current));
    await expect(page.getByRole('button', { name: 'Nächster Monat' })).toBeDisabled();
    expect(new URL(page.url()).searchParams.has('month')).toBe(false);

    // Der Sprung geht über das Dropdown und nicht über den Zurück-Pfeil: Juni 2025 liegt mehr
    // als ein Dutzend Monate zurück, der Stepper bräuchte ebenso viele Klicks. Der Pfeil kommt
    // im Fehlerpfad unten zum Zug — so ist jeder der beiden Bedienwege einmal abgedeckt.
    const monthSelect = page.getByLabel('Monat direkt wählen');
    // Erst die Option abwarten: Sie stammt aus `GET /api/transactions/months`, das die Seite
    // beim Aufbau lädt. Ohne diese Assertion schlüge ein zu früher `selectOption` mit einer
    // Meldung über einen fehlenden Wert fehl statt mit der Aussage, dass die Monatsliste den
    // importierten Monat nicht führt.
    await expect(monthSelect.locator(`option[value="${FIXTURE_MONTH}"]`)).toHaveCount(1);
    await monthSelect.selectOption(FIXTURE_MONTH);

    // Der Wechsel zieht die URL nach (`Dashboard.goTo`) — nur so überlebt der gewählte Monat
    // einen Reload und ist als Link teilbar.
    await expect(page).toHaveURL(new RegExp(`[?&]month=${FIXTURE_MONTH}(&|$)`));
    await expect(monthNavLabel(page)).toHaveText(FIXTURE_MONTH_LABEL);

    // AC 1, Kern: «Abgeschlossen» statt einer Zahl. Der Titel steht im `.notice__title`, nicht
    // im textContent des Hosts — app-notice rendert seit FE-UI-07 ein eigenes Icon, das dort
    // mit einflösse.
    const closed = page.locator('app-notice.closed-banner');
    await expect(closed).toBeVisible();
    await expect(closed.locator('.notice__title')).toHaveText('Abgeschlossen');

    // Die Gegenprobe zum Banner: Für einen abgeschlossenen Monat wird die Safe-to-Spend-Card gar
    // nicht erst gerendert (`dashboard.html`, @if closed() / @else if status === 'OPEN'). Ohne
    // diese Assertion bliebe offen, ob der Nutzer neben dem Banner noch einen Betrag sieht — und
    // ein Betrag für einen abgelaufenen Monat wäre genau die Aussage, die US-12 ausschliesst.
    await expect(page.locator('app-card.safe-to-spend-card')).toHaveCount(0);

    // Und der Keine-Daten-Hinweis steht nicht daneben. Die beiden schliessen sich aus
    // (`Dashboard.closed`), und die naheliegende falsche Implementierung — `status === 'CLOSED'`
    // ohne `&& !noData()` — liesse für einen leeren vergangenen Monat beides zugleich stehen.
    // Genau diese Kombination hält die Zeile fest.
    await expect(page.locator('.status.empty')).toHaveCount(0);

    // Die Drei-Monats-Übersicht wandert mit: Fenster und Hervorhebung enden beim gewählten Monat.
    await expect(page.locator('app-card.totals-card .card__title')).toHaveText(
      `Drei Monate bis ${FIXTURE_MONTH_LABEL}`,
    );
    const selectedRow = page.locator('tr.totals__row--selected');
    await expect(selectedRow).toHaveCount(1);
    await expect(selectedRow.locator('th.totals__month')).toContainText(FIXTURE_MONTH_LABEL);

    // Drei Beträge statt dreier Gedankenstriche — der Nachweis, dass die importierten Buchungen
    // wirklich in diesem Monat gelandet sind. Die Assertions darüber belegen das nicht: Wäre der
    // Import in einem anderen Monat gelandet, wären Banner und Hervorhebung unverändert da, nur
    // die Zeile trüge dann `–` (`dashboard.html`, @if row.income !== null).
    await expect(selectedRow.locator('app-amount')).toHaveCount(3);

    /*
     * Querprobe gegen das Backend statt eines im Test nachgebauten Monatsvergleichs.
     *
     * Sie beantwortet den Teil von AC 1, den die Oberfläche naturgemäss nicht zeigen kann:
     * Safe-to-Spend ist für diesen Monat nicht bloss ausgeblendet, sondern gar nicht berechnet.
     * Die Regel selbst («vergangener Monat ⇒ CLOSED») liegt bei `SafeToSpendServiceTest` mit
     * fixer Clock; hier gehört die Frage hin, ob die Seite denselben Monat meint wie das
     * Backend.
     */
    const response = await context.request.get(`/api/budget/safe-to-spend?month=${FIXTURE_MONTH}`);
    expect(response.status(), 'Querprobe: GET /api/budget/safe-to-spend').toBe(200);
    const body = await response.json();
    expect(body.status, 'Ein vergangener Monat ist abgeschlossen').toBe('CLOSED');
    expect(body.amount, 'Für einen abgeschlossenen Monat wird nichts gerechnet').toBeNull();
    expect(body.weeksLeft, 'Keine verbleibende Woche in einem abgelaufenen Monat').toBe(0);

    // AC 1, letzter Teil: Die Kategorie-Übersicht steht auf demselben Monat. Der Deep-Link ist
    // die Kopplung — beide Seiten lesen `month` aus der URL (`shared/month.ts`).
    await page.goto(`/categories?month=${FIXTURE_MONTH}`);
    await expect(page.getByRole('heading', { name: 'Kategorie-Übersicht' })).toBeVisible();
    await expect(monthNavLabel(page)).toHaveText(FIXTURE_MONTH_LABEL);

    // Die sichtbare Tabellenzeile ist der ganze Beweis, dass die Seite Daten dieses Monats zeigt:
    // `loading`, `errorMessage`, `isEmpty` und `summary` liegen in gegenseitig ausschliessenden
    // @else-if-Zweigen (`category-overview.html`).
    await expect(page.locator('button.drilldown-toggle').first()).toBeVisible();
    await expect(page.locator('app-card.chart-card .card__meta')).toHaveText(FIXTURE_MONTH_LABEL);
  });

  test('Fehlerpfad: Wechsel in einen Monat ohne importierte Daten zeigt den Keine-Daten-Hinweis mit Upload-CTA', async ({
    authenticatedContext: context,
    authenticatedPage: page,
  }) => {
    // Importiert wird auch hier — das Konto ist also NICHT leer. Genau das macht den Fall zum
    // Fehlerpfad von US-12 und nicht zu dem eines frischen Kontos: Der Hinweis muss am gewählten
    // Monat hängen, nicht daran, dass es überhaupt keine Buchungen gibt. Ohne den Import wäre
    // der Test auch gegen eine Implementierung grün, die den Hinweis pauschal zeigt.
    await importFixtureStatement(context.request);

    const { current, previous } = currentAndPreviousMonth();
    const previousLabel = monthLabel(previous);

    await page.goto('/dashboard');
    await expect(monthNavLabel(page)).toHaveText(monthLabel(current));

    // Diesmal über den Stepper: ein Klick zurück, in einen Monat, den die Fixture nicht
    // abdeckt. `disablePrev` ist auf dem Dashboard nicht gesetzt, der Pfeil ist also bedienbar.
    await page.getByRole('button', { name: 'Vorheriger Monat' }).click();
    await expect(page).toHaveURL(new RegExp(`[?&]month=${previous}(&|$)`));
    await expect(monthNavLabel(page)).toHaveText(previousLabel);

    // AC 2: der Hinweis im Wortlaut von FE-CAT-08, mit dem gewählten Monat darin. Exakt statt
    // Teilstring — der Monatsname ist die halbe Aussage, und `toContainText('Keine Daten')`
    // bliebe auch dann grün, wenn dort der falsche Monat stünde.
    const hint = page.locator('.status.empty');
    await expect(hint).toBeVisible();
    await expect(hint).toHaveText(`Keine Daten für ${previousLabel} — PDF hochladen?`);

    // Der Vormonat ist vergangen und damit `CLOSED` — trotzdem steht hier kein
    // «Abgeschlossen»-Banner: Der Keine-Daten-Hinweis verdrängt es (`Dashboard.closed`), weil
    // «Abgeschlossen» für einen Monat ohne jede Buchung nichts benennt, woran der Nutzer etwas
    // ändern könnte, «PDF hochladen?» dagegen schon. Ein Betrag steht ebenfalls nicht da.
    await expect(page.locator('app-notice.closed-banner')).toHaveCount(0);
    await expect(page.locator('app-card.safe-to-spend-card')).toHaveCount(0);

    /*
     * Querprobe: Der Monat ist wirklich leer, und der Hinweis ist keine Fehlanzeige einer
     * Ansicht, die bloss nicht geladen hat. Massgeblich ist dieselbe Quelle wie für
     * `Dashboard.noData` — alle drei Beträge `null` heisst laut `MonthlyTotals` genau dann, dass
     * der Monat keine Buchung trägt. `GET /api/transactions/months` wäre hier untauglich: Die
     * Liste führt nur Monate MIT Ausgaben und könnte einen Monat mit reinen Gutschriften
     * fälschlich als leer ausweisen.
     */
    const response = await context.request.get(
      `/api/transactions/monthly-totals?month=${previous}`,
    );
    expect(response.status(), 'Querprobe: GET /api/transactions/monthly-totals').toBe(200);
    const rows = (await response.json()) as {
      month: string;
      income: number | null;
      expenses: number | null;
      difference: number | null;
    }[];
    const row = rows.find((entry) => entry.month === previous);
    expect(row, `Übersicht enthält eine Zeile für ${previous}`).toBeDefined();
    expect(row?.income).toBeNull();
    expect(row?.expenses).toBeNull();
    expect(row?.difference).toBeNull();

    // Die CTA zuletzt, weil sie die Seite verlässt: Sie ist der Teil, der dem Nutzer einen
    // Ausweg lässt — ein Hinweis ohne Weg dorthin wäre bloss eine Feststellung. Geprüft wird sie
    // am Ziel, nicht am Attribut: `routerLink` steht nach dem Rendern nicht mehr im DOM, und ein
    // `href` allein belegt nicht, dass der Router die Route auch kennt.
    const cta = hint.getByRole('link', { name: 'PDF hochladen?' });
    await expect(cta).toBeVisible();
    await cta.click();
    await expect(page).toHaveURL(/\/import$/);
    await expect(page.getByRole('heading', { name: 'Import' })).toBeVisible();
  });
});
