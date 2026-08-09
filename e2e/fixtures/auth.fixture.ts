import { randomUUID } from 'node:crypto';

import { type BrowserContext, type Page, expect, test as base } from '@playwright/test';

/** Credentials eines für einen Test erzeugten Kontos. */
export interface TestUser {
  email: string;
  password: string;
}

/**
 * Erzeugt Credentials, die innerhalb eines Laufs garantiert frei sind.
 *
 * Die E-Mail ist unique, weil alle Tests eines Laufs dieselbe Datenbank teilen (ein Backend,
 * ein Schema) und `POST /auth/register` auf eine vergebene Adresse mit 409 antwortet. Eine feste
 * Adresse würde den zweiten Test im Lauf reissen lassen.
 *
 * `.test` als TLD ist per RFC 2606 für Tests reserviert und kann nie einer echten Domain gehören
 * — kein Risiko, dass ein Testlauf versehentlich eine reale Adresse belegt.
 */
export function uniqueTestUser(): TestUser {
  return {
    email: `e2e-${randomUUID()}@budgetbuddy.test`,
    // ≥ 8 Zeichen, sonst lehnt die Bean-Validation von RegisterRequest ab.
    password: 'e2e-test-passwort',
  };
}

/**
 * Auth-Fixtures für die E2E-Tests (INFRA-14).
 *
 * Die spätere Abdeckung der Must-Have-Stories (US-03…US-06) setzt überall eine eingeloggte
 * Session voraus. Die Anmeldung dort jedes Mal durchs Login-Formular zu klicken, würde jeden
 * dieser Tests zusätzlich am Auth-UI aufhängen — ein Fehler im Login-Formular liesse dann die
 * halbe Suite rot werden, ohne Hinweis auf die eigentliche Ursache. Deshalb registriert die
 * Fixture über die API.
 *
 * Der Trick mit dem httpOnly-Cookie: `context.request` teilt den Cookie-Jar mit dem
 * BrowserContext. Ein `POST /auth/register` darüber landet also im Browser, obwohl das Cookie
 * `HttpOnly` ist und JS es nie sehen kann (ADR-7) — genau deshalb wäre `document.cookie` oder
 * `addInitScript` hier kein Ersatz.
 */
export const test = base.extend<{
  testUser: TestUser;
  authenticatedContext: BrowserContext;
  authenticatedPage: Page;
}>({
  /** Frische Credentials pro Test — auch für Tests, die selbst registrieren wollen. */
  testUser: async ({}, use) => {
    await use(uniqueTestUser());
  },

  /** BrowserContext mit gültigem JWT-Cookie, Konto frisch über die API angelegt. */
  authenticatedContext: async ({ browser, baseURL, testUser }, use) => {
    // baseURL explizit: browser.newContext() erbt die `use`-Optionen der Config nicht.
    const context = await browser.newContext({ baseURL });

    const response = await context.request.post('/auth/register', { data: testUser });
    expect(
      response.status(),
      `Auth-Fixture: POST /auth/register für ${testUser.email} fehlgeschlagen`,
    ).toBe(201);

    await use(context);
    await context.close();
  },

  /** Page im eingeloggten Context — der übliche Einstieg für geschützte Routen. */
  authenticatedPage: async ({ authenticatedContext }, use) => {
    const page = await authenticatedContext.newPage();
    await use(page);
    await page.close();
  },
});

export { expect };
