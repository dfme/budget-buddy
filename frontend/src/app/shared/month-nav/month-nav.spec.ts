import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MonthNav } from './month-nav';

describe('MonthNav', () => {
  let fixture: ComponentFixture<MonthNav>;

  function btn(label: string): HTMLButtonElement {
    return fixture.nativeElement.querySelector(`button[aria-label="${label}"]`);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MonthNav] }).compileComponents();
    fixture = TestBed.createComponent(MonthNav);
    fixture.componentRef.setInput('label', 'Juli 2026');
    fixture.detectChanges();
  });

  it('zeigt das Label', () => {
    expect(fixture.nativeElement.querySelector('.month-nav__label').textContent).toContain(
      'Juli 2026',
    );
  });

  it('emittiert prev/next bei Klick', () => {
    const prev = vi.fn();
    const next = vi.fn();
    fixture.componentInstance.prev.subscribe(prev);
    fixture.componentInstance.next.subscribe(next);

    btn('Vorheriger Monat').click();
    btn('Nächster Monat').click();

    expect(prev).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledTimes(1);
  });

  it('sperrt den Vor-Pfeil über disableNext', () => {
    fixture.componentRef.setInput('disableNext', true);
    fixture.detectChanges();
    expect(btn('Nächster Monat').disabled).toBe(true);
    expect(btn('Vorheriger Monat').disabled).toBe(false);
  });

  describe('Direktsprung (FE-CAT-04)', () => {
    const MONTHS = [
      { value: '2026-07', label: 'Juli 2026' },
      { value: '2025-06', label: 'Juni 2025' },
      { value: '2019-08', label: 'August 2019' },
    ];

    function jump(): HTMLSelectElement | null {
      return fixture.nativeElement.querySelector('.month-nav__jump select');
    }

    function withMonths() {
      fixture.componentRef.setInput('months', MONTHS);
      fixture.componentRef.setInput('selected', '2026-07');
      fixture.detectChanges();
      return jump()!;
    }

    it('zeigt ohne Monatsliste nur den Stepper', () => {
      // Der Styleguide bindet MonthNav ohne months ein — dort darf nichts Zusätzliches auftauchen.
      expect(jump()).toBeNull();
      expect(btn('Vorheriger Monat')).not.toBeNull();
    });

    it('bietet jeden übergebenen Monat an, mit dem angezeigten vorausgewählt', () => {
      const select = withMonths();

      expect(Array.from(select.options).map((o) => o.value)).toEqual([
        '2026-07',
        '2025-06',
        '2019-08',
      ]);
      expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
        'Juli 2026',
        'Juni 2025',
        'August 2019',
      ]);
      expect(select.value).toBe('2026-07');
    });

    it('emittiert den gewählten Monat', () => {
      const select = withMonths();
      const selected = vi.fn();
      fixture.componentInstance.select.subscribe(selected);

      select.value = '2019-08';
      select.dispatchEvent(new Event('change'));

      expect(selected).toHaveBeenCalledWith('2019-08');
    });

    // AC 5: per Tastatur bedienbar und beschriftet.
    it('ist beschriftet und ohne Maus bedienbar', () => {
      const select = withMonths();

      // Ein natives <select> in einem <label> trägt Fokus und Tastaturbedienung von sich aus —
      // nachgebaut werden müsste beides nur bei einem div-basierten Dropdown.
      const label = select.closest('label');
      expect(label).not.toBeNull();
      // Die Beschriftung steht im visually-hidden-Span, nicht im textContent des <label> — dort
      // stünden auch die Option-Texte drin und die Assertion wäre wertlos.
      expect(label?.querySelector('.visually-hidden')?.textContent?.trim()).toBe(
        'Monat direkt wählen',
      );
      expect(select.disabled).toBe(false);
    });

    it('lässt den Stepper unangetastet', () => {
      withMonths();
      const prev = vi.fn();
      fixture.componentInstance.prev.subscribe(prev);

      btn('Vorheriger Monat').click();

      expect(prev).toHaveBeenCalledTimes(1);
    });
  });
});
