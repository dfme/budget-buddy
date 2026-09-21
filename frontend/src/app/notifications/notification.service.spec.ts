import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NotificationResponse } from './notification.model';
import { NotificationService } from './notification.service';

const UNREAD: NotificationResponse = {
  id: 1,
  type: 'RECURRING_EXPENSE_DETECTED',
  referenceId: 42,
  message: 'Netflix wurde als Abo erkannt',
  read: false,
  createdAt: '2026-09-08T10:15:00Z',
};

const READ: NotificationResponse = {
  id: 2,
  type: 'RECURRING_EXPENSE_DETECTED',
  referenceId: null,
  message: 'Spotify wurde als Abo erkannt',
  read: true,
  createdAt: '2026-09-01T08:00:00Z',
};

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lädt die Benachrichtigungen und schreibt sie in den State', () => {
    let received: NotificationResponse[] | undefined;
    service.load().subscribe((response) => (received = response));

    const req = httpMock.expectOne('/api/notifications');
    expect(req.request.method).toBe('GET');
    req.flush([UNREAD, READ]);

    expect(received).toEqual([UNREAD, READ]);
    expect(service.notifications()).toEqual([UNREAD, READ]);
  });

  it('leitet unreadCount aus den ungelesenen Einträgen ab', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications').flush([UNREAD, READ]);

    expect(service.unreadCount()).toBe(1);
  });

  it('bündelt gleichzeitige load()-Aufrufe auf einen Request', () => {
    service.load().subscribe();
    service.load().subscribe();

    httpMock.expectOne('/api/notifications').flush([UNREAD]);

    expect(service.notifications()).toEqual([UNREAD]);
  });

  it('lädt bei einem erneuten Aufruf nach Abschluss wieder frisch', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications').flush([UNREAD]);

    service.load().subscribe();
    const secondReq = httpMock.expectOne('/api/notifications');
    secondReq.flush([READ]);

    expect(service.notifications()).toEqual([READ]);
  });

  // FE-NOTIF-04: der Reload nach einem Import darf sich nicht an ein GET hängen, das vor dem
  // Abschluss losging — sonst kommt der Stand davor zurück, und zwar ohne zweiten Versuch.
  it('reload() startet einen eigenen Request, auch wenn noch einer läuft', () => {
    service.load().subscribe();
    const stale = httpMock.expectOne('/api/notifications');

    service.reload().subscribe();
    const fresh = httpMock.expectOne('/api/notifications');

    stale.flush([]);
    fresh.flush([UNREAD]);

    expect(service.notifications()).toEqual([UNREAD]);
  });

  it('der Abschluss des alten Requests verwirft den jüngeren nicht', () => {
    service.load().subscribe();
    const stale = httpMock.expectOne('/api/notifications');
    service.reload().subscribe();
    const fresh = httpMock.expectOne('/api/notifications');
    stale.flush([]);

    // Solange der jüngere läuft, hängt sich ein weiterer load() an ihn — kein dritter Request.
    service.load().subscribe();
    httpMock.expectNone('/api/notifications');
    fresh.flush([UNREAD]);

    expect(service.notifications()).toEqual([UNREAD]);
  });

  it('markiert eine Benachrichtigung als gelesen und ersetzt sie im State', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications').flush([UNREAD, READ]);

    const updated: NotificationResponse = { ...UNREAD, read: true };
    let response: NotificationResponse | undefined;
    service.markAsRead(1).subscribe((r) => (response = r));

    const req = httpMock.expectOne('/api/notifications/1/read');
    expect(req.request.method).toBe('POST');
    req.flush(updated);

    expect(response).toEqual(updated);
    expect(service.notifications()).toEqual([updated, READ]);
    expect(service.unreadCount()).toBe(0);
  });

  // FE-NOTIF-04: eine Aktion für alle ungelesenen.
  it('markiert alle als gelesen und übernimmt die Antwort in der bestehenden Reihenfolge', () => {
    // Wie GET sie liefert: die ältere ungelesene (20.8.) vor der neueren gelesenen (1.9.).
    const olderUnread: NotificationResponse = {
      ...UNREAD,
      id: 3,
      createdAt: '2026-08-20T08:00:00Z',
    };
    service.load().subscribe();
    httpMock.expectOne('/api/notifications').flush([olderUnread, READ]);

    // Das Backend sortiert nach dem Markieren nur noch nach createdAt desc — READ käme zuerst.
    const fromServer: NotificationResponse[] = [READ, { ...olderUnread, read: true }];
    let response: NotificationResponse[] | undefined;
    service.markAllAsRead().subscribe((r) => (response = r));

    const req = httpMock.expectOne('/api/notifications/read-all');
    expect(req.request.method).toBe('POST');
    req.flush(fromServer);

    expect(response).toEqual(fromServer);
    // Im offenen Dropdown springt nichts: lokale Reihenfolge, Werte vom Server.
    expect(service.notifications()).toEqual([{ ...olderUnread, read: true }, READ]);
    expect(service.unreadCount()).toBe(0);
  });

  it('markAllAsRead hängt lokal unbekannte Einträge der Antwort hinten an', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications').flush([UNREAD]);

    service.markAllAsRead().subscribe();
    httpMock.expectOne('/api/notifications/read-all').flush([READ, { ...UNREAD, read: true }]);

    expect(service.notifications()).toEqual([{ ...UNREAD, read: true }, READ]);
  });

  it('clear() leert den State ohne Backend-Call', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications').flush([UNREAD]);
    expect(service.notifications()).toEqual([UNREAD]);

    service.clear();

    expect(service.notifications()).toEqual([]);
    expect(service.unreadCount()).toBe(0);
  });
});
