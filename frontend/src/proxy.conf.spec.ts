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
   * Die Prefixe, die die SPA über den Dev-Server erreichen muss — je ein
   * `@RequestMapping`-Wurzelpfad: `/auth` (AuthController), `/users` (UserController,
   * `/users/me`), `/import` (PdfImportController), `/transactions`
   * (TransactionSummaryController + TransactionCategoryController), `/fixed-costs`
   * (FE-FC-01; der Controller entsteht erst mit #12).
   *
   * <p>Nicht identisch mit «alle Backend-Endpoints»: `/v3/api-docs`, `/swagger-ui`,
   * `/actuator/health`, `/actuator/info` und `/error` sind ebenfalls Backend-Pfade
   * (`SecurityConfig`), stehen hier aber bewusst nicht — die SPA ruft sie nicht auf, und wer
   * Swagger im Dev braucht, geht direkt auf `:8080`.
   *
   * <p>Diese Liste war der Fehler von FE-CAT-02: `/transactions` kam mit BE-CAT-05 dazu,
   * ohne dass Proxy und Test nachgezogen wurden. `ng serve` beantwortete den Pfad daraufhin
   * mit dem SPA-Fallback `index.html` statt ihn ans Backend zu reichen — die
   * Kategorie-Übersicht lief in der lokalen Entwicklung in ihren Fehlerzweig, während
   * Produktion und E2E (ein Origin aus dem JAR, kein Proxy) unauffällig blieben.
   *
   * <p>**Neuer Prefix, den die SPA aufruft → hier und in `proxy.conf.json` eintragen.**
   */
  const proxiedPrefixes = ['/auth', '/users', '/import', '/transactions', '/fixed-costs'];

  it.each(proxiedPrefixes)('leitet %s an das Backend auf :8080 weiter', (prefix) => {
    const entry = (proxyConfig as Record<string, { target: string; changeOrigin: boolean }>)[
      prefix
    ];
    expect(entry).toBeDefined();
    expect(entry.target).toBe(backend);
    expect(entry.changeOrigin).toBe(true);
  });

  // Gleichheit in beide Richtungen: ein fehlender Prefix bricht die SPA im Dev-Server, ein
  // zusätzlicher ist entweder überflüssig oder ein Hinweis, dass die Liste oben veraltet ist.
  it('deckt sich exakt mit der Prefix-Liste — kein fehlender, kein zusätzlicher Eintrag', () => {
    expect(Object.keys(proxyConfig).sort()).toEqual([...proxiedPrefixes].sort());
  });

  it('verdrahtet proxy.conf.json in der serve-Konfiguration von angular.json', () => {
    const serveOptions = angularJson.projects.budgetbuddy.architect.serve.options;
    expect(serveOptions.proxyConfig).toBe('proxy.conf.json');
  });
});
