import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Card } from './card';

/** So schreiben es die Aufruforte: statisches Attribut statt Signal-Binding. */
@Component({
  imports: [Card],
  template: `<app-card title="Ausgaben nach Kategorie">Inhalt</app-card>`,
})
class StaticTitleHost {}

describe('Card', () => {
  let fixture: ComponentFixture<Card>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Card, StaticTitleHost] }).compileComponents();
    fixture = TestBed.createComponent(Card);
  });

  it('lässt den Kopf weg, wenn weder Titel noch Meta gesetzt sind', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.card__head')).toBeNull();
  });

  it('rendert Titel und Meta', () => {
    fixture.componentRef.setInput('title', 'Ausgaben');
    fixture.componentRef.setInput('meta', 'Juli 2026');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.card__title').textContent).toContain('Ausgaben');
    expect(fixture.nativeElement.querySelector('.card__meta').textContent).toContain('Juli 2026');
  });

  it('lässt das globale title-Attribut nicht im DOM stehen', () => {
    // `title` ist zugleich ein globales HTML-Attribut. Ohne Gegenmassnahme setzt Angular bei
    // `title="…"` den Input *und* belässt das Attribut am Host — das gäbe einen nativen Tooltip
    // über der ganzen Karte und einen Accessible Name auf dem Host, der die sichtbare
    // Überschrift (`.card__title`) doppelt vorträgt (FE-UI-08).
    const staticFixture = TestBed.createComponent(StaticTitleHost);
    staticFixture.detectChanges();

    const host: HTMLElement = staticFixture.nativeElement.querySelector('app-card');
    expect(host.hasAttribute('title')).toBe(false);
    // Der Input ist trotzdem angekommen.
    expect(host.querySelector('.card__title')?.textContent).toContain('Ausgaben nach Kategorie');
  });
});
