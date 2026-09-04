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
  return httpMock.expectOne((req) => req.url === '/api/budget/safe-to-spend');
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

  afterEach(() => {
    // BE-PDF-10: Beim Aufbau wird zusätzlich die Zahl der ungeprüften Buchungsrichtungen geladen.
    // Die Fälle unten befassen sich nicht damit und bekommen sie hier zentral mit einer leeren
    // Liste beantwortet; wer den Hinweis selbst prüft, holt den Request vorher ab.
    httpMock
      .match((req) => req.url === '/api/transactions/uncertain')
      .filter((req) => !req.cancelled)
      .forEach((req) => req.flush([]));
    httpMock.verify();
  });

  /** URL-Matcher für die Prüfliste der unsicheren Buchungsrichtungen (BE-PDF-10). */
  function expectUncertainRequest() {
    return httpMock.expectOne((req) => req.url === '/api/transactions/uncertain');
  }

  /** Eine unsicher markierte Buchung — nur die Felder, die der Zähler im Dashboard braucht. */
  function uncertainTransaction(id: number) {
    return {
      id,
      buchungsdatum: '2026-07-03',
      buchungstext: 'GIRO POST',
      buchungsdetails: null,
      betrag: 120,
      income: false,
      directionUncertain: true,
      category: 'Sonstiges',
    };
  }

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
    // `.notice__body` statt des ganzen Banners: seit FE-UI-07 rendert app-notice sein Icon
    // selbst, das im textContent des Hosts mitliefe.
    expect(banner.querySelector('.notice__body').textContent.trim()).toBe(
      'Achtung: Dein Budget für diese Woche ist überzogen',
    );
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
  it('shows the no-income state with both the issue and the US-06 wording', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('.no-income__notice');
    expect(notice).not.toBeNull();
    // Titel und Erlaeuterung kommen seit FE-UI-07 aus app-notice: der Titel aus dem
    // [title]-Input, die Erlaeuterung als projizierter Inhalt darunter.
    expect(notice.querySelector('.notice__title').textContent.trim()).toBe(
      'Kein Einkommen erfasst',
    );
    expect(notice.querySelector('.notice__body').textContent).toContain(
      'Bitte erfasse dein Monatseinkommen in den Einstellungen',
    );
    // Aufbau wie die Design-Baseline (design/variant-a/index.html, `hero hero--muted`):
    // der Zustand steht *in* der Safe-to-Spend-Card, nicht als Banner darueber.
    expect(fixture.nativeElement.querySelector('app-card .no-income')).not.toBeNull();
  });

  // Der Fall, den die erste Fassung falsch hatte: dort hing das Notice am Vorschlag, und
  // ohne erkanntes Gutschriftsmuster — der haeufigste Fall — blieb nur stiller Fliesstext
  // uebrig. Der AC verlangt das Banner, sobald noIncome gilt.
  it('shows the banner even when no suggestion was found', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME_WITHOUT_SUGGESTION);
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('.no-income__notice');
    expect(notice).not.toBeNull();
    expect(notice.getAttribute('role')).toBe('status');
    expect(notice.querySelector('.notice__title').textContent.trim()).toBe(
      'Kein Einkommen erfasst',
    );
    expect(notice.querySelector('.notice__body').textContent).toContain(
      'Bitte erfasse dein Monatseinkommen in den Einstellungen',
    );
  });

  it('hides the no-income state when an income is on file', () => {
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income')).toBeNull();
  });

  it('renders the income suggestion below the notice in Swiss format', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income__suggestion').textContent.trim()).toBe(
      "Regelmässige Gutschrift von 3'800.00 CHF erkannt — als Monatseinkommen übernehmen?",
    );

    const notice = fixture.nativeElement.querySelector('.no-income__notice');
    // `warning`, nicht `error`: ein fehlendes Einkommen ist kein Fehler, der den
    // Screenreader unterbrechen darf. Das assertive role="alert" gehoert FE-STS-02.
    expect(notice.classList).not.toContain('notice--error');
    expect(notice.getAttribute('role')).toBe('status');
    // Das Icon liefert app-notice seit FE-UI-07 selbst; es ist Dekoration und darf nicht
    // mitgelesen werden.
    const noticeIcon = notice.querySelector('.notice__icon');
    expect(noticeIcon.textContent.trim()).toBe('!');
    expect(noticeIcon.getAttribute('aria-hidden')).toBe('true');

    const button = fixture.nativeElement.querySelector('.no-income__apply');
    expect(button.textContent.trim()).toBe('Übernehmen');
    // Der Button steht ausserhalb der Live-Region, sonst laese der Screenreader seine
    // Beschriftung bei jeder Aenderung des Hinweises mit vor.
    expect(notice.contains(button)).toBe(false);
  });

  it('offers no suggestion and no apply button when the heuristic found none', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME_WITHOUT_SUGGESTION);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income__suggestion')).toBeNull();
    expect(fixture.nativeElement.querySelector('.no-income__apply')).toBeNull();
  });

  it('applies the suggestion via PUT /api/users/me/income and reloads safe-to-spend', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.no-income__apply').click();

    const put = httpMock.expectOne('/api/users/me/income');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ betrag: 3800 });
    put.flush({ id: 1, email: 'lara@example.ch', monthlyIncome: 3800, onboardingCompleted: true });

    // Der neu geladene Betrag ist die Bestaetigung: ohne den Reload bliebe der
    // Platzhalter stehen, den der Nutzer gerade loswerden wollte.
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income')).toBeNull();
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
      .expectOne('/api/users/me/income')
      .flush({ id: 1, email: 'lara@example.ch', monthlyIncome: 3800, onboardingCompleted: true });
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
  });

  it('keeps the no-income state and reports the failure when applying the suggestion fails', () => {
    expectSafeToSpendRequest(httpMock).flush(NO_INCOME);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.no-income__apply').click();
    httpMock
      .expectOne('/api/users/me/income')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.no-income')).not.toBeNull();
    const error = fixture.nativeElement.querySelector('.no-income__error');
    expect(error.querySelector('.notice__body').textContent.trim()).toBe(
      'Das Einkommen konnte nicht gespeichert werden.',
    );
    // Ein fehlgeschlagener Submit ist der Fall, fuer den app-notice das assertive
    // role="alert" vorsieht (notice.ts) — anders als der No-Income-Zustand selbst.
    expect(error.getAttribute('role')).toBe('alert');
    // Der Button bleibt bedienbar — ein Serverfehler ist ein Grund zum Wiederholen.
    expect(fixture.nativeElement.querySelector('.no-income__apply').disabled).toBe(false);
  });

  it('adds the "Letzte Woche des Monats" hint in the final week (US-06)', () => {
    expectSafeToSpendRequest(httpMock).flush(SINGLE_WEEK);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.safe-to-spend__last-week').textContent.trim(),
    ).toBe('Letzte Woche des Monats');
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

  it('warns about unchecked booking directions and says which way the number can be wrong', () => {
    // BE-PDF-10, AC 2: Der Hinweis steht dort, wo der Schaden eintritt. Er nennt ausdrücklich die
    // Richtung des Fehlers — «zu tief» —, weil erst das dem Nutzer sagt, was er damit anfangen
    // soll.
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    expectUncertainRequest().flush([uncertainTransaction(1), uncertainTransaction(2)]);
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.uncertain-banner');
    expect(banner).not.toBeNull();
    expect(banner.textContent).toContain('2 Buchungen');
    expect(banner.textContent).toContain('zu tief');
  });

  it('uses the singular for a single unchecked booking', () => {
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    expectUncertainRequest().flush([uncertainTransaction(1)]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.uncertain-banner').textContent).toContain(
      '1 Buchung ',
    );
  });

  it('stays quiet when every booking direction is settled', () => {
    // Der Normalfall. Ein Banner, das immer da steht, wird nicht gelesen.
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    expectUncertainRequest().flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.uncertain-banner')).toBeNull();
  });

  it('shows both banners when the budget is overdrawn and directions are unchecked', () => {
    // Gerade dann ist der Hinweis wichtig: Womöglich ist das Budget gar nicht überzogen, sondern
    // eine Gutschrift steht auf der falschen Seite.
    expectSafeToSpendRequest(httpMock).flush(NEGATIVE);
    expectUncertainRequest().flush([uncertainTransaction(1)]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.negative-banner')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.uncertain-banner')).not.toBeNull();
  });

  it('keeps the amount visible when the uncertainty count cannot be loaded', () => {
    // Ein ausgefallener Zusatzhinweis darf die Zahl daneben nicht verdrängen — und bekommt auch
    // keine eigene rote Meldung.
    expectSafeToSpendRequest(httpMock).flush(NORMAL);
    expectUncertainRequest().error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.uncertain-banner')).toBeNull();
    expect(fixture.nativeElement.textContent as string).toContain('500.00');
  });
});
