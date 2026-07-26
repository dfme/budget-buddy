import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Styleguide } from './styleguide';

describe('Styleguide', () => {
  let fixture: ComponentFixture<Styleguide>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Styleguide] }).compileComponents();
    fixture = TestBed.createComponent(Styleguide);
    fixture.detectChanges();
  });

  // Der Theme-Toggle schreibt data-theme auf <html> — nach jedem Test entfernen,
  // damit der Zustand nicht in andere Tests leakt.
  afterEach(() => document.documentElement.removeAttribute('data-theme'));

  it('rendert alle 13 Kategorie-Badges', () => {
    const badges = fixture.nativeElement.querySelectorAll('app-badge');
    expect(badges.length).toBe(13);
  });

  it('schaltet data-theme auf <html> um (dev-only Toggle)', () => {
    expect(document.documentElement.getAttribute('data-theme')).toBeNull();

    fixture.componentInstance.toggleTheme();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(fixture.componentInstance.theme()).toBe('dark');

    fixture.componentInstance.toggleTheme();
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('aktualisiert den Segment-Zustand bei Klick', () => {
    const incomeButton = Array.from<HTMLButtonElement>(
      fixture.nativeElement.querySelectorAll('app-segment button'),
    ).find((b) => b.textContent?.includes('Einnahmen'))!;
    incomeButton.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.segmentValue()).toBe('income');
  });
});
