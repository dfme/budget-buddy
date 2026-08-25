import indexHtml from '../../../index.html';

import { DARK_MEDIA_QUERY, THEME_ATTRIBUTE, THEME_STORAGE_KEY } from './theme';

/**
 * Hält das Pre-Paint-Script in `src/index.html` mit {@link Theme} zusammen (AC4).
 *
 * <p>Das Script muss inline im `<head>` stehen: bis das Angular-Bundle läuft, hat der Browser
 * längst gezeichnet, und bei gewähltem Dunkel-Theme blitzte die helle Oberfläche auf. Der
 * Preis dafür ist eine Verdopplung — Schlüssel und Attributwerte stehen dort ohne Import ein
 * zweites Mal. Läuft eine Seite davon weg, kommt der Flash zurück, ohne dass irgendein
 * Verhaltenstest anschlägt: die App korrigiert das Theme ja Sekundenbruchteile später. Genau
 * diese stumme Regression fängt dieser Test ab.
 */
describe('Pre-Paint-Theme in index.html', () => {
  const head = indexHtml.slice(0, indexHtml.indexOf('</head>'));

  it('führt das Script im <head> aus, nicht erst im <body>', () => {
    expect(indexHtml).toContain('</head>');
    expect(head).toContain('<script>');
  });

  it('liest denselben localStorage-Schlüssel wie der Service', () => {
    expect(head).toContain(`localStorage.getItem('${THEME_STORAGE_KEY}')`);
  });

  it('setzt dasselbe Attribut mit denselben Werten wie der Service', () => {
    expect(head).toContain(`setAttribute('${THEME_ATTRIBUTE}'`);
    expect(head).toContain("'dark' : 'light'");
  });

  it('fragt dieselbe Media Query ab wie der Service', () => {
    expect(head).toContain(`matchMedia('${DARK_MEDIA_QUERY}')`);
  });

  it('fängt einen gesperrten Storage ab, statt den Seitenaufbau abzubrechen', () => {
    expect(head).toMatch(/try\s*{[\s\S]*localStorage[\s\S]*}\s*catch/);
  });
});
