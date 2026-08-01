import { TestBed } from '@angular/core/testing';

import { clearTokens, setTokens } from '../../../testing/tokens';
import { CATEGORY_SLUGS } from '../category';
import { ChartTheme } from './chart-theme';

/** Wartet, bis der MutationObserver seine Callbacks zugestellt hat. */
function flushMutations(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('ChartTheme', () => {
  beforeEach(() => {
    setTokens({
      '--c-ink': '#111111',
      '--c-ink-2': '#222222',
      '--c-surface': '#333333',
      '--c-line': '#444444',
      '--c-line-strong': '#555555',
      '--c-accent': '#666666',
      '--cat-wohnen': '#777777',
    });
  });

  afterEach(() => clearTokens());

  it('liest die Basisfarben aus den Custom Properties', () => {
    const palette = TestBed.inject(ChartTheme).palette();

    expect(palette.ink).toBe('#111111');
    expect(palette.ink2).toBe('#222222');
    expect(palette.surface).toBe('#333333');
    expect(palette.line).toBe('#444444');
    expect(palette.lineStrong).toBe('#555555');
    expect(palette.accent).toBe('#666666');
  });

  it('deckt alle 13 Kategorien ab', () => {
    const palette = TestBed.inject(ChartTheme).palette();

    expect(Object.keys(palette.categories).sort()).toEqual([...CATEGORY_SLUGS].sort());
    expect(palette.categories['wohnen']).toBe('#777777');
  });

  it('aktualisiert die Palette beim Theme-Wechsel', async () => {
    const theme = TestBed.inject(ChartTheme);
    expect(theme.palette().accent).toBe('#666666');

    // Wie beim echten Theme-Wechsel: neue Token-Werte + data-theme auf <html>.
    setTokens({ '--c-accent': '#aabbcc' });
    document.documentElement.setAttribute('data-theme', 'dark');
    await flushMutations();

    expect(theme.palette().accent).toBe('#aabbcc');
  });

  it('trennt den Observer beim Zerstören des Injectors', async () => {
    const theme = TestBed.inject(ChartTheme);
    expect(theme.palette().accent).toBe('#666666');

    TestBed.resetTestingModule();
    setTokens({ '--c-accent': '#aabbcc' });
    document.documentElement.setAttribute('data-theme', 'dark');
    await flushMutations();

    // Kein Update mehr: das Signal liefert den zuletzt gelesenen Wert.
    expect(theme.palette().accent).toBe('#666666');
  });
});
