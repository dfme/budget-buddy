/**
 * Typ der Benachrichtigung, die BE-REC-01 pro Erkennungslauf anlegt — seit FE-NOTIF-04 eine
 * pro Import, die alle neu erkannten Abos bündelt («3 neue Abos erkannt: …»). Die Glocke führt
 * bei diesem Typ in die Abo-Übersicht (FE-REC-01).
 */
export const RECURRING_EXPENSE_DETECTED = 'RECURRING_EXPENSE_DETECTED';

/**
 * Abschluss eines Import-Jobs (BE-PDF-15, `ImportJobRunner.NOTIFICATION_TYPE_COMPLETED`).
 * `referenceId` ist die Job-ID; die Glocke führt bei diesem Typ nach `/import?job=<id>`
 * (FE-NOTIF-05), wo die Import-Seite die Übersicht des Imports über den bestehenden
 * Status-Endpoint nachlädt.
 */
export const IMPORT_COMPLETED = 'IMPORT_COMPLETED';

/** Wie {@link IMPORT_COMPLETED}, aber der Watchdog hat einen Teil auf «Sonstiges» gesetzt. */
export const IMPORT_DEGRADED = 'IMPORT_DEGRADED';

/** Fehlgeschlagener Import-Job (BE-PDF-15) — dasselbe Sprungziel, die Seite zeigt den Fehler. */
export const IMPORT_FAILED = 'IMPORT_FAILED';

/**
 * Die drei Import-Typen, die die Glocke auf die Import-Seite abbildet. Als Set, weil `select`
 * pro Klick nur eine Mitgliedschaftsfrage stellt und die Liste an genau einer Stelle stehen soll.
 */
export const IMPORT_NOTIFICATION_TYPES: ReadonlySet<string> = new Set([
  IMPORT_COMPLETED,
  IMPORT_DEGRADED,
  IMPORT_FAILED,
]);

/**
 * Eine Benachrichtigung — spiegelt das Backend-DTO `NotificationResponse` (BE-NOTIF-01).
 *
 * <p>Bewusst nicht `Notification` genannt: das kollidiert mit der globalen Browser-API
 * `window.Notification`. `referenceId` ist nur durchgereicht: bei Abo-Benachrichtigungen seit
 * FE-NOTIF-04 `null` (eine Benachrichtigung meldet mehrere Einträge, der Verweis liegt deshalb
 * auf deren Seite — `recurring_expenses.notification_id`, V14), bei den Import-Typen die Job-ID
 * (BE-PDF-15).
 */
export interface NotificationResponse {
  id: number;
  type: string;
  referenceId: number | null;
  message: string;
  read: boolean;
  createdAt: string;
}
