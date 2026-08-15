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

  test('/categories mit Monat im Query-String liefert die SPA aus', async ({ request }) => {
    // FE-CAT-04: der Direktsprung schreibt den Monat als ?month=YYYY-MM in die URL. Dass der
    // Query-String kein Teil des Pfads ist und das exakte Pattern /categories weiterhin greift,
    // ist genau die Annahme, auf der die Entscheidung gegen /categories/2026-06 beruht — und die
    // Sorte Annahme, die INFRA-17 (#126) in Produktion aufgedeckt hat. Eine TestBed-Assertion
    // belegt nur, dass die Komponente den Parameter liest, nicht dass der Server die URL
    // ausliefert.
    const response = await request.get('/categories?month=2025-06');

    expect(response.status(), 'Deep-Link mit Monat').toBe(200);
    expect(await response.text()).toContain('<app-root>');
  });

  test('die API bleibt ohne Cookie geschützt', async ({ request }) => {
    // Gegenprobe zur SPA-Freigabe: sie darf die API nicht mit aufmachen.
    const response = await request.get('/users/me');

    expect(response.status()).toBe(401);
  });
});
