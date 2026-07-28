import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Field } from './field';

// Host-Test: das Feld projiziert eine native Eingabe, daher über einen Wrapper getestet.
@Component({
  imports: [Field],
  template: `
    <app-field [label]="label()" [inputId]="inputId()" [error]="error()">
      <input [id]="inputId()" type="email" />
    </app-field>
  `,
})
class Host {
  readonly label = signal('E-Mail');
  readonly inputId = signal('email');
  readonly error = signal<string | null>(null);
}

describe('Field', () => {
  let fixture: ComponentFixture<Host>;

  function el(selector: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(selector);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it('rendert das Label und verknüpft es per for mit der Eingabe (a11y)', () => {
    const label = el('label')!;
    expect(label.textContent?.trim()).toBe('E-Mail');
    expect(label.getAttribute('for')).toBe('email');
  });

  it('projiziert die native Eingabe', () => {
    expect(el('input')?.tagName).toBe('INPUT');
  });

  it('zeigt keine Fehlermeldung, solange error null ist', () => {
    expect(el('.field__error')).toBeNull();
    expect(el('input')!.hasAttribute('aria-describedby')).toBe(false);
    expect(el('input')!.hasAttribute('aria-invalid')).toBe(false);
  });

  it('verknüpft die Fehlermeldung per aria-describedby mit der Eingabe (a11y)', () => {
    fixture.componentInstance.error.set('E-Mail ist erforderlich.');
    fixture.detectChanges();

    const error = el('.field__error')!;
    const input = el('input')!;
    expect(error.textContent?.trim()).toBe('E-Mail ist erforderlich.');
    expect(error.id).toBe('email-error');
    expect(input.getAttribute('aria-describedby')).toBe('email-error');
    expect(input.getAttribute('aria-invalid')).toBe('true');
  });

  it('räumt die a11y-Verknüpfung wieder ab, sobald der Fehler behoben ist', () => {
    fixture.componentInstance.error.set('E-Mail ist erforderlich.');
    fixture.detectChanges();
    fixture.componentInstance.error.set(null);
    fixture.detectChanges();

    expect(el('.field__error')).toBeNull();
    expect(el('input')!.hasAttribute('aria-describedby')).toBe(false);
    expect(el('input')!.hasAttribute('aria-invalid')).toBe(false);
  });
});
