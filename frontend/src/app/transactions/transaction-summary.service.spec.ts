import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CategorySummary } from './category-summary.model';
import { TransactionSummaryService } from './transaction-summary.service';

const SUMMARY: CategorySummary = {
  month: '2026-07',
  totalAmount: 1200,
  totalCount: 5,
  categories: [{ category: 'Lebensmittel', amount: 1200, count: 5, percentage: 100 }],
};

describe('TransactionSummaryService', () => {
  let service: TransactionSummaryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TransactionSummaryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the summary for the given month', () => {
    let received: CategorySummary | undefined;
    service.getSummary('2026-07').subscribe((summary) => (received = summary));

    const req = httpMock.expectOne('/transactions/summary?month=2026-07');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('month')).toBe('2026-07');
    req.flush(SUMMARY);

    expect(received).toEqual(SUMMARY);
  });
});
