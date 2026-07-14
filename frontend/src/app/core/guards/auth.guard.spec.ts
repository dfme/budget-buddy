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
import { Observable, isObservable } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { User } from '../../auth/user.model';
import { authGuard } from './auth.guard';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
};

// Guard nutzt `inject`, wird darum in einem Injection-Context ausgeführt.
function runGuard() {
  return TestBed.runInInjectionContext(() =>
    authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  );
}

describe('authGuard', () => {
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

  it('grants access immediately when already authenticated', () => {
    // State setzen, ohne Backend-Call
    auth.login('lara@example.ch', 'supersecret').subscribe();
    httpMock.expectOne('/auth/login').flush(LARA);
    expect(auth.isAuthenticated()).toBe(true);

    const result = runGuard();

    expect(result).toBe(true);
  });

  it('restores state via /users/me and grants access when the cookie is valid', () => {
    const result = runGuard();
    expect(isObservable(result)).toBe(true);

    let resolved: boolean | UrlTree | undefined;
    (result as Observable<boolean | UrlTree>).subscribe((value) => (resolved = value));

    const req = httpMock.expectOne('/users/me');
    expect(req.request.method).toBe('GET');
    req.flush(LARA);

    expect(resolved).toBe(true);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('redirects to /login when not authenticated and /users/me returns 401', () => {
    const result = runGuard();

    let resolved: boolean | UrlTree | undefined;
    (result as Observable<boolean | UrlTree>).subscribe((value) => (resolved = value));

    httpMock.expectOne('/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    const expectedTree = TestBed.inject(Router).createUrlTree(['/login']);
    expect(resolved).toBeInstanceOf(UrlTree);
    expect((resolved as UrlTree).toString()).toBe(expectedTree.toString());
    expect(auth.isAuthenticated()).toBe(false);
  });
});
