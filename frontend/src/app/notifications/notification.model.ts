/**
 * Eine Benachrichtigung — spiegelt das Backend-DTO `NotificationResponse` (BE-NOTIF-01).
 *
 * <p>Bewusst nicht `Notification` genannt: das kollidiert mit der globalen Browser-API
 * `window.Notification`. `type` und `referenceId` sind für dieses Issue nur durchgereicht — kein
 * Consumer interpretiert sie im Frontend (die Abo-Erkennung aus US-08 ist ein eigenes,
 * nicht eingeplantes Folge-Issue).
 */
export interface NotificationResponse {
  id: number;
  type: string;
  referenceId: number | null;
  message: string;
  read: boolean;
  createdAt: string;
}
