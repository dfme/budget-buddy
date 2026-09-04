import { type Page } from '@playwright/test';

import { expect, test } from '../fixtures/auth.fixture';

/**
 * E2E-Abdeckung des Onboarding-Abschlusses aus US-03 (E2E-FC-02).
 *
 * Der Moment, in dem Lara den Wizard verlässt: `finishOnboarding()` ruft
 * `POST /users/me/onboarding-complete` und navigiert aufs Dashboard — erst dadurch lässt der
 * `onboardingGuard` die Route überhaupt passieren.
 *
 * Einstieg über `freshUserPage` und nicht über `authenticatedPage`. Das ist keine Stilfrage:
 * `authenticatedPage` schliesst das Onboarding per API ab, `onboardingCompleted` steht dort beim
 * Teststart also schon auf `true`. Der Übergang `false → true` ist über jene Fixture prinzipiell
 * nicht beobachtbar — deshalb gibt es die zweite.
 *
 * Zwei Tests, weil US-03 zwei Wege aus dem Wizard kennt: «Keine Fixkosten» bestätigen und
 * Abschluss nach mindestens einer gespeicherten Position. Der Wizard unterscheidet sie
 * ausschliesslich über die Button-Beschriftung, die Aktion ist dieselbe
 * (`fixed-cost-wizard.ts`, `hasSaved`). Dass beide wirklich zum Dashboard führen und nicht bloss
 * gleich aussehen, war bisher nur im Unit-Test belegt.
 *
 * Das gilt zusätzlich zu den beiden Fällen aus E2E-FC-01 (`fixed-cost-wizard.spec.ts`), die den
 * von CLAUDE.md geforderten Happy Path und Fehlerpfad für US-03 abdecken. Die Vorgabe ist ein
 * Minimum, keine Obergrenze.
 */
test.describe('Onboarding-Abschluss', () => {
  /**
   * Die beiden Beschriftungen des Abschluss-Buttons.
   *
   * Der Trennstrich ist ein Geviertstrich (U+2014), kein ASCII-Bindestrich. Er steht deshalb als
   * `—`-Escape und nicht als literales Zeichen — dieselbe Falle wie beim U+2019 in
   * `fixed-cost-wizard.spec.ts`: im Quelltext ist er von `-` kaum zu unterscheiden, und die
   * Verwechslung schriebe der Test sich sonst selbst hinein.
   */
  const BUTTON_MIT_POSITION = 'Fertig — weiter zum Dashboard';
  const BUTTON_OHNE_POSITION = 'Keine Fixkosten — weiter zum Dashboard';

  /** Irgendeine gültige Position — der Betrag spielt hier keine Rolle, nur ihre Existenz. */
  const POSITION = { bezeichnung: 'Miete', betrag: '1450', intervall: 'monatlich' };

  /**
   * Belegt den Ausgangszustand: Das Konto ist eingeloggt, aber nicht onboardet, und der
   * `onboardingGuard` wirft die Navigation aufs Dashboard in den Wizard zurück.
   *
   * Steht am Anfang beider Tests und nicht in einem `beforeEach`: Ohne diesen Nachweis könnte ein
   * Test grün werden, obwohl die Fixture das Onboarding versehentlich doch abschliesst — dann
   * bewiese der spätere Dashboard-Aufruf nichts mehr. Die Zeile ist die Vorbedingung, gegen die
   * der Rest überhaupt erst etwas aussagt.
   */
  async function erwarteWizardZwang(page: Page): Promise<void> {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByRole('heading', { name: 'Fixkosten erfassen' })).toBeVisible();
  }

  /**
   * Die Gegenprobe aus dem AC: Der Übergang muss echt sein, nicht bloss eine geglückte Navigation.
   *
   * Der Reload ist dabei der stärkere der beiden Nachweise — er wirft den `AuthService`-State weg
   * und lässt den Guard gegen `GET /api/users/me` neu entscheiden. Bliebe das Flag nur im
   * Speicher, käme der Wizard hier zurück.
   *
   * Die API-Abfrage danach ist kein zweiter Beweis, sondern Diagnose: Ohne sie sagt ein
   * Fehlschlag nur «URL ist /onboarding statt /dashboard», und ob der Request nie hinausging oder
   * der Guard falsch entschied, bliebe offen.
   */
  async function erwarteDauerhaftOnboardet(page: Page): Promise<void> {
    await page.reload();
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard$/);

    const profile = await page.request.get('/api/users/me');
    expect(profile.status()).toBe(200);
    expect(
      (await profile.json()).onboardingCompleted,
      'onboardingCompleted muss serverseitig gesetzt sein',
    ).toBe(true);
  }

  test('Weg A: nach einer gespeicherten Position führt der Abschluss aufs Dashboard', async ({
    freshUserPage: page,
  }) => {
    await erwarteWizardZwang(page);

    // Über die Labels statt über IDs: `app-field` verknüpft `<label for>` mit der projizierten
    // Eingabe, das ist derselbe Weg, den ein Screenreader-Nutzer nimmt.
    await page.getByLabel('Bezeichnung').fill(POSITION.bezeichnung);
    await page.getByLabel('Betrag (CHF)').fill(POSITION.betrag);
    await page.getByLabel('Intervall').selectOption(POSITION.intervall);
    await page.getByRole('button', { name: 'Fixkosten speichern' }).click();

    // Auf die Erfolgsmeldung warten, bevor die Beschriftung geprüft wird: `hasSaved` kippt im
    // Success-Handler desselben Requests. Ohne dieses Warten prüfte der Test die Beschriftung,
    // während der Request noch läuft — und läse verlässlich die falsche.
    await expect(page.locator('app-notice.notice--info .notice__body')).toHaveText(
      `«${POSITION.bezeichnung}» wurde gespeichert.`,
    );

    // Die Beschriftung ist der einzige sichtbare Unterschied zwischen den beiden Wegen aus US-03.
    // Sie hier festzunageln ist das, was diesen Test von Weg B unterscheidbar macht — ohne sie
    // liefen beide durch dieselbe Zusicherung.
    const abschluss = page.getByRole('button', { name: BUTTON_MIT_POSITION });
    await expect(abschluss).toBeVisible();
    await abschluss.click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    await erwarteDauerhaftOnboardet(page);
  });

  test('Weg B: ohne Position führt «Keine Fixkosten» ebenfalls aufs Dashboard', async ({
    freshUserPage: page,
  }) => {
    await erwarteWizardZwang(page);

    // Nichts gespeichert — `hasSaved` ist falsch, der Button trägt die andere Beschriftung.
    const abschluss = page.getByRole('button', { name: BUTTON_OHNE_POSITION });
    await expect(abschluss).toBeVisible();
    await abschluss.click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    await erwarteDauerhaftOnboardet(page);
  });
});
