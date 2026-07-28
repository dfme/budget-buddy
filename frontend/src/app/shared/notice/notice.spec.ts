import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Notice } from './notice';

@Component({
  imports: [Notice],
  template: `<app-notice [variant]="variant()">Achtung</app-notice>`,
})
class Host {
  readonly variant = signal<'warning' | 'info' | 'error'>('warning');
}

describe('Notice', () => {
  let fixture: ComponentFixture<Host>;

  function notice(): HTMLElement {
    return fixture.nativeElement.querySelector('app-notice');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it('projiziert den Inhalt und meldet sich als status', () => {
    expect(notice().textContent).toContain('Achtung');
    expect(notice().getAttribute('role')).toBe('status');
  });

  it('setzt die info-Klasse nur bei variant=info', () => {
    expect(notice().classList).not.toContain('notice--info');
    fixture.componentInstance.variant.set('info');
    fixture.detectChanges();
    expect(notice().classList).toContain('notice--info');
  });

  it('meldet sich bei variant=error assertiv als alert und färbt als Fehler', () => {
    fixture.componentInstance.variant.set('error');
    fixture.detectChanges();

    expect(notice().getAttribute('role')).toBe('alert');
    expect(notice().classList).toContain('notice--error');
  });

  it('bleibt bei variant=info höflich (status)', () => {
    fixture.componentInstance.variant.set('info');
    fixture.detectChanges();

    expect(notice().getAttribute('role')).toBe('status');
  });
});
