import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { User } from './auth/user.model';
import { authGuard } from './core/guards/auth.guard';
import { onboardingGuard } from './core/guards/onboarding.guard';

/** Eine Nutzerin mit abgeschlossenem Onboarding — die Guards von /budget lassen sie durch. */
const LARA_ONBOARDED: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
  firstName: null,
  lastName: null,
};

/**
 * Die Umleitungen auf die Budget-Seite (FE-FC-09, #360): `/ausgaben` (Name der Seite bis
 * FE-FC-09), `/fixkosten` (Name der Seite bis FE-FC-07) und `/abos` (eigene Seite bis FE-FC-05)
 * führen alle drei nach `/budget`, damit Bookmarks und ältere Links nicht im Catch-all aufs
 * Dashboard landen.
 *
 * <p>Zwei Ebenen, wie in `onboarding.guard.spec.ts`: die Navigation am echten Router belegt das
 * Verhalten, die Struktur-Tests belegen `pathMatch: 'full'`. Letzteres lässt sich am Router
 * nicht beobachten — `/abos/x` landet mit und ohne `pathMatch: 'full'` im Catch-all, einmal
 * direkt, einmal über `/budget/x`. Der Unterschied ist nur in der Route-Definition sichtbar.
 */
describe('Umleitungen auf /budget (FE-FC-09)', () => {
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
   * Beantwortet das `GET /api/users/me` der Guards am Ziel der Umleitung. Das `setTimeout(0)`
   * ist nötig: `navigateByUrl` startet asynchron, der Guard läuft erst in einem späteren Tick
   * (siehe `onboarding.guard.spec.ts`).
   */
  async function answerProfile(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    httpMock.expectOne('/api/users/me').flush(LARA_ONBOARDED);
  }

  it.each(['/ausgaben', '/fixkosten', '/abos'])('leitet %s nach /budget um', async (oldPath) => {
    const navigation = router.navigateByUrl(oldPath);
    await answerProfile();
    await navigation;

    expect(router.url).toBe('/budget');
  });

  it('erreicht /budget direkt, mit beiden Guards', async () => {
    const navigation = router.navigateByUrl('/budget');
    await answerProfile();
    await navigation;

    expect(router.url).toBe('/budget');
  });
});

describe('Routen-Definition der Budget-Seite (FE-FC-09)', () => {
  function routeOf(path: string) {
    const route = routes.find((candidate) => candidate.path === path);
    expect(route, `Route '${path}' fehlt`).toBeDefined();
    return route!;
  }

  it('schützt /budget mit authGuard und onboardingGuard', () => {
    expect(routeOf('budget').canActivate).toEqual([authGuard, onboardingGuard]);
  });

  it.each(['ausgaben', 'fixkosten', 'abos'])(
    'definiert /%s als vollständige Umleitung ohne eigene Guards',
    (oldPath) => {
      const route = routeOf(oldPath);

      expect(route.redirectTo).toBe('budget');
      // Ohne `pathMatch: 'full'` griffe der Redirect als Präfix und schickte /abos/x nach
      // /budget/x — ein Pfad, den es nicht gibt.
      expect(route.pathMatch).toBe('full');
      // Guards gehören ans Ziel, nicht an die Umleitung: /budget bringt seine eigenen mit.
      expect(route.canActivate).toBeUndefined();
    },
  );
});
