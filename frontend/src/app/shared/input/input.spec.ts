import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Input } from './input';

// Host-Test: appInput ist ein Attribut auf nativem <input>, daher über einen Wrapper getestet.
@Component({
  imports: [Input],
  template: `<input appInput type="email" />`,
})
class Host {}

describe('Input', () => {
  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it('bleibt ein natives input-Element (a11y)', () => {
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.tagName).toBe('INPUT');
    expect(input.type).toBe('email');
  });
});
