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

  it.each(['/auth', '/users'])('leitet %s an das Backend auf :8080 weiter', (prefix) => {
    const entry = (proxyConfig as Record<string, { target: string; changeOrigin: boolean }>)[
      prefix
    ];
    expect(entry).toBeDefined();
    expect(entry.target).toBe(backend);
    expect(entry.changeOrigin).toBe(true);
  });

  it('verdrahtet proxy.conf.json in der serve-Konfiguration von angular.json', () => {
    const serveOptions =
      angularJson.projects.budgetbuddy.architect.serve.options;
    expect(serveOptions.proxyConfig).toBe('proxy.conf.json');
  });
});
