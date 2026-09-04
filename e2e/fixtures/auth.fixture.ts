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
 * ein Schema) und `POST /api/auth/register` auf eine vergebene Adresse mit 409 antwortet. Eine
 * feste Adresse würde den zweiten Test im Lauf reissen lassen.
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
 * Legt das Testkonto über die API an und bringt dabei das JWT-Cookie in den BrowserContext.
 *
 * Gemeinsame Grundlage der beiden Context-Fixtures unten: Sie unterscheiden sich einzig darin, ob
 * danach noch das Onboarding abgeschlossen wird. Als Kopie in beiden Fixturen wäre derselbe
 * Aufruf samt Assertion zweimal vorhanden und liefe auseinander, sobald
 * `POST /api/auth/register` einmal ein Feld mehr verlangt.
 */
async function registerViaApi(context: BrowserContext, testUser: TestUser): Promise<void> {
  const response = await context.request.post('/api/auth/register', {
    data: testUser,
  });
  expect(
    response.status(),
    `Auth-Fixture: POST /api/auth/register für ${testUser.email} fehlgeschlagen`,
  ).toBe(201);
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
 * BrowserContext. Ein `POST /api/auth/register` darüber landet also im Browser, obwohl das Cookie
 * `HttpOnly` ist und JS es nie sehen kann (ADR-7) — genau deshalb wäre `document.cookie` oder
 * `addInitScript` hier kein Ersatz.
 *
 * Ein frisch registriertes Konto hat `onboardingCompleted = false` (`User`-Konstruktor,
 * BE-AUTH-03); seit FE-FC-02 leitet der `onboardingGuard` einen solchen Account von
 * `/dashboard`, `/categories` und `/import` auf `/onboarding` um. Ohne den Abschluss hier
 * würde die Fixture ihr eigenes Versprechen brechen — US-04…US-06 kämen nie auf ihrer
 * Zielroute an, sondern landeten jedes Mal im Wizard.
 *
 * Genau dieser Abschluss macht die Fixture für einen Fall aber unbrauchbar: den Abschluss selbst.
 * Dafür gibt es seit E2E-FC-02 `freshUserPage` — registriert, aber nicht onboardet. Die beiden
 * Einstiege schliessen sich gegenseitig aus; welcher wofür gedacht ist, steht an ihnen.
 */
export const test = base.extend<{
  testUser: TestUser;
  authenticatedContext: BrowserContext;
  authenticatedPage: Page;
  freshUserContext: BrowserContext;
  freshUserPage: Page;
}>({
  /** Frische Credentials pro Test — auch für Tests, die selbst registrieren wollen. */
  testUser: async ({}, use) => {
    await use(uniqueTestUser());
  },

  /**
   * BrowserContext mit gültigem JWT-Cookie, Konto frisch über die API angelegt.
   *
   * Aufgesetzt auf Playwrights eingebaute `context`-Fixture statt auf ein eigenes
   * `browser.newContext()`. Der Unterschied ist nicht kosmetisch: `video`, `trace`, `screenshot`
   * und `baseURL` aus dem `use`-Block der Config werden nicht vom Browser gelesen, sondern von
   * Playwrights `_contextFactory` beim Erzeugen des Contexts zusammengebaut — sie schreibt
   * `recordVideo` hinein und hängt das Video beim Schliessen an den Report. Ein selbst erzeugter
   * Context geht daran vorbei und liefert stumm keine Artefakte: jeder Test auf dieser Fixture
   * blieb ohne Video, während die Tests auf der eingebauten `page`-Fixture welche hatten.
   *
   * Kein eigenes `context.close()` mehr: das erledigt die eingebaute Fixture — und nur ihr
   * Schliessen speichert das Video an den Ort, den der Report erwartet.
   */
  authenticatedContext: async ({ context, testUser }, use) => {
    await registerViaApi(context, testUser);

    // Onboarding abschliessen, sonst würde der onboardingGuard jede spätere Navigation auf
    // /dashboard, /categories oder /import in den Wizard zurückwerfen.
    const onboarded = await context.request.post('/api/users/me/onboarding-complete');
    expect(
      onboarded.status(),
      `Auth-Fixture: POST /api/users/me/onboarding-complete für ${testUser.email} fehlgeschlagen`,
    ).toBe(200);

    await use(context);
  },

  /** Page im eingeloggten Context — der übliche Einstieg für geschützte Routen. */
  authenticatedPage: async ({ authenticatedContext }, use) => {
    const page = await authenticatedContext.newPage();
    await use(page);
    await page.close();
  },

  /**
   * BrowserContext mit gültigem JWT-Cookie, aber **ohne** abgeschlossenes Onboarding (E2E-FC-02).
   *
   * Der Gegenentwurf zu `authenticatedContext`, und zwar aus einem Grund, der sich nicht umgehen
   * lässt: Jene Fixture ruft `onboarding-complete` unbedingt, `onboardingCompleted` steht beim
   * Teststart also immer schon auf `true`. Der Übergang `false → true` — der Moment, in dem Lara
   * den Wizard verlässt — ist über sie damit *prinzipiell* nicht beobachtbar, nicht bloss
   * umständlich.
   *
   * Wer diese Fixture nimmt, bekommt deshalb genau das, was `authenticatedContext` bewusst
   * verhindert: Der `onboardingGuard` wirft jede Navigation auf `/dashboard`, `/categories`,
   * `/import`, `/fixkosten` und `/einstellungen` in den Wizard zurück. Für alles andere als den
   * Onboarding-Abschluss ist `authenticatedPage` der richtige Einstieg.
   *
   * **Nicht mit `authenticatedContext` im selben Test kombinieren.** Beide sitzen auf derselben
   * eingebauten `context`-Fixture (Begründung dort), teilen sich also einen Cookie-Jar — die
   * zweite Registrierung überschriebe das Cookie der ersten, und der Test liefe gegen ein
   * anderes Konto als gedacht.
   */
  freshUserContext: async ({ context, testUser }, use) => {
    await registerViaApi(context, testUser);
    await use(context);
  },

  /** Page im Context ohne abgeschlossenes Onboarding — Einstieg für E2E-FC-02. */
  freshUserPage: async ({ freshUserContext }, use) => {
    const page = await freshUserContext.newPage();
    await use(page);
    await page.close();
  },
});

export { expect };
