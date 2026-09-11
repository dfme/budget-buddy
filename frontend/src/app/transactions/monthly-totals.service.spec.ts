import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MonthlyTotals } from './monthly-totals.model';
import { MONTHLY_TOTALS_WINDOW, MonthlyTotalsService } from './monthly-totals.service';

const WINDOW: MonthlyTotals[] = [
  { month: '2026-07', income: 5000, expenses: 100, difference: 4900 },
  { month: '2026-06', income: 800, expenses: 1250.5, difference: -450.5 },
  { month: '2026-05', income: null, expenses: null, difference: null },
];

describe('MonthlyTotalsService', () => {
  let service: MonthlyTotalsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MonthlyTotalsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the window for the given month with the default size', () => {
    let received: MonthlyTotals[] | undefined;
    service.getMonthlyTotals('2026-07').subscribe((response) => (received = response));

    const req = httpMock.expectOne(
      (candidate) => candidate.url === '/api/transactions/monthly-totals',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('month')).toBe('2026-07');
    expect(req.request.params.get('months')).toBe(String(MONTHLY_TOTALS_WINDOW));
    req.flush(WINDOW);

    expect(received).toEqual(WINDOW);
  });

  it('passes a custom window size on', () => {
    service.getMonthlyTotals('2026-07', 6).subscribe();

    const req = httpMock.expectOne(
      (candidate) => candidate.url === '/api/transactions/monthly-totals',
    );
    expect(req.request.params.get('months')).toBe('6');
    req.flush([]);
  });

  it('passes null amounts through untouched', () => {
    // null ist nicht 0 und darf auf dem Weg durch den Service nicht dazu werden — die Übersicht
    // unterscheidet «keine Buchungen» von «Summe null» genau daran.
    let received: MonthlyTotals[] | undefined;
    service.getMonthlyTotals('2026-05').subscribe((response) => (received = response));

    httpMock
      .expectOne((candidate) => candidate.url === '/api/transactions/monthly-totals')
      .flush([{ month: '2026-05', income: null, expenses: null, difference: null }]);

    expect(received?.[0].income).toBeNull();
    expect(received?.[0].expenses).toBeNull();
    expect(received?.[0].difference).toBeNull();
  });
});
