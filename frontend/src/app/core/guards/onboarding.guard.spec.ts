import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable } from 'rxjs';

import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';
import { User } from '../../auth/user.model';
import { authGuard } from './auth.guard';
import { onboardingGuard } from './onboarding.guard';

/** Lara nach der Registrierung: eingeloggt, aber Onboarding offen. */
const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
};

/** Dieselbe Nutzerin nach abgeschlossenem Onboarding. */
const LARA_ONBOARDED: User = { ...LARA, onboardingCompleted: true };

// Guard nutzt `inject`, wird darum in einem Injection-Context ausgeführt.
function runGuard() {
  return TestBed.runInInjectionContext(() =>
    onboardingGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  ) as Observable<boolean | UrlTree>;
}

function resolve(result: Observable<boolean | UrlTree>): boolean | UrlTree | undefined {
  let resolved: boolean | UrlTree | undefined;
  result.subscribe((value) => (resolved = value));
  return resolved;
}

describe('onboardingGuard', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  /** Setzt den Auth-State ohne Umweg über den Guard. */
  function login(user: User): void {
    auth.login(user.email, 'supersecret').subscribe();
    httpMock.expectOne('/auth/login').flush(user);
  }

  // --- AC1: nicht onboardeter User landet im Wizard ---

  it('leitet einen nicht onboardeten User auf /onboarding um', () => {
    login(LARA);

    const resolved = resolve(runGuard());

    const expectedTree = TestBed.inject(Router).createUrlTree(['/onboarding']);
    expect(resolved).toBeInstanceOf(UrlTree);
    expect((resolved as UrlTree).toString()).toBe(expectedTree.toString());
  });

  it('laesst einen onboardeten User durch', () => {
    login(LARA_ONBOARDED);

    expect(resolve(runGuard())).toBe(true);
  });

  // --- AC3: Status kommt aus GET /users/me ---

  it('holt das Profil per GET /users/me, wenn der State leer ist', () => {
    const result = runGuard();
    let resolved: boolean | UrlTree | undefined;
    result.subscribe((value) => (resolved = value));

    const req = httpMock.expectOne('/users/me');
    expect(req.request.method).toBe('GET');
    req.flush(LARA);

    // Der Status stammt aus genau dieser Antwort — vorher war er im State unbekannt.
    expect(resolved).toBeInstanceOf(UrlTree);
    expect((resolved as UrlTree).toString()).toBe('/onboarding');
  });

  it('entscheidet aus dem geladenen State, ohne einen zweiten Request abzusetzen', () => {
    login(LARA_ONBOARDED);

    expect(resolve(runGuard())).toBe(true);
    // Wichtig, weil `authGuard` unmittelbar davor am selben Route-Eintrag laeuft: sonst
    // stuende pro Navigation ein zusaetzliches GET /users/me auf der Leitung.
    httpMock.expectNone('/users/me');
  });

  // --- Zusammenspiel mit dem authGuard ---

  it('ueberlaesst die Entscheidung ueber anonyme Nutzer dem authGuard', () => {
    // Kein Cookie: /users/me antwortet 401. Dieser Guard gibt `true` zurueck, statt selbst
    // auf /onboarding umzuleiten — sonst landete ein Ausgeloggter im Wizard statt im Login.
    // Er aeussert im Anonymfall gar keine Meinung, deshalb bleibt der UrlTree des authGuard
    // der einzige und gewinnt, ohne dass eine Ausfuehrungsreihenfolge noetig waere.
    const result = runGuard();
    let resolved: boolean | UrlTree | undefined;
    result.subscribe((value) => (resolved = value));

    httpMock.expectOne('/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(resolved).toBe(true);
  });

  it('teilt sich den Profil-Request mit dem parallel laufenden authGuard', () => {
    // Angular fuehrt die Guards eines canActivate-Arrays nebenlaeufig aus: beide sehen den
    // State leer und wuerden je ein eigenes GET /users/me ausloesen. Ein Cache-Check allein
    // half nicht — zum Zeitpunkt des zweiten Aufrufs gibt es noch keine Antwort zu cachen.
    const fromAuth = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    const fromOnboarding = runGuard();

    let authResolved: boolean | UrlTree | undefined;
    let onboardingResolved: boolean | UrlTree | undefined;
    fromAuth.subscribe((value) => (authResolved = value));
    fromOnboarding.subscribe((value) => (onboardingResolved = value));

    // expectOne schlaegt fehl, sobald zwei Requests offen sind — genau der Regressionsfall.
    httpMock.expectOne('/users/me').flush(LARA);

    expect(authResolved).toBe(true);
    expect(onboardingResolved).toBeInstanceOf(UrlTree);
  });

  it('laedt nach abgeschlossenem Request wieder frisch statt die alte Antwort zu wiederholen', () => {
    // Sonst haette der erste 401 den Nutzer dauerhaft als anonym festgeschrieben — ein
    // Login danach koennte den State nicht mehr ueber diesen Weg herstellen.
    runGuard().subscribe();
    httpMock.expectOne('/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    let resolved: boolean | UrlTree | undefined;
    runGuard().subscribe((value) => (resolved = value));
    httpMock.expectOne('/users/me').flush(LARA_ONBOARDED);

    expect(resolved).toBe(true);
  });
});

describe('onboardingGuard am echten Router', () => {
  // Die Tests oben pruefen den Rueckgabewert des Guards. Hier navigiert der echte Router
  // durch `app.routes`: das belegt AC1 als Verhalten statt als Zusicherung ueber einen
  // UrlTree — und genau das deckte auf, dass Angular die Guards eines `canActivate`-Arrays
  // NICHT der Reihe nach ausfuehrt, sondern nebenlaeufig (siehe `onboarding.guard.ts` und
  // `ensureCurrentUser` in `auth.service.ts`).
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

  /**
   * Beantwortet das `GET /users/me` des Guards; `null` steht fuer «kein gueltiges Cookie».
   *
   * <p>Das `setTimeout(0)` ist noetig, nicht kosmetisch: `navigateByUrl` startet die
   * Navigation asynchron, der Guard laeuft erst in einem spaeteren Tick. Ohne das Warten
   * steht zum Zeitpunkt der Erwartung noch gar kein Request an.
   */
  async function answerProfile(user: User | null): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    const req = httpMock.expectOne('/users/me');
    if (user === null) {
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    } else {
      req.flush(user);
    }
  }

  it('landet auf /onboarding statt auf /dashboard, solange das Onboarding offen ist', async () => {
    const navigation = router.navigateByUrl('/dashboard');
    await answerProfile(LARA);
    await navigation;

    expect(router.url).toBe('/onboarding');
  });

  it('laesst /dashboard zu, sobald das Onboarding abgeschlossen ist', async () => {
    const navigation = router.navigateByUrl('/dashboard');
    await answerProfile(LARA_ONBOARDED);
    await navigation;

    expect(router.url).toBe('/dashboard');
  });

  it('landet ohne Login auf /login und nicht im Wizard', async () => {
    const navigation = router.navigateByUrl('/dashboard');
    await answerProfile(null);
    await navigation;

    expect(router.url).toBe('/login');
  });

  it('laesst den Wizard selbst erreichbar, ohne sich im Kreis umzuleiten', async () => {
    const navigation = router.navigateByUrl('/onboarding');
    await answerProfile(LARA);
    await navigation;

    expect(router.url).toBe('/onboarding');
  });
});

describe('Guard-Zuordnung in app.routes', () => {
  function guardsOf(path: string) {
    const route = routes.find((candidate) => candidate.path === path);
    expect(route, `Route '${path}' fehlt`).toBeDefined();
    return route!.canActivate ?? [];
  }

  it.each(['dashboard', 'categories', 'import'])(
    'schuetzt /%s mit authGuard und onboardingGuard',
    (path) => {
      // Geprueft wird, dass beide Guards haengen — nicht, in welcher Reihenfolge sie
      // laufen: Angular fuehrt sie nebenlaeufig aus (siehe Navigationstests oben).
      expect(guardsOf(path)).toEqual([authGuard, onboardingGuard]);
    },
  );

  it('haengt den onboardingGuard nicht an /onboarding selbst', () => {
    // Sonst leitete das Ziel der Umleitung wieder auf sich selbst um.
    expect(guardsOf('onboarding')).toEqual([authGuard]);
  });
});
