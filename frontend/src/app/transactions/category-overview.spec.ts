import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { BaseChartDirective } from 'ng2-charts';

import { installCanvasStub, restoreCanvasStub } from '../../testing/canvas';
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

/** Anderer Monat mit anderen Kategorien — Gegenprobe für die Chart-Aktualisierung. */
const PREVIOUS_MONTH_SUMMARY: CategorySummary = {
  month: '2026-06',
  totalAmount: 200,
  totalCount: 2,
  categories: [{ category: 'Transport', amount: 200, count: 2, percentage: 100 }],
};

/** Label, das die Frontend-Kategorienliste nicht kennt (z. B. neue Backend-Kategorie). */
const UNKNOWN_CATEGORY_SUMMARY: CategorySummary = {
  month: '2026-07',
  totalAmount: 60,
  totalCount: 1,
  categories: [{ category: 'Kryptowährung', amount: 60, count: 1, percentage: 100 }],
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
    // Der Donut (FE-CAT-02) ist ein echtes Chart.js-Canvas — ohne 2D-Kontext und
    // ResizeObserver käme es in jsdom nicht hoch.
    installCanvasStub();
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

  // `finally`, damit ein fehlgeschlagenes verify() den Canvas-Stub trotzdem zurücknimmt —
  // sonst bliebe HTMLCanvasElement.prototype.getContext für die Folgetests überschrieben.
  afterEach(() => {
    try {
      httpMock.verify();
    } finally {
      fixture.destroy();
      restoreCanvasStub();
    }
  });

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

    expect(fixture.nativeElement.textContent as string).toContain('Lebensmittel');
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

  it('renders the donut chart with the loaded data', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);
    fixture.detectChanges();

    const canvas = (fixture.nativeElement as HTMLElement).querySelector(
      'app-donut-chart canvas',
    ) as HTMLCanvasElement;
    expect(canvas).not.toBeNull();
    expect(canvas.getAttribute('role')).toBe('img');
    // Kurzbeschreibung statt der generierten Aufzählung — die Beträge stehen auf dieser Seite
    // schon in Legende und Tabelle. Die Aufzählung selbst ist in donut-chart.spec.ts abgedeckt.
    expect(canvas.getAttribute('aria-label')).toBe(
      'Donut-Diagramm der Ausgabenverteilung nach Kategorie. Die einzelnen Beträge stehen in ' +
        'der Legende und in der Tabelle darunter.',
    );
    expect(canvas.getAttribute('aria-label')).not.toContain('350.50');

    // Ein vorhandenes <canvas> beweist noch nicht, dass Chart.js hochgekommen ist — die
    // Instanz an der Direktive tut es. Sie existiert nur, wenn der Aufbau ohne Fehler lief.
    const chart = fixture.debugElement
      .query(By.directive(BaseChartDirective))
      .injector.get(BaseChartDirective).chart;
    expect(chart).toBeDefined();
    expect(chart!.data.labels).toEqual(['Wohnen', 'Lebensmittel']);
    expect(chart!.data.datasets[0].data).toEqual([1000, 350.5]);
  });

  it('maps every category to the slug that also colours its table badge', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);
    fixture.detectChanges();

    expect(component.slices()).toEqual([
      { slug: 'wohnen', label: 'Wohnen', value: 1000 },
      { slug: 'lebensmittel', label: 'Lebensmittel', value: 350.5 },
    ]);

    // Gegenprobe: Chart-Segment und Tabellen-Badge derselben Zeile teilen den Slug —
    // damit ziehen beide dasselbe --cat-<slug>-Token.
    const legendSlugs = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.legend__dot'),
    ).map((dot) => dot.getAttribute('data-cat'));
    expect(legendSlugs).toEqual(component.slices().map((slice) => slice.slug));
    expect(legendSlugs[0]).toBe(component.categorySlug('Wohnen'));
  });

  it('falls back to the raw label when the category is unknown', () => {
    expectSummaryRequest(httpMock).flush(UNKNOWN_CATEGORY_SUMMARY);
    fixture.detectChanges();

    // Kein --cat-Token trifft "Kryptowährung" → das Segment bleibt neutral grau,
    // statt die Farbe einer fremden Kategorie zu belegen.
    expect(component.slices()).toEqual([
      { slug: 'Kryptowährung', label: 'Kryptowährung', value: 60 },
    ]);
  });

  it('lists every category with its amount in the chart legend', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);
    fixture.detectChanges();

    const items = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.legend__item'),
    );
    expect(items).toHaveLength(2);
    expect(items[0].querySelector('.legend__name')?.textContent?.trim()).toBe('Wohnen');
    expect(items[0].querySelector('.legend__value')?.textContent?.trim()).toBe("1'000.00");
    expect(items[1].querySelector('.legend__name')?.textContent?.trim()).toBe('Lebensmittel');
    expect(items[1].querySelector('.legend__value')?.textContent?.trim()).toBe('350.50');
  });

  it('updates the chart when the month changes', () => {
    expectSummaryRequest(httpMock).flush(SUMMARY);
    fixture.detectChanges();

    component.previousMonth();
    expectSummaryRequest(httpMock).flush(PREVIOUS_MONTH_SUMMARY);
    fixture.detectChanges();

    expect(component.slices()).toEqual([{ slug: 'transport', label: 'Transport', value: 200 }]);

    const items = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.legend__item'),
    );
    expect(items).toHaveLength(1);
    expect(items[0].querySelector('.legend__name')?.textContent?.trim()).toBe('Transport');
    expect(items[0].querySelector('.legend__value')?.textContent?.trim()).toBe('200.00');
  });

  it('shows no chart in the empty state', () => {
    expectSummaryRequest(httpMock).flush(EMPTY_SUMMARY);
    fixture.detectChanges();

    expect(component.slices()).toEqual([]);
    expect((fixture.nativeElement as HTMLElement).querySelector('app-donut-chart')).toBeNull();
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
