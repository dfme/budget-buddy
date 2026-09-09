import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { formatMonth } from '../shared/month';

import { Dashboard } from './dashboard';
import { SafeToSpendResponse } from './safe-to-spend.model';

/** Laufender Monat als `YYYY-MM` — dieselbe Ableitung wie in der Komponente (Ortszeit). */
function monthString(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** `delta` Monate vom laufenden Monat aus. */
function relativeMonth(delta: number): string {
  const now = new Date();
  return monthString(new Date(now.getFullYear(), now.getMonth() + delta, 1));
}

const CURRENT_MONTH = relativeMonth(0);
const PREVIOUS_MONTH = relativeMonth(-1);
const OLDER_MONTH = relativeMonth(-2);

/**
 * Antwort von `GET /api/transactions/months` im Normalfall: der laufende Monat hat Daten.
 * Damit bleibt der Keine-Daten-Hinweis weg, und die Fälle, die sich nicht um den Monatswechsel
 * drehen, verhalten sich wie vor FE-STS-04.
 */
const AVAILABLE_MONTHS = [CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH];

/** Vergangener Monat: das Backend rechnet nicht, es meldet «abgeschlossen» (BE-STS-06). */
const CLOSED: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 0,
  negative: false,
  noIncome: false,
  incomeSuggestion: null,
  status: 'CLOSED',
};

const NORMAL: SafeToSpendResponse = {
  amount: 500,
  weeksLeft: 2,
  negative: false,
  noIncome: false,
  incomeSuggestion: null,
  status: 'OPEN',
};

const SINGLE_WEEK: SafeToSpendResponse = {
  amount: 120,
  weeksLeft: 1,
  negative: false,
  noIncome: false,
  incomeSuggestion: null,
  status: 'OPEN',
};

/** Budget überzogen — `negative` ist gesetzt, `amount` entsprechend kleiner 0 (BE-STS-03). */
const NEGATIVE: SafeToSpendResponse = {
  amount: -120,
  weeksLeft: 2,
  negative: true,
  noIncome: false,
  incomeSuggestion: null,
  status: 'OPEN',
};

const NO_INCOME: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: 3800,
  status: 'OPEN',
};

/** Kein Einkommen und keine wiederkehrende Gutschrift gefunden (BE-STS-02 liefert dann null). */
const NO_INCOME_WITHOUT_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: null,
  status: 'OPEN',
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
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Catch-all ohne Component: Die Komponente wird hier von Hand erzeugt, nicht vom Router
        // gerendert. Gebraucht wird er trotzdem, weil goTo() relativ zur aktuellen Route
        // navigiert — ohne passende Route bräche die Navigation ab und der Monat käme nie in die
        // URL. provideLocationMocks() hält das im Speicher statt in der echten History.
        provideRouter([{ path: '**', children: [] }]),
        provideLocationMocks(),
      ],
    }).compileComponents();

    // Ohne initialNavigation() setzt der Router seinen Location-Listener nie auf — er wird hier
    // ja nicht über ein Bootstrap gestartet.
    TestBed.inject(Router).initialNavigation();

    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
    // FE-STS-04: Beim Aufbau wird daneben die Monatsliste geladen — sie speist Dropdown und
    // Keine-Daten-Hinweis. Einmal zentral beantwortet, damit die Fälle darunter sich nicht damit
    // befassen müssen; wer den Aufbau selbst prüft, baut über `recreate()` neu auf.
    httpMock.expectOne('/api/transactions/months').flush(AVAILABLE_MONTHS);
  });

  afterEach(() => {
    // BE-PDF-10: Beim Aufbau wird zusätzlich die Zahl der ungeprüften Buchungsrichtungen geladen.
    // Die Fälle unten befassen sich nicht damit und bekommen sie hier zentral mit einer leeren
    // Liste beantwortet; wer den Hinweis selbst prüft, holt den Request vorher ab.
    httpMock
      .match((req) => req.url === '/api/transactions/uncertain')
      .filter((req) => !req.cancelled)
      .forEach((req) => req.flush([]));
    // FE-STS-04: Dasselbe für die Drei-Monats-Übersicht — sie lädt bei jedem Monatswechsel mit.
    // `!req.cancelled`, weil ein Monatswechsel den vorherigen Request abbricht und ein
    // abgebrochener sich nicht mehr beantworten lässt.
    httpMock
      .match((req) => req.url === '/api/transactions/monthly-totals')
      .filter((req) => !req.cancelled)
      .forEach((req) => req.flush([]));
    httpMock.verify();
  });

  /** URL-Matcher für die Drei-Monats-Übersicht (BE-STS-07). */
  function expectTotalsRequest() {
    return httpMock.expectOne((req) => req.url === '/api/transactions/monthly-totals');
  }

  /**
   * Ein Übersichts-Fenster: gewählter Monat und die zwei davor, neuester zuerst — so, wie der
   * Endpoint es liefert. Der älteste Monat trägt bewusst `null`s (Monat ohne jede Buchung).
   */
  function totalsWindow(newest: string, older: string, oldest: string) {
    return [
      { month: newest, income: 5000, expenses: 100, difference: 4900 },
      { month: older, income: 800, expenses: 1250.5, difference: -450.5 },
      { month: oldest, income: null, expenses: null, difference: null },
    ];
  }

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
    // FE-STS-04: Der Text nennt den Monat, statt «dieses Monats» zu sagen — sonst wäre beim
    // Blättern nicht erkennbar, welchem Monat der Hinweis gilt.
    expect(banner.textContent).toContain(`im ${formatMonth(CURRENT_MONTH)}`);
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
  describe('Monatswechsel (FE-STS-04, US-12)', () => {
    /** Die Pfeil-Buttons der MonthNav: [0] zurück, [1] vor. */
    function arrows(): HTMLButtonElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.month-nav__btn'));
    }

    /** Das Direktsprung-Dropdown. */
    function jump(): HTMLSelectElement {
      return fixture.nativeElement.querySelector('.month-nav__jump select');
    }

    /**
     * Räumt die Instanz aus dem `beforeEach` ab und baut eine neue auf — für die Fälle, die den
     * Zustand *vor* dem Aufbau brauchen (Deep-Link, kaputter Parameter, fehlgeschlagene Liste).
     *
     * <p>Die Reihenfolge ist wesentlich: erst die offenen Requests der alten Instanz beantworten,
     * dann zerstören, dann navigieren. Wer vor dem Zerstören navigiert, weckt die alte
     * Subscription und bekommt zwei Safe-to-Spend-Requests.
     *
     * @returns den offenen Monatslisten-Request der neuen Instanz — die Fälle unten bestimmen
     *     selbst, wann und womit er beantwortet wird.
     */
    async function recreate(queryParams: Record<string, string> = {}) {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      httpMock
        .match((req) => req.url === '/api/transactions/uncertain')
        .filter((req) => !req.cancelled)
        .forEach((req) => req.flush([]));
      // Wie die Prüfliste: die Requests der noch stehenden Instanz abräumen, damit die Fälle
      // unten nur die des neu aufgebauten Dashboards vor sich haben.
      httpMock
        .match((req) => req.url === '/api/transactions/monthly-totals')
        .filter((req) => !req.cancelled)
        .forEach((req) => req.flush([]));
      fixture.destroy();
      await TestBed.inject(Router).navigate([], { queryParams });
      fixture = TestBed.createComponent(Dashboard);
      return httpMock.expectOne('/api/transactions/months');
    }

    it('sends the displayed month with the request', () => {
      const req = expectSafeToSpendRequest(httpMock);

      expect(req.request.params.get('month')).toBe(CURRENT_MONTH);
    });

    // AC 1: der laufende Monat, wie in der Kategorie-Übersicht — und zwar auch dann, wenn er
    // noch keine Buchungen trägt. Der Default aus der Monatsliste stellte die beiden
    // Schwesteransichten am selben Tag auf verschiedene Monate, und weil Kontoauszüge erst nach
    // Monatsende kommen, wäre das der Regelfall: Die Startseite zeigte statt der Kernzahl das
    // «Abgeschlossen»-Banner des Vormonats.
    it('starts in the current month when the URL carries none, even without data for it', async () => {
      const months = await recreate();

      // Ohne auf die Liste zu warten — der Default hängt nicht an ihr.
      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(CURRENT_MONTH);
      req.flush(NORMAL);
      months.flush([PREVIOUS_MONTH, OLDER_MONTH]);
      fixture.detectChanges();

      // Geprüft wird das Ergebnis, nicht bloss der Request-Parameter: Der Nutzer bekommt die
      // Zahl zu sehen, für die er die App geöffnet hat, und keine Sackgasse.
      expect(fixture.nativeElement.querySelector('.closed-banner')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-card')).not.toBeNull();
      expect(fixture.nativeElement.textContent as string).toContain('500.00');
      // Der laufende Monat fehlt in der Liste — der Hinweis steht, ohne die Karte zu verdrängen.
      expect(fixture.nativeElement.querySelector('.status.empty')).not.toBeNull();
    });

    // Der Deep-Link gewinnt gegen den Default und wartet auf nichts.
    it('starts in the month from the URL instead of the current month', async () => {
      const months = await recreate({ month: OLDER_MONTH });

      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(OLDER_MONTH);
      req.flush(CLOSED);

      // Die Liste trifft erst danach ein und löst keinen zweiten Request aus — httpMock.verify()
      // im afterEach fängt ihn, falls doch.
      months.flush(AVAILABLE_MONTHS);
      fixture.detectChanges();
    });

    it('falls back to the current month when the URL carries nonsense, and rewrites it', async () => {
      const months = await recreate({ month: '2026-13' });
      months.flush(AVAILABLE_MONTHS);

      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(CURRENT_MONTH);
      req.flush(NORMAL);
      fixture.detectChanges();
      await fixture.whenStable();

      // Eine URL, die etwas anderes behauptet als die Seite, bleibt nicht stehen.
      expect(TestBed.inject(Location).path()).toContain(`month=${CURRENT_MONTH}`);
    });

    it('steps to the previous month with a single request and writes it into the URL', async () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      fixture.detectChanges();

      arrows()[0].click();
      fixture.detectChanges();

      // expectOne wirft, sobald mehr als ein Request offen ist.
      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(PREVIOUS_MONTH);
      req.flush(CLOSED);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(TestBed.inject(Location).path()).toContain(`month=${PREVIOUS_MONTH}`);
    });

    it('jumps two months back from the dropdown with a single request', () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      fixture.detectChanges();

      const select = jump();
      select.value = OLDER_MONTH;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(OLDER_MONTH);
      req.flush(CLOSED);
    });

    // Das Backend beantwortet einen Zukunftsmonat mit 400; die Oberfläche bietet ihn deshalb gar
    // nicht erst an — in keinem der beiden Bedienelemente.
    it('offers no future month, in neither control', () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      fixture.detectChanges();

      expect(arrows()[1].disabled).toBe(true);
      const values = Array.from(jump().options).map((option) => option.value);
      expect(values.every((value) => value <= CURRENT_MONTH)).toBe(true);
    });

    // AC 3: für vergangene Monate steht «Abgeschlossen» statt einer Berechnung.
    it('shows the closed banner instead of a calculation for a past month', async () => {
      const months = await recreate({ month: PREVIOUS_MONTH });
      months.flush(AVAILABLE_MONTHS);
      expectSafeToSpendRequest(httpMock).flush(CLOSED);
      fixture.detectChanges();

      const banner = fixture.nativeElement.querySelector('.closed-banner');
      expect(banner.querySelector('.notice__title').textContent.trim()).toBe('Abgeschlossen');
      // Kein Betrag und keine Safe-to-Spend-Card. Auf `.safe-to-spend-card` statt auf `app-card`
      // geprüft: die Übersicht darunter ist seit der Drei-Monats-Übersicht ebenfalls eine Card
      // und steht hier zu Recht — sie gilt nicht dem Betrag, sondern den Monaten.
      expect(fixture.debugElement.query(By.css('.safe-to-spend__amount'))).toBeNull();
      expect(fixture.nativeElement.querySelector('.safe-to-spend-card')).toBeNull();
      // Die Übersicht wird auch für einen abgeschlossenen Monat geladen — sie ist genau der
      // Grund, warum man in einen vergangenen Monat blättert.
      expect(httpMock.match((req) => req.url === '/api/transactions/monthly-totals')).toHaveLength(
        1,
      );
    });

    /**
     * Gegenstück zum vorherigen Fall und eine bewusste Abweichung vom ersten Anlauf (PR #280):
     * dort unterblieb der Prüflisten-Request für einen vergangenen Monat, weil es dort keine
     * Zahl gab, die eine falsch übernommene Richtung verzerrt hätte. Mit der Drei-Monats-
     * Übersicht gibt es sie — eine als Belastung importierte Gutschrift drückt dort `income` und
     * hebt `expenses` (BE-STS-07). Der AC verlangt entsprechend, dass der Hinweis dem
     * **gewählten** Monat gilt.
     */
    it('loads the uncertain-direction count for a past month too, and says what it affects there', async () => {
      const months = await recreate({ month: PREVIOUS_MONTH });
      months.flush(AVAILABLE_MONTHS);
      expectSafeToSpendRequest(httpMock).flush(CLOSED);
      expectUncertainRequest().flush([uncertainTransaction(1), uncertainTransaction(2)]);
      fixture.detectChanges();

      const message = fixture.nativeElement.querySelector('.uncertain-banner').textContent;
      // Nennt den Monat statt «dieses Monats» …
      expect(message).toContain(`Bei 2 Buchungen im ${formatMonth(PREVIOUS_MONTH)}`);
      // … und die Folge, die für einen abgeschlossenen Monat zutrifft: dort gibt es keinen
      // Safe-to-Spend, der zu tief sein könnte.
      expect(message).toContain('Die Einnahmen und Ausgaben in der Übersicht');
      expect(message).not.toContain('Safe-to-Spend kann deshalb zu tief sein');
    });

    // AC 4, laufender Monat: Der Hinweis tritt neben den Betrag, er verdrängt ihn nicht — ohne
    // Buchungen ist er aus Einkommen minus Fixkosten weiterhin gültig.
    it('adds the no-data hint above the card without hiding a valid amount', async () => {
      const months = await recreate({ month: CURRENT_MONTH });
      months.flush([PREVIOUS_MONTH]);
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      fixture.detectChanges();

      const hint = fixture.nativeElement.querySelector('.status.empty');
      expect(hint.textContent).toContain('Keine Daten für');
      expect(hint.querySelector('a').getAttribute('href')).toBe('/import');

      const card = fixture.nativeElement.querySelector('app-card');
      expect(card).not.toBeNull();
      expect(hint.compareDocumentPosition(card)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    });

    // AC 4, vergangener Monat: «Abgeschlossen» sagt dort nichts, woran der Nutzer etwas ändern
    // könnte — der Hinweis mit dem Weg zum Import schon.
    it('replaces the closed banner with the no-data hint for a past month without data', async () => {
      const months = await recreate({ month: OLDER_MONTH });
      months.flush([CURRENT_MONTH]);
      expectSafeToSpendRequest(httpMock).flush(CLOSED);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.status.empty')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.closed-banner')).toBeNull();
    });

    // Die Antwort des verlassenen Monats darf die des neuen nicht überschreiben. Sichtbar wäre
    // das nicht bloss als falsche Zahl: Die Card trägt ihr Monatslabel als `meta`, der Betrag
    // stünde also unter der Überschrift eines anderen Monats.
    it('discards a stale safe-to-spend response when the month changed meanwhile', async () => {
      const stale = expectSafeToSpendRequest(httpMock);
      expectUncertainRequest().flush([]);
      fixture.detectChanges();

      arrows()[0].click();
      fixture.detectChanges();

      expect(stale.cancelled).toBe(true);
      expectSafeToSpendRequest(httpMock).flush(CLOSED);
      fixture.detectChanges();
      await fixture.whenStable();

      // Das Banner des Vormonats bleibt stehen, und es erscheint kein Betrag daneben.
      expect(fixture.nativeElement.querySelector('.closed-banner')).not.toBeNull();
      expect(fixture.debugElement.query(By.css('app-amount'))).toBeNull();
    });

    // Dieselbe Ursache, eigene Fixstelle: Der Prüflisten-Request läuft seit der Drei-Monats-
    // Übersicht auch für einen vergangenen Monat (der Hinweis gilt dem gewählten Monat), also
    // gibt es hier zwei Antworten, die sich überschreiben könnten. Die alte wird gecancelt,
    // damit nicht der Zähler des laufenden Monats über der Anzeige des vorherigen steht.
    it('discards a stale uncertainty count when stepping into a past month', async () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      const stale = expectUncertainRequest();
      fixture.detectChanges();

      arrows()[0].click();
      fixture.detectChanges();

      expect(stale.cancelled).toBe(true);
      expectSafeToSpendRequest(httpMock).flush(CLOSED);
      // Die späte Antwort des laufenden Monats trägt drei unsichere Buchungen. Sie darf den
      // Hinweis des jetzt angezeigten Vormonats nicht setzen — der Request ist gecancelt, sein
      // `flush` läuft ins Leere.
      expect(() =>
        stale.flush([uncertainTransaction(1), uncertainTransaction(2), uncertainTransaction(3)]),
      ).toThrow();
      // Der neue Request gilt dem Vormonat und ist noch offen; ohne Antwort steht kein Hinweis.
      const fresh = expectUncertainRequest();
      expect(fresh.request.params.get('month')).toBe(PREVIOUS_MONTH);
      fresh.flush([]);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(fixture.nativeElement.querySelector('.uncertain-banner')).toBeNull();
      expect(fixture.nativeElement.querySelector('.closed-banner')).not.toBeNull();
    });

    it('claims nothing about missing data when the month list cannot be loaded', async () => {
      const months = await recreate();
      months.error(new ProgressEvent('error'));

      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(CURRENT_MONTH);
      req.flush(NORMAL);
      expectUncertainRequest().flush([]);
      fixture.detectChanges();

      // Kein Hinweis: Die Seite weiss nicht, ob es Daten gibt — und behauptet deshalb nichts.
      expect(fixture.nativeElement.querySelector('.status.empty')).toBeNull();
      expect(fixture.nativeElement.textContent as string).toContain('500.00');
    });
  });

  describe('Drei-Monats-Übersicht (FE-STS-04, US-12)', () => {
    /** Bringt die Seite in den Normalzustand und beantwortet die Übersicht mit `rows`. */
    function loadWith(rows: unknown[]) {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      expectTotalsRequest().flush(rows);
      fixture.detectChanges();
    }

    /** Die Datenzeilen der Übersicht. */
    function rows(): HTMLTableRowElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.totals tbody tr'));
    }

    /** Die vier Zellen einer Zeile: Monat, Einnahmen, Ausgaben, Differenz. */
    function cells(row: HTMLTableRowElement): HTMLElement[] {
      return Array.from(row.querySelectorAll('th, td'));
    }

    it('requests the window ending at the displayed month', () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);

      const req = expectTotalsRequest();
      // Der gewählte Monat ist der jüngste des Fensters; die drei Monate enden bei ihm und
      // wandern beim Blättern mit — nicht fix «die letzten drei ab heute».
      expect(req.request.params.get('month')).toBe(CURRENT_MONTH);
      expect(req.request.params.get('months')).toBe('3');
      req.flush([]);
    });

    it('moves the window along when the month changes', () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      expectTotalsRequest().flush(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));
      fixture.detectChanges();

      const back: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('.month-nav__btn'),
      );
      back[0].click();
      fixture.detectChanges();

      expectSafeToSpendRequest(httpMock).flush(CLOSED);
      expect(expectTotalsRequest().request.params.get('month')).toBe(PREVIOUS_MONTH);
    });

    it('renders one row per month, newest first, as delivered', () => {
      loadWith(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));

      expect(rows()).toHaveLength(3);
      // Reihenfolge unverändert vom Backend — hier wird nicht sortiert.
      expect(cells(rows()[0])[0].textContent).toContain(formatMonth(CURRENT_MONTH));
      expect(cells(rows()[1])[0].textContent).toContain(formatMonth(PREVIOUS_MONTH));
      expect(cells(rows()[2])[0].textContent).toContain(formatMonth(OLDER_MONTH));
    });

    it('shows income and expenses through app-amount, without a "+"', () => {
      loadWith(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));

      const first = fixture.debugElement
        .queryAll(By.css('.totals tbody tr'))[0]
        .queryAll(By.css('app-amount'));
      // Einnahmen und Ausgaben sind Beträge, keine Veränderungen — ein «+» wäre Rauschen.
      expect(first[0].componentInstance.value()).toBe(5000);
      expect(first[0].componentInstance.hidePositiveSign()).toBe(true);
      expect(first[1].componentInstance.value()).toBe(100);
      expect(first[1].componentInstance.hidePositiveSign()).toBe(true);
      // Kein eigenes Format, keine Zahl aus dem Template: die Beträge gehen durch app-amount.
      expect(fixture.nativeElement.querySelector('.totals').textContent).toContain("5'000.00");
    });

    it('marks a negative difference by sign and colour, not by colour alone', () => {
      loadWith(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));

      const difference = fixture.debugElement
        .queryAll(By.css('.totals tbody tr'))[1]
        .queryAll(By.css('app-amount'))[2];

      expect(difference.componentInstance.value()).toBe(-450.5);
      // Ohne hidePositiveSign: das Vorzeichen steht sichtbar da …
      expect(difference.componentInstance.hidePositiveSign()).toBe(false);
      expect(difference.componentInstance.sign()).toBe('−');
      // … die Farbe kommt zusätzlich, und das aria-label nennt die Richtung ebenfalls.
      expect(difference.nativeElement.classList).toContain('amount--negative');
      expect(difference.nativeElement.getAttribute('aria-label')).toContain('minus');
    });

    it('shows a positive difference with a "+"', () => {
      loadWith(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));

      const difference = fixture.debugElement
        .queryAll(By.css('.totals tbody tr'))[0]
        .queryAll(By.css('app-amount'))[2];

      expect(difference.componentInstance.value()).toBe(4900);
      expect(difference.componentInstance.sign()).toBe('+');
    });

    it('shows a dash instead of 0.00 for a month without any bookings', () => {
      loadWith(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));

      const empty = rows()[2];
      // Eine Null behauptete erfasste Nullbeträge. Genau dafür kann das Backend null liefern.
      expect(cells(empty)[1].textContent).toContain('–');
      expect(cells(empty)[1].textContent).not.toContain('0.00');
      expect(cells(empty)[2].textContent).toContain('–');
      expect(cells(empty)[3].textContent).toContain('–');
      // Kein app-amount in dieser Zeile — dessen `value` ist `number`, nicht `number | null`.
      expect(
        fixture.debugElement.queryAll(By.css('.totals tbody tr'))[2].queryAll(By.css('app-amount')),
      ).toHaveLength(0);
      // Und für Screenreader steht es nicht nur als Strich da.
      expect(cells(empty)[1].querySelector('.visually-hidden')?.textContent).toBe('Keine Daten');
    });

    it('keeps zero expenses as 0.00 when the month does have bookings', () => {
      // Die Gegenprobe zum Strich: hier GIBT es Buchungen, die Summe der Belastungen ist wirklich
      // null — das ist von «keine Daten» unterscheidbar und muss es bleiben.
      loadWith([{ month: CURRENT_MONTH, income: 5000, expenses: 0, difference: 5000 }]);

      const cell = cells(rows()[0])[2];
      expect(cell.textContent).toContain('0.00');
      expect(cell.textContent).not.toContain('–');
    });

    it('highlights the selected month', () => {
      loadWith(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));

      expect(rows()[0].classList).toContain('totals__row--selected');
      expect(rows()[1].classList).not.toContain('totals__row--selected');
      // Die Hervorhebung ist nicht rein visuell — Farbe allein sagt einem Screenreader nichts.
      expect(cells(rows()[0])[0].querySelector('.visually-hidden')?.textContent).toContain(
        'gewählter Monat',
      );
    });

    it('keeps the safe-to-spend block loaded and usable when the overview fails', () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      expectTotalsRequest().error(new ProgressEvent('error'));
      fixture.detectChanges();

      // Ausdrücklicher AC: die Übersicht reisst nicht die ganze Seite in den Fehlerzustand.
      expect(fixture.nativeElement.querySelector('.safe-to-spend-card')).not.toBeNull();
      expect(fixture.nativeElement.textContent as string).toContain('500.00');
      // Der Monatswechsel bleibt bedienbar — er ist der Weg aus dem Fehler heraus.
      expect(fixture.nativeElement.querySelectorAll('.month-nav__btn')).toHaveLength(2);
      // Die Übersicht trägt ihre eigene Meldung, statt einfach leer zu bleiben: leer wäre von
      // «keine Daten» nicht zu unterscheiden.
      expect(fixture.nativeElement.querySelector('.totals-card').textContent).toContain(
        'Die Monatsübersicht konnte nicht geladen werden.',
      );
      expect(fixture.nativeElement.querySelector('.totals')).toBeNull();
    });

    it('keeps the overview when the safe-to-spend request fails', () => {
      // Die Gegenrichtung: die beiden Blöcke laden unabhängig, und die Übersicht trägt eine
      // Aussage, die ohne die Kernzahl weiterhin gilt.
      expectSafeToSpendRequest(httpMock).error(new ProgressEvent('error'));
      expectUncertainRequest().flush([]);
      expectTotalsRequest().flush(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));
      fixture.detectChanges();

      expect(rows()).toHaveLength(3);
      expect(fixture.nativeElement.querySelector('.safe-to-spend-card')).toBeNull();
    });

    it('renders the table inside its own horizontal scroll container', () => {
      // FE-CAT-06: auf schmalen Schirmen scrollt die Tabelle in der Card, statt die Seite
      // seitlich rauslaufen zu lassen.
      loadWith(totalsWindow(CURRENT_MONTH, PREVIOUS_MONTH, OLDER_MONTH));

      const scroll = fixture.nativeElement.querySelector('.totals-card .table-scroll');
      expect(scroll).not.toBeNull();
      expect(scroll.querySelector('table.totals')).not.toBeNull();
    });

    it('discards a stale overview response when the month changed meanwhile', () => {
      expectSafeToSpendRequest(httpMock).flush(NORMAL);
      expectUncertainRequest().flush([]);
      const stale = expectTotalsRequest();
      fixture.detectChanges();

      const back: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('.month-nav__btn'),
      );
      back[0].click();
      fixture.detectChanges();

      // Sonst stünde das Fenster des laufenden Monats unter der Navigation des vorherigen.
      expect(stale.cancelled).toBe(true);
      expectSafeToSpendRequest(httpMock).flush(CLOSED);
      expectTotalsRequest().flush(totalsWindow(PREVIOUS_MONTH, OLDER_MONTH, relativeMonth(-3)));
      fixture.detectChanges();

      expect(cells(rows()[0])[0].textContent).toContain(formatMonth(PREVIOUS_MONTH));
    });
  });
});
