import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Register } from './register';
import { User } from './user.model';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
};

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let component: Register;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('does not call the backend when the form is empty', () => {
    component.submit();

    httpMock.expectNone('/auth/register');
    expect(component.form.invalid).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not call the backend when the password is shorter than 8 characters', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'short' });

    component.submit();

    httpMock.expectNone('/auth/register');
    expect(component.form.controls.password.hasError('minlength')).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('registers and redirects to the dashboard on success', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();

    const req = httpMock.expectOne('/auth/register');
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

  it('shows a specific error and does not redirect on 409', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();

    httpMock.expectOne('/auth/register').flush(null, { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage()).toBe('E-Mail bereits vergeben');
    expect(navigate).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });
});
