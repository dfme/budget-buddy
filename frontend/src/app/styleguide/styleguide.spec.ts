import { ComponentFixture, TestBed } from '@angular/core/testing';

import { installCanvasStub, restoreCanvasStub } from '../../testing/canvas';
import { installMatchMedia, restoreMatchMedia } from '../../testing/prefers-color-scheme';
import { THEME_STORAGE_KEY } from '../core/theme/theme';
import { Styleguide } from './styleguide';

describe('Styleguide', () => {
  let fixture: ComponentFixture<Styleguide>;

  beforeEach(async () => {
    // Der Showcase enthält seit FE-UI-05 echte Charts — ohne Canvas-Kontext käme
    // Chart.js in jsdom nicht hoch (die Registerables bringen die Chart-Komponenten mit).
    installCanvasStub();
    installMatchMedia(false);
    await TestBed.configureTestingModule({ imports: [Styleguide] }).compileComponents();
    fixture = TestBed.createComponent(Styleguide);
    fixture.detectChanges();
  });

  // Der Toggle schreibt seit FE-SET-04 über den Theme-Service, also data-theme auf <html>
  // UND die Wahl in den localStorage — beides nach jedem Test entfernen, damit der Zustand
  // nicht in andere Tests leakt.
  afterEach(() => {
    fixture.destroy();
    restoreCanvasStub();
    restoreMatchMedia();
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  it('rendert alle 13 Kategorie-Badges', () => {
    const badges = fixture.nativeElement.querySelectorAll('app-badge');
    expect(badges.length).toBe(13);
  });

  // Seit FE-SET-04 geht der Toggle über den Theme-Service statt selbst aufs Attribut: zwei
  // Schreiber auf einem data-theme hätten sich bei Präferenz „System" gegenseitig überholt.
  // Deshalb steht das Attribut hier schon vor dem ersten Klick — der Service hat es gesetzt.
  it('schaltet data-theme auf <html> um (dev-only Toggle)', () => {
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');

    fixture.componentInstance.toggleTheme();
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(fixture.componentInstance.theme()).toBe('dark');

    fixture.componentInstance.toggleTheme();
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('zeigt Donut und Bar mit zugänglicher Beschreibung', () => {
    const charts: HTMLCanvasElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('app-donut-chart canvas, app-bar-chart canvas'),
    );

    expect(charts).toHaveLength(2);
    for (const chart of charts) {
      expect(chart.getAttribute('role')).toBe('img');
      expect(chart.getAttribute('aria-label')).toBeTruthy();
    }
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
