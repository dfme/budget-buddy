import type { Page } from '@playwright/test';

import { expect, test } from '../fixtures/auth.fixture';

/**
 * Seitentitel fluchten auf jeder Seite an derselben Stelle (Befund zu PR #369, FE-STS-06).
 *
 * Budget, Import, Kategorie-Übersicht und Einstellungen sind je eine zentrierte Spalte von 40rem,
 * und der `h1` sitzt darin. Das Dashboard gab die Breite bis dahin jedem Element einzeln — nur
 * dem Titel nicht: Er lief über den ganzen Inhaltsbereich und stand auf breiten Schirmen links
 * aussen, während die Spalte darunter zentriert war.
 *
 * <p>Ein Layout-Befund, deshalb ein Browser-Test: jsdom rechnet keine Boxen. Breit genug, dass
 * die 40rem-Spalte deutlich schmaler ist als der Inhaltsbereich — auf schmalen Schirmen füllt die
 * Spalte ohnehin alles, und der Unterschied wäre unsichtbar.
 */
test.describe('Seitentitel', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  /** Linke Kante des Seitentitels, auf ganze Pixel gerundet. */
  async function titleLeft(page: Page, path: string): Promise<number> {
    await page.goto(path);
    const title = page.getByRole('heading', { level: 1 });
    await expect(title).toBeVisible();
    const box = await title.boundingBox();
    expect(box, `Box des h1 auf ${path}`).not.toBeNull();
    return Math.round(box!.x);
  }

  test('steht auf dem Dashboard an derselben Stelle wie auf den übrigen Seiten', async ({
    authenticatedPage: page,
  }) => {
    const reference = await titleLeft(page, '/budget');

    for (const path of ['/dashboard', '/import', '/categories', '/einstellungen']) {
      expect(await titleLeft(page, path), `linke Kante des Titels auf ${path}`).toBe(reference);
    }
  });

  test('fluchtet auf dem Dashboard mit der Spalte darunter', async ({
    authenticatedPage: page,
  }) => {
    const left = await titleLeft(page, '/dashboard');

    const selector = await page.locator('.month-selector').boundingBox();
    const totals = await page.locator('.totals-card').boundingBox();
    expect(Math.round(selector!.x)).toBe(left);
    expect(Math.round(totals!.x)).toBe(left);
  });
});
