import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Button } from './button';

// Host-Test: der Button ist ein Attribut auf nativem <button>, daher über einen
// Wrapper getestet statt direkt instanziiert. Signals, damit die zoneless
// Change-Detection Zustandswechsel im Test sauber mitbekommt.
@Component({
  imports: [Button],
  template: `<button appButton [variant]="variant()" [block]="block()">Klick</button>`,
})
class Host {
  readonly variant = signal<'primary' | 'ghost'>('primary');
  readonly block = signal(false);
}

describe('Button', () => {
  let fixture: ComponentFixture<Host>;

  function button(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it('bleibt ein natives button-Element (a11y)', () => {
    expect(button().tagName).toBe('BUTTON');
  });

  it('setzt die primary-Klasse als Default', () => {
    expect(button().classList).toContain('btn--primary');
    expect(button().classList).not.toContain('btn--ghost');
  });

  it('wechselt auf die ghost-Variante', () => {
    fixture.componentInstance.variant.set('ghost');
    fixture.detectChanges();
    expect(button().classList).toContain('btn--ghost');
    expect(button().classList).not.toContain('btn--primary');
  });

  it('setzt block nur, wenn aktiviert', () => {
    expect(button().classList).not.toContain('btn--block');
    fixture.componentInstance.block.set(true);
    fixture.detectChanges();
    expect(button().classList).toContain('btn--block');
  });
});
