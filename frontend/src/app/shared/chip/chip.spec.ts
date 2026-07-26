import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Chip } from './chip';

@Component({
  imports: [Chip],
  template: `<button appChip [selected]="selected()">Lebensmittel</button>`,
})
class Host {
  readonly selected = signal(false);
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
});
