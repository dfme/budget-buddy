import proxyConfig from '../proxy.conf.json';
import angularJson from '../angular.json';

/**
 * INFRA-07/INFRA-17: Verifiziert die Verdrahtung des Angular Dev-Proxy.
 *
 * <p>Kein End-to-End-Proxytest (bräuchte beide laufende Server) — geprüft wird stattdessen,
 * dass die Proxy-Konfiguration den einzigen Backend-Präfix `/api` an `:8080` leitet und dass
 * `ng serve` sie über `angular.json` automatisch lädt.
 *
 * <p>Vor INFRA-17 stand hier eine Liste mit einem Eintrag pro `@RequestMapping`-Wurzelpfad
 * (`/auth`, `/users`, `/import`, `/transactions`, `/fixed-costs`, `/budget`) — jeder neue
 * Backend-Prefix musste hier UND in `proxy.conf.json` nachgetragen werden. Genau das verpasste
 * FE-CAT-02: `/transactions` kam mit BE-CAT-05 dazu, `ng serve` beantwortete den Pfad daraufhin
 * mit dem SPA-Fallback `index.html` statt ihn ans Backend zu reichen. Seit alle Endpoints unter
 * `/api/**` liegen, gibt es nur noch einen Eintrag — ein neuer Controller-Pfad kann diese Liste
 * nicht mehr vergessen, weil es keine Liste mehr gibt.
 */
describe('Dev-Proxy Konfiguration', () => {
  const backend = 'http://localhost:8080';

  it('leitet /api an das Backend auf :8080 weiter', () => {
    const entry = (proxyConfig as Record<string, { target: string; changeOrigin: boolean }>)['/api'];
    expect(entry).toBeDefined();
    expect(entry.target).toBe(backend);
    expect(entry.changeOrigin).toBe(true);
  });

  it('enthält genau einen Eintrag — kein zurückgelassener Alt-Prefix', () => {
    expect(Object.keys(proxyConfig)).toEqual(['/api']);
  });

  it('verdrahtet proxy.conf.json in der serve-Konfiguration von angular.json', () => {
    const serveOptions = angularJson.projects.budgetbuddy.architect.serve.options;
    expect(serveOptions.proxyConfig).toBe('proxy.conf.json');
  });
});
