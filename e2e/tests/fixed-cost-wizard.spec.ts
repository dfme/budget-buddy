import type { Page } from '@playwright/test';

import { expect, test } from '../fixtures/auth.fixture';

/** Schliesst das Onboarding per API ab, damit `/budget` erreichbar wird (siehe Kopfkommentar). */
async function completeOnboarding(page: Page): Promise<void> {
  const response = await page.request.post('/api/users/me/onboarding-complete');
  expect(response.status(), 'POST /api/users/me/onboarding-complete').toBe(200);
}

/**
 * E2E-Abdeckung der Must-Have-Story US-03 «Fixkosten erfassen» (E2E-FC-01).
 *
 * Ein Happy Path und ein Fehlerpfad — die in CLAUDE.md («Testing: Frameworks») vorgeschriebene
 * Menge, und zwar pro Story, nicht pro Issue.
 *
 * Einstieg über `freshUserPage`, nicht über `authenticatedPage`: seit FE-FC-12 (#375) sperrt der
 * `onboardingPendingGuard` den Wizard für onboardete User und leitet sie auf `/budget` um. Der
 * Wizard ist damit nur noch mit offenem Onboarding erreichbar — genau der Zustand, den
 * `freshUserPage` liefert. Die Direktnavigation auf `/onboarding` ersetzt den erzwungenen
 * Redirect des `onboardingGuard`; der ist in `auth.spec.ts` für Registrierung und Login schon
 * doppelt belegt.
 *
 * Für die Gegenprobe in der Liste unter `/budget` schliesst {@link completeOnboarding} das
 * Onboarding per API ab — mit offenem Onboarding würde der `onboardingGuard` die Navigation
 * dorthin zurück in den Wizard werfen. Der Abschluss über den Wizard-Button selbst ist Sache von
 * `onboarding-completion.spec.ts` (E2E-FC-02).
 */
test.describe('Fixkosten-Wizard', () => {
  /**
   * Bewusst `quartalsweise` mit vierstelligem Betrag: nur so trägt ein einziger Fall beide
   * Aussagen, die die AC verlangt. Bei `monatlich` wären Betrag und Monatsbetrag identisch — der
   * Test könnte die beiden Spalten nicht auseinanderhalten und die Normalisierung nicht prüfen.
   */
  const POSITION = { bezeichnung: 'Krankenkasse', betrag: '1200', intervall: 'quartalsweise' };

  /**
   * Erwartete CHF-Beträge in der Liste.
   *
   * Der `CurrencyPipe` liefert unter `de-CH` kein ASCII: als Tausendertrennung steht ein Right
   * Single Quotation Mark (U+2019), nach «CHF» ein No-Break Space (U+00A0). Ein naives
   * `CHF 1'200.00` mit ASCII-Apostroph reisst — nachgestellt, nicht vermutet.
   *
   * Das U+2019 steht deshalb als `\u2019`-Escape und nicht als literales Zeichen: von einem
   * ASCII-`'` ist es im Quelltext kaum zu unterscheiden, und genau diese Verwechslung ist die
   * Falle, die dieser Test sonst selbst hineinschreiben würde.
   *
   * Der NBSP dagegen als `\s`: Playwright normalisiert Whitespace beim Textvergleich, ASCII-Space
   * und NBSP sind dort austauschbar (beide Varianten am Lauf geprüft). `\s` sagt genau das aus.
   *
   * Betrag pro Intervall und Intervall stehen seit FE-FC-08 (#356) als Unterzeile unter der
   * Bezeichnung, nicht mehr in eigenen Spalten — `\s` auch um den Mittelpunkt.
   */
  const UNTERZEILE = /^CHF\s1\u2019200\.00\s·\squartalsweise$/;
  /** 1200 ÷ 3 — die Normalisierung aus `FixedCostService.monatsbetrag`. */
  const MONATSBETRAG = /^CHF\s400\.00$/;

  test('Happy Path: erfasste Position erscheint mit korrektem Betrag in der Liste', async ({
    freshUserPage: page,
  }) => {
    await page.goto('/onboarding');
    await expect(page.getByRole('heading', { name: 'Budget', exact: true })).toBeVisible();

    // Über die Labels statt über IDs: `app-field` verknüpft `<label for>` mit der projizierten
    // Eingabe, das ist derselbe Weg, den ein Screenreader-Nutzer nimmt.
    await page.getByLabel('Bezeichnung').fill(POSITION.bezeichnung);
    await page.getByLabel('Betrag (CHF)').fill(POSITION.betrag);
    await page.getByLabel('Intervall').selectOption(POSITION.intervall);
    await page.getByRole('button', { name: 'Fixkosten speichern' }).click();

    // Der Erfolg meldet sich als `variant="info"` und damit höflich (role="status") — eine
    // gespeicherte Position soll den Screenreader nicht unterbrechen (`notice.ts`). `variant` ist
    // ein Angular-Input und im DOM unsichtbar; geprüft werden seine beiden Abdrücke.
    const success = page.locator('app-notice.notice--info[role="status"]');
    // Der Text wird am `.notice__body` geprüft, nicht am Host: app-notice rendert seit
    // FE-UI-07 ein eigenes Icon, das in den textContent des Hosts mit einflösse.
    const successText = success.locator('.notice__body');
    await expect(success).toBeVisible();
    await expect(successText).toHaveText(`«${POSITION.bezeichnung}» wurde gespeichert.`);

    // Gegenprobe zur Erfolgsmeldung: die trägt nur die Bezeichnung aus der HTTP-Response. Dass die
    // Position wirklich persistiert ist und über einen zweiten Endpoint wieder herauskommt, zeigt
    // erst die Liste.
    await completeOnboarding(page);
    await page.goto('/budget');

    const row = page.getByRole('row').filter({ hasText: POSITION.bezeichnung });
    await expect(row).toHaveCount(1);

    // Zellen einzeln statt als Zeilentext: nur so ist belegt, dass der Betrag pro Intervall und
    // der Monatsbetrag an den *richtigen* Stellen stehen und nicht bloss irgendwo in der Zeile.
    // Seit FE-FC-08 (#356) drei Spalten: Betrag und Intervall stehen als Unterzeile in der
    // Bezeichnungs-Zelle, der Monatsbetrag in der zweiten.
    await expect(row.getByRole('cell').nth(0).locator('.subline')).toHaveText(UNTERZEILE);
    await expect(row.getByRole('cell').nth(1)).toHaveText(MONATSBETRAG);
  });

  test('Fehlerpfad: ungültige Eingaben melden den Fehler, ohne zu speichern', async ({
    freshUserPage: page,
  }) => {
    await page.goto('/onboarding');

    // FE-FC-09 (#360): der Button ist bis zur Gültigkeit deaktiviert — dasselbe Muster wie bei
    // der eingebetteten Einkommens-Card. Ein Klick auf das leere Formular ist deshalb kein Weg
    // mehr, die Feldfehler auszulösen; sie erscheinen beim Verlassen des jeweiligen Felds
    // (blur), einzeln statt gesammelt per `markAllAsTouched()`.
    const submitButton = page.getByRole('button', { name: 'Fixkosten speichern' });
    await expect(submitButton).toBeDisabled();

    await page.getByLabel('Bezeichnung').focus();
    await page.getByLabel('Betrag (CHF)').focus(); // blurt Bezeichnung
    await page.getByLabel('Intervall').focus(); // blurt Betrag

    await expect(page.locator('p.field__error')).toHaveText([
      'Bezeichnung ist erforderlich.',
      'Betrag ist erforderlich.',
    ]);
    await expect(submitButton).toBeDisabled();

    // Zweite Variante aus dem AC-Wortlaut («Pflichtfeld leer bzw. ungültiger Betrag»): ein Betrag
    // mit drei Nachkommastellen. Ohne `maxTwoDecimals` liefe er bis in den Request und würde in
    // DECIMAL(10,2) still gerundet — stilles Runden ist bei Geld die unangenehme Variante.
    await page.getByLabel('Bezeichnung').fill('Handy');
    await page.getByLabel('Betrag (CHF)').fill('10.999');
    await page.getByLabel('Intervall').focus(); // blurt Betrag erneut mit dem neuen Wert

    await expect(page.locator('p.field__error')).toHaveText([
      'Betrag darf höchstens zwei Nachkommastellen haben.',
    ]);
    await expect(submitButton).toBeDisabled();

    // Kein Erfolgszustand daneben, und kein Wizard-Abschluss: die URL bleibt der Wizard — die für
    // den Nutzer sichtbare Bedeutung ist, dass ihn nichts aufs Dashboard trägt.
    await expect(page.locator('app-notice.notice--info')).toHaveCount(0);
    await expect(page).toHaveURL(/\/onboarding$/);

    // Und der eigentliche Beleg für «kein Speichern»: die Liste ist leer. Dass im Formular keine
    // Erfolgsmeldung steht, zeigt das nicht — ein Request könnte trotzdem rausgegangen sein.
    await completeOnboarding(page);
    await page.goto('/budget');
    await expect(page.getByText('Noch keine Fixkosten erfasst.')).toBeVisible();
  });
});
