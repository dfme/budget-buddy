import { TestBed } from '@angular/core/testing';

import {
  installMatchMedia,
  restoreMatchMedia,
  setSystemDark,
  systemListenerCount,
} from '../../../testing/prefers-color-scheme';
import { THEME_STORAGE_KEY, Theme } from './theme';

/** Liest das Attribut, das die Themes in `styles.scss` umschaltet. */
function appliedTheme(): string | null {
  return document.documentElement.getAttribute('data-theme');
}

/** Erzeugt den Service und lässt seinen `effect()` einmal laufen. */
function createTheme(): Theme {
  const theme = TestBed.inject(Theme);
  TestBed.tick();
  return theme;
}

describe('Theme', () => {
  beforeEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    installMatchMedia(false);
  });

  afterEach(() => {
    restoreMatchMedia();
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  // --- AC3: Default „System" ---

  it('startet ohne gespeicherte Wahl auf „System" und folgt dem hellen Betriebssystem', () => {
    const theme = createTheme();

    expect(theme.preference()).toBe('system');
    expect(theme.resolved()).toBe('light');
    expect(appliedTheme()).toBe('light');
  });

  it('folgt dem dunklen Betriebssystem, wenn keine Wahl getroffen wurde', () => {
    installMatchMedia(true);

    const theme = createTheme();

    expect(theme.preference()).toBe('system');
    expect(appliedTheme()).toBe('dark');
  });

  it('übernimmt einen späteren Wechsel im Betriebssystem ohne Zutun', () => {
    createTheme();
    expect(appliedTheme()).toBe('light');

    setSystemDark(true);
    TestBed.tick();

    expect(appliedTheme()).toBe('dark');
  });

  it('behandelt einen unbekannten gespeicherten Wert wie „System"', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'solarized');

    expect(createTheme().preference()).toBe('system');
  });

  // --- AC1/AC6: Umschalten setzt data-theme, Wahl wird gespeichert ---

  it('setzt data-theme und speichert die Wahl', () => {
    const theme = createTheme();

    theme.select('dark');
    TestBed.tick();

    expect(theme.preference()).toBe('dark');
    expect(appliedTheme()).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    theme.select('light');
    TestBed.tick();

    expect(appliedTheme()).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('speichert auch die Rückkehr zu „System"', () => {
    const theme = createTheme();

    theme.select('dark');
    theme.select('system');
    TestBed.tick();

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('system');
    expect(appliedTheme()).toBe('light');
  });

  // --- AC4: Die Wahl überlebt den Reload ---

  it('stellt eine gespeicherte Wahl beim nächsten Start wieder her', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');

    const theme = createTheme();

    expect(theme.preference()).toBe('dark');
    expect(appliedTheme()).toBe('dark');
  });

  // --- AC1/AC3: Die explizite Wahl schlägt das Betriebssystem ---

  it('ignoriert den Wechsel im Betriebssystem, solange eine feste Wahl gilt', () => {
    const theme = createTheme();
    theme.select('light');
    TestBed.tick();

    setSystemDark(true);
    TestBed.tick();

    expect(appliedTheme()).toBe('light');
  });

  it('gibt die Führung mit „System" ans Betriebssystem zurück', () => {
    const theme = createTheme();
    theme.select('light');
    setSystemDark(true);
    TestBed.tick();
    expect(appliedTheme()).toBe('light');

    theme.select('system');
    TestBed.tick();

    expect(appliedTheme()).toBe('dark');
  });

  // --- Robustheit ---

  it('lädt auch, wenn der Zugriff auf localStorage geblockt ist', () => {
    const descriptor = Object.getOwnPropertyDescriptor(window, 'localStorage');
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      get() {
        throw new Error('Storage ist in diesem Kontext gesperrt');
      },
    });

    try {
      const theme = createTheme();
      expect(theme.preference()).toBe('system');

      // Die Umschaltung selbst darf am Speicherfehler nicht scheitern.
      theme.select('dark');
      TestBed.tick();
      expect(appliedTheme()).toBe('dark');
    } finally {
      if (descriptor) {
        Object.defineProperty(window, 'localStorage', descriptor);
      } else {
        Reflect.deleteProperty(window, 'localStorage');
      }
    }
  });

  it('kommt ohne matchMedia aus und bleibt dann hell', () => {
    restoreMatchMedia();

    const theme = createTheme();

    expect(theme.preference()).toBe('system');
    expect(appliedTheme()).toBe('light');
  });

  it('meldet den Listener beim Zerstören des Injectors wieder ab', () => {
    createTheme();
    expect(systemListenerCount()).toBe(1);

    TestBed.resetTestingModule();

    expect(systemListenerCount()).toBe(0);
  });
});
