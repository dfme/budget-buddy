import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Meter } from './meter';

describe('Meter', () => {
  let fixture: ComponentFixture<Meter>;

  function track(): HTMLElement {
    return fixture.nativeElement.querySelector('.meter__track');
  }
  function fill(): HTMLElement {
    return fixture.nativeElement.querySelector('.meter__fill');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Meter] }).compileComponents();
    fixture = TestBed.createComponent(Meter);
  });

  it('setzt Füllbreite und aria-valuenow', () => {
    fixture.componentRef.setInput('value', 42);
    fixture.detectChanges();
    expect(track().getAttribute('role')).toBe('progressbar');
    expect(track().getAttribute('aria-valuenow')).toBe('42');
    expect(fill().style.width).toBe('42%');
  });

  it('begrenzt Werte ausserhalb 0–100', () => {
    fixture.componentRef.setInput('value', 150);
    fixture.detectChanges();
    expect(track().getAttribute('aria-valuenow')).toBe('100');

    fixture.componentRef.setInput('value', -20);
    fixture.detectChanges();
    expect(track().getAttribute('aria-valuenow')).toBe('0');
  });

  it('färbt die Füllung bei variant=negative um', () => {
    fixture.componentRef.setInput('value', 80);
    fixture.componentRef.setInput('variant', 'negative');
    fixture.detectChanges();
    expect(track().classList).toContain('meter__track--negative');
  });
});
