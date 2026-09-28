import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Button } from './button';

// Host-Test: der Button ist ein Attribut auf nativem <button>, daher über einen
// Wrapper getestet statt direkt instanziiert. Signals, damit die zoneless
// Change-Detection Zustandswechsel im Test sauber mitbekommt.
@Component({
  imports: [Button],
  template: `
    <button appButton [variant]="variant()" [block]="block()">Klick</button>
    <button appButton class="with-icon" [iconOnlyMobile]="iconOnlyMobile()">
      <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24"></svg>
      Löschen
    </button>
  `,
})
class Host {
  readonly variant = signal<'primary' | 'ghost'>('primary');
  readonly block = signal(false);
  readonly iconOnlyMobile = signal(false);
}

describe('Button', () => {
  let fixture: ComponentFixture<Host>;

  function button(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button');
  }

  function iconButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button.with-icon');
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

  // --- FE-FC-08: Icon-Slot und Label ---

  it('legt den Text in ein eigenes Label-Element, ohne den Text zu verändern', () => {
    const label = button().querySelector('.btn__label');
    expect(label?.textContent?.trim()).toBe('Klick');
    expect(button().textContent?.trim()).toBe('Klick');
  });

  it('projiziert ein svg als Icon vor das Label, nicht hinein', () => {
    const svg = iconButton().querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg?.closest('.btn__label')).toBeNull();
    expect(iconButton().firstElementChild).toBe(svg);
    // Das Icon trägt keinen Text bei: die Beschriftung bleibt genau das Label.
    expect(iconButton().textContent?.trim()).toBe('Löschen');
  });

  it('setzt iconOnlyMobile nur, wenn aktiviert', () => {
    expect(iconButton().classList).not.toContain('btn--icon-only-mobile');
    fixture.componentInstance.iconOnlyMobile.set(true);
    fixture.detectChanges();
    expect(iconButton().classList).toContain('btn--icon-only-mobile');
    // Das Label bleibt im DOM — versteckt wird es nur visuell, per CSS am Breakpoint.
    expect(iconButton().querySelector('.btn__label')?.textContent?.trim()).toBe('Löschen');
  });
});
