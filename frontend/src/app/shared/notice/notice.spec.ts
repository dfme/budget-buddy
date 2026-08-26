import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Notice, type NoticeVariant } from './notice';

@Component({
  imports: [Notice],
  template: `<app-notice [variant]="variant()" [title]="title()">Achtung</app-notice>`,
})
class Host {
  readonly variant = signal<NoticeVariant>('warning');
  readonly title = signal<string | undefined>(undefined);
}

/** So schreiben es die Aufruforte: statisches Attribut statt Signal-Binding. */
@Component({
  imports: [Notice],
  template: `<app-notice title="Kein Einkommen erfasst">Bitte erfasse dein Einkommen</app-notice>`,
})
class StaticTitleHost {}

describe('Notice', () => {
  let fixture: ComponentFixture<Host>;

  function notice(): HTMLElement {
    return fixture.nativeElement.querySelector('app-notice');
  }

  function icon(): HTMLElement | null {
    return notice().querySelector('.notice__icon');
  }

  function body(): HTMLElement {
    return notice().querySelector('.notice__body') as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host, StaticTitleHost] }).compileComponents();
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

  // -- FE-UI-07: Icon ---------------------------------------------------------

  const iconPerVariant: ReadonlyArray<readonly [NoticeVariant, string]> = [
    ['warning', '!'],
    ['info', 'i'],
    ['error', '✕'],
  ];

  for (const [variant, glyph] of iconPerVariant) {
    it(`rendert für variant=${variant} das Icon «${glyph}» selbst`, () => {
      fixture.componentInstance.variant.set(variant);
      fixture.detectChanges();

      expect(icon()?.textContent?.trim()).toBe(glyph);
    });
  }

  it('hält das Icon aus dem Screenreader heraus — die Variante trägt allein role', () => {
    expect(icon()?.getAttribute('aria-hidden')).toBe('true');

    fixture.componentInstance.variant.set('error');
    fixture.detectChanges();

    // Kein zweites Signal für dieselbe Information: das Icon bleibt stumm, role wechselt.
    expect(icon()?.getAttribute('aria-hidden')).toBe('true');
    expect(notice().getAttribute('role')).toBe('alert');
  });

  // -- FE-UI-07: Titel --------------------------------------------------------

  it('rendert ohne Titel kein Titel-Element und projiziert den Inhalt unverändert', () => {
    expect(notice().querySelector('.notice__title')).toBeNull();
    expect(body().textContent?.trim()).toBe('Achtung');
  });

  it('stellt den Titel über den Inhalt, nicht daneben', () => {
    fixture.componentInstance.title.set('Kein Einkommen erfasst');
    fixture.detectChanges();

    const title = body().querySelector('.notice__title') as HTMLElement;
    expect(title.textContent?.trim()).toBe('Kein Einkommen erfasst');

    // «Nicht daneben» ist eine Aussage über die Flex-Zeile von :host: die trägt genau zwei
    // Kinder — Icon und Body. Titel und Inhalt liegen beide *im* Body, der stapelt.
    const flexRow = [...notice().children];
    expect(flexRow.map((el) => el.className)).toEqual(['notice__icon', 'notice__body']);
    expect(body().contains(title)).toBe(true);

    // «Über dem Inhalt» ist die Dokumentreihenfolge innerhalb des Bodys.
    const content = [...body().childNodes].find(
      (node) => node !== title && (node.textContent ?? '').trim() === 'Achtung',
    );
    expect(content).toBeDefined();
    expect(title.compareDocumentPosition(content as Node) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('lässt das globale title-Attribut nicht im DOM stehen', async () => {
    // `title` ist zugleich ein globales HTML-Attribut. Ohne Gegenmassnahme setzt Angular bei
    // `title="…"` den Input *und* belässt das Attribut am Host — das gäbe einen nativen
    // Tooltip über dem ganzen Banner und einen Accessible Name auf der Live-Region, der den
    // sichtbaren Titel doppelt vortragen liesse.
    const staticFixture = TestBed.createComponent(StaticTitleHost);
    staticFixture.detectChanges();

    const host: HTMLElement = staticFixture.nativeElement.querySelector('app-notice');
    expect(host.hasAttribute('title')).toBe(false);
    // Der Input ist trotzdem angekommen.
    expect(host.querySelector('.notice__title')?.textContent?.trim()).toBe(
      'Kein Einkommen erfasst',
    );
  });

  it('lässt Icon und role vom Titel unberührt', () => {
    fixture.componentInstance.title.set('Kein Einkommen erfasst');
    fixture.detectChanges();

    expect(icon()?.textContent?.trim()).toBe('!');
    expect(notice().getAttribute('role')).toBe('status');
  });
});
