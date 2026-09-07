import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

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
 * Damit bleibt der Default der laufende Monat, und die Fälle, die sich nicht um den
 * Monatswechsel drehen, verhalten sich wie vor FE-STS-04.
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
    // FE-STS-04: Beim Aufbau wird die Monatsliste geladen; ohne brauchbaren Query-Parameter hängt
    // der Default-Monat an ihr. Einmal zentral beantwortet, damit die Fälle darunter sich nicht
    // damit befassen müssen — wer den Aufbau selbst prüft, baut über `recreate()` neu auf.
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
      fixture.destroy();
      await TestBed.inject(Router).navigate([], { queryParams });
      fixture = TestBed.createComponent(Dashboard);
      return httpMock.expectOne('/api/transactions/months');
    }

    it('sends the displayed month with the request', () => {
      const req = expectSafeToSpendRequest(httpMock);

      expect(req.request.params.get('month')).toBe(CURRENT_MONTH);
    });

    // AC 1, in der Lesart von US-12: der aktuellste Monat *mit Daten*, nicht der Kalendermonat.
    it('starts in the newest month with data when the URL carries none', async () => {
      const months = await recreate();
      months.flush([PREVIOUS_MONTH, OLDER_MONTH]);

      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(PREVIOUS_MONTH);
      req.flush(CLOSED);
    });

    // Der Deep-Link ist die einzige Auskunft, die der Server nicht liefern muss — er darf deshalb
    // nicht auf die Monatsliste warten.
    it('starts in the month from the URL without waiting for the month list', async () => {
      const months = await recreate({ month: OLDER_MONTH });

      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(OLDER_MONTH);
      req.flush(CLOSED);

      // Die Liste trifft erst danach ein und löst keinen zweiten Request aus — httpMock.verify()
      // im afterEach fängt ihn, falls doch.
      months.flush(AVAILABLE_MONTHS);
      fixture.detectChanges();
    });

    it('falls back to the newest month with data when the URL carries nonsense, and rewrites it', async () => {
      const months = await recreate({ month: '2026-13' });
      months.flush([PREVIOUS_MONTH, OLDER_MONTH]);

      const req = expectSafeToSpendRequest(httpMock);
      expect(req.request.params.get('month')).toBe(PREVIOUS_MONTH);
      req.flush(CLOSED);
      fixture.detectChanges();
      await fixture.whenStable();

      // Eine URL, die etwas anderes behauptet als die Seite, bleibt nicht stehen.
      expect(TestBed.inject(Location).path()).toContain(`month=${PREVIOUS_MONTH}`);
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
      expect(fixture.debugElement.query(By.css('app-amount'))).toBeNull();
      expect(fixture.nativeElement.querySelector('app-card')).toBeNull();
      // Und es wird auch nichts nachgeladen, was ohne Berechnung nichts aussagt: Der Hinweis zur
      // Buchungsrichtung begründet sich allein über den Betrag daneben.
      expect(httpMock.match((req) => req.url === '/api/transactions/uncertain')).toHaveLength(0);
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
});
