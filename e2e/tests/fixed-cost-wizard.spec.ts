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
 * Für die Gegenprobe in der Tabelle unter `/budget` schliesst {@link completeOnboarding} das
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

    // Seit FE-FC-13 (#376) dasselbe Muster wie auf /budget: kein Inline-Formular mehr, sondern
    // «+ Neue Position» öffnet den Dialog. Die Felder werden im Dialog gesucht — über die Labels
    // statt über IDs: `app-field` verknüpft `<label for>` mit der projizierten Eingabe, das ist
    // derselbe Weg, den ein Screenreader-Nutzer nimmt.
    await page.getByRole('button', { name: '+ Neue Position' }).click();
    const dialog = page.getByRole('dialog', { name: 'Neue Position' });
    await dialog.getByLabel('Bezeichnung').fill(POSITION.bezeichnung);
    await dialog.getByLabel('Betrag (CHF)').fill(POSITION.betrag);
    await dialog.getByLabel('Intervall').selectOption(POSITION.intervall);
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(dialog).toBeHidden();

    // Die Position steht sofort in der Tabelle des Wizards — ohne Seitenwechsel. Zellen einzeln
    // statt als Zeilentext: nur so ist belegt, dass der Betrag pro Intervall und der
    // Monatsbetrag an den *richtigen* Stellen stehen und nicht bloss irgendwo in der Zeile. Seit
    // FE-FC-08 (#356) drei Spalten: Betrag und Intervall stehen als Unterzeile in der
    // Bezeichnungs-Zelle, der Monatsbetrag in der zweiten.
    await expect(page).toHaveURL(/\/onboarding$/);
    const wizardRow = page.getByRole('row').filter({ hasText: POSITION.bezeichnung });
    await expect(wizardRow).toHaveCount(1);
    await expect(wizardRow.getByRole('cell').nth(0).locator('.subline')).toHaveText(UNTERZEILE);
    await expect(wizardRow.getByRole('cell').nth(1)).toHaveText(MONATSBETRAG);

    // Gegenprobe über eine zweite Seite: die Tabelle im Wizard lädt zwar selbst per GET neu,
    // aber erst ein frischer Seitenaufbau schliesst aus, dass sie bloss lokalen Zustand zeigt.
    await completeOnboarding(page);
    await page.goto('/budget');

    const row = page.getByRole('row').filter({ hasText: POSITION.bezeichnung });
    await expect(row).toHaveCount(1);
    await expect(row.getByRole('cell').nth(0).locator('.subline')).toHaveText(UNTERZEILE);
    await expect(row.getByRole('cell').nth(1)).toHaveText(MONATSBETRAG);
  });

  test('Fehlerpfad: ungültige Eingaben melden den Fehler, ohne zu speichern', async ({
    freshUserPage: page,
  }) => {
    await page.goto('/onboarding');

    // Seit FE-FC-13 (#376) im Dialog «Neue Position». Anders als das frühere Inline-Formular
    // sperrt der Dialog «Speichern» nicht bis zur Gültigkeit (FE-FC-10): der Klick auf das leere
    // Formular zeigt beide Feldfehler gesammelt und lässt den Dialog offen.
    await page.getByRole('button', { name: '+ Neue Position' }).click();
    const dialog = page.getByRole('dialog', { name: 'Neue Position' });
    const saveButton = dialog.getByRole('button', { name: 'Speichern' });
    await saveButton.click();

    await expect(dialog.locator('p.field__error')).toHaveText([
      'Bezeichnung ist erforderlich.',
      'Betrag ist erforderlich.',
    ]);
    await expect(dialog).toBeVisible();

    // Zweite Variante aus dem AC-Wortlaut («Pflichtfeld leer bzw. ungültiger Betrag»): ein Betrag
    // mit drei Nachkommastellen. Ohne `maxTwoDecimals` liefe er bis in den Request und würde in
    // DECIMAL(10,2) still gerundet — stilles Runden ist bei Geld die unangenehme Variante.
    await dialog.getByLabel('Bezeichnung').fill('Handy');
    await dialog.getByLabel('Betrag (CHF)').fill('10.999');
    await saveButton.click();

    await expect(dialog.locator('p.field__error')).toHaveText([
      'Betrag darf höchstens zwei Nachkommastellen haben.',
    ]);
    await expect(dialog).toBeVisible();

    // Kein Wizard-Abschluss: die URL bleibt der Wizard — die für den Nutzer sichtbare Bedeutung
    // ist, dass ihn nichts aufs Dashboard trägt.
    await expect(page).toHaveURL(/\/onboarding$/);

    // Und der eigentliche Beleg für «kein Speichern»: die Liste ist leer. Dass der Dialog offen
    // bleibt, zeigt das nicht — ein Request könnte trotzdem rausgegangen sein.
    await completeOnboarding(page);
    await page.goto('/budget');
    await expect(page.getByText('Noch keine Fixkosten erfasst.')).toBeVisible();
  });
});
