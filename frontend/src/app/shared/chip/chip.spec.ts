import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Chip } from './chip';

@Component({
  imports: [Chip],
  template: `<button appChip [selected]="selected()" [category]="category()">Lebensmittel</button>`,
})
class Host {
  readonly selected = signal(false);
  readonly category = signal<string | undefined>(undefined);
}

describe('Chip', () => {
  let fixture: ComponentFixture<Host>;

  function chip(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it('ist ein natives button mit type=button', () => {
    expect(chip().tagName).toBe('BUTTON');
    expect(chip().getAttribute('type')).toBe('button');
  });

  it('spiegelt den Auswahlzustand in Klasse und aria-pressed', () => {
    expect(chip().classList).not.toContain('chip--selected');
    expect(chip().getAttribute('aria-pressed')).toBe('false');

    fixture.componentInstance.selected.set(true);
    fixture.detectChanges();

    expect(chip().classList).toContain('chip--selected');
    expect(chip().getAttribute('aria-pressed')).toBe('true');
  });

  // BE-CAT-10: Der Chip wird nicht nur für Kategorien verwendet. Ohne Slug muss er deshalb
  // exakt so aussehen wie vorher — ein leeres Icon-Element wäre schon eine Verhaltensänderung.
  it('rendert ohne Kategorie kein Icon', () => {
    expect(fixture.nativeElement.querySelector('.chip__icon')).toBeNull();
    expect(chip().textContent?.trim()).toBe('Lebensmittel');
  });

  it('stellt bei gesetzter Kategorie deren Icon voran', () => {
    fixture.componentInstance.category.set('lebensmittel');
    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('.chip__icon');
    expect(icon?.textContent?.trim()).toBe('🛒');
    expect(icon?.getAttribute('aria-hidden')).toBe('true');
  });

  it('rendert bei unbekannter Kategorie kein Icon', () => {
    fixture.componentInstance.category.set('weltraumtourismus');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.chip__icon')).toBeNull();
  });
});
