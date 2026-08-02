import { ChartPalette } from './chart-theme';
import { chartFont, chfTooltip, thousandsTick, tooltipOptions } from './chart-options';

function palette(overrides: Partial<ChartPalette> = {}): ChartPalette {
  return {
    ink: '#111111',
    ink2: '#222222',
    surface: '#333333',
    line: '#444444',
    lineStrong: '#555555',
    accent: '#666666',
    categories: {},
    fontFamily: 'system-ui, sans-serif',
    ...overrides,
  };
}

describe('chart-options', () => {
  it('formatiert Tooltip-Beträge im Schweizer Format', () => {
    expect(chfTooltip(1234.5)).toBe("CHF 1'234.50");
    expect(chfTooltip(0)).toBe('CHF 0.00');
  });

  it('beschriftet die Achse in Tausenderschritten, die Null aber als 0', () => {
    expect(thousandsTick(2500)).toBe('2.5k');
    expect(thousandsTick(3000)).toBe('3k');
    expect(thousandsTick(0)).toBe('0');
  });

  it('nimmt Tooltip-Farben aus der Palette (Grund = Ink, Text = Surface)', () => {
    const options = tooltipOptions(palette());

    expect(options.backgroundColor).toBe('#111111');
    expect(options.titleColor).toBe('#333333');
    expect(options.bodyColor).toBe('#333333');
  });

  it('lässt die Schriftfamilie offen, wenn sie nicht ermittelbar ist', () => {
    expect(chartFont(palette()).family).toBe('system-ui, sans-serif');
    expect(chartFont(palette({ fontFamily: '' })).family).toBeUndefined();
  });
});
