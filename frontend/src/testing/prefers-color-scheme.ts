/**
 * Stellt `window.matchMedia` für Tests bereit und macht `prefers-color-scheme` steuerbar.
 *
 * <p>Die Testumgebung kennt `matchMedia` nicht (`window.matchMedia is not a function`).
 * {@link Theme} trägt das und lässt „System" dann hell — für die Tests von AC3 muss der
 * OS-Zustand aber setzbar sein *und* sich ändern können. Der Stub hält deshalb die
 * registrierten Listener und ruft sie bei {@link setSystemDark} auf, genau wie ein Browser
 * es beim Umstellen des Betriebssystem-Themes täte.
 */

interface StubbedQuery {
  matches: boolean;
  readonly listeners: Set<(event: MediaQueryListEvent) => void>;
}

let query: StubbedQuery | null = null;
let original: typeof window.matchMedia | undefined;

/**
 * Installiert den Stub. `dark` ist der Zustand, den das Betriebssystem zu Beginn meldet.
 * Gehört in ein `beforeEach`, gegengleich zu {@link restoreMatchMedia}.
 */
export function installMatchMedia(dark = false): void {
  const stub: StubbedQuery = { matches: dark, listeners: new Set() };
  query = stub;
  original = window.matchMedia;

  window.matchMedia = ((): MediaQueryList => {
    return {
      get matches() {
        return stub.matches;
      },
      media: '(prefers-color-scheme: dark)',
      addEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) =>
        stub.listeners.add(listener),
      removeEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) =>
        stub.listeners.delete(listener),
    } as unknown as MediaQueryList;
  }) as typeof window.matchMedia;
}

/** Simuliert den Wechsel des Betriebssystem-Themes und benachrichtigt die Listener. */
export function setSystemDark(dark: boolean): void {
  if (!query) {
    throw new Error('setSystemDark() ohne installMatchMedia() aufgerufen');
  }
  query.matches = dark;
  for (const listener of query.listeners) {
    listener({ matches: dark } as MediaQueryListEvent);
  }
}

/** Anzahl noch registrierter Listener — Nachweis, dass beim Destroy abgemeldet wird. */
export function systemListenerCount(): number {
  return query?.listeners.size ?? 0;
}

/** Nimmt den Stub zurück. Gehört in ein `afterEach`. */
export function restoreMatchMedia(): void {
  if (original === undefined) {
    Reflect.deleteProperty(window, 'matchMedia');
  } else {
    window.matchMedia = original;
  }
  original = undefined;
  query = null;
}
