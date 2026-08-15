import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Amount } from './amount';
import { formatSwissAmount } from '../format';

describe('formatSwissAmount', () => {
  it('formatiert im Schweizer Format mit Apostroph und zwei Dezimalen', () => {
    expect(formatSwissAmount(1234.5)).toBe("1'234.50");
    expect(formatSwissAmount(980)).toBe('980.00');
    expect(formatSwissAmount(1234567.89)).toBe("1'234'567.89");
  });

  it('ignoriert das Vorzeichen (nur Betrag)', () => {
    expect(formatSwissAmount(-980)).toBe('980.00');
  });
});

describe('Amount', () => {
  let fixture: ComponentFixture<Amount>;

  function setValue(value: number): void {
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Amount] }).compileComponents();
    fixture = TestBed.createComponent(Amount);
  });

  it('zeigt bei positivem Betrag ein +, Klasse und Betrag', () => {
    setValue(1234.5);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.classList).toContain('amount--positive');
    expect(el.querySelector('.amount__sign')!.textContent).toBe('+');
    expect(el.textContent).toContain("1'234.50");
  });

  it('zeigt bei negativem Betrag ein Minuszeichen und die negative Klasse', () => {
    setValue(-980);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.classList).toContain('amount--negative');
    expect(el.querySelector('.amount__sign')!.textContent).toBe('−');
    expect(el.textContent).toContain('980.00');
  });

  it('trägt die Richtung auch ins aria-label (nicht nur Farbe)', () => {
    setValue(-980);
    expect(fixture.nativeElement.getAttribute('aria-label')).toBe('minus 980.00 Franken');
  });

  it('blendet CHF nur bei showCurrency ein', () => {
    setValue(100);
    expect(fixture.nativeElement.querySelector('.amount__currency')).toBeNull();
    fixture.componentRef.setInput('showCurrency', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.amount__currency').textContent).toContain('CHF');
  });

  it('unterdrückt bei hidePositiveSign nur das + eines positiven Betrags, nicht das − eines negativen', () => {
    fixture.componentRef.setInput('hidePositiveSign', true);
    setValue(1234.5);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.amount__sign')!.textContent).toBe('');
    expect(fixture.nativeElement.getAttribute('aria-label')).toBe("1'234.50 Franken");

    setValue(-980);
    expect(el.querySelector('.amount__sign')!.textContent).toBe('−');
    expect(fixture.nativeElement.getAttribute('aria-label')).toBe('minus 980.00 Franken');
  });
});
