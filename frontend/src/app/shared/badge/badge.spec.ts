import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Badge } from './badge';

describe('Badge', () => {
  let fixture: ComponentFixture<Badge>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Badge] }).compileComponents();
    fixture = TestBed.createComponent(Badge);
  });

  it('rendert das Label und einen Farbpunkt', () => {
    fixture.componentRef.setInput('label', 'Lebensmittel');
    fixture.componentRef.setInput('category', 'lebensmittel');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.badge__label').textContent).toContain(
      'Lebensmittel',
    );
    expect(fixture.nativeElement.querySelector('.badge__dot')).not.toBeNull();
  });

  it('spiegelt den Kategorie-Slug als data-cat für die Token-Farbe', () => {
    fixture.componentRef.setInput('label', 'Wohnen');
    fixture.componentRef.setInput('category', 'wohnen');
    fixture.detectChanges();

    expect(fixture.nativeElement.getAttribute('data-cat')).toBe('wohnen');
  });
});
