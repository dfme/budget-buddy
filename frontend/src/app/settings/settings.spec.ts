import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import {
  installMatchMedia,
  restoreMatchMedia,
  setSystemDark,
} from '../../testing/prefers-color-scheme';
import { routes } from '../app.routes';
import { User } from '../auth/user.model';
import { authGuard } from '../core/guards/auth.guard';
import { onboardingGuard } from '../core/guards/onboarding.guard';
import { THEME_STORAGE_KEY, Theme } from '../core/theme/theme';
import { Settings } from './settings';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
};

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

    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
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

  // --- AC3: Überschrift und drei Abschnitte als Cards ---

  it('rendert die Überschrift "Einstellungen"', () => {
    expect(fixture.nativeElement.querySelector('h1')?.textContent?.trim()).toBe('Einstellungen');
  });

  it('rendert die Abschnitte "Passwort", "Einkommen" und "Erscheinungsbild" als Cards', () => {
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
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.settings__hint');

    expect(hint?.textContent).toContain('nur in diesem Browser');
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
});
