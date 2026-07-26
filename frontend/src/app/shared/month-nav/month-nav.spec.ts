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
});
