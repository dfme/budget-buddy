import { ComponentFixture, TestBed } from '@angular/core/testing';

import { installCanvasStub, restoreCanvasStub } from '../../../testing/canvas';
import { clearTokens, setTokens } from '../../../testing/tokens';
import { BarChart, BarPoint } from './bar-chart';

const MONTHS: readonly BarPoint[] = [
  { label: 'Mai', value: 2640.2 },
  { label: 'Jun', value: 2405.6 },
  { label: 'Jul', value: 2265.4 },
];

describe('BarChart', () => {
  let fixture: ComponentFixture<BarChart>;

  function canvas(): HTMLCanvasElement {
    return fixture.nativeElement.querySelector('canvas');
  }
  function dataset() {
    return fixture.componentInstance.chartData().datasets[0];
  }

  beforeEach(async () => {
    installCanvasStub();
    setTokens({
      '--c-accent': '#00ff00',
      '--c-line': '#444444',
      '--c-line-strong': '#555555',
      '--c-ink-2': '#222222',
    });

    await TestBed.configureTestingModule({ imports: [BarChart] }).compileComponents();

    fixture = TestBed.createComponent(BarChart);
    fixture.componentRef.setInput('data', MONTHS);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    restoreCanvasStub();
    clearTokens();
  });

  it('rendert ein Canvas mit role="img" und markiert den hervorgehobenen Monat', () => {
    expect(canvas().getAttribute('role')).toBe('img');
    expect(canvas().getAttribute('aria-label')).toBe(
      "Balkendiagramm der monatlichen Ausgaben: Mai CHF 2'640.20, Jun CHF 2'405.60, " +
        "Jul CHF 2'265.40 (hervorgehoben).",
    );
  });

  it('übernimmt eine explizit gesetzte Beschreibung', () => {
    fixture.componentRef.setInput('ariaLabel', 'Ausgabenverlauf 2026');
    fixture.detectChanges();

    expect(canvas().getAttribute('aria-label')).toBe('Ausgabenverlauf 2026');
  });

  it('beschreibt auch den leeren Zustand', () => {
    fixture.componentRef.setInput('data', []);
    fixture.detectChanges();

    expect(canvas().getAttribute('aria-label')).toBe(
      'Balkendiagramm der monatlichen Ausgaben: keine Daten vorhanden.',
    );
  });

  it('übernimmt Labels und Werte', () => {
    expect(fixture.componentInstance.chartData().labels).toEqual(['Mai', 'Jun', 'Jul']);
    expect(dataset().data).toEqual([2640.2, 2405.6, 2265.4]);
  });

  it('hebt standardmässig den letzten Balken im Akzent hervor', () => {
    expect(dataset().backgroundColor).toEqual(['#555555', '#555555', '#00ff00']);
  });

  it('respektiert einen explizit gesetzten highlightIndex', () => {
    fixture.componentRef.setInput('highlightIndex', 0);
    fixture.detectChanges();

    expect(dataset().backgroundColor).toEqual(['#00ff00', '#555555', '#555555']);
    expect(canvas().getAttribute('aria-label')).toContain("Mai CHF 2'640.20 (hervorgehoben)");
  });

  it('hebt bei einem Index ausserhalb des Bereichs keinen Balken hervor', () => {
    fixture.componentRef.setInput('highlightIndex', 99);
    fixture.detectChanges();

    expect(dataset().backgroundColor).toEqual(['#555555', '#555555', '#555555']);
    expect(canvas().getAttribute('aria-label')).not.toContain('hervorgehoben');
  });

  it('nimmt Gitter- und Beschriftungsfarben aus den Tokens', () => {
    const scales = fixture.componentInstance.chartOptions().scales;

    expect(scales?.['y']?.grid?.color).toBe('#444444');
    expect(scales?.['y']?.ticks?.color).toBe('#222222');
    expect(scales?.['x']?.border?.color).toBe('#444444');
  });
});
