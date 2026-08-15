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

    const req = httpMock.expectOne('/budget/safe-to-spend');
    expect(req.request.method).toBe('GET');
    req.flush(RESPONSE);

    expect(received).toEqual(RESPONSE);
  });
});
