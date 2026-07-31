import { existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

/**
 * Gemeinsame Angaben zur Backend-Instanz, gegen die die E2E-Tests laufen (INFRA-14).
 *
 * Getestet wird das JAR aus dem `prod`-Maven-Profil: es liefert die Angular-SPA aus
 * `BOOT-INF/static/` UND die REST-API vom selben Origin — genau das Artefakt, das auf Render
 * deployt wird (ADR-10, Single-Artifact). Same-Origin ist hier nicht bloss bequem, sondern
 * Voraussetzung dafür, dass sich das `SameSite=Strict`-JWT-Cookie im Test so verhält wie in
 * Produktion (ADR-7).
 */

/**
 * Port der Testinstanz — bewusst NICHT der Dev-Port 8080.
 *
 * Auf 8080 läuft im Alltag das Dev-Backend. Liefen die Tests dort, würden sie entweder mit ihm
 * kollidieren oder ihn adoptieren (`reuseExistingServer`) — und dann gegen die Dev-Datenbank
 * testen, während {@link resetDatabase} eine andere Datei löscht. Genau das ist beim Aufsetzen
 * einmal passiert: die Suite hing an einer fremden Instanz, deren DB-Datei unter ihr weg war,
 * und produzierte fünf irreführende Fehlschläge.
 *
 * Mit eigenem Port ist ein besetzter Port ein lauter Startfehler statt stiller Kontamination.
 */
export const BACKEND_PORT = 8081;

/** Origin für `baseURL` — SPA und API liegen dahinter. */
export const BASE_URL = `http://localhost:${BACKEND_PORT}`;

/**
 * SQLite-Datei der Testinstanz, via `SQLITE_DB_PATH` gesetzt. Liegt unter `.tmp/` (gitignored)
 * statt im Repo-Root, damit sie nicht mit der Dev-Datenbank kollidiert; {@link resetDatabase}
 * löscht sie vor jedem Lauf.
 */
export const DB_PATH = resolve(__dirname, '..', '.tmp', 'e2e.db');

/**
 * Signier-Secret der Testinstanz. In CI wird `JWT_SECRET` zufällig erzeugt (siehe
 * `.github/workflows/build.yml`); dieser Fallback existiert nur, damit ein lokaler Lauf ohne
 * Env-Setup funktioniert.
 *
 * Das ist kein Credential im Sinne von CLAUDE.md ("Keine Secrets im Git"): es signiert
 * ausschliesslich Tokens gegen die Wegwerf-SQLite-Datei oben und ist in keiner Umgebung
 * gültig, die echte Daten hält. `JwtProperties` verlangt mindestens 32 Zeichen.
 */
export const LOCAL_TEST_JWT_SECRET = 'e2e-local-test-secret-not-a-production-credential';

/**
 * Findet das gebaute Backend-JAR. Wirft mit Bau-Anweisung, wenn keines existiert — ohne diese
 * Meldung würde Playwright nur einen unverständlichen webServer-Timeout zeigen.
 *
 * Bei mehreren Treffern wird ebenfalls geworfen, statt eines zu wählen: `readdirSync` liefert
 * keine definierte Reihenfolge, ein stiller Griff ins Verzeichnis könnte also nach einem
 * Versionswechsel ohne `mvn clean` das alte Artefakt testen. Ein grüner Lauf gegen ein veraltetes
 * JAR ist schlimmer als ein Abbruch — man sucht den Fehler garantiert an der falschen Stelle.
 */
export function resolveBackendJar(): string {
  const targetDir = resolve(__dirname, '..', '..', 'backend', 'target');
  const jars = existsSync(targetDir)
    ? readdirSync(targetDir).filter((file) => /^budgetbuddy-.*\.jar$/.test(file))
    : [];

  if (jars.length === 0) {
    throw new Error(
      `Kein Backend-JAR in ${targetDir} gefunden.\n` +
        'Vorher bauen (das prod-Profil bündelt die Angular-SPA ins JAR):\n' +
        '  cd backend && ./mvnw -Pprod -DskipTests package',
    );
  }

  if (jars.length > 1) {
    throw new Error(
      `Mehrere Backend-JARs in ${targetDir} gefunden:\n` +
        jars.map((jar) => `  - ${jar}`).join('\n') +
        '\nWelches gemeint ist, lässt sich nicht zuverlässig bestimmen. Aufräumen und neu bauen:\n' +
        '  cd backend && ./mvnw -Pprod -DskipTests clean package',
    );
  }

  return join(targetDir, jars[0]);
}

/**
 * Startet den Lauf mit einer leeren SQLite-Datei.
 *
 * Die Tests sind nicht auf einen leeren Zustand angewiesen — jeder erzeugt seinen User mit einer
 * eindeutigen E-Mail. Eine mitgeschleppte Datei liesse Läufe aber voneinander abhängen und würde
 * lokal eine andere Ausgangslage erzeugen als in CI (dort ist sie immer frisch).
 *
 * Muss aus dem Playwright-Hauptprozess aufgerufen werden, BEVOR die Instanz startet — deshalb
 * nicht als `globalSetup`: der `webServer` ist als Plugin implementiert und wird vor `globalSetup`
 * gestartet. Die Datei dann zu löschen würde bloss den Inode unter dem laufenden Backend
 * weg-unlinken statt den Zustand zurückzusetzen.
 *
 * `-journal`/`-wal`/`-shm` mit löschen: eine zurückgebliebene Journal-Datei ohne die zugehörige
 * DB bringt SQLite beim Öffnen aus dem Tritt.
 */
export function resetDatabase(): void {
  mkdirSync(dirname(DB_PATH), { recursive: true });

  for (const suffix of ['', '-journal', '-wal', '-shm']) {
    rmSync(`${DB_PATH}${suffix}`, { force: true });
  }
}

/**
 * `true` im Playwright-Hauptprozess, `false` in den Test-Workern. Playwright lädt die Config in
 * jedem Worker erneut; Nebeneffekte wie {@link resetDatabase} dürfen dort nicht laufen, sonst
 * würde ein startender Worker die DB des bereits laufenden Backends unter ihm wegziehen.
 */
export const isPlaywrightMainProcess = process.env.TEST_WORKER_INDEX === undefined;
