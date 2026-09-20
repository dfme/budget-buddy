/**
 * Typ der Benachrichtigung, die BE-REC-01 zu jedem neu erkannten Abo anlegt. Die Glocke führt
 * bei diesem Typ in die Abo-Übersicht (FE-REC-01) — der einzige `type`, den das Frontend
 * bisher interpretiert.
 */
export const RECURRING_EXPENSE_DETECTED = 'RECURRING_EXPENSE_DETECTED';

/**
 * Eine Benachrichtigung — spiegelt das Backend-DTO `NotificationResponse` (BE-NOTIF-01).
 *
 * <p>Bewusst nicht `Notification` genannt: das kollidiert mit der globalen Browser-API
 * `window.Notification`. `referenceId` ist nur durchgereicht — die Abo-Übersicht zeigt die
 * ganze Liste, nicht den einzelnen Eintrag, und braucht die ID deshalb (noch) nicht.
 */
export interface NotificationResponse {
  id: number;
  type: string;
  referenceId: number | null;
  message: string;
  read: boolean;
  createdAt: string;
}
