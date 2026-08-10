import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Transaction } from './transaction.model';
import { TransactionService } from './transaction.service';

const TRANSACTION: Transaction = {
  id: 7,
  buchungsdatum: '2026-07-20',
  buchungstext: 'COOP PRONTO BERN',
  betrag: 34.2,
  income: false,
  category: 'Lebensmittel',
};

describe('TransactionService', () => {
  let service: TransactionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TransactionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the transactions of a month', () => {
    let received: Transaction[] | undefined;
    service.list('2026-07').subscribe((transactions) => (received = transactions));

    const req = httpMock.expectOne('/transactions?month=2026-07');
    expect(req.request.method).toBe('GET');
    req.flush([TRANSACTION]);

    expect(received).toEqual([TRANSACTION]);
  });

  it('adds the category filter when given', () => {
    service.list('2026-07', 'Lebensmittel').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/transactions');
    expect(req.request.params.get('month')).toBe('2026-07');
    expect(req.request.params.get('category')).toBe('Lebensmittel');
    req.flush([]);
  });

  it('omits the category parameter when no filter is given', () => {
    service.list('2026-07').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/transactions');
    expect(req.request.params.has('category')).toBe(false);
    req.flush([]);
  });

  it('puts the new category for a transaction', () => {
    let received: Transaction | undefined;
    service.updateCategory(7, 'Restaurant').subscribe((tx) => (received = tx));

    const req = httpMock.expectOne('/transactions/7/category');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ category: 'Restaurant' });
    req.flush({ ...TRANSACTION, category: 'Restaurant' });

    expect(received?.category).toBe('Restaurant');
  });
});
