import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Segment, SegmentOption } from './segment';

const OPTIONS: SegmentOption[] = [
  { value: 'all', label: 'Alle' },
  { value: 'income', label: 'Einnahmen' },
];

describe('Segment', () => {
  let fixture: ComponentFixture<Segment>;

  function buttons(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('button'));
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Segment] }).compileComponents();
    fixture = TestBed.createComponent(Segment);
    fixture.componentRef.setInput('options', OPTIONS);
    fixture.componentRef.setInput('value', 'all');
    fixture.detectChanges();
  });

  it('markiert die aktive Option per Klasse und aria-pressed', () => {
    const [all, income] = buttons();
    expect(all.classList).toContain('segment__item--active');
    expect(all.getAttribute('aria-pressed')).toBe('true');
    expect(income.getAttribute('aria-pressed')).toBe('false');
  });

  it('aktualisiert value beim Klick (Zweiweg-Bindung)', () => {
    buttons()[1].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe('income');
    expect(buttons()[1].classList).toContain('segment__item--active');
  });
});
