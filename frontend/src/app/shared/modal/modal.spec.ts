import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Modal } from './modal';

@Component({
  imports: [Modal],
  template: `
    <button type="button" id="opener">Öffnen</button>
    @if (open()) {
      <app-modal
        title="Kontoauszug bereits importiert"
        confirmLabel="Trotzdem importieren"
        (confirm)="confirmed.set(true)"
        (cancel)="open.set(false)"
      >
        Dieser Kontoauszug wurde bereits importiert.
      </app-modal>
    }
  `,
})
class Host {
  readonly open = signal(true);
  readonly confirmed = signal(false);
}

describe('Modal', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;

  function panel(): HTMLElement {
    return fixture.nativeElement.querySelector('.modal__panel');
  }

  function button(label: string): HTMLButtonElement {
    const match = Array.from<HTMLButtonElement>(
      fixture.nativeElement.querySelectorAll('.modal__actions button'),
    ).find((btn) => btn.textContent?.trim() === label);
    if (!match) {
      throw new Error(`Kein Button mit der Beschriftung "${label}"`);
    }
    return match;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    // attachToDocument: der Fokus-Trap braucht die Komponente im echten Dokument.
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => fixture.nativeElement.remove());

  it('meldet sich als modaler Dialog und wird über seinen Titel benannt', () => {
    expect(panel().getAttribute('role')).toBe('dialog');
    expect(panel().getAttribute('aria-modal')).toBe('true');

    const titleId = panel().getAttribute('aria-labelledby');
    expect(titleId).toBeTruthy();
    expect(fixture.nativeElement.querySelector(`#${titleId}`).textContent).toContain(
      'Kontoauszug bereits importiert',
    );
  });

  it('projiziert den Inhalt und beschriftet beide Aktionen', () => {
    expect(panel().textContent).toContain('Dieser Kontoauszug wurde bereits importiert.');
    expect(button('Trotzdem importieren')).toBeTruthy();
    // Ohne cancelLabel greift der Default.
    expect(button('Abbrechen')).toBeTruthy();
  });

  it('emittiert confirm beim Klick auf die bestätigende Aktion', () => {
    button('Trotzdem importieren').click();

    expect(host.confirmed()).toBe(true);
  });

  it('emittiert cancel beim Klick auf Abbrechen', () => {
    button('Abbrechen').click();
    fixture.detectChanges();

    expect(host.open()).toBe(false);
    expect(host.confirmed()).toBe(false);
  });

  it('emittiert cancel beim Klick auf den Hintergrund', () => {
    fixture.nativeElement.querySelector('.modal__backdrop').click();
    fixture.detectChanges();

    expect(host.open()).toBe(false);
  });

  it('emittiert cancel bei Escape', () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(host.open()).toBe(false);
    expect(host.confirmed()).toBe(false);
  });

  it('spannt eine Fokus-Falle um das Panel und markiert die folgenlose Aktion als Startfokus', () => {
    // Geprüft wird die Verdrahtung, nicht der tatsächliche Fokus: jsdom rechnet kein Layout,
    // `getClientRects()` ist immer leer (verifiziert) — CDKs InteractivityChecker hält damit
    // jedes Element für unsichtbar und fokussiert nichts. Die Anker beweisen, dass die Falle
    // aktiv ist; dass der Fokus real hineinspringt, ist Browser-Verhalten.
    expect(fixture.nativeElement.querySelectorAll('.cdk-focus-trap-anchor')).toHaveLength(2);
    expect(button('Abbrechen').hasAttribute('cdkFocusInitial')).toBe(true);
    expect(button('Trotzdem importieren').hasAttribute('cdkFocusInitial')).toBe(false);
  });
});
