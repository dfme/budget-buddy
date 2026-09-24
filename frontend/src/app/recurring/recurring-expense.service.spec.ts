import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RecurringExpenseResponse } from './recurring-expense.model';
import { RecurringExpenseService } from './recurring-expense.service';

const NETFLIX: RecurringExpenseResponse = {
  id: 1,
  payeeKey: 'NETFLIX',
  amount: 17.9,
  status: 'DETECTED',
  firstDetectedMonth: '2026-07',
  createdAt: '2026-09-08T10:15:00Z',
  isNew: true,
};

const SPOTIFY: RecurringExpenseResponse = {
  id: 2,
  payeeKey: 'SPOTIFY',
  amount: 12.95,
  status: 'DETECTED',
  firstDetectedMonth: '2026-06',
  createdAt: '2026-09-01T08:00:00Z',
  isNew: false,
};

describe('RecurringExpenseService', () => {
  let service: RecurringExpenseService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RecurringExpenseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lädt die Abo-Übersicht und schreibt sie in den State', () => {
    let received: RecurringExpenseResponse[] | undefined;
    service.load().subscribe((response) => (received = response));

    const req = httpMock.expectOne('/api/recurring-expenses');
    expect(req.request.method).toBe('GET');
    req.flush([NETFLIX, SPOTIFY]);

    expect(received).toEqual([NETFLIX, SPOTIFY]);
    expect(service.expenses()).toEqual([NETFLIX, SPOTIFY]);
    expect(service.detected().length).toBe(2);
  });

  // FE-NOTIF-03: `GET` liefert beide Status. Der Service trennt sie; das Dashboard-Total summiert
  // nur Abos — ein verneinter Eintrag ist keines.
  it('trennt die Liste nach Status und zählt nur erkannte Abos', () => {
    service.load().subscribe();
    const dismissed: RecurringExpenseResponse = { ...SPOTIFY, status: 'DISMISSED', isNew: false };
    httpMock.expectOne('/api/recurring-expenses').flush([NETFLIX, dismissed]);

    expect(service.detected()).toEqual([NETFLIX]);
    expect(service.dismissed()).toEqual([dismissed]);
    expect(service.detected().length).toBe(1);
  });

  it('markiert einen Eintrag als Kein Abo und ersetzt ihn im State durch die Antwort', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/recurring-expenses').flush([NETFLIX, SPOTIFY]);

    let received: RecurringExpenseResponse | undefined;
    service.dismiss(1).subscribe((response) => (received = response));

    const req = httpMock.expectOne('/api/recurring-expenses/1/dismiss');
    expect(req.request.method).toBe('POST');
    const dismissed: RecurringExpenseResponse = { ...NETFLIX, status: 'DISMISSED', isNew: false };
    req.flush(dismissed);

    expect(received).toEqual(dismissed);
    // Position bleibt: der Eintrag wechselt den Abschnitt, nicht die Reihenfolge.
    expect(service.expenses()).toEqual([dismissed, SPOTIFY]);
    expect(service.detected()).toEqual([SPOTIFY]);
    expect(service.dismissed()).toEqual([dismissed]);
    expect(service.detected().length).toBe(1);
  });

  it('lässt den State bei einem fehlschlagenden dismiss unverändert', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/recurring-expenses').flush([NETFLIX]);

    service.dismiss(1).subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/recurring-expenses/1/dismiss')
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(service.expenses()).toEqual([NETFLIX]);
  });

  it('leert den State ohne Backend-Call', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/recurring-expenses').flush([NETFLIX]);

    service.clear();

    expect(service.expenses()).toEqual([]);
    expect(service.detected().length).toBe(0);
  });
});
