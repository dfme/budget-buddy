import type { APIRequestContext, Page } from '@playwright/test';

import { expect, test } from '../fixtures/auth.fixture';

/**
 * Fixkosten-Tabelle auf `/budget` in beiden Varianten (FE-FC-08, #356).
 *
 * Die Tabelle hat auf jeder Breite drei Spalten — Bezeichnung mit Unterzeile, Monatsbetrag,
 * Aktionen. Unter `$bp-desktop` (900px) sind Bearbeiten und Löschen Icon-Buttons, ab 900px tragen
 * sie Icon und Text. In beiden Varianten scrollt nichts; auf main lief die fünfspaltige Tabelle
 * auch auf Desktop über (702px in 590px Card, Scope-Entscheid zu #356).
 *
 * <p>Der Wechsel Icon ↔ Icon+Text ist reines CSS am Breakpoint, ein Markup für beide Varianten.
 * Die Component-Tests (jsdom) belegen das Markup; Sichtbarkeit, Grössen und Überlauf brauchen
 * Layout und damit einen echten Browser — deshalb dieser Test.
 *
 * <p>Positionen über die API wie in `recurring-expenses.spec.ts`: der Wizard hat seine eigene
 * Abdeckung (E2E-FC-01). Die lange Bezeichnung ist Absicht — sie ist der Fall, in dem die
 * schmale Tabelle am ehesten über die Card hinausliefe.
 */
const POSITIONS = [
  { bezeichnung: 'Miete', betrag: 1_450, intervall: 'monatlich' },
  { bezeichnung: 'Serafe', betrag: 335, intervall: 'jaehrlich' },
  {
    bezeichnung: 'Hausrat- und Privathaftpflichtversicherung',
    betrag: 312.4,
    intervall: 'jaehrlich',
  },
] as const;

async function createPositions(request: APIRequestContext): Promise<void> {
  for (const data of POSITIONS) {
    const response = await request.post('/api/fixed-costs', { data });
    expect(response.status(), `Vorbedingung: POST /api/fixed-costs (${data.bezeichnung})`).toBe(
      201,
    );
  }
}

/** Breite des Inhalts minus sichtbare Breite des Scroll-Containers — 0 heisst: kein Überlauf. */
async function horizontalOverflow(page: Page): Promise<number> {
  return page.locator('.table-scroll').evaluate((el) => el.scrollWidth - el.clientWidth);
}

test.describe('Fixkosten-Tabelle (FE-FC-08)', () => {
  test.describe('Smartphone, 390px', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('drei Spalten, kein horizontales Scrollen, Icon-Buttons mit Positionsnamen', async ({
      authenticatedContext: context,
      authenticatedPage: page,
    }) => {
      await createPositions(context.request);
      await page.goto('/budget');

      const serafe = page.getByRole('row').filter({ hasText: 'Serafe' });
      await expect(serafe).toHaveCount(1);

      // AC: kein horizontales Scrollen bei 390px.
      expect(await horizontalOverflow(page)).toBe(0);

      // Drei Spalten; Betrag und Intervall stehen in der Unterzeile.
      await expect(page.getByRole('columnheader')).toHaveText([
        'Bezeichnung',
        'Monatsbetrag',
        'Aktionen',
      ]);
      await expect(page.getByRole('columnheader', { name: 'Monatsbetrag' })).toBeVisible();
      // Der Aktionen-Kopf ist nur visuell versteckt — für Screenreader bleibt er da.
      const actionsLabel = await page.locator('.actions__label').boundingBox();
      expect(actionsLabel?.width).toBeLessThanOrEqual(1);

      await expect(serafe.locator('.subline')).toBeVisible();
      await expect(serafe.locator('.subline')).toHaveText('CHF 335.00 · jährlich');
      await expect(
        page.getByRole('row').filter({ hasText: 'Miete' }).locator('.subline'),
      ).toHaveText('monatlich');

      // Icon-Buttons: per Rolle mit der Position im Namen auffindbar, je mindestens 44×44px.
      for (const name of ['Bearbeiten: Miete', 'Löschen: Miete']) {
        const button = page.getByRole('button', { name });
        await expect(button).toBeVisible();
        const box = await button.boundingBox();
        expect(box?.width, name).toBeGreaterThanOrEqual(44);
        expect(box?.height, name).toBeGreaterThanOrEqual(44);
        // Nur das Icon: ein sichtbares Label daneben machte den Button deutlich breiter.
        expect(box?.width, name).toBeLessThan(48);
        // Ohne sichtbaren Text zeigt der Tooltip das Label.
        await expect(button).toHaveAttribute('title', name.split(':')[0]);
      }
    });

    test('Bearbeiten und Löschen funktionieren auch über die Icon-Buttons', async ({
      authenticatedContext: context,
      authenticatedPage: page,
    }) => {
      await createPositions(context.request);
      await page.goto('/budget');

      await page.getByRole('button', { name: 'Bearbeiten: Serafe' }).click();
      await expect(page.getByLabel('Bezeichnung')).toHaveValue('Serafe');
      // Auch das aufgeklappte Formular bleibt innerhalb der Card.
      expect(await horizontalOverflow(page)).toBe(0);
      await page.getByRole('button', { name: 'Abbrechen' }).click();

      await page.getByRole('button', { name: 'Löschen: Serafe' }).click();
      await expect(page.getByText('«Serafe» wirklich löschen?')).toBeVisible();
    });
  });

  test.describe('Desktop, 1280px', () => {
    test('drei Spalten, kein horizontales Scrollen, Buttons mit Icon und Text', async ({
      authenticatedContext: context,
      authenticatedPage: page,
    }) => {
      await createPositions(context.request);
      await page.goto('/budget');

      await expect(page.getByRole('columnheader', { name: 'Aktionen' })).toBeVisible();
      await expect(
        page.getByRole('row').filter({ hasText: 'Serafe' }).locator('.subline'),
      ).toHaveText('CHF 335.00 · jährlich');

      // Auf main lief die Tabelle hier über (702px in 590px), «Löschen» lag ausserhalb.
      expect(await horizontalOverflow(page)).toBe(0);

      const container = await page.locator('.table-scroll').boundingBox();
      for (const [name, label] of [
        ['Bearbeiten: Miete', 'Bearbeiten'],
        ['Löschen: Miete', 'Löschen'],
      ]) {
        const button = page.getByRole('button', { name });
        await expect(button.locator('svg')).toBeVisible();
        // Das Label steht sichtbar neben dem Icon — breiter als ein 1px-Screenreader-Text.
        const labelBox = await button.locator('.btn__label').boundingBox();
        expect(labelBox?.width, name).toBeGreaterThan(40);
        await expect(button.locator('.btn__label')).toHaveText(label);
        // Innerhalb der sichtbaren Breite des Scroll-Containers, nicht rechts davon abgeschnitten.
        const box = await button.boundingBox();
        expect(box!.x + box!.width, name).toBeLessThanOrEqual(container!.x + container!.width);
      }
    });
  });
});
