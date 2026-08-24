import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Transaction, TransactionPage } from './transaction.model';
import { TransactionService } from './transaction.service';

const TRANSACTION: Transaction = {
  id: 7,
  buchungsdatum: '2026-07-20',
  buchungstext: 'COOP PRONTO BERN',
  buchungsdetails: null,
  betrag: 34.2,
  income: false,
  category: 'Lebensmittel',
};

/** Leere letzte Seite — die Antwort, wenn nur die Request-Parameter geprüft werden. */
const EMPTY_PAGE: TransactionPage = { transactions: [], hasMore: false };

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

  it('requests the first page of a month and passes the answer through', () => {
    const page: TransactionPage = { transactions: [TRANSACTION], hasMore: true };
    let received: TransactionPage | undefined;
    service.list('2026-07').subscribe((result) => (received = result));

    const req = httpMock.expectOne((r) => r.url === '/api/transactions');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('month')).toBe('2026-07');
    req.flush(page);

    expect(received).toEqual(page);
  });

  // AC 4: die Liste zeigt initial 20 Buchungen — die Zahl steht im Service, nicht im Aufrufer.
  it('asks for the first 20 entries when no window is given', () => {
    service.list('2026-07').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/transactions');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(EMPTY_PAGE);
  });

  it('requests the given window', () => {
    service.list('2026-07', 'Lebensmittel', 2, 40).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/transactions');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('40');
    req.flush(EMPTY_PAGE);
  });

  it('adds the category filter when given', () => {
    service.list('2026-07', 'Lebensmittel').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/transactions');
    expect(req.request.params.get('month')).toBe('2026-07');
    expect(req.request.params.get('category')).toBe('Lebensmittel');
    req.flush(EMPTY_PAGE);
  });

  it('omits the category parameter when no filter is given', () => {
    service.list('2026-07').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/transactions');
    expect(req.request.params.has('category')).toBe(false);
    req.flush(EMPTY_PAGE);
  });

  it('puts the new category for a transaction', () => {
    let received: Transaction | undefined;
    service.updateCategory(7, 'Restaurant').subscribe((tx) => (received = tx));

    const req = httpMock.expectOne('/api/transactions/7/category');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ category: 'Restaurant' });
    req.flush({ ...TRANSACTION, category: 'Restaurant' });

    expect(received?.category).toBe('Restaurant');
  });
});
