import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Input } from './input';

// Host-Test: appInput ist ein Attribut auf nativem <input>/<select>, daher über einen Wrapper.
@Component({
  imports: [Input],
  template: `<input appInput type="email" />
    <select appInput>
      <option value="a">A</option>
    </select>`,
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

  it('greift auch auf einem nativen select-Element (FE-FC-01)', () => {
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.tagName).toBe('SELECT');
    // Das Attribut allein beweist nichts — entscheidend ist, dass Angular die Komponente
    // instanziiert und damit ihr gekapseltes Styling anhängt (_ngcontent-Attribut).
    expect(select.getAttributeNames().some((name) => name.startsWith('_nghost'))).toBe(true);
  });
});
