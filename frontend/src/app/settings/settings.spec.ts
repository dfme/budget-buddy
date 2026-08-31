import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import {
  installMatchMedia,
  restoreMatchMedia,
  setSystemDark,
} from '../../testing/prefers-color-scheme';
import { App } from '../app';
import { routes } from '../app.routes';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { authGuard } from '../core/guards/auth.guard';
import { onboardingGuard } from '../core/guards/onboarding.guard';
import { THEME_STORAGE_KEY, Theme } from '../core/theme/theme';
import { Dashboard } from '../dashboard/dashboard';
import { SafeToSpendResponse } from '../dashboard/safe-to-spend.model';
import { Settings } from './settings';

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
  let component: Settings;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    installMatchMedia(false);

    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    // Der Constructor überspringt den Vorschlags-Call für LARA (siehe settings.ts): sie hat
    // bereits ein Einkommen, das Backend liefert für diesen Fall laut SafeToSpendResponse-Doku
    // ohnehin immer `incomeSuggestion: null`. `afterEach`s `httpMock.verify()` deckt einen
    // ungewollten Call auf, falls die Optimierung doch einmal bricht.
    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // Der Screen hat keinen Ladepfad — die Theme-Wahl liegt client-only im localStorage
  // (US-14, Scope-Entscheid). `verify()` deckt auf, falls doch einmal ein Request
  // abgesetzt würde, statt das nur an einem Textinhalt zu vermuten.
  afterEach(() => {
    httpMock.verify();
    restoreMatchMedia();
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  /** Die drei Buttons des Erscheinungsbild-Umschalters. */
  function themeButtons(): HTMLButtonElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
        'app-segment button',
      ),
    );
  }

  /** Klickt die Option mit der gegebenen Beschriftung an. */
  function clickTheme(label: string): void {
    themeButtons()
      .find((button) => button.textContent?.trim() === label)!
      .click();
    TestBed.tick();
    fixture.detectChanges();
  }

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

  // --- FE-SET-02: Passwort ändern ---

  it('sperrt den Submit-Button, solange das Formular ungültig ist', () => {
    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  it('deaktiviert den Submit-Button, solange der zu kurze neue Passwort clientseitig abgelehnt wird', () => {
    component.passwordForm.setValue({ aktuellesPasswort: 'altesPasswort', neuesPasswort: 'kurz' });

    component.submitPassword();

    httpMock.expectNone('/api/users/me/password');
    expect(component.passwordForm.invalid).toBe(true);
    expect(component.neuesPasswortError()).toBe('Passwort muss mindestens 8 Zeichen lang sein.');
  });

  it('ändert das Passwort und zeigt eine In-App-Bestätigung, Felder werden geleert', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });

    component.submitPassword();

    const req = httpMock.expectOne('/api/users/me/password');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });
    req.flush(null);
    fixture.detectChanges();

    expect(component.passwordForm.controls.aktuellesPasswort.value).toBe('');
    expect(component.passwordForm.controls.neuesPasswort.value).toBe('');
    expect(component.passwordSubmitting()).toBe(false);
    expect(component.passwordErrorMessage()).toBeNull();

    const notice: HTMLElement = fixture.nativeElement.querySelector('app-notice');
    expect(notice.getAttribute('role')).toBe('status');
    expect(notice.querySelector('.notice__body')?.textContent?.trim()).toBe('Passwort geändert.');
  });

  it('lehnt ein falsches aktuelles Passwort mit "Aktuelles Passwort falsch" ab, der User bleibt eingeloggt', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'falschesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });

    component.submitPassword();

    httpMock
      .expectOne('/api/users/me/password')
      .flush(
        { message: 'Aktuelles Passwort falsch' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(component.passwordErrorMessage()).toBe('Aktuelles Passwort falsch');
    expect(component.passwordSaved()).toBe(false);
    expect(component.passwordSubmitting()).toBe(false);

    const notice: HTMLElement = fixture.nativeElement.querySelector('app-notice');
    expect(notice.getAttribute('role')).toBe('alert');
    expect(notice.querySelector('.notice__body')?.textContent?.trim()).toBe(
      'Aktuelles Passwort falsch',
    );
  });

  it('zeigt die Backend-Meldung bei einem 400 wegen ungültigem neuen Passwort, nicht "Aktuelles Passwort falsch"', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: '        ',
    });

    component.submitPassword();

    httpMock
      .expectOne('/api/users/me/password')
      .flush(
        { message: 'Neues Passwort ist erforderlich.' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(component.passwordErrorMessage()).toBe('Neues Passwort ist erforderlich.');
  });

  it('setzt die alte Erfolgsmeldung vor einem neuen Versuch zurück', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });
    component.submitPassword();
    httpMock.expectOne('/api/users/me/password').flush(null);
    expect(component.passwordSaved()).toBe(true);

    component.passwordForm.setValue({
      aktuellesPasswort: 'falschesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });
    component.submitPassword();

    expect(component.passwordSaved()).toBe(false);
    httpMock
      .expectOne('/api/users/me/password')
      .flush(null, { status: 400, statusText: 'Bad Request' });
  });

  // --- AC2: Ist ein Einkommen erfasst, steht der aktuelle Wert im Feld ---

  it('belegt das Betragsfeld mit dem aktuellen Monatseinkommen vor', () => {
    expect(fixture.componentInstance.incomeForm.controls.betrag.value).toBe(3000);

    const input = (fixture.nativeElement as HTMLElement).querySelector(
      '#monthlyIncome',
    ) as HTMLInputElement;
    expect(input.value).toBe('3000');
  });

  // --- AC1: Das Feld ist im UI als optional gekennzeichnet ---

  it('weist im Hinweistext darauf hin, dass das Betragsfeld optional ist', () => {
    const hint = (fixture.nativeElement as HTMLElement).querySelector('form .settings__hint');

    expect(hint?.textContent).toContain('Optional');
  });

  // --- AC3: Kein Vorschlag, solange bereits ein Einkommen erfasst ist ---

  it('zeigt keine Vorschlags-Notice, wenn bereits ein Einkommen erfasst ist', () => {
    expect(fixture.componentInstance.incomeSuggestionText()).toBeNull();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.income-suggestion'),
    ).toBeNull();
  });

  it('lädt den Einkommens-Vorschlag nicht, wenn bereits ein Einkommen erfasst ist', () => {
    httpMock.expectNone('/api/budget/safe-to-spend');
  });

  // --- AC1/AC4: Betrag ist optional, Beträge <= 0 werden clientseitig abgefangen ---

  it.each([0, -5, -0.01])('lehnt den Betrag %s ab und sendet nicht', (betrag) => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(betrag);

    fixture.componentInstance.submitIncome();

    httpMock.expectNone('/api/users/me/income');
    expect(fixture.componentInstance.incomeForm.controls.betrag.hasError('min')).toBe(true);
    expect(fixture.componentInstance.incomeError()).toBe('Betrag muss grösser als 0 sein.');
  });

  it('zeigt bei einem zu niedrigen Betrag mit zu vielen Nachkommastellen die Nachkommastellen-Meldung', () => {
    // 0.005 verletzt beide Regeln (< 0.01 und > 2 Nachkommastellen) — "muss grösser als 0 sein"
    // wäre hier irreführend, die eigentliche Verletzung ist die Nachkommastellen-Regel.
    fixture.componentInstance.incomeForm.controls.betrag.setValue(0.005);

    fixture.componentInstance.submitIncome();

    expect(fixture.componentInstance.incomeError()).toBe(
      'Betrag darf höchstens zwei Nachkommastellen haben.',
    );
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

  // --- FE-SET-04: Erscheinungsbild ---

  // --- AC1: Auswahl Hell / Dunkel / System, sofort und ohne Reload ---

  it('bietet genau die drei Optionen "Hell", "Dunkel" und "System" an', () => {
    expect(themeButtons().map((button) => button.textContent?.trim())).toEqual([
      'Hell',
      'Dunkel',
      'System',
    ]);
  });

  it('stellt bei Klick sofort um und markiert die aktive Option', () => {
    clickTheme('Dunkel');

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(TestBed.inject(Theme).preference()).toBe('dark');
    expect(
      themeButtons().find((button) => button.getAttribute('aria-pressed') === 'true')?.textContent,
    ).toContain('Dunkel');
  });

  // --- AC6: Persistenz über localStorage ---

  it('merkt sich die Wahl im localStorage', () => {
    clickTheme('Dunkel');

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  // --- AC3: "System" ist der Ausgangszustand und folgt dem Betriebssystem ---

  it('startet auf "System" und folgt einem Wechsel im Betriebssystem', () => {
    expect(
      themeButtons().find((button) => button.getAttribute('aria-pressed') === 'true')?.textContent,
    ).toContain('System');

    setSystemDark(true);
    TestBed.tick();

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  // --- AC5: Der Hinweis benennt die Reichweite der Wahl ---

  it('weist darauf hin, dass die Wahl nur in diesem Browser gilt', () => {
    const hint = (fixture.nativeElement as HTMLElement).querySelector(
      'app-segment + .settings__hint',
    );

    expect(hint?.textContent).toContain('nur in diesem Browser');
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

  // Die geladene Settings-Komponente zieht den Theme-Service mit und setzt dabei
  // `data-theme` — nach den Routing-Tests wieder abräumen.
  afterEach(() => {
    httpMock.verify();
    document.documentElement.removeAttribute('data-theme');
  });

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
