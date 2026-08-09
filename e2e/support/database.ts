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
 * Tabellen mit Nutzerdaten, in Abhängigkeitsreihenfolge.
 *
 * `category_lookup` steht bewusst NICHT hier: ihr Inhalt kommt aus der Migration V04 (Seed-Daten
 * bekannter Händler) und ist Teil des Schemas, nicht Zustand eines Testlaufs. Ein TRUNCATE würde
 * die Seeds löschen, die Flyway danach nicht neu einspielt — die nächste Migrationsprüfung liefe
 * gegen eine leere Lookup-Tabelle.
 */
const USER_DATA_TABLES = ['transactions', 'fixed_costs', 'users'];

/**
 * Leert die Nutzertabellen der E2E-Datenbank.
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
    await client.query(
      `TRUNCATE TABLE ${USER_DATA_TABLES.join(', ')} RESTART IDENTITY CASCADE`,
    );
  } finally {
    await client.end();
  }
}
