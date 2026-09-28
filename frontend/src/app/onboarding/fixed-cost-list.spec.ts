import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { App } from '../app';
import { routes } from '../app.routes';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { Dashboard } from '../dashboard/dashboard';
import { IncomeCard } from '../income/income-card';
import { RecurringExpenseResponse } from '../recurring/recurring-expense.model';
import { FixedCostList } from './fixed-cost-list';
import { FixedCostDetail, FixedCostSummary } from './fixed-cost.model';

// Der CurrencyPipe nutzt den app-weiten LOCALE_ID (de-CH); die Locale-Daten müssen dafür
// registriert sein — im echten App-Bootstrap erledigt das app.config.ts.
registerLocaleData(localeDeCh);

/**
 * Eingeloggte Nutzerin mit bereits erfasstem Einkommen (FE-FC-09) — der Normalfall für die
 * meisten Tests dieser Datei, die mit Fixkosten/Abos arbeiten und sich nicht für die
 * Einkommens-Card interessieren. Der Konstruktor überspringt dank `monthlyIncome` den
 * Vorschlags-Call (`GET /api/budget/safe-to-spend`) — ohne Login läse `AuthService.currentUser()`
 * `null` und der Call bliebe unbeantwortet offen, was `httpMock.verify()` aufdeckte.
 */
const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
  firstName: null,
  lastName: null,
};

/** Loggt via `AuthService.login()` ein, damit `currentUser()` synchron befüllt ist. */
function loginAs(mock: HttpTestingController, user: User): void {
  TestBed.inject(AuthService).login(user.email, 'irrelevant').subscribe();
  mock.expectOne('/api/auth/login').flush(user);
}

const MIETE: FixedCostDetail = {
  id: 1,
  bezeichnung: 'Miete',
  betrag: 1200,
  intervall: 'monatlich',
  monatsbetrag: 1200,
};
const SERAFE: FixedCostDetail = {
  id: 2,
  bezeichnung: 'Serafe',
  betrag: 335,
  intervall: 'jaehrlich',
  monatsbetrag: 27.92,
};

/** Erkanntes Abo — zählte bis FE-STS-06 ins Total dieser Seite. */
const NETFLIX: RecurringExpenseResponse = {
  id: 1,
  payeeKey: 'NETFLIX',
  amount: 17.9,
  status: 'DETECTED',
  firstDetectedMonth: '2026-07',
  createdAt: '2026-09-08T10:15:00Z',
  isNew: false,
};

/** Per «Kein Abo» verneint — bleibt auf der Seite (FE-NOTIF-03). */
const COOP_DISMISSED: RecurringExpenseResponse = {
  id: 2,
  payeeKey: 'COOP PRONTO',
  amount: 24.5,
  status: 'DISMISSED',
  firstDetectedMonth: '2026-03',
  createdAt: '2026-09-01T10:15:00Z',
  isNew: false,
};

function summaryOf(
  fixedCosts: FixedCostDetail[],
  monthlyIncome: number | null,
  exceedsIncome: boolean,
): FixedCostSummary {
  const summeMonatlich = fixedCosts.reduce((sum, item) => sum + item.monatsbetrag, 0);
  return { fixedCosts, summeMonatlich, monthlyIncome, exceedsIncome };
}

describe('FixedCostList', () => {
  let fixture: ComponentFixture<FixedCostList>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FixedCostList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'de-CH' },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    fixture = TestBed.createComponent(FixedCostList);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(
    summary: FixedCostSummary,
    recurringExpenses: RecurringExpenseResponse[] = [],
  ): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/fixed-costs').flush(summary);
    flushRecurringExpenses(recurringExpenses);
    fixture.detectChanges();
  }

  /**
   * Der eingebettete Abschnitt «Erkannte Abos» (FE-FC-05) lädt beim Aufbau seine eigene Liste.
   * Meist leer beantwortet: was er damit macht, prüft `recurring-expense-list.spec.ts` — dieser
   * Test kümmert sich nur darum, dass der Request nicht offen bleibt (`httpMock.verify()`).
   */
  function flushRecurringExpenses(expenses: RecurringExpenseResponse[] = []): void {
    httpMock.expectOne('/api/recurring-expenses').flush(expenses);
  }

  /**
   * Textinhalt mit normalisiertem Whitespace. Die CurrencyPipe setzt unter de-CH ein
   * geschütztes Leerzeichen zwischen `CHF` und Betrag — im Test soll `'CHF 1’245.82'` mit
   * gewöhnlichem Leerzeichen genügen.
   */
  function text(): string {
    return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  }

  /** Zeile in der Fixkosten-Tabelle, die `bezeichnung` enthält. */
  function rowFor(bezeichnung: string): HTMLElement {
    const row = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody > tr'),
    ).find((tr) => tr.textContent?.includes(bezeichnung));
    if (!row) {
      throw new Error(`Keine Zeile mit "${bezeichnung}"`);
    }
    return row as HTMLElement;
  }

  // Tabelle, Bearbeiten, Löschen, Dialog «Neue Position» und Einkommens-Warnung prüft seit FE-FC-13
  // `fixed-cost-section.spec.ts` — hier bleibt, was die Seite betrifft: Überschriften, Reihenfolge
  // und Verbindung der eingebetteten Abschnitte.

  // FE-FC-07 (#355): die Seite hiess «Ausgaben», die Tabelle bekommt eine Zwischenüberschrift.
  // FE-FC-09 (#360) benennt die Seite zu «Budget» um. Das Total aus Fixkosten-Monatssumme und
  // erkannten Abos (FE-FC-07) stand hier bis FE-STS-06 (#366) — seither auf dem Dashboard, sein
  // Verhalten prüft `dashboard.spec.ts`.
  describe('Seite «Budget» (FE-FC-07, FE-FC-09, FE-STS-06)', () => {
    function heading(level: 1 | 2 | 3, name: string): HTMLElement | null {
      return (
        Array.from(
          (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(`h${level}`),
        ).find((h) => h.textContent?.trim() === name) ?? null
      );
    }

    it('trägt den Titel «Budget» und die Zwischenüberschriften «Einkommen», «Ausgaben», «Erfasste Fixkosten» und «Erkannte Abos»', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      expect(heading(1, 'Budget')).not.toBeNull();
      expect(heading(1, 'Ausgaben')).toBeNull();
      expect(heading(1, 'Fixkosten')).toBeNull();
      expect(heading(2, 'Einkommen')).not.toBeNull();
      // «Ausgaben» gruppiert die beiden Unterabschnitte als h2, die selbst eine Stufe tiefer
      // (h3) sitzen — analog «Einkommen», aber mit einer zusätzlichen Ebene darunter.
      expect(heading(2, 'Ausgaben')).not.toBeNull();
      expect(heading(3, 'Erfasste Fixkosten')).not.toBeNull();
      expect(heading(3, 'Erkannte Abos')).not.toBeNull();
      expect(heading(2, 'Erfasste Fixkosten')).toBeNull();
      expect(heading(2, 'Erkannte Abos')).toBeNull();
    });

    // FE-FC-09 (#360): «Einkommen» steht wie «Erfasste Fixkosten» als eigene Zwischenüberschrift
    // ausserhalb der Card, nicht als `app-card`-Titel — beide Abschnitte auf derselben Ebene.
    it('stellt die Zwischenüberschrift «Einkommen» ausserhalb der Card, nicht als Card-Titel', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const root = fixture.nativeElement as HTMLElement;
      expect(heading(2, 'Einkommen')?.closest('app-card')).toBeNull();
      const cardTitles = Array.from(root.querySelectorAll('app-card .card__title')).map((el) =>
        el.textContent?.trim(),
      );
      expect(cardTitles).not.toContain('Einkommen');
    });

    it('stellt «+ Neue Position» neben die Zwischenüberschrift, nicht neben den Titel', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
        '.section-header button[appButton]',
      );
      expect(button?.textContent?.trim()).toBe('+ Neue Position');
      // Seit FE-FC-10 ein Button, der den Dialog öffnet — kein Link mehr in den Wizard.
      expect(button?.type).toBe('button');
      expect(button?.hasAttribute('href')).toBe(false);
      expect(button?.parentElement?.querySelector('h3')?.textContent?.trim()).toBe(
        'Erfasste Fixkosten',
      );
    });

    it('benennt den Tabellen-Scrollbereich über die Zwischenüberschrift', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const region = (fixture.nativeElement as HTMLElement).querySelector('.table-scroll');
      expect(region?.getAttribute('aria-labelledby')).toBe('fixed-costs-heading');
      expect(heading(3, 'Erfasste Fixkosten')?.id).toBe('fixed-costs-heading');
    });

    // FE-STS-06 (#366): die Card «Monatliche fixe Ausgaben» ist auf das Dashboard umgezogen —
    // weder die Card noch ihr Ersatzsatz bei einem fehlenden Summanden steht noch hier.
    it('zeigt die Card «Monatliche fixe Ausgaben» nicht mehr', () => {
      flushInitialLoad(summaryOf([MIETE, SERAFE], 3000, false), [NETFLIX, COOP_DISMISSED]);

      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('.monthly-total')).toBeNull();
      expect(text()).not.toContain('Monatliche fixe Ausgaben');
      // Die restlichen Abschnitte stehen unverändert: Einkommen, Fixkosten, erkannte Abos.
      expect(root.querySelector('app-income-card')).not.toBeNull();
      expect(rowFor('Serafe')).toBeTruthy();
      expect(root.querySelector('app-recurring-expense-list')?.textContent).toContain('NETFLIX');
    });

    it('zeigt auch keinen Total-Hinweis, wenn ein Request fehlschlägt', () => {
      fixture.detectChanges();
      httpMock
        .expectOne('/api/fixed-costs')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      httpMock
        .expectOne('/api/recurring-expenses')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();

      expect(
        (fixture.nativeElement as HTMLElement).querySelector('.monthly-total__unavailable'),
      ).toBeNull();
      expect(text()).not.toContain('Total nicht verfügbar');
    });
  });

  // FE-FC-09 (#360): die Card «Einkommen» — bis dahin FE-SET-03 in den Einstellungen — steht
  // jetzt ganz oben auf der Budget-Seite, als eigene Komponente (`IncomeCard`), weil derselbe
  // Abschnitt auch im Onboarding-Wizard eingebettet ist. Ihr eigenes Verhalten deckt
  // `income-card.spec.ts` ab; hier geht es nur um die Einbettung.
  describe('Abschnitt «Einkommen» (FE-FC-09)', () => {
    it('rendert die Einkommens-Card oberhalb der Fixkosten-Tabelle', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const root = fixture.nativeElement as HTMLElement;
      const income = root.querySelector('app-income-card');
      expect(income).not.toBeNull();
      const table = root.querySelector('table');
      expect(
        income!.compareDocumentPosition(table!) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    // Card und Warnung «Fixkosten übersteigen dein Einkommen» stehen auf derselben Seite: ohne
    // Neuladen widerspräche die Warnung dem gerade gespeicherten Einkommen.
    it('lädt die Fixkosten neu, sobald die Card ein Einkommen gespeichert hat', () => {
      flushInitialLoad(summaryOf([MIETE], 1000, true));
      expect(text()).toContain('übersteigen dein Einkommen');

      const card = fixture.debugElement.query(By.directive(IncomeCard))
        .componentInstance as IncomeCard;
      card.incomeForm.controls.betrag.setValue(4000);
      card.submitIncome();
      httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 4000 });

      httpMock.expectOne('/api/fixed-costs').flush(summaryOf([MIETE], 4000, false));
      fixture.detectChanges();

      expect(text()).not.toContain('übersteigen dein Einkommen');
    });

    it('lädt nicht neu, wenn das Speichern des Einkommens fehlschlägt', () => {
      flushInitialLoad(summaryOf([MIETE], 1000, true));

      const card = fixture.debugElement.query(By.directive(IncomeCard))
        .componentInstance as IncomeCard;
      card.incomeForm.controls.betrag.setValue(4000);
      card.submitIncome();
      httpMock
        .expectOne('/api/users/me/income')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });

      httpMock.expectNone('/api/fixed-costs');
    });
  });

  // FE-FC-05: die Abo-Übersicht ist ein Abschnitt dieser Seite. Zwei getrennte Requests, zwei
  // getrennte Zustände — ein Fehler in der Fixkosten-Liste nimmt den Abo-Abschnitt nicht mit.
  describe('Abschnitt «Erkannte Abos» (FE-FC-05)', () => {
    function recurringSection(): HTMLElement | null {
      return (fixture.nativeElement as HTMLElement).querySelector('app-recurring-expense-list');
    }

    it('rendert den Abschnitt unterhalb der Fixkosten-Tabelle', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const section = recurringSection();
      expect(section).not.toBeNull();
      expect(section?.querySelector('h3')?.textContent?.trim()).toBe('Erkannte Abos');
      // Reihenfolge im DOM: erst die Fixkosten-Tabelle, dann der Abo-Abschnitt.
      const table = (fixture.nativeElement as HTMLElement).querySelector('table');
      expect(
        table!.compareDocumentPosition(section!) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    it('zeigt den Abschnitt auch, wenn die Fixkosten nicht geladen werden konnten', () => {
      fixture.detectChanges();
      httpMock
        .expectOne('/api/fixed-costs')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      httpMock.expectOne('/api/recurring-expenses').flush([
        {
          id: 1,
          payeeKey: 'NETFLIX',
          amount: 17.9,
          status: 'DETECTED',
          firstDetectedMonth: '2026-07',
          createdAt: '2026-09-08T10:15:00Z',
          isNew: false,
        },
      ]);
      fixture.detectChanges();

      expect(text()).toContain('konnten nicht geladen werden');
      expect(recurringSection()?.querySelector('.expense__payee')?.textContent).toContain(
        'NETFLIX',
      );
    });
  });
});

// FE-FC-09 (#360): dieselbe Vorschlags-Logik wie zuvor in settings.spec.ts, jetzt gegen die
// Budget-Seite — eigenes TestBed, weil der Konstruktor den Vorschlags-Call nur auslöst, solange
// kein Einkommen erfasst ist (siehe `LARA` oben).
// FE-FC-09 (#360): verschoben aus settings.spec.ts («Route /einstellungen», AC5) — die Karte, die
// das Einkommen ändert, liegt jetzt auf /budget statt /einstellungen.
describe('Route /budget (FE-FC-09)', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  /** Beantwortet das `GET /api/users/me` des Guards. */
  async function answerProfile(user: User): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    httpMock.expectOne('/api/users/me').flush(user);
  }

  /**
   * FE-STS-04: Das Dashboard lädt beim Aufbau daneben die Monatsliste — sie speist Dropdown und
   * Keine-Daten-Hinweis. Der laufende Monat trägt hier Daten, damit das Dashboard in seinem
   * Normalzustand steht und der Hinweis wegbleibt.
   */
  function flushDashboardMonths(): void {
    const now = new Date();
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    httpMock.expectOne('/api/transactions/months').flush([month]);
  }

  it('zeigt auf dem Dashboard den neuen Safe-to-Spend-Betrag, nachdem das Einkommen auf der Budget-Seite gespeichert wurde — ohne Reload', async () => {
    const root = TestBed.createComponent(App);
    root.detectChanges();

    const firstNavigation = router.navigateByUrl('/budget');
    await answerProfile({
      id: 1,
      email: 'lara@example.ch',
      monthlyIncome: null,
      onboardingCompleted: true,
      firstName: null,
      lastName: null,
    });
    await firstNavigation;
    // Konstruktor der Einkommens-Card: kein Einkommen erfasst, also der Vorschlags-Call — dieser
    // Request gehört der Budget-Seite selbst, nicht dem Dashboard.
    httpMock.expectOne('/api/budget/safe-to-spend').flush({
      amount: null,
      weeksLeft: 3,
      negative: false,
      noIncome: true,
      incomeSuggestion: null,
      status: 'OPEN',
    });
    root.detectChanges();
    // ngOnInit lädt die Fixkosten, das eingebettete app-recurring-expense-list seine eigene
    // Liste. FE-NOTIF-01: Sobald isAuthenticated() kippt, rendert die Shell
    // app-notification-bell (Topbar + Sidebar) und beide Instanzen laden beim Erstellen —
    // gebündelt auf einen Request.
    httpMock
      .expectOne('/api/fixed-costs')
      .flush({ fixedCosts: [], summeMonatlich: 0, monthlyIncome: null, exceedsIncome: false });
    httpMock.expectOne('/api/recurring-expenses').flush([]);
    httpMock.expectOne('/api/notifications').flush([]);
    root.detectChanges();

    const incomeCard = root.debugElement.query(By.directive(IncomeCard))
      .componentInstance as IncomeCard;
    incomeCard.incomeForm.controls.betrag.setValue(3800);
    incomeCard.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({
      id: 1,
      email: 'lara@example.ch',
      monthlyIncome: 3800,
      onboardingCompleted: true,
      firstName: null,
      lastName: null,
    });
    // Die Budget-Seite lädt nach dem Speichern ihre Fixkosten neu (Warnung hängt am Einkommen).
    httpMock
      .expectOne('/api/fixed-costs')
      .flush({ fixedCosts: [], summeMonatlich: 0, monthlyIncome: 3800, exceedsIncome: false });
    root.detectChanges();

    // Reine SPA-Navigation — kein `location.reload()`, kein neuer `App`-Fixture: dieselbe
    // Instanz von AuthService/HttpClient wie beim Speichern eben.
    const secondNavigation = router.navigateByUrl('/dashboard');
    await secondNavigation;
    flushDashboardMonths();
    // Prädikat statt Zeichenkette: Das Dashboard hängt seit FE-STS-04 `?month=` an, und der
    // String-Matcher von expectOne vergleicht die URL samt Query-String.
    const req = httpMock.expectOne((r) => r.url === '/api/budget/safe-to-spend');
    req.flush({
      amount: 650,
      weeksLeft: 2,
      negative: false,
      noIncome: false,
      incomeSuggestion: null,
      status: 'OPEN',
    });
    // BE-PDF-10: Das Dashboard lädt daneben die Zahl der ungeprüften Buchungsrichtungen. Für
    // diesen Fall ohne Belang, aber `verify()` im afterEach stolperte sonst darüber.
    httpMock.expectOne((r) => r.url === '/api/transactions/uncertain').flush([]);
    // FE-NOTIF-01: Jede Navigation löst bei app-notification-bell einen Reload aus.
    httpMock.expectOne('/api/notifications').flush([]);
    // FE-STS-04: Dasselbe für die Drei-Monats-Übersicht (BE-STS-07).
    httpMock.expectOne((r) => r.url === '/api/transactions/monthly-totals').flush([]);
    // FE-STS-06: Und die beiden Summanden der Card «Monatliche fixe Ausgaben» — Fixkosten und
    // erkannte Abos (bis dahin nur die Abos, für den Abo-Teaser aus FE-REC-01).
    httpMock
      .expectOne('/api/fixed-costs')
      .flush({ fixedCosts: [], summeMonatlich: 0, monthlyIncome: 3800, exceedsIncome: false });
    httpMock.expectOne('/api/recurring-expenses').flush([]);
    root.detectChanges();

    const dashboard = root.debugElement.query(By.directive(Dashboard))
      .componentInstance as Dashboard;
    expect(dashboard.data()?.amount).toBe(650);
    expect((root.nativeElement as HTMLElement).textContent).not.toContain('Kein Betrag verfügbar');
  });
});
