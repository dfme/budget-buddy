import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SafeToSpendResponse } from './safe-to-spend.model';
import { SafeToSpendService } from './safe-to-spend.service';

const RESPONSE: SafeToSpendResponse = {
  amount: 500,
  weeksLeft: 3,
  negative: false,
  noIncome: false,
  incomeSuggestion: null,
  status: 'OPEN',
};

describe('SafeToSpendService', () => {
  let service: SafeToSpendService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SafeToSpendService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the safe-to-spend amount without parameters', () => {
    let received: SafeToSpendResponse | undefined;
    service.getSafeToSpend().subscribe((response) => (received = response));

    const req = httpMock.expectOne('/api/budget/safe-to-spend');
    expect(req.request.method).toBe('GET');
    // Ohne Argument bleibt der Request zeichengleich wie vor FE-STS-04 — ein leerer
    // `month`-Parameter beantwortete das Backend mit 400 statt mit dem laufenden Monat.
    expect(req.request.params.keys()).toEqual([]);
    req.flush(RESPONSE);

    expect(received).toEqual(RESPONSE);
  });

  it('passes the requested month on as a query parameter (BE-STS-06)', () => {
    let received: SafeToSpendResponse | undefined;
    service.getSafeToSpend('2026-07').subscribe((response) => (received = response));

    const req = httpMock.expectOne((candidate) => candidate.url === '/api/budget/safe-to-spend');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('month')).toBe('2026-07');
    req.flush({ ...RESPONSE, amount: null, weeksLeft: 0, status: 'CLOSED' });

    expect(received?.status).toBe('CLOSED');
  });
});
