import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Badge } from './badge';

describe('Badge', () => {
  let fixture: ComponentFixture<Badge>;

  function icon(): HTMLElement | null {
    return fixture.nativeElement.querySelector('.badge__icon');
  }

  function dot(): HTMLElement | null {
    return fixture.nativeElement.querySelector('.badge__dot');
  }

  function render(label: string, category?: string): void {
    fixture.componentRef.setInput('label', label);
    fixture.componentRef.setInput('category', category);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Badge] }).compileComponents();
    fixture = TestBed.createComponent(Badge);
  });

  it('rendert das Label und das Kategorie-Icon', () => {
    render('Lebensmittel', 'lebensmittel');

    expect(fixture.nativeElement.querySelector('.badge__label').textContent).toContain(
      'Lebensmittel',
    );
    expect(icon()?.textContent?.trim()).toBe('🛒');
  });

  it('spiegelt den Kategorie-Slug als data-cat für die Token-Farbe', () => {
    render('Wohnen', 'wohnen');

    expect(fixture.nativeElement.getAttribute('data-cat')).toBe('wohnen');
  });

  it('rendert auch für die Kategorien aus BE-CAT-10 ein Icon', () => {
    render('Bargeldbezug', 'bargeldbezug');

    expect(icon()?.textContent?.trim()).toBe('🏧');
  });

  // Das Label steht direkt daneben und sagt dasselbe. Ohne aria-hidden läse ein Screenreader
  // je nach Plattform «Geldautomat Bargeldbezug» — die Ansage verdoppelt sich, ohne dass ein
  // Wort dazukommt.
  it('blendet das Icon für Screenreader aus', () => {
    render('Lebensmittel', 'lebensmittel');

    expect(icon()?.getAttribute('aria-hidden')).toBe('true');
  });

  // Vor BE-CAT-10 trug der Punkt die Kategoriefarbe; jetzt trägt sie der Text und der Punkt ist
  // nur noch da, wo kein Icon auflösbar ist. Beides gleichzeitig darf nie erscheinen.
  it('zeigt Icon statt Punkt, solange die Kategorie bekannt ist', () => {
    render('Lebensmittel', 'lebensmittel');

    expect(icon()).not.toBeNull();
    expect(dot()).toBeNull();
  });

  it('fällt bei unbekanntem Slug auf den neutralen Punkt zurück', () => {
    render('Weltraumtourismus', 'weltraumtourismus');

    expect(icon()).toBeNull();
    expect(dot()).not.toBeNull();
  });

  it('fällt ohne Kategorie auf den neutralen Punkt zurück', () => {
    render('Ohne Kategorie');

    expect(icon()).toBeNull();
    expect(dot()).not.toBeNull();
  });
});
