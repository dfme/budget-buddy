import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { Dashboard } from './dashboard';
import { SafeToSpendResponse } from './safe-to-spend.model';

const NORMAL: SafeToSpendResponse = {
  amount: 500,
  weeksLeft: 2,
  negative: false,
  noIncome: false,
  incomeSuggestion: null,
};

const SINGLE_WEEK: SafeToSpendResponse = {
  amount: 120,
  weeksLeft: 1,
  negative: false,
  noIncome: false,
  incomeSuggestion: null,
};

/** Budget überzogen — `negative` ist gesetzt, `amount` entsprechend kleiner 0 (BE-STS-03). */
const NEGATIVE: SafeToSpendResponse = {
  amount: -120,
  weeksLeft: 2,
  negative: true,
  noIncome: false,
  incomeSuggestion: null,
};

const NO_INCOME: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: 3800,
};

/** Kein Einkommen und keine wiederkehrende Gutschrift gefunden (BE-STS-02 liefert dann null). */
const NO_INCOME_WITHOUT_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: null,
};

/** URL-Matcher, unabhängig von etwaigen zukünftigen Query-Parametern. */
function expectSafeToSpendRequest(httpMock: HttpTestingController) {
  return httpMock.expectOne((req) => req.url === '/budget/safe-to-spend');
}

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows a loading state while the request is in flight', () => {
    expectSafeToSpendRequest(httpMock);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.status')?.textContent).toContain('Lädt');
  });

  it('renders the amount in the hero style, without a "+" for a positive balance, and a pluralized week label', () => {
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    fixture.detectChanges();

    const amount = fixture.debugElement.query(By.css('app-amount'));
    expect(amount.nativeElement.classList).toContain('safe-to-spend__amount');
    expect(amount.componentInstance.value()).toBe(500);
    expect(amount.componentInstance.hidePositiveSign()).toBe(true);
    expect(fixture.nativeElement.querySelector('.safe-to-spend__week-label').textContent).toBe(
      'noch 2 Wochen im Monat',
    );
  });

  it('uses the singular week label when exactly one week remains', () => {
    expectSafeToSpendRequest(httpMock).flush(SINGLE_WEEK);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.safe-to-spend__week-label').textContent).toBe(
      'noch 1 Woche im Monat',
    );
  });

  it('shows a placeholder instead of a misleading amount when no income is set', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('app-amount'))).toBeNull();
    const placeholder = fixture.nativeElement.querySelector('.safe-to-spend__amount--placeholder');
    expect(placeholder.querySelector('.safe-to-spend__amount-currency').textContent).toBe('CHF');
    expect(placeholder.querySelector('[aria-hidden="true"]').textContent).toBe('—');
    expect(placeholder.querySelector('.visually-hidden').textContent).toBe('Kein Betrag verfügbar');
  });

  it('shows the red overdrawn banner above the card when the budget is negative', () => {
    expectSafeToSpendRequest(httpMock).flush(NEGATIVE);
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.negative-banner');
    expect(banner).not.toBeNull();
    expect(banner.textContent.trim()).toBe('Achtung: Dein Budget für diese Woche ist überzogen');
    // Rot + fett kommen aus der error-Variante von app-notice (notice.scss); die Klasse ist
    // hier der Nachweis, dass genau diese Variante gewählt wurde.
    expect(banner.classList).toContain('notice--error');
    expect(banner.getAttribute('role')).toBe('alert');
    // "am oberen Rand" (US-06): das Banner steht vor der Card, nicht darunter.
    expect(banner.compareDocumentPosition(fixture.nativeElement.querySelector('app-card'))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('hides the overdrawn banner when the budget is not negative', () => {
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.negative-banner')).toBeNull();
  });

  it('shows no overdrawn banner when no income is set and there is no amount at all', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.negative-banner')).toBeNull();
  });

  it('shows an error notice when the request fails', () => {
    expectSafeToSpendRequest(httpMock).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const notice = fixture.debugElement.query(By.css('app-notice'));
    expect(notice).not.toBeNull();
    expect(notice.nativeElement.textContent).toContain('konnte nicht geladen werden');
  });
  it('shows the no-income banner with both the issue and the US-06 wording', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.no-income-banner');
    expect(banner).not.toBeNull();
    expect(banner.querySelector('.no-income__headline').textContent.trim()).toBe(
      'Kein Einkommen erfasst',
    );
    expect(banner.querySelector('.no-income__hint').textContent.trim()).toBe(
      'Bitte erfasse dein Monatseinkommen in den Einstellungen',
    );
    // `warning`, nicht `error`: ein fehlendes Einkommen ist kein Fehler, der den
    // Screenreader unterbrechen darf. Das assertive role="alert" gehoert FE-STS-02.
    expect(banner.classList).not.toContain('notice--error');
    expect(banner.getAttribute('role')).toBe('status');
  });

  it('hides the no-income banner when an income is on file', () => {
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income-banner')).toBeNull();
  });

  it('renders the income suggestion with the amount in Swiss format', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income__suggestion').textContent.trim()).toBe(
      "Regelmässige Gutschrift von 3'800.00 CHF erkannt — als Monatseinkommen übernehmen?",
    );
    expect(fixture.nativeElement.querySelector('.no-income__apply').textContent.trim()).toBe(
      'Übernehmen',
    );
  });

  it('offers no suggestion and no apply button when the heuristic found none', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME_WITHOUT_SUGGESTION);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income-banner')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.no-income__suggestion')).toBeNull();
    expect(fixture.nativeElement.querySelector('.no-income__apply')).toBeNull();
  });

  it('applies the suggestion via PUT /users/me/income and reloads safe-to-spend', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.no-income__apply').click();

    const put = httpMock.expectOne('/users/me/income');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ betrag: 3800 });
    put.flush({ id: 1, email: 'lara@example.ch', monthlyIncome: 3800, onboardingCompleted: true });

    // Der neu geladene Betrag ist die Bestaetigung: ohne den Reload bliebe der
    // Platzhalter stehen, den der Nutzer gerade loswerden wollte.
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income-banner')).toBeNull();
    expect(fixture.debugElement.query(By.css('app-amount')).componentInstance.value()).toBe(500);
  });

  it('disables the apply button while the request is in flight', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.no-income__apply');
    expect(button.disabled).toBe(false);

    button.click();
    fixture.detectChanges();

    expect(button.disabled).toBe(true);
    expect(button.textContent.trim()).toBe('Wird übernommen …');

    httpMock
      .expectOne('/users/me/income')
      .flush({ id: 1, email: 'lara@example.ch', monthlyIncome: 3800, onboardingCompleted: true });
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
  });

  it('keeps the banner and reports the failure when applying the suggestion fails', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.no-income__apply').click();
    httpMock
      .expectOne('/users/me/income')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income-banner')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.no-income__error').textContent.trim()).toBe(
      'Das Einkommen konnte nicht gespeichert werden.',
    );
    // Der Button bleibt bedienbar — ein Serverfehler ist ein Grund zum Wiederholen.
    expect(fixture.nativeElement.querySelector('.no-income__apply').disabled).toBe(false);
  });

  it('adds the "Letzte Woche des Monats" hint in the final week (US-06)', () => {
    expectSafeToSpendRequest(httpMock).flush(SINGLE_WEEK);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.safe-to-spend__last-week').textContent.trim()).toBe(
      'Letzte Woche des Monats',
    );
    // Der Hinweis tritt neben das Wochen-Label, er ersetzt es nicht.
    expect(fixture.nativeElement.querySelector('.safe-to-spend__week-label').textContent).toBe(
      'noch 1 Woche im Monat',
    );
  });

  it('omits the final-week hint while more than one week remains', () => {
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.safe-to-spend__last-week')).toBeNull();
  });
});
