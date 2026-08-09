import { Client } from 'pg';

/**
 * Datenbank der E2E-Testinstanz (DB-05, ADR-12).
 *
 * Seit der Umstellung auf PostgreSQL ist die Datenbank kein Wegwerf-File mehr, das sich vor dem
 * Lauf einfach löschen lässt. Sie ist ein Server, den sich die Suite mit der Dev-Umgebung teilt —
 * deshalb eine **eigene Datenbank** (`budgetbuddy_e2e`, angelegt von
 * `docker/postgres-init/01-create-e2e-database.sql`) und nicht die Dev-Datenbank.
 *
 * Alle Werte sind überschreibbar, damit CI denselben Code gegen den Service-Container fahren kann.
 */
export const DB_HOST = process.env.E2E_DB_HOST ?? 'localhost';
export const DB_PORT = Number(process.env.E2E_DB_PORT ?? 5432);
export const DB_NAME = process.env.E2E_DB_NAME ?? 'budgetbuddy_e2e';
export const DB_USER = process.env.E2E_DB_USER ?? 'budgetbuddy';
export const DB_PASSWORD = process.env.E2E_DB_PASSWORD ?? 'budgetbuddy';

/** JDBC-URL für die Testinstanz — Spring versteht nur die `jdbc:`-Form. */
export const JDBC_URL = `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`;

/**
 * Tabellen, die vom Zurücksetzen ausgenommen bleiben.
 *
 * Bewusst als **Ausnahmeliste** statt als Liste der zu leerenden Tabellen: Der frühere Reset
 * (SQLite-Datei löschen) war bauartbedingt vollständig — eine neue Tabelle war automatisch
 * miterfasst. Eine Aufzählung der Nutzertabellen hätte diese Eigenschaft aufgegeben: `savings_goals`
 * (US-07) und `import_jobs` fielen später still durch, und der Fehler zeigte sich als flackernder
 * E2E-Lauf statt als Fehlermeldung. Mit der Ausnahmeliste bleibt die sichere Variante die
 * Voreinstellung, und jede Ausnahme muss begründet hier eingetragen werden.
 *
 * - `flyway_schema_history`: gehört Flyway. Geleert, hielte Flyway das Schema für unmigriert.
 * - `category_lookup`: Inhalt stammt aus Migration V04 (Seed-Daten bekannter Händler) und ist Teil
 *   des Schemas, nicht Zustand eines Laufs. Flyway spielt ihn nicht erneut ein.
 */
const PRESERVED_TABLES = ['flyway_schema_history', 'category_lookup'];

/**
 * Leert alle Tabellen der E2E-Datenbank ausser {@link PRESERVED_TABLES}.
 *
 * Die Tests sind nicht auf einen leeren Zustand angewiesen — jeder erzeugt seinen User mit einer
 * eindeutigen E-Mail. Ohne Zurücksetzen würde die Datenbank aber über Läufe hinweg wachsen und
 * lokal eine andere Ausgangslage erzeugen als in CI, wo sie immer frisch ist.
 *
 * Aufgerufen aus `global-setup.ts`, also **nachdem** Playwright die Testinstanz gestartet hat.
 * Das ist hier zulässig und war es unter SQLite nicht: dort hätte ein Löschen zu diesem Zeitpunkt
 * die Datei unter dem laufenden Backend weg-unlinked. `TRUNCATE` wirkt dagegen in derselben
 * Datenbank, an der das Backend hängt, und lässt Schema und Flyway-Historie unangetastet.
 *
 * `RESTART IDENTITY`: ohne das liefen die IDs über Läufe hinweg weiter und ein Test, der sich auf
 * eine frische ID verlässt, verhielte sich beim zweiten Lauf anders als beim ersten.
 */
export async function resetDatabase(): Promise<void> {
  const client = new Client({
    host: DB_HOST,
    port: DB_PORT,
    database: DB_NAME,
    user: DB_USER,
    password: DB_PASSWORD,
  });

  await client.connect();
  try {
    const { rows } = await client.query<{ table_name: string }>(
      `SELECT table_name
       FROM information_schema.tables
       WHERE table_schema = current_schema()
         AND table_type = 'BASE TABLE'
         AND table_name <> ALL ($1)`,
      [PRESERVED_TABLES],
    );

    if (rows.length === 0) {
      // Kein stiller No-op: Eine leere Trefferliste heisst, dass Flyway nicht gelaufen ist oder
      // die Verbindung auf der falschen Datenbank liegt. Ohne diesen Abbruch liefe die Suite
      // gegen ein leeres Schema und meldete Folgefehler, die nichts über die App aussagen.
      throw new Error(
        `Keine Tabellen in Datenbank "${DB_NAME}" gefunden. Läuft das Backend gegen dieselbe ` +
          'Datenbank, und ist Flyway durchgelaufen?',
      );
    }

    // Ein einzelnes TRUNCATE über alle Tabellen: CASCADE löst die Fremdschlüssel auf, sodass
    // die Reihenfolge keine Rolle spielt. RESTART IDENTITY setzt die Sequenzen zurück — sonst
    // liefen die IDs über Läufe hinweg weiter und ein Test verhielte sich beim zweiten Lauf
    // anders als beim ersten.
    const tables = rows.map((row) => `"${row.table_name}"`).join(', ');
    await client.query(`TRUNCATE TABLE ${tables} RESTART IDENTITY CASCADE`);
  } finally {
    await client.end();
  }
}
