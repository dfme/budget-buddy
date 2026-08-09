import proxyConfig from '../proxy.conf.json';
import angularJson from '../angular.json';

/**
 * INFRA-07: Verifiziert die Verdrahtung des Angular Dev-Proxy.
 *
 * <p>Kein End-to-End-Proxytest (bräuchte beide laufende Server) — geprüft wird stattdessen,
 * dass die Proxy-Konfiguration die real existierenden Backend-Prefixe an `:8080` leitet und
 * dass `ng serve` sie über `angular.json` automatisch lädt.
 */
describe('Dev-Proxy Konfiguration', () => {
  const backend = 'http://localhost:8080';

  /**
   * Alle Pfad-Prefixe, unter denen das Backend Endpoints anbietet — je ein
   * `@RequestMapping`-Wurzelpfad: `/auth` (AuthController), `/users` (UserController,
   * `/users/me`), `/import` (PdfImportController), `/transactions`
   * (TransactionSummaryController + TransactionCategoryController).
   *
   * <p>Diese Liste war der Fehler von FE-CAT-02: `/transactions` kam mit BE-CAT-05 dazu,
   * ohne dass Proxy und Test nachgezogen wurden. `ng serve` beantwortete den Pfad daraufhin
   * mit dem SPA-Fallback `index.html` statt ihn ans Backend zu reichen — die
   * Kategorie-Übersicht lief in der lokalen Entwicklung in ihren Fehlerzweig, während
   * Produktion und E2E (ein Origin aus dem JAR, kein Proxy) unauffällig blieben.
   *
   * <p>**Neuer Backend-Prefix → hier und in `proxy.conf.json` eintragen.**
   */
  const backendPrefixes = ['/auth', '/users', '/import', '/transactions'];

  it.each(backendPrefixes)('leitet %s an das Backend auf :8080 weiter', (prefix) => {
    const entry = (proxyConfig as Record<string, { target: string; changeOrigin: boolean }>)[
      prefix
    ];
    expect(entry).toBeDefined();
    expect(entry.target).toBe(backend);
    expect(entry.changeOrigin).toBe(true);
  });

  // Gegenrichtung: ein Prefix in der Konfiguration, der hier nicht steht, ist entweder
  // überflüssig oder ein Hinweis, dass die Liste oben veraltet ist.
  it('enthält keine Prefixe ausserhalb der bekannten Backend-Pfade', () => {
    expect(Object.keys(proxyConfig).sort()).toEqual([...backendPrefixes].sort());
  });

  it('verdrahtet proxy.conf.json in der serve-Konfiguration von angular.json', () => {
    const serveOptions = angularJson.projects.budgetbuddy.architect.serve.options;
    expect(serveOptions.proxyConfig).toBe('proxy.conf.json');
  });
});
