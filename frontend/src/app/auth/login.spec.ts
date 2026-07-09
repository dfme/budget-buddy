import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Login } from './login';
import { User } from './user.model';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
};

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('does not call the backend when the form is empty', () => {
    component.submit();

    httpMock.expectNone('/auth/login');
    expect(component.form.invalid).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('logs in and redirects to the dashboard on success', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();

    const req = httpMock.expectOne('/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'lara@example.ch',
      password: 'supersecret',
    });
    req.flush(LARA);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.errorMessage()).toBeNull();
    expect(component.submitting()).toBe(false);
  });

  it('shows a neutral error and does not redirect on 401', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'wrong-password' });

    component.submit();

    httpMock.expectOne('/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(component.errorMessage()).toBe('E-Mail oder Passwort falsch');
    expect(navigate).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });
});
