import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { BaseChartDirective } from 'ng2-charts';

import { CATEGORIES } from '../shared/category';
import { installCanvasStub, restoreCanvasStub } from '../../testing/canvas';
import { CategoryOverview } from './category-overview';
import { CategorySummary } from './category-summary.model';
import { Transaction } from './transaction.model';

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

/** Buchungen hinter der Kategorie "Lebensmittel" (FE-CAT-03). */
const TRANSACTIONS: Transaction[] = [
  {
    id: 1,
    buchungsdatum: '2026-07-20',
    buchungstext: 'COOP PRONTO BERN',
    betrag: 34.2,
    income: false,
    category: 'Lebensmittel',
  },
  {
    id: 2,
    buchungsdatum: '2026-07-05',
    buchungstext: 'MIGROS MM ZENTRUM',
    betrag: 52.1,
    income: false,
    category: 'Lebensmittel',
  },
];

/** URL-Matcher unabhängig vom (vom aktuellen Datum abhängigen) Monat. */
function expectSummaryRequest(httpMock: HttpTestingController) {
  return httpMock.expectOne((req) => req.url === '/transactions/summary');
}

/** Wie {@link expectSummaryRequest}, aber für die Liste der Einzelbuchungen. */
function expectListRequest(httpMock: HttpTestingController) {
  return httpMock.expectOne((req) => req.url === '/transactions');
}

/**
 * Wählt eine Kategorie im Dropdown so, wie es ein Nutzer täte: Wert setzen und `change`
 * auslösen. Ein direkter Aufruf von `changeCategory` würde die Template-Verdrahtung
 * überspringen — genau die Stelle, an der ein falscher Event-Ausdruck unbemerkt bliebe.
 */
function selectCategory(dropdown: HTMLSelectElement, category: string) {
  dropdown.value = category;
  dropdown.dispatchEvent(new Event('change'));
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

  describe('Kategorie-Korrektur (FE-CAT-03)', () => {
    /** Klappt "Lebensmittel" auf und beantwortet den Listen-Request mit {@link TRANSACTIONS}. */
    function expandLebensmittel(transactions = TRANSACTIONS) {
      expectSummaryRequest(httpMock).flush(SUMMARY);
      fixture.detectChanges();

      toggleFor('Lebensmittel').click();
      fixture.detectChanges();

      const req = expectListRequest(httpMock);
      req.flush(transactions);
      fixture.detectChanges();
      return req;
    }

    /** Der Aufklapp-Button der Zeile mit diesem Kategorie-Label. */
    function toggleFor(category: string): HTMLButtonElement {
      const toggle = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
          '.drilldown-toggle',
        ),
      ).find((button) => button.textContent?.trim() === category);
      expect(toggle).toBeDefined();
      return toggle!;
    }

    /** Die Dropdowns der aufgeklappten Buchungen, in Reihenfolge der Liste. */
    function selects(): HTMLSelectElement[] {
      return Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLSelectElement>(
          '.transaction select',
        ),
      );
    }

    it('requests the transactions of the category when a row is expanded', () => {
      expectSummaryRequest(httpMock).flush(SUMMARY);
      fixture.detectChanges();

      const toggle = toggleFor('Lebensmittel');
      expect(toggle.getAttribute('aria-expanded')).toBe('false');

      toggle.click();
      fixture.detectChanges();

      const req = expectListRequest(httpMock);
      expect(req.request.params.get('month')).toBe(component.month());
      expect(req.request.params.get('category')).toBe('Lebensmittel');
      req.flush(TRANSACTIONS);
      fixture.detectChanges();

      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      const rendered = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('.transaction__text'),
      ).map((el) => el.textContent?.trim());
      expect(rendered).toEqual(['COOP PRONTO BERN', 'MIGROS MM ZENTRUM']);
    });

    it('collapses the row again on a second click without a further request', () => {
      expandLebensmittel();

      toggleFor('Lebensmittel').click();
      fixture.detectChanges();

      expect(component.drilldown()).toBeNull();
      expect(selects()).toHaveLength(0);
      // httpMock.verify() im afterEach beweist, dass kein weiterer Request offen ist.
    });

    // AC 1: Dropdown zeigt alle 13 Kategorien aus CLAUDE.md
    it('offers all 13 categories in every dropdown, preselected with the current one', () => {
      expandLebensmittel();

      const dropdowns = selects();
      expect(dropdowns).toHaveLength(2);

      for (const dropdown of dropdowns) {
        const options = Array.from(dropdown.options).map((option) => option.value);
        expect(options).toHaveLength(13);
        // Gegenprobe gegen die geteilte Liste statt gegen eine Kopie im Test: eine 14.
        // Kategorie im Backend-Enum fällt so hier auf und nicht erst im Betrieb.
        expect(options).toEqual(CATEGORIES.map((c) => c.label));
      }

      expect(dropdowns[0].value).toBe('Lebensmittel');
    });

    // AC 2: Kategorie-Änderung wird sofort im UI reflektiert (optimistic update)
    it('shows the new category before the server has answered', () => {
      expandLebensmittel();

      selectCategory(selects()[0], 'Restaurant');
      // Der Browser rendert zwischen Auswahl und Server-Antwort — ohne diesen Durchlauf
      // hätte Angular den optimistischen Stand nie gesehen und könnte ihn später auch nicht
      // zurücknehmen.
      fixture.detectChanges();

      // Noch nichts geflusht — der PUT ist offen, die Anzeige steht aber schon auf dem neuen Wert.
      const put = httpMock.expectOne('/transactions/1/category');
      expect(put.request.method).toBe('PUT');
      expect(put.request.body).toEqual({ category: 'Restaurant' });

      // Diese Assertion trägt den Nachweis für AC 2 — sie ist die einzige, die ohne das
      // optimistische Update umfällt.
      expect(component.drilldown()?.transactions[0].category).toBe('Restaurant');

      // Die DOM-Zeile darunter beweist das optimistische Update dagegen *nicht*: den Wert hat
      // selectCategory() selbst gesetzt, und Angular schreibt bei unverändertem Binding nicht
      // dagegen an. Sie bleibt als Regressionsschutz dafür stehen, dass nichts die Auswahl
      // vorzeitig zurückzieht — nicht als Beleg. Wer das optimistische Update entfernt, sieht
      // es am Rollback-Test unten, der dann rot wird.
      expect(selects()[0].value).toBe('Restaurant');
      expect(component.saveErrorMessage()).toBeNull();

      put.flush({ ...TRANSACTIONS[0], category: 'Restaurant' });
      // Erfolg lädt Summary und offene Liste nach, damit Donut und Summen nicht auf dem
      // alten Stand stehenbleiben.
      expectSummaryRequest(httpMock).flush(SUMMARY);
      expectListRequest(httpMock).flush([TRANSACTIONS[1]]);
      fixture.detectChanges();

      expect(component.saveErrorMessage()).toBeNull();
    });

    // AC 3: Bei API-Fehler wird die Änderung zurückgerollt
    it('rolls the change back and explains itself when the server rejects it', () => {
      expandLebensmittel();

      selectCategory(selects()[0], 'Restaurant');
      fixture.detectChanges();
      expect(selects()[0].value).toBe('Restaurant');

      httpMock
        .expectOne('/transactions/1/category')
        .flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(component.drilldown()?.transactions[0].category).toBe('Lebensmittel');
      expect(selects()[0].value).toBe('Lebensmittel');
      expect(component.saveErrorMessage()).toBe('Die Kategorie konnte nicht gespeichert werden.');
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('.save-notice')?.textContent,
      ).toContain('nicht gespeichert');
      // Die Übersicht selbst bleibt stehen — der Ladefehler-Zweig darf sie nicht ersetzen.
      expect((fixture.nativeElement as HTMLElement).querySelector('table')).not.toBeNull();
      expect(component.errorMessage()).toBeNull();
    });

    it('does not let a finished correction undo a second one that is still running', () => {
      expandLebensmittel();

      selectCategory(selects()[0], 'Restaurant');
      selectCategory(selects()[1], 'Transport');
      fixture.detectChanges();

      const puts = httpMock.match((req) => req.url.endsWith('/category'));
      expect(puts).toHaveLength(2);

      // Nur der erste PUT ist fertig. Sein Nachladen dürfte jetzt nicht laufen: die Antwort des
      // Servers kennt die zweite Korrektur noch nicht und würde sie sichtbar zurückwerfen.
      puts[0].flush({ ...TRANSACTIONS[0], category: 'Restaurant' });
      fixture.detectChanges();

      expect(component.drilldown()?.transactions[1].category).toBe('Transport');
      expect(selects()[1].value).toBe('Transport');

      // Erst mit dem zweiten PUT wird nachgeladen — dann für beide.
      puts[1].flush({ ...TRANSACTIONS[1], category: 'Transport' });
      expectSummaryRequest(httpMock).flush(SUMMARY);
      expectListRequest(httpMock).flush([]);
      fixture.detectChanges();

      expect(component.saveErrorMessage()).toBeNull();
    });

    it('does not report a failed correction after the user has moved to another month', () => {
      expandLebensmittel();

      selectCategory(selects()[0], 'Restaurant');
      fixture.detectChanges();
      const put = httpMock.expectOne('/transactions/1/category');

      // Monatswechsel, bevor der Server geantwortet hat.
      component.previousMonth();
      fixture.detectChanges();
      expectSummaryRequest(httpMock).flush(SUMMARY);
      fixture.detectChanges();

      // Der Monatswechsel bricht die Korrektur-Subscription ab. Damit kann der Error-Handler gar
      // nicht mehr feuern — deshalb wird hier die Absage nicht mehr eingespielt, sondern der
      // Abbruch selbst geprüft. Der PUT ist beim Server angekommen; abgebrochen ist nur die
      // Reaktion des UI darauf.
      expect(put.cancelled).toBe(true);

      expect(component.saveErrorMessage()).toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('.save-notice')).toBeNull();
    });

    it('does not send a request when the selected category is unchanged', () => {
      expandLebensmittel();

      selectCategory(selects()[0], 'Lebensmittel');
      fixture.detectChanges();

      expect(component.drilldown()?.transactions[0].category).toBe('Lebensmittel');
      // httpMock.verify() im afterEach schlägt fehl, wenn doch ein PUT rausging.
    });

    it('reports a failed load of the transactions', () => {
      expectSummaryRequest(httpMock).flush(SUMMARY);
      fixture.detectChanges();

      toggleFor('Lebensmittel').click();
      fixture.detectChanges();
      expectListRequest(httpMock).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(component.drilldown()?.error).not.toBeNull();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('.drilldown .status.error'),
      ).not.toBeNull();
    });

    it('closes the expanded category when the month changes', () => {
      expandLebensmittel();

      component.previousMonth();
      expectSummaryRequest(httpMock).flush(EMPTY_SUMMARY);
      fixture.detectChanges();

      // Die Buchungen gehören zum alten Monat — sie dürfen unter dem neuen nicht stehenbleiben.
      expect(component.drilldown()).toBeNull();
      expect(selects()).toHaveLength(0);
    });
  });
});
