import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { SafeToSpendResponse } from '../dashboard/safe-to-spend.model';
import { IncomeCard } from './income-card';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
  firstName: null,
  lastName: null,
};

const LARA_NO_INCOME: User = { ...LARA, monthlyIncome: null };

const NO_INCOME_WITH_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: 3800,
  status: 'OPEN',
};

const NO_INCOME_WITHOUT_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: null,
  status: 'OPEN',
};

/** Loggt via `AuthService.login()` ein, damit `currentUser()` synchron befüllt ist. */
function loginAs(mock: HttpTestingController, user: User): void {
  TestBed.inject(AuthService).login(user.email, 'irrelevant').subscribe();
  mock.expectOne('/api/auth/login').flush(user);
}

describe('IncomeCard', () => {
  let fixture: ComponentFixture<IncomeCard>;
  let component: IncomeCard;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IncomeCard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    fixture = TestBed.createComponent(IncomeCard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  // --- Zwischenüberschrift ausserhalb der Card ---

  it('rendert die Zwischenüberschrift «Einkommen» ausserhalb der Card', () => {
    const root = fixture.nativeElement as HTMLElement;
    const heading = Array.from(root.querySelectorAll('h2')).find(
      (h) => h.textContent?.trim() === 'Einkommen',
    );
    expect(heading).toBeDefined();
    expect(heading!.closest('app-card')).toBeNull();
  });

  // --- AC2: Ist ein Einkommen erfasst, steht der aktuelle Wert im Feld ---

  it('belegt das Betragsfeld mit dem aktuellen Monatseinkommen vor', () => {
    expect(component.incomeForm.controls.betrag.value).toBe(3000);
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      '#monthlyIncome',
    ) as HTMLInputElement;
    expect(input.value).toBe('3000');
  });

  // --- AC1: Das Feld ist im UI als optional gekennzeichnet ---

  it('weist im Hinweistext darauf hin, dass das Betragsfeld optional ist', () => {
    const hint = (fixture.nativeElement as HTMLElement).querySelector('form .income-hint');
    expect(hint?.textContent).toContain('Optional');
  });

  // --- AC3: Kein Vorschlag, solange bereits ein Einkommen erfasst ist ---

  it('zeigt keine Vorschlags-Notice, wenn bereits ein Einkommen erfasst ist', () => {
    expect(component.incomeSuggestionText()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.income-suggestion')).toBeNull();
  });

  it('lädt den Einkommens-Vorschlag nicht, wenn bereits ein Einkommen erfasst ist', () => {
    httpMock.expectNone('/api/budget/safe-to-spend');
  });

  // --- AC1/AC4: Betrag ist optional, Beträge <= 0 werden clientseitig abgefangen ---

  it.each([0, -5, -0.01])('lehnt den Betrag %s ab und sendet nicht', (betrag) => {
    component.incomeForm.controls.betrag.setValue(betrag);

    component.submitIncome();

    httpMock.expectNone('/api/users/me/income');
    expect(component.incomeForm.controls.betrag.hasError('min')).toBe(true);
    expect(component.incomeError()).toBe('Betrag muss grösser als 0 sein.');
  });

  it('zeigt bei einem zu niedrigen Betrag mit zu vielen Nachkommastellen die Nachkommastellen-Meldung', () => {
    // 0.005 verletzt beide Regeln (< 0.01 und > 2 Nachkommastellen) — "muss grösser als 0 sein"
    // wäre hier irreführend, die eigentliche Verletzung ist die Nachkommastellen-Regel.
    component.incomeForm.controls.betrag.setValue(0.005);

    component.submitIncome();

    expect(component.incomeError()).toBe('Betrag darf höchstens zwei Nachkommastellen haben.');
  });

  it.each([10.999, 0.015, 3000.123])(
    'lehnt den Betrag %s mit mehr als zwei Nachkommastellen ab',
    (betrag) => {
      component.incomeForm.controls.betrag.setValue(betrag);

      component.submitIncome();

      httpMock.expectNone('/api/users/me/income');
      expect(component.incomeForm.controls.betrag.hasError('maxDecimals')).toBe(true);
      expect(component.incomeError()).toBe('Betrag darf höchstens zwei Nachkommastellen haben.');
    },
  );

  it('sendet keinen Request, wenn das Feld geleert wird — leer lassen ist erlaubt', () => {
    component.incomeForm.controls.betrag.setValue(null);

    component.submitIncome();

    httpMock.expectNone('/api/users/me/income');
    expect(component.incomeForm.valid).toBe(true);
  });

  // --- AC4: Speichern ruft PUT /users/me/income auf ---

  it('speichert einen gültigen Betrag und zeigt eine Bestätigung', () => {
    component.incomeForm.controls.betrag.setValue(3800);

    component.submitIncome();

    const req = httpMock.expectOne('/api/users/me/income');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ betrag: 3800 });
    req.flush({ ...LARA, monthlyIncome: 3800 });
    fixture.detectChanges();

    expect(component.incomeSaved()).toBe(true);
    expect(component.incomeSubmitting()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Einkommen gespeichert.');
  });

  it('meldet ein erfolgreiches Speichern über `saved`, ein fehlgeschlagenes nicht', () => {
    let emissions = 0;
    component.saved.subscribe(() => emissions++);
    component.incomeForm.controls.betrag.setValue(3800);

    component.submitIncome();
    httpMock
      .expectOne('/api/users/me/income')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    expect(emissions).toBe(0);

    component.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 3800 });
    expect(emissions).toBe(1);
  });

  it('zeigt die Fehlermeldung aus dem Backend bei einer 400-Antwort direkt an', () => {
    component.incomeForm.controls.betrag.setValue(100_000_000);

    component.submitIncome();

    httpMock
      .expectOne('/api/users/me/income')
      .flush(
        { field: 'betrag', message: "Betrag darf 99'999'999.99 nicht überschreiten." },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(component.incomeErrorMessage()).toBe("Betrag darf 99'999'999.99 nicht überschreiten.");
    expect(component.incomeSaved()).toBe(false);
  });

  it('zeigt eine generische Meldung bei einem Serverfehler', () => {
    component.incomeForm.controls.betrag.setValue(3800);

    component.submitIncome();

    httpMock
      .expectOne('/api/users/me/income')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.incomeErrorMessage()).toBe(
      'Einkommen konnte nicht gespeichert werden. Bitte versuche es später erneut.',
    );
  });

  it('räumt die alte Erfolgsmeldung weg, bevor der nächste Versuch läuft', () => {
    component.incomeForm.controls.betrag.setValue(3800);
    component.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 3800 });
    expect(component.incomeSaved()).toBe(true);

    component.incomeForm.controls.betrag.setValue(4000);
    component.submitIncome();

    expect(component.incomeSaved()).toBe(false);
    expect(component.incomeSubmitting()).toBe(true);
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 4000 });
  });
});

describe('IncomeCard ohne erfasstes Einkommen', () => {
  let fixture: ComponentFixture<IncomeCard>;
  let httpMock: HttpTestingController;

  async function createWith(response: SafeToSpendResponse): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [IncomeCard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA_NO_INCOME);

    fixture = TestBed.createComponent(IncomeCard);
    httpMock.expectOne('/api/budget/safe-to-spend').flush(response);
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('lässt das Betragsfeld leer, solange kein Einkommen erfasst ist', async () => {
    await createWith(NO_INCOME_WITHOUT_SUGGESTION);

    expect(fixture.componentInstance.incomeForm.controls.betrag.value).toBeNull();
  });

  it('zeigt keine Vorschlags-Notice, wenn die Heuristik nichts gefunden hat', async () => {
    await createWith(NO_INCOME_WITHOUT_SUGGESTION);

    expect((fixture.nativeElement as HTMLElement).querySelector('.income-suggestion')).toBeNull();
  });

  // --- AC3: Vorschlag erscheint am Feld ---

  it('zeigt den Vorschlagssatz mit dem erkannten Betrag', async () => {
    await createWith(NO_INCOME_WITH_SUGGESTION);

    expect(fixture.componentInstance.incomeSuggestionText()).toBe(
      "Regelmässige Gutschrift von 3'800.00 CHF erkannt — als Monatseinkommen übernehmen?",
    );
    const notice = (fixture.nativeElement as HTMLElement).querySelector('.income-suggestion');
    expect(notice?.textContent).toContain("3'800.00 CHF erkannt");
  });

  it('übernimmt den Vorschlag per Klick und speichert ihn sofort', async () => {
    await createWith(NO_INCOME_WITH_SUGGESTION);

    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((candidate) => candidate.textContent?.trim() === 'Übernehmen');
    expect(button).toBeDefined();

    button!.click();

    expect(fixture.componentInstance.incomeForm.controls.betrag.value).toBe(3800);
    const req = httpMock.expectOne('/api/users/me/income');
    expect(req.request.body).toEqual({ betrag: 3800 });
    req.flush({ ...LARA_NO_INCOME, monthlyIncome: 3800 });
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeSaved()).toBe(true);
  });
});
