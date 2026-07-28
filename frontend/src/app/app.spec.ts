import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RouterOutlet, provideRouter } from '@angular/router';

import { App } from './app';
import { Shell } from './core/layout/shell';

describe('App', () => {
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
  });

  it('should create the app', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  // Das Root hält nur noch Shell und Outlet — Navigation, Konto und Logout
  // werden in `core/layout/shell.spec.ts` abgedeckt.
  it('rendert die App-Shell mit genau einem Router-Outlet darin', () => {
    expect(fixture.debugElement.query(By.directive(Shell))).not.toBeNull();
    expect(fixture.debugElement.queryAll(By.directive(RouterOutlet)).length).toBe(1);
  });
});
