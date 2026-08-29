import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../../auth/auth.service';
import { User } from '../../auth/user.model';
import { Shell } from './shell';

const LARA: User = {
  id: 1,
  email: 'lara.meier@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
  firstName: null,
  lastName: null,
};

/** Minimale Route-Ziele, damit `routerLinkActive` echte Navigation sehen kann. */
@Component({ template: 'stub' })
class RouteStub {}

describe('Shell', () => {
  let fixture: ComponentFixture<Shell>;
  let auth: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'dashboard', component: RouteStub },
          { path: 'categories', component: RouteStub },
          { path: 'import', component: RouteStub },
          { path: 'fixkosten', component: RouteStub },
          { path: 'einstellungen', component: RouteStub },
          { path: 'login', component: RouteStub },
        ]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Shell);
    auth = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  /** Meldet einen User an, indem der Login-Call gemockt und der State gesetzt wird. */
  function login(user: User = LARA): void {
    auth.login(user.email, 'supersecret').subscribe();
    httpMock.expectOne('/api/auth/login').flush(user);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function query<T extends HTMLElement>(selector: string): T | null {
    return el().querySelector<T>(selector);
  }

  function avatarButton(): HTMLButtonElement {
    return query<HTMLButtonElement>('.topbar__avatar')!;
  }

  it('rendert Topbar, Navigation und Konto-Block im eingeloggten Zustand', () => {
    login();

    expect(query('.topbar')).not.toBeNull();
    expect(query('nav[aria-label="Hauptnavigation"]')).not.toBeNull();
    expect(query('.nav__account')).not.toBeNull();
    expect(query('.topbar__brand')?.textContent).toContain('BudgetBuddy');
  });

  it('blendet Topbar und Navigation aus, solange niemand eingeloggt ist', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(query('.topbar')).toBeNull();
    expect(query('nav')).toBeNull();
    // Der Inhalt selbst bleibt da — Login/Register rendern ohne Chrome, aber
    // in derselben gepolsterten `<main>`.
    expect(query('main.main')).not.toBeNull();
  });

  it('führt genau die vier verfügbaren Ziele in der Tab-Bar/Sidebar-Navigation', () => {
    login();

    const links = Array.from(el().querySelectorAll<HTMLAnchorElement>('.nav__item'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/dashboard',
      '/categories',
      '/import',
      '/fixkosten',
    ]);
    expect(links.map((a) => a.textContent?.trim().replace(/\s+/g, ' '))).toEqual([
      '◎ Übersicht',
      '≡ Transaktionen',
      '↑ Import',
      '▦ Fixkosten',
    ]);
  });

  // «Einstellungen» ist bewusst kein Tab-Bar-Ziel, sondern ein Konto-Ziel im Konto-Block
  // (siehe Kommentar an `navItems` in shell.ts) — hier doppelt verlinkt wie «Abmelden»:
  // im mobilen Popover und am Fuss der Desktop-Sidebar.

  it('verlinkt Einstellungen im Konto-Block der Sidebar', () => {
    login();

    const link = query<HTMLAnchorElement>('.nav__settings');
    expect(link?.getAttribute('href')).toBe('/einstellungen');
    expect(link?.textContent?.trim().replace(/\s+/g, ' ')).toBe('⚙ Einstellungen');
  });

  it('markiert Einstellungen im Sidebar-Konto-Block als aktiv, wenn die Route offen ist', async () => {
    login();

    await router.navigate(['/einstellungen']);
    fixture.detectChanges();

    const link = query<HTMLAnchorElement>('.nav__settings');
    expect(link?.classList.contains('nav__settings--active')).toBe(true);
    expect(link?.getAttribute('aria-current')).toBe('page');
  });

  it('markiert das aktive Ziel mit aria-current und Aktiv-Klasse', async () => {
    login();

    await router.navigate(['/categories']);
    fixture.detectChanges();

    const active = query<HTMLAnchorElement>('.nav__item--active');
    expect(active?.getAttribute('href')).toBe('/categories');
    expect(active?.getAttribute('aria-current')).toBe('page');

    // Nur das aktive Ziel trägt die Markierung.
    const current = el().querySelectorAll('.nav__item[aria-current="page"]');
    expect(current.length).toBe(1);
  });

  it.each([
    ['lara.meier@example.ch', 'LM'],
    ['lara@example.ch', 'LA'],
    ['marc_keller@example.ch', 'MK'],
  ])('leitet die Initialen ohne Namen aus %s als %s ab (E-Mail-Fallback)', (email, expected) => {
    login({ ...LARA, email });

    expect(avatarButton().textContent?.trim()).toBe(expected);
    expect(query('.nav__avatar')?.textContent?.trim()).toBe(expected);
  });

  // BE-AUTH-05 (#114): sobald ein Name hinterlegt ist, hat er Vorrang vor dem E-Mail-Fallback.
  it.each([
    ['Lara', 'Meier', 'LM'],
    ['Marc', null, 'MA'],
    [null, 'Keller', 'KE'],
  ])(
    'leitet die Initialen aus firstName=%s/lastName=%s als %s ab',
    (firstName, lastName, expected) => {
      login({ ...LARA, firstName, lastName });

      expect(avatarButton().textContent?.trim()).toBe(expected);
      expect(query('.nav__avatar')?.textContent?.trim()).toBe(expected);
    },
  );

  it('zeigt die E-Mail im Konto-Block der Sidebar', () => {
    login();

    expect(query('.nav__user-mail')?.textContent?.trim()).toBe(LARA.email);
  });

  it('zeigt ohne hinterlegten Namen keinen Namen im Konto-Block — nur die E-Mail', () => {
    login();

    expect(query('.nav__user-name')).toBeNull();
    expect(query('.account-menu__name')).toBeNull();
  });

  it('zeigt den Namen im Konto-Block der Sidebar, wenn hinterlegt', () => {
    login({ ...LARA, firstName: 'Lara', lastName: 'Meier' });

    expect(query('.nav__user-name')?.textContent?.trim()).toBe('Lara Meier');
    expect(query('.nav__user-mail')?.textContent?.trim()).toBe(LARA.email);
  });

  it('zeigt den Namen im mobilen Konto-Popover, wenn hinterlegt', () => {
    login({ ...LARA, firstName: 'Lara', lastName: 'Meier' });

    avatarButton().click();
    fixture.detectChanges();

    expect(query('.account-menu__name')?.textContent?.trim()).toBe('Lara Meier');
    expect(query('.account-menu__mail')?.textContent?.trim()).toBe(LARA.email);
  });

  describe('Konto-Popover (Mobile)', () => {
    it('ist initial geschlossen', () => {
      login();

      expect(avatarButton().getAttribute('aria-expanded')).toBe('false');
      expect(avatarButton().getAttribute('aria-controls')).toBe('account-menu');
      expect(query('#account-menu')).toBeNull();
    });

    it('öffnet auf Klick und zeigt E-Mail plus Einstellungen und Abmelden', () => {
      login();

      avatarButton().click();
      fixture.detectChanges();

      expect(avatarButton().getAttribute('aria-expanded')).toBe('true');
      expect(query('#account-menu')).not.toBeNull();
      expect(query('.account-menu__mail')?.textContent?.trim()).toBe(LARA.email);
      expect(query<HTMLAnchorElement>('a.account-menu__item')?.getAttribute('href')).toBe(
        '/einstellungen',
      );
      expect(query('button.account-menu__item')?.textContent).toContain('Abmelden');
    });

    it('schliesst das Popover beim Klick auf Einstellungen', () => {
      login();
      avatarButton().click();
      fixture.detectChanges();

      query<HTMLAnchorElement>('a.account-menu__item')!.click();
      fixture.detectChanges();

      expect(query('#account-menu')).toBeNull();
    });

    it('markiert Einstellungen im Popover als aktiv, wenn die Route offen ist', async () => {
      login();

      await router.navigate(['/einstellungen']);
      avatarButton().click();
      fixture.detectChanges();
      // `RouterLinkActive.update()` toggelt die Klasse in einem `queueMicrotask` — anders als
      // bei `.nav__settings`, das schon vor der Navigation im DOM steht und dessen Update
      // während des `await router.navigate(...)` bereits durchlief, wird dieser Link erst mit
      // dem Öffnen des Popovers gerade eben erzeugt. Ein Microtask-Tick muss deshalb noch
      // verstreichen, bevor die Klasse gesetzt ist.
      await Promise.resolve();
      fixture.detectChanges();

      const link = query<HTMLAnchorElement>('a.account-menu__item');
      expect(link?.classList.contains('account-menu__item--active')).toBe(true);
      expect(link?.getAttribute('aria-current')).toBe('page');
    });

    // Das Popover ist ein Disclosure. `role="menu"` verspricht Pfeiltasten und
    // erlaubt kein <p> als Kind — die E-Mail-Zeile wäre dann für Screenreader
    // überspringbar. Dieser Test hält die Rollen draussen.
    it('ist als Disclosure ausgezeichnet, nicht als WAI-ARIA-Menu', () => {
      login();
      avatarButton().click();
      fixture.detectChanges();

      expect(avatarButton().getAttribute('aria-haspopup')).toBeNull();
      expect(query('[role="menu"]')).toBeNull();
      expect(query('[role="menuitem"]')).toBeNull();
    });

    it('schliesst auf Escape und gibt den Fokus an den Avatar zurück', () => {
      login();
      avatarButton().click();
      fixture.detectChanges();

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(query('#account-menu')).toBeNull();
      expect(document.activeElement).toBe(avatarButton());
    });

    it('schliesst bei einem Klick ausserhalb der Shell', () => {
      login();
      avatarButton().click();
      fixture.detectChanges();

      document.body.click();
      fixture.detectChanges();

      expect(query('#account-menu')).toBeNull();
    });

    it('bleibt bei einem Klick im Popover offen', () => {
      login();
      avatarButton().click();
      fixture.detectChanges();

      query<HTMLElement>('.account-menu__mail')!.click();
      fixture.detectChanges();

      expect(query('#account-menu')).not.toBeNull();
    });
  });

  describe('Logout', () => {
    let navigate: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    });

    it('meldet über die Sidebar ab: POST /api/auth/logout, State leer, Redirect /login', () => {
      login();

      query<HTMLButtonElement>('.nav__logout')!.click();

      const req = httpMock.expectOne('/api/auth/logout');
      expect(req.request.method).toBe('POST');
      req.flush(null);

      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });

    it('meldet auch über das Konto-Popover ab', () => {
      login();
      avatarButton().click();
      fixture.detectChanges();

      query<HTMLButtonElement>('button.account-menu__item')!.click();

      httpMock.expectOne('/api/auth/logout').flush(null);

      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });

    it('leert den State und leitet um, auch wenn der Logout-Call fehlschlägt', () => {
      login();

      query<HTMLButtonElement>('.nav__logout')!.click();

      httpMock
        .expectOne('/api/auth/logout')
        .flush(null, { status: 500, statusText: 'Server Error' });

      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });
  });
});
