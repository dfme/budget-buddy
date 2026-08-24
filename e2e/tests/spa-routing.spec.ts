import { expect, test } from '@playwright/test';

/**
 * Deep-Link-Verhalten des ausgelieferten Artefakts (INFRA-14, INFRA-17).
 *
 * Diese Fälle prüfen HTTP-Status statt UI, weil es um die Server-Seite des Pushstate-Routings
 * geht: was passiert, wenn eine client-seitige Route per Hard-Reload zuerst den Server trifft.
 *
 * Es gibt dafür schon `SpaRoutingTest` (RANDOM_PORT) im Backend — aber genau diese Lücke war in
 * Produktion offen, während frühere MockMvc-basierte Unit-Tests grün waren: MockMvc lief gegen
 * Test-Fixtures unter `src/test/resources/static/`, nicht gegen das JAR mit dem echten
 * Angular-Build. Hier antwortet das Artefakt selbst.
 *
 * Seit INFRA-17 liegen alle REST-Endpoints unter `/api/**`, und ein Catch-all im
 * `SpaForwardController` leitet jede andere GET-Route auf `index.html` weiter — keine
 * enumerierte Liste mehr, die mit `frontend/src/app/app.routes.ts` synchron gehalten werden
 * muss. Vor INFRA-14 antworteten `/register`, `/categories` und `/import` in Produktion mit
 * 401, weil sie in einer der beiden (damals enumerierten) Backend-Listen fehlten.
 */
test.describe('SPA-Deep-Links', () => {
  for (const route of [
    '/dashboard', '/login', '/register', '/categories', '/import',
    '/onboarding', '/fixkosten', '/styleguide',
  ]) {
    test(`${route} liefert die SPA aus`, async ({ request }) => {
      const response = await request.get(route);

      expect(response.status(), `Hard-Reload auf ${route}`).toBe(200);
      expect(await response.text()).toContain('<app-root>');
    });
  }

  test('verschachtelte Route liefert die SPA aus', async ({ request }) => {
    // Kind-Route ohne eigenen Listeneintrag — der Catch-all deckt Nesting strukturell ab
    // (INFRA-17). Der Pfad muss nicht real vom Angular-Router bedient werden; es geht nur um
    // die Server-Seite des Forwards.
    const response = await request.get('/categories/lebensmittel');

    expect(response.status()).toBe(200);
    expect(await response.text()).toContain('<app-root>');
  });

  test('GET /api/import/{jobId}/status bleibt geschützt', async ({ request }) => {
    // /import ist Frontend-Route UND API-Prefix-Namensvetter (PdfImportController liegt unter
    // /api/import). Der Status-Endpoint muss 401 bleiben, nicht vom Catch-all geschluckt
    // werden — sonst wären Transaktionsdaten ohne Auth lesbar (Risiko #2).
    //
    // Seit BE-PDF-09 ist der Endpoint real (vorher nahm dieser Test ihn vorweg). Er beantwortet
    // damit zwei Fragen auf einmal: Der Pfad landet im Controller statt in der SPA, und ohne
    // Cookie kommt niemand an einen Job-Status.
    const response = await request.get('/api/import/42/status');

    expect(response.status(), 'darf nicht 200 mit index.html sein').toBe(401);
  });

  test('/categories mit Monat im Query-String liefert die SPA aus', async ({ request }) => {
    // FE-CAT-04: der Direktsprung schreibt den Monat als ?month=YYYY-MM in die URL. Dass der
    // Query-String kein Teil des Pfads ist und die Route weiterhin greift, ist genau die
    // Annahme, auf der die Entscheidung gegen /categories/2026-06 beruht — und die Sorte
    // Annahme, die INFRA-17 (#126) in Produktion aufgedeckt hat. Eine TestBed-Assertion belegt
    // nur, dass die Komponente den Parameter liest, nicht dass der Server die URL ausliefert.
    const response = await request.get('/categories?month=2025-06');

    expect(response.status(), 'Deep-Link mit Monat').toBe(200);
    expect(await response.text()).toContain('<app-root>');
  });

  test('die API bleibt ohne Cookie geschützt', async ({ request }) => {
    // Gegenprobe zur SPA-Freigabe: sie darf die API nicht mit aufmachen.
    const response = await request.get('/api/users/me');

    expect(response.status()).toBe(401);
  });

  test('Actuator und OpenAPI-Docs werden nicht vom Catch-all verschluckt', async ({ request }) => {
    // Regression-Guard für INFRA-17: /actuator/health hat wie eine Angular-Kind-Route zwei
    // Segmente ohne Punkt — ohne den expliziten Ausschluss im SpaForwardController-Regex hätte
    // der Catch-all ihn geschluckt und die SPA-Shell statt echter Health-Daten geliefert.
    const health = await request.get('/actuator/health');
    expect(health.status()).toBe(200);
    expect(await health.text()).not.toContain('<app-root>');

    const apiDocs = await request.get('/v3/api-docs');
    expect(apiDocs.status()).toBe(200);
    expect(await apiDocs.text()).not.toContain('<app-root>');
  });
});
