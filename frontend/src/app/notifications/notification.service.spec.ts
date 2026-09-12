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

  it('clear() leert den State ohne Backend-Call', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications').flush([UNREAD]);
    expect(service.notifications()).toEqual([UNREAD]);

    service.clear();

    expect(service.notifications()).toEqual([]);
    expect(service.unreadCount()).toBe(0);
  });
});
