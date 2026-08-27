import { expect, test } from '../fixtures/auth.fixture';

/**
 * Verifikations-Suite für die E2E-Harness (INFRA-14, US-01).
 *
 * Das Setup ist ohne Testfall nicht überprüfbar: dass der Browser startet, dass das Backend
 * antwortet, dass die SPA aus dem JAR kommt und dass das httpOnly-Cookie im Testkontext ankommt,
 * lässt sich nur zeigen, indem ein Test es benutzt. Ein grüner CI-Job ohne Assertions wäre
 * schlechter als kein Job — er erzeugt falsche Sicherheit.
 *
 * Der Auth-Flow ist als Verifikation gewählt, weil er ohne Abhängigkeit lieferbar ist (Auth ist
 * mit #53–#57 fertig) und trotzdem genau den kritischen Teil der Harness durchläuft.
 *
 * Die acht Must-Have-Story-Fälle (US-03…US-06) sind bewusst nicht Teil davon — Folgearbeit.
 */
test.describe('Auth-Flow', () => {
  test('liefert die im JAR gebündelte SPA aus', async ({ page }) => {
    // Zuerst die Grundannahme: das JAR enthält die Angular-SPA. Schlägt nur dieser Test fehl,
    // wurde das Backend ohne `-Pprod` gebaut — dann fehlt BOOT-INF/static und alle folgenden
    // Tests scheitern mit irreführenden Selector-Timeouts.
    const response = await page.goto('/login');

    expect(response?.status(), 'GET /login muss die SPA ausliefern (JAR mit -Pprod gebaut?)').toBe(
      200,
    );
    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible();
  });

  test('Registrierung übers Formular führt in den Onboarding-Wizard', async ({
    page,
    testUser,
  }) => {
    // Deep-Link auf /register: trifft zuerst den Server (Pushstate-Routing), muss also vom
    // SpaForwardController auf index.html weitergeleitet werden. Genau diese Route fehlte in
    // SecurityConfig und antwortete in Produktion mit 401 (mitgefixt in diesem PR).
    await page.goto('/register');

    await page.getByLabel('E-Mail').fill(testUser.email);
    await page.getByLabel('Passwort').fill(testUser.password);
    await page.getByRole('button', { name: 'Konto erstellen' }).click();

    // Ein frisches Konto hat onboardingCompleted = false; der onboardingGuard (FE-FC-02)
    // fängt die Navigation auf /dashboard ab und leitet auf den Wizard um.
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByRole('heading', { name: 'Fixkosten erfassen' })).toBeVisible();
  });

  test('setzt das JWT als httpOnly-Cookie mit SameSite=Strict', async ({
    page,
    context,
    testUser,
  }) => {
    await page.goto('/register');
    await page.getByLabel('E-Mail').fill(testUser.email);
    await page.getByLabel('Passwort').fill(testUser.password);
    await page.getByRole('button', { name: 'Konto erstellen' }).click();
    // Ein frisches Konto landet im Onboarding-Wizard, nicht auf /dashboard (FE-FC-02) —
    // das Cookie interessiert hier aber nicht, wo die Navigation endet.
    await expect(page).toHaveURL(/\/onboarding$/);

    // ADR-7 im echten Browser statt nur im Unit-Test: httpOnly schützt gegen XSS,
    // SameSite=Strict ersetzt den CSRF-Token. Beides wäre serverseitig leicht zu verlieren,
    // ohne dass ein Flow-Test es merkt.
    const jwtCookie = (await context.cookies()).find((cookie) => cookie.name === 'jwt');

    expect(jwtCookie, 'jwt-Cookie muss nach der Registrierung gesetzt sein').toBeDefined();
    expect(jwtCookie?.httpOnly).toBe(true);
    expect(jwtCookie?.sameSite).toBe('Strict');
    expect(jwtCookie?.value).not.toBe('');

    // Gegenprobe zum httpOnly-Flag: das Cookie darf für die SPA unsichtbar sein. Wäre es
    // lesbar, würde ein XSS das Token abgreifen können — und `withCredentials` wäre umsonst.
    await expect(page.evaluate(() => document.cookie)).resolves.not.toContain('jwt');
  });

  test('Login übers Formular führt in den Onboarding-Wizard', async ({
    page,
    request,
    testUser,
  }) => {
    // Konto out-of-band anlegen: die `request`-Fixture hat einen eigenen Cookie-Jar, der
    // Browser bleibt also anonym. Sonst wäre der Login-Pfad nicht isoliert vom Register-Pfad.
    const registered = await request.post('/api/auth/register', { data: testUser });
    expect(registered.status()).toBe(201);

    await page.goto('/login');
    await page.getByLabel('E-Mail').fill(testUser.email);
    await page.getByLabel('Passwort').fill(testUser.password);
    await page.getByRole('button', { name: 'Einloggen' }).click();

    // Das Konto ist frisch angelegt und damit nicht onboardet — derselbe onboardingGuard
    // wie bei der Registrierung greift auch hier (FE-FC-02).
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByRole('heading', { name: 'Fixkosten erfassen' })).toBeVisible();
  });

  test('Fehlerpfad: geschützte Route ohne Cookie leitet auf /login', async ({ page }) => {
    // Jeder Test bekommt einen frischen Context, dieser hier ist also anonym. Erwartet wird der
    // authGuard: /api/users/me antwortet 401, der Guard leitet weiter — ein E2E-Beweis, dass die
    // geschützte Route nicht doch die Shell zeigt.
    await page.goto('/dashboard');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible();
  });

  test('Auth-Fixture liefert eine eingeloggte Session ohne Formular-Klicks', async ({
    authenticatedPage,
  }) => {
    // Das ist der Test, an dem die späteren Must-Have-Tests hängen: die Fixture muss als
    // Vorbedingung genügen, damit US-03…US-06 direkt auf ihrer Route einsteigen können, ohne
    // sich vorher durchs Auth-UI zu klicken.
    await authenticatedPage.goto('/dashboard');

    await expect(authenticatedPage).toHaveURL(/\/dashboard$/);
    await expect(authenticatedPage.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  });
});
