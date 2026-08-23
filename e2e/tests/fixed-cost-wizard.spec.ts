import { expect, test } from '../fixtures/auth.fixture';

/**
 * E2E-Abdeckung der Must-Have-Story US-03 «Fixkosten erfassen» (E2E-FC-01).
 *
 * Ein Happy Path und ein Fehlerpfad — die in CLAUDE.md («Testing: Frameworks») vorgeschriebene
 * Menge, und zwar pro Story, nicht pro Issue.
 *
 * Einstieg über `authenticatedPage`. Die Fixture schliesst das Onboarding per API ab, der Wizard
 * wird deshalb per Direktnavigation erreicht statt über den erzwungenen Redirect des
 * `onboardingGuard`. Möglich ist das, weil `/onboarding` bewusst ohne diesen Guard registriert ist
 * (`app.routes.ts`: «das Ziel der Umleitung darf sich nicht selbst umleiten»). Der Redirect selbst
 * ist kein Verlust — er ist in `auth.spec.ts` für Registrierung und Login schon doppelt belegt.
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
   */
  const BETRAG_PRO_INTERVALL = /^CHF\s1\u2019200\.00$/;
  /** 1200 ÷ 3 — die Normalisierung aus `FixedCostService.monatsbetrag`. */
  const MONATSBETRAG = /^CHF\s400\.00$/;

  test('Happy Path: erfasste Position erscheint mit korrektem Betrag in der Liste', async ({
    authenticatedPage: page,
  }) => {
    await page.goto('/onboarding');
    await expect(page.getByRole('heading', { name: 'Fixkosten erfassen' })).toBeVisible();

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
    await expect(success).toBeVisible();
    await expect(success).toHaveText(`«${POSITION.bezeichnung}» wurde gespeichert.`);

    // Gegenprobe zur Erfolgsmeldung: die trägt nur die Bezeichnung aus der HTTP-Response. Dass die
    // Position wirklich persistiert ist und über einen zweiten Endpoint wieder herauskommt, zeigt
    // erst die Liste.
    await page.goto('/fixkosten');

    const row = page.getByRole('row').filter({ hasText: POSITION.bezeichnung });
    await expect(row).toHaveCount(1);

    // Zellen einzeln statt als Zeilentext: nur so ist belegt, dass der Betrag pro Intervall und
    // der Monatsbetrag in den *richtigen* Spalten stehen und nicht bloss irgendwo in der Zeile.
    await expect(row.getByRole('cell').nth(1)).toHaveText(BETRAG_PRO_INTERVALL);
    await expect(row.getByRole('cell').nth(2)).toHaveText(POSITION.intervall);
    await expect(row.getByRole('cell').nth(3)).toHaveText(MONATSBETRAG);
  });

  test('Fehlerpfad: ungültige Eingaben melden den Fehler, ohne zu speichern', async ({
    authenticatedPage: page,
  }) => {
    await page.goto('/onboarding');

    // Leeres Formular absenden. `submit()` bricht bei `form.invalid` ab und markiert alle Felder
    // als berührt — erst dadurch zeigen die Felder ihre Meldung überhaupt an.
    await page.getByRole('button', { name: 'Fixkosten speichern' }).click();

    await expect(page.locator('p.field__error')).toHaveText([
      'Bezeichnung ist erforderlich.',
      'Betrag ist erforderlich.',
    ]);

    // Zweite Variante aus dem AC-Wortlaut («Pflichtfeld leer bzw. ungültiger Betrag»): ein Betrag
    // mit drei Nachkommastellen. Ohne `maxTwoDecimals` liefe er bis in den Request und würde in
    // DECIMAL(10,2) still gerundet — stilles Runden ist bei Geld die unangenehme Variante.
    await page.getByLabel('Bezeichnung').fill('Handy');
    await page.getByLabel('Betrag (CHF)').fill('10.999');

    await expect(page.locator('p.field__error')).toHaveText([
      'Betrag darf höchstens zwei Nachkommastellen haben.',
    ]);

    // Kein Erfolgszustand daneben, und kein Wizard-Abschluss: die URL bleibt der Wizard. Das Flag
    // `onboardingCompleted` steht durch die Fixture schon auf true, taugt hier also nicht als
    // Beleg — die für den Nutzer sichtbare Bedeutung ist, dass ihn nichts aufs Dashboard trägt.
    await expect(page.locator('app-notice.notice--info')).toHaveCount(0);
    await expect(page).toHaveURL(/\/onboarding$/);

    // Und der eigentliche Beleg für «kein Speichern»: die Liste ist leer. Dass im Formular keine
    // Erfolgsmeldung steht, zeigt das nicht — ein Request könnte trotzdem rausgegangen sein.
    await page.goto('/fixkosten');
    await expect(page.getByText('Noch keine Fixkosten erfasst.')).toBeVisible();
  });
});
