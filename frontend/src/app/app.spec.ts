import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { App } from './app';
import { AuthService } from './auth/auth.service';
import { User } from './auth/user.model';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
};

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let auth: AuthService;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    auth = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  /** Meldet Lara an, indem der Login-Call gemockt und der State gesetzt wird. */
  function login(): void {
    auth.login(LARA.email, 'supersecret').subscribe();
    httpMock.expectOne('/auth/login').flush(LARA);
    fixture.detectChanges();
  }

  function logoutButton(): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('button.logout');
  }

  it('should create the app', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the app title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('BudgetBuddy');
  });

  it('hides the logout button when not authenticated', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(logoutButton()).toBeNull();
  });

  it('shows the logout button when authenticated', () => {
    login();

    expect(auth.isAuthenticated()).toBe(true);
    expect(logoutButton()).not.toBeNull();
  });

  it('shows the categories nav link when authenticated', () => {
    login();

    const link = (fixture.nativeElement as HTMLElement).querySelector('a[routerLink="/categories"]');
    expect(link).not.toBeNull();
    expect(link?.textContent).toContain('Kategorien');
  });

  it('logs out on click: POSTs /auth/logout, clears state, redirects to /login', () => {
    login();

    logoutButton()!.click();

    const req = httpMock.expectOne('/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('clears state and redirects even if the logout call fails', () => {
    login();

    logoutButton()!.click();

    httpMock.expectOne('/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
