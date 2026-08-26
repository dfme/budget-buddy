import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { App } from '../app';
import { routes } from '../app.routes';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { authGuard } from '../core/guards/auth.guard';
import { onboardingGuard } from '../core/guards/onboarding.guard';
import { Dashboard } from '../dashboard/dashboard';
import { SafeToSpendResponse } from '../dashboard/safe-to-spend.model';
import { Settings } from './settings';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
};

const LARA_NO_INCOME: User = { ...LARA, monthlyIncome: null };

/** Antwort von `GET /api/budget/safe-to-spend`, wenn bereits ein Einkommen erfasst ist. */
const WITH_INCOME: SafeToSpendResponse = {
  amount: 500,
  weeksLeft: 2,
  negative: false,
  noIncome: false,
  incomeSuggestion: null,
};

const NO_INCOME_WITH_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: 3800,
};

const NO_INCOME_WITHOUT_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: null,
};

/** Loggt via `AuthService.login()` ein, damit `currentUser()` synchron befüllt ist. */
function loginAs(httpMock: HttpTestingController, user: User): void {
  TestBed.inject(AuthService).login(user.email, 'irrelevant').subscribe();
  httpMock.expectOne('/api/auth/login').flush(user);
}

describe('Settings', () => {
  let fixture: ComponentFixture<Settings>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    fixture = TestBed.createComponent(Settings);
    // Der Constructor lädt den Vorschlag unabhängig vom aktuellen Einkommen (siehe
    // settings.ts) — bei erfasstem Einkommen liefert das Backend laut SafeToSpendResponse-Doku
    // ohnehin `incomeSuggestion: null`.
    httpMock.expectOne('/api/budget/safe-to-spend').flush(WITH_INCOME);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  // --- AC (FE-SET-01): Überschrift und drei Abschnitte als Cards ---

  it('rendert die Überschrift "Einstellungen"', () => {
    expect(fixture.nativeElement.querySelector('h1')?.textContent?.trim()).toBe('Einstellungen');
  });

  it('rendert die drei Abschnitte "Passwort", "Einkommen" und "Erscheinungsbild" als Cards', () => {
    const cardTitles = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-card .card__title'),
    ).map((el) => el.textContent?.trim());

    expect(cardTitles).toEqual(['Passwort', 'Einkommen', 'Erscheinungsbild']);
  });

  // --- AC2: Ist ein Einkommen erfasst, steht der aktuelle Wert im Feld ---

  it('belegt das Betragsfeld mit dem aktuellen Monatseinkommen vor', () => {
    expect(fixture.componentInstance.incomeForm.controls.betrag.value).toBe(3000);

    const input = (fixture.nativeElement as HTMLElement).querySelector(
      '#monthlyIncome',
    ) as HTMLInputElement;
    expect(input.value).toBe('3000');
  });

  // --- AC3: Kein Vorschlag, solange bereits ein Einkommen erfasst ist ---

  it('zeigt keine Vorschlags-Notice, wenn bereits ein Einkommen erfasst ist', () => {
    expect(fixture.componentInstance.incomeSuggestionText()).toBeNull();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.income-suggestion'),
    ).toBeNull();
  });

  // --- AC1/AC4: Betrag ist optional, Beträge <= 0 werden clientseitig abgefangen ---

  it.each([0, -5, -0.01])('lehnt den Betrag %s ab und sendet nicht', (betrag) => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(betrag);

    fixture.componentInstance.submitIncome();

    httpMock.expectNone('/api/users/me/income');
    expect(fixture.componentInstance.incomeForm.controls.betrag.hasError('min')).toBe(true);
    expect(fixture.componentInstance.incomeError()).toBe('Betrag muss grösser als 0 sein.');
  });

  it.each([10.999, 0.015, 3000.123])(
    'lehnt den Betrag %s mit mehr als zwei Nachkommastellen ab',
    (betrag) => {
      fixture.componentInstance.incomeForm.controls.betrag.setValue(betrag);

      fixture.componentInstance.submitIncome();

      httpMock.expectNone('/api/users/me/income');
      expect(fixture.componentInstance.incomeForm.controls.betrag.hasError('maxDecimals')).toBe(
        true,
      );
      expect(fixture.componentInstance.incomeError()).toBe(
        'Betrag darf höchstens zwei Nachkommastellen haben.',
      );
    },
  );

  it('sendet keinen Request, wenn das Feld geleert wird — leer lassen ist erlaubt', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(null);

    fixture.componentInstance.submitIncome();

    httpMock.expectNone('/api/users/me/income');
    expect(fixture.componentInstance.incomeForm.valid).toBe(true);
  });

  // --- AC4: Speichern ruft PUT /users/me/income auf ---

  it('speichert einen gültigen Betrag und zeigt eine Bestätigung', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(3800);

    fixture.componentInstance.submitIncome();

    const req = httpMock.expectOne('/api/users/me/income');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ betrag: 3800 });
    req.flush({ ...LARA, monthlyIncome: 3800 });
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeSaved()).toBe(true);
    expect(fixture.componentInstance.incomeSubmitting()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Einkommen gespeichert.');
  });

  it('zeigt die Fehlermeldung aus dem Backend bei einer 400-Antwort direkt an', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(100_000_000);

    fixture.componentInstance.submitIncome();

    httpMock
      .expectOne('/api/users/me/income')
      .flush(
        { field: 'betrag', message: 'Betrag darf 99\'999\'999.99 nicht überschreiten.' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeErrorMessage()).toBe(
      "Betrag darf 99'999'999.99 nicht überschreiten.",
    );
    expect(fixture.componentInstance.incomeSaved()).toBe(false);
  });

  it('zeigt eine generische Meldung bei einem Serverfehler', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(3800);

    fixture.componentInstance.submitIncome();

    httpMock
      .expectOne('/api/users/me/income')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeErrorMessage()).toBe(
      'Einkommen konnte nicht gespeichert werden. Bitte versuche es später erneut.',
    );
  });

  it('räumt die alte Erfolgsmeldung weg, bevor der nächste Versuch läuft', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(3800);
    fixture.componentInstance.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 3800 });
    expect(fixture.componentInstance.incomeSaved()).toBe(true);

    fixture.componentInstance.incomeForm.controls.betrag.setValue(4000);
    fixture.componentInstance.submitIncome();

    expect(fixture.componentInstance.incomeSaved()).toBe(false);
    expect(fixture.componentInstance.incomeSubmitting()).toBe(true);
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 4000 });
  });
});

describe('Einkommen-Formular ohne erfasstes Einkommen', () => {
  let fixture: ComponentFixture<Settings>;
  let httpMock: HttpTestingController;

  async function createWith(response: SafeToSpendResponse): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA_NO_INCOME);

    fixture = TestBed.createComponent(Settings);
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

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.income-suggestion'),
    ).toBeNull();
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

describe('Route /einstellungen', () => {
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
  async function answerProfile(user: User | null): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    const req = httpMock.expectOne('/api/users/me');
    if (user === null) {
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    } else {
      req.flush(user);
    }
  }

  // --- AC4: Anonymer Aufruf landet auf /login ---

  it('leitet einen anonymen Aufruf von /einstellungen auf /login um', async () => {
    const navigation = router.navigateByUrl('/einstellungen');
    await answerProfile(null);
    await navigation;

    expect(router.url).toBe('/login');
  });

  // --- AC5: Route ist erreichbar ---

  it('erreicht /einstellungen als eingeloggter, onboardeter User', async () => {
    // Ohne gerendertes RouterOutlet in diesem Test wird die Ziel-Komponente nicht
    // instanziiert — der Guard-Erfolg (Navigation landet auf /einstellungen) ist unabhängig
    // davon, dass Settings selbst noch GET /api/budget/safe-to-spend nachlädt (siehe AC5-Test
    // unten, der App als Root-Fixture nutzt).
    const navigation = router.navigateByUrl('/einstellungen');
    await answerProfile(LARA);
    await navigation;

    expect(router.url).toBe('/einstellungen');
  });

  it('hängt authGuard und onboardingGuard an /einstellungen wie bei den übrigen geschützten Routes', () => {
    const route = routes.find((candidate) => candidate.path === 'einstellungen');
    expect(route, "Route 'einstellungen' fehlt").toBeDefined();
    expect(route!.canActivate ?? []).toEqual([authGuard, onboardingGuard]);
  });

  // --- AC5: Dashboard zeigt den neuen Betrag ohne Reload der App ---

  it('zeigt auf dem Dashboard den neuen Safe-to-Spend-Betrag, nachdem das Einkommen in den Einstellungen gespeichert wurde — ohne Reload', async () => {
    const root = TestBed.createComponent(App);
    root.detectChanges();

    const firstNavigation = router.navigateByUrl('/einstellungen');
    await answerProfile(LARA_NO_INCOME);
    await firstNavigation;
    httpMock.expectOne('/api/budget/safe-to-spend').flush(NO_INCOME_WITHOUT_SUGGESTION);
    root.detectChanges();

    const settings = root.debugElement.query(By.directive(Settings)).componentInstance as Settings;
    settings.incomeForm.controls.betrag.setValue(3800);
    settings.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA_NO_INCOME, monthlyIncome: 3800 });
    root.detectChanges();

    // Reine SPA-Navigation — kein `location.reload()`, kein neuer `App`-Fixture: dieselbe
    // Instanz von AuthService/HttpClient wie beim Speichern eben.
    const secondNavigation = router.navigateByUrl('/dashboard');
    await secondNavigation;
    const req = httpMock.expectOne('/api/budget/safe-to-spend');
    req.flush({ amount: 650, weeksLeft: 2, negative: false, noIncome: false, incomeSuggestion: null });
    root.detectChanges();

    const dashboard = root.debugElement.query(By.directive(Dashboard)).componentInstance as Dashboard;
    expect(dashboard.data()?.amount).toBe(650);
    expect((root.nativeElement as HTMLElement).textContent).not.toContain(
      'Kein Betrag verfügbar',
    );
  });
});
