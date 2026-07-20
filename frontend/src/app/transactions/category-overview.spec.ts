import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CategoryOverview } from './category-overview';
import { CategorySummary } from './category-summary.model';

// Der CurrencyPipe nutzt den app-weiten LOCALE_ID (de-CH); die Locale-Daten müssen
// dafür registriert sein — im echten App-Bootstrap erledigt das app.config.ts.
registerLocaleData(localeDeCh);

const SUMMARY: CategorySummary = {
  month: '2026-07',
  totalAmount: 1350.5,
  totalCount: 7,
  categories: [
    { category: 'Wohnen', amount: 1000, count: 1, percentage: 74.05 },
    { category: 'Lebensmittel', amount: 350.5, count: 6, percentage: 25.95 },
  ],
};

const EMPTY_SUMMARY: CategorySummary = {
  month: '2026-07',
  totalAmount: 0,
  totalCount: 0,
  categories: [],
};

const OLDER_MONTH_SUMMARY: CategorySummary = {
  month: '2026-05',
  totalAmount: 42,
  totalCount: 3,
  categories: [],
};

/** URL-Matcher unabhängig vom (vom aktuellen Datum abhängigen) Monat. */
function expectSummaryRequest(httpMock: HttpTestingController) {
  return httpMock.expectOne((req) => req.url === '/transactions/summary');
}

describe('CategoryOverview', () => {
  let fixture: ComponentFixture<CategoryOverview>;
  let component: CategoryOverview;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CategoryOverview],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'de-CH' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CategoryOverview);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads and renders every category with amount, count and percentage', () => {
    const req = expectSummaryRequest(httpMock);
    expect(req.request.params.get('month')).toBe(component.month());
    req.flush(SUMMARY);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);

    const cells = rows[0].querySelectorAll('td');
    expect(cells[0].textContent?.trim()).toBe('Wohnen');
    expect(cells[1].textContent).toContain('1’000.00');
    expect(cells[2].textContent?.trim()).toBe('1');
    expect(cells[3].textContent?.trim()).toBe('74.05%');

    expect((fixture.nativeElement.textContent as string)).toContain('Lebensmittel');
    expect(component.isEmpty()).toBe(false);
  });

  it('communicates the empty state when the month has no expenses', () => {
    expectSummaryRequest(httpMock).flush(EMPTY_SUMMARY);
    fixture.detectChanges();

    expect(component.isEmpty()).toBe(true);
    expect(fixture.nativeElement.querySelector('.status.empty')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('navigates to the previous month and reloads', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);
    const initialMonth = component.month();

    component.previousMonth();

    const req = expectSummaryRequest(httpMock);
    expect(req.request.params.get('month')).toBe(component.month());
    expect(component.month()).not.toBe(initialMonth);
    req.flush(EMPTY_SUMMARY);
  });

  it('navigates to the next month and reloads', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);
    const initialMonth = component.month();

    component.nextMonth();

    const req = expectSummaryRequest(httpMock);
    expect(req.request.params.get('month')).toBe(component.month());
    expect(component.month()).not.toBe(initialMonth);
    req.flush(SUMMARY);
  });

  it('shows an error message when the request fails', () => {
    expectSummaryRequest(httpMock).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(component.errorMessage()).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.status.error')).not.toBeNull();
    expect(component.summary()).toBeNull();
  });

  it('disables the next-month button on the current month and enables it after navigating back', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);
    fixture.detectChanges();

    const nextButton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'button[aria-label="Nächster Monat"]',
    )!;
    expect(nextButton.disabled).toBe(true);

    component.previousMonth();
    expectSummaryRequest(httpMock).flush(SUMMARY);
    fixture.detectChanges();

    expect(nextButton.disabled).toBe(false);
  });

  it('discards a stale response when the month changes again before it arrives', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);

    component.previousMonth();
    const staleRequest = expectSummaryRequest(httpMock);

    component.previousMonth();
    const latestRequest = expectSummaryRequest(httpMock);

    expect(staleRequest.cancelled).toBe(true);

    latestRequest.flush(OLDER_MONTH_SUMMARY);
    fixture.detectChanges();

    expect(component.summary()?.totalCount).toBe(3);
  });
});
