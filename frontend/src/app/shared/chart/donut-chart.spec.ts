import { ComponentFixture, TestBed } from '@angular/core/testing';

import { installCanvasStub, restoreCanvasStub } from '../../../testing/canvas';
import { clearTokens, setTokens } from '../../../testing/tokens';
import { DonutChart, DonutSlice } from './donut-chart';

const SLICES: readonly DonutSlice[] = [
  { slug: 'wohnen', label: 'Wohnen', value: 980 },
  { slug: 'lebensmittel', label: 'Lebensmittel', value: 412.65 },
];

/** Wartet, bis der MutationObserver seine Callbacks zugestellt hat. */
function flushMutations(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('DonutChart', () => {
  let fixture: ComponentFixture<DonutChart>;

  function canvas(): HTMLCanvasElement {
    return fixture.nativeElement.querySelector('canvas');
  }
  function legendItems(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.legend__item'));
  }
  function dataset() {
    return fixture.componentInstance.chartData().datasets[0];
  }

  beforeEach(async () => {
    installCanvasStub();
    setTokens({
      '--c-surface': '#333333',
      '--c-line-strong': '#555555',
      '--cat-wohnen': '#aa0000',
      '--cat-lebensmittel': '#00aa00',
    });

    await TestBed.configureTestingModule({ imports: [DonutChart] }).compileComponents();

    fixture = TestBed.createComponent(DonutChart);
    fixture.componentRef.setInput('data', SLICES);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    restoreCanvasStub();
    clearTokens();
  });

  it('rendert ein Canvas mit role="img" und generierter Beschreibung', () => {
    expect(canvas().getAttribute('role')).toBe('img');
    expect(canvas().getAttribute('aria-label')).toBe(
      'Donut-Diagramm der Ausgaben nach Kategorie: Wohnen CHF 980.00, Lebensmittel CHF 412.65.',
    );
  });

  it('übernimmt eine explizit gesetzte Beschreibung', () => {
    fixture.componentRef.setInput('ariaLabel', 'Ausgaben Juli 2026');
    fixture.detectChanges();

    expect(canvas().getAttribute('aria-label')).toBe('Ausgaben Juli 2026');
  });

  it('beschreibt auch den leeren Zustand', () => {
    fixture.componentRef.setInput('data', []);
    fixture.detectChanges();

    expect(canvas().getAttribute('aria-label')).toBe(
      'Donut-Diagramm der Ausgaben nach Kategorie: keine Ausgaben vorhanden.',
    );
  });

  it('zeigt je Segment eine Legendenzeile mit Slug und Betrag', () => {
    const items = legendItems();

    expect(items).toHaveLength(2);
    expect(items[0].querySelector('.legend__dot')?.getAttribute('data-cat')).toBe('wohnen');
    expect(items[0].querySelector('.legend__name')?.textContent?.trim()).toBe('Wohnen');
    expect(items[0].querySelector('.legend__value')?.textContent?.trim()).toBe('980.00');
    expect(items[1].querySelector('.legend__value')?.textContent?.trim()).toBe('412.65');
  });

  it('blendet die Legende auf Wunsch aus', () => {
    fixture.componentRef.setInput('showLegend', false);
    fixture.detectChanges();

    expect(legendItems()).toHaveLength(0);
  });

  it('nimmt die Segmentfarben aus den --cat-Tokens', () => {
    expect(dataset().backgroundColor).toEqual(['#aa0000', '#00aa00']);
    expect(dataset().borderColor).toBe('#333333');
  });

  it('färbt ein Segment mit unbekanntem Slug neutral', () => {
    fixture.componentRef.setInput('data', [{ slug: 'gibtsnicht', label: 'Unbekannt', value: 10 }]);
    fixture.detectChanges();

    expect(dataset().backgroundColor).toEqual(['#555555']);
  });

  it('baut die Farben beim Theme-Wechsel neu auf', async () => {
    expect(dataset().backgroundColor).toEqual(['#aa0000', '#00aa00']);

    setTokens({ '--cat-wohnen': '#0000aa', '--cat-lebensmittel': '#00aaaa' });
    document.documentElement.setAttribute('data-theme', 'dark');
    await flushMutations();
    fixture.detectChanges();

    expect(dataset().backgroundColor).toEqual(['#0000aa', '#00aaaa']);
  });
});
