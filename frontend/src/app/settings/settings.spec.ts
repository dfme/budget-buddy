import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { routes } from '../app.routes';
import { User } from '../auth/user.model';
import { authGuard } from '../core/guards/auth.guard';
import { onboardingGuard } from '../core/guards/onboarding.guard';
import { Settings } from './settings';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
};

describe('Settings', () => {
  let fixture: ComponentFixture<Settings>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Settings);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  // Der Screen ist ein reines Gerüst ohne Ladepfad — `verify()` deckt auf, falls doch
  // einmal ein Request abgesetzt würde, statt das nur an einem Textinhalt zu vermuten.
  afterEach(() => httpMock.verify());

  // --- AC3: Überschrift und drei leere Abschnitte als Cards ---

  it('rendert die Überschrift "Einstellungen"', () => {
    expect(fixture.nativeElement.querySelector('h1')?.textContent?.trim()).toBe('Einstellungen');
  });

  it('rendert die drei leeren Abschnitte "Passwort", "Einkommen" und "Erscheinungsbild" als Cards', () => {
    const cardTitles = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-card .card__title'),
    ).map((el) => el.textContent?.trim());

    expect(cardTitles).toEqual(['Passwort', 'Einkommen', 'Erscheinungsbild']);
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
