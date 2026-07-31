import { expect, test } from '@playwright/test';

/**
 * Deep-Link-Verhalten des ausgelieferten Artefakts (INFRA-14).
 *
 * Diese Fälle prüfen HTTP-Status statt UI, weil es um die Server-Seite des Pushstate-Routings
 * geht: was passiert, wenn eine client-seitige Route per Hard-Reload zuerst den Server trifft.
 *
 * Es gibt dafür schon `SpaRoutingTest` (MockMvc) im Backend — aber genau diese Lücke war in
 * Produktion offen, während die Unit-Tests grün waren: MockMvc lief gegen Test-Fixtures unter
 * `src/test/resources/static/`, nicht gegen das JAR mit dem echten Angular-Build. Hier antwortet
 * das Artefakt selbst.
 */
test.describe('SPA-Deep-Links', () => {
  // Die client-seitigen Routen aus frontend/src/app/app.routes.ts. Ein Hard-Reload muss die SPA
  // ausliefern; vor diesem PR antworteten /register, /categories und /import in Produktion 401,
  // weil sie in SecurityConfig und SpaForwardController fehlten.
  for (const route of ['/dashboard', '/login', '/register', '/categories', '/import']) {
    test(`${route} liefert die SPA aus`, async ({ request }) => {
      const response = await request.get(route);

      expect(response.status(), `Hard-Reload auf ${route}`).toBe(200);
      expect(await response.text()).toContain('<app-root>');
    });
  }

  test('GET /import/{jobId}/status bleibt geschützt', async ({ request }) => {
    // /import ist Frontend-Route UND API-Prefix (PdfImportController). Deshalb stehen in
    // CLIENT_ROUTE_PATTERNS exakte Pfade und kein /import/**: mit Wildcard wäre dieser Pfad
    // permitAll und der geplante Status-Endpoint ohne Auth lesbar (Risiko #2, Datenleck).
    const response = await request.get('/import/42/status');

    expect(response.status(), 'darf nicht 200 mit index.html sein').toBe(401);
  });

  test('/styleguide bleibt in Produktion verschlossen', async ({ request }) => {
    // Dev-only Showcase (devOnlyGuard im Frontend). Bewusst NICHT in CLIENT_ROUTE_PATTERNS —
    // der Guard allein würde nur die client-seitige Navigation abdecken, nicht den Deep-Link.
    const response = await request.get('/styleguide');

    expect(response.status()).toBe(401);
  });

  test('die API bleibt ohne Cookie geschützt', async ({ request }) => {
    // Gegenprobe zur SPA-Freigabe: sie darf die API nicht mit aufmachen.
    const response = await request.get('/users/me');

    expect(response.status()).toBe(401);
  });
});
