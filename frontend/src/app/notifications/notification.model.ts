/**
 * Typ der Benachrichtigung, die BE-REC-01 pro Erkennungslauf anlegt — seit FE-NOTIF-04 eine
 * pro Import, die alle neu erkannten Abos bündelt («3 neue Abos erkannt: …»). Die Glocke führt
 * bei diesem Typ in die Abo-Übersicht (FE-REC-01) — der einzige `type`, den das Frontend
 * bisher interpretiert.
 */
export const RECURRING_EXPENSE_DETECTED = 'RECURRING_EXPENSE_DETECTED';

/**
 * Eine Benachrichtigung — spiegelt das Backend-DTO `NotificationResponse` (BE-NOTIF-01).
 *
 * <p>Bewusst nicht `Notification` genannt: das kollidiert mit der globalen Browser-API
 * `window.Notification`. `referenceId` ist nur durchgereicht und bei Abo-Benachrichtigungen
 * seit FE-NOTIF-04 `null`: eine Benachrichtigung meldet mehrere Einträge, der Verweis liegt
 * deshalb auf deren Seite (`recurring_expenses.notification_id`, V14).
 */
export interface NotificationResponse {
  id: number;
  type: string;
  referenceId: number | null;
  message: string;
  read: boolean;
  createdAt: string;
}
