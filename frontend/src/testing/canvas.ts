/**
 * jsdom-Ersatz für die Browser-APIs, die Chart.js beim Rendern braucht.
 *
 * <p>jsdom implementiert weder einen 2D-Canvas-Kontext noch `ResizeObserver`. Ohne Kontext
 * baut Chart.js kein Chart auf (`Failed to create chart: can't acquire context`), und
 * `responsive: true` greift auf `ResizeObserver` zu. Ein echtes `canvas`-Paket wäre ein
 * nativer Build nur für die Tests — dieser Stub reicht, weil die Specs die
 * Chart-*Konfiguration* und das DOM prüfen, nicht gezeichnete Pixel.
 */

/** Merkt sich die Originale, damit {@link restoreCanvasStub} sie zurückgeben kann. */
let originalGetContext: typeof HTMLCanvasElement.prototype.getContext | undefined;
let hadResizeObserver = false;

/**
 * Zeichenmethoden, die Chart.js auf dem 2D-Kontext aufruft. Bewusst als feste Liste und
 * nicht als Catch-all-Proxy: ein Proxy, der auf *jede* Property eine Funktion liefert,
 * beantwortet auch `length` — und Chart.js' `getCanvas()` hält den Kontext dann für ein
 * Array-artiges Objekt und greift auf `item[0]` zu. Ergebnis wäre ein Chart ohne Canvas.
 */
const NO_OP_METHODS = [
  'arc',
  'arcTo',
  'beginPath',
  'bezierCurveTo',
  'clearRect',
  'clip',
  'closePath',
  'drawImage',
  'ellipse',
  'fill',
  'fillRect',
  'fillText',
  'lineTo',
  'moveTo',
  'putImageData',
  'quadraticCurveTo',
  'rect',
  'resetTransform',
  'restore',
  'rotate',
  'roundRect',
  'save',
  'scale',
  'setLineDash',
  'setTransform',
  'stroke',
  'strokeRect',
  'strokeText',
  'transform',
  'translate',
] as const;

/** Minimaler 2D-Kontext: No-op-Zeichenmethoden plus die Rückgaben, die Chart.js auswertet. */
function createContextStub(canvas: HTMLCanvasElement): CanvasRenderingContext2D {
  const noop = (): undefined => undefined;
  const context: Record<string, unknown> = {
    canvas,
    // Chart.js misst Achsenbeschriftungen; ohne Breite kollabiert das Layout in NaN.
    measureText: (text: string) => ({ width: String(text).length * 6 }),
    getLineDash: () => [],
    createLinearGradient: () => ({ addColorStop: noop }),
    createRadialGradient: () => ({ addColorStop: noop }),
    createPattern: () => null,
    getImageData: () => ({ data: [] }),
    getTransform: () => ({ a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 }),
    isPointInPath: () => false,
  };
  for (const method of NO_OP_METHODS) {
    context[method] = noop;
  }
  return context as unknown as CanvasRenderingContext2D;
}

/** Installiert Canvas-Kontext und `ResizeObserver` im aktuellen jsdom-Fenster. */
export function installCanvasStub(): void {
  originalGetContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement) {
    return createContextStub(this);
  } as unknown as typeof HTMLCanvasElement.prototype.getContext;

  hadResizeObserver = 'ResizeObserver' in globalThis;
  if (!hadResizeObserver) {
    (globalThis as { ResizeObserver?: unknown }).ResizeObserver = class {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    };
  }
}

/** Macht {@link installCanvasStub} rückgängig — im `afterEach` der Chart-Specs. */
export function restoreCanvasStub(): void {
  if (originalGetContext) {
    HTMLCanvasElement.prototype.getContext = originalGetContext;
    originalGetContext = undefined;
  }
  if (!hadResizeObserver) {
    delete (globalThis as { ResizeObserver?: unknown }).ResizeObserver;
  }
}
