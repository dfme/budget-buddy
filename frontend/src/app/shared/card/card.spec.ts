import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Card } from './card';

describe('Card', () => {
  let fixture: ComponentFixture<Card>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Card] }).compileComponents();
    fixture = TestBed.createComponent(Card);
  });

  it('lässt den Kopf weg, wenn weder Titel noch Meta gesetzt sind', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.card__head')).toBeNull();
  });

  it('rendert Titel und Meta', () => {
    fixture.componentRef.setInput('title', 'Ausgaben');
    fixture.componentRef.setInput('meta', 'Juli 2026');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.card__title').textContent).toContain('Ausgaben');
    expect(fixture.nativeElement.querySelector('.card__meta').textContent).toContain('Juli 2026');
  });
});
