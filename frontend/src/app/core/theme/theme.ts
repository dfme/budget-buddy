import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, Signal, computed, effect, inject, signal } from '@angular/core';

/** Die drei wählbaren Werte des Abschnitts „Erscheinungsbild" (FE-SET-04, US-14). */
export type ThemePreference = 'light' | 'dark' | 'system';

/** Tatsächlich dargestelltes Theme — `system` ist hier bereits aufgelöst. */
export type ResolvedTheme = 'light' | 'dark';

/**
 * `localStorage`-Schlüssel der Theme-Wahl.
 *
 * <p>Der Wert steht ein zweites Mal im Inline-Script in `src/index.html` — dort ohne
 * Import, weil das Script vor dem Bundle läuft. `theme-boot.spec.ts` hält beide Stellen
 * zusammen.
 */
export const THEME_STORAGE_KEY = 'bb-theme';

/** Attribut auf `<html>`, an dem `styles.scss` die Theme-Werte umschaltet (FE-UI-02). */
export const THEME_ATTRIBUTE = 'data-theme';

/** Media Query, aus der „System" seinen Wert bezieht. */
export const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)';

/**
 * Hält die nutzerseitige Theme-Wahl und schreibt sie als `data-theme` auf `<html>`
 * (FE-SET-04, US-14).
 *
 * <p>Die Wahl liegt *client-only* in `localStorage` — kein Feld an `users`, keine Migration,
 * kein Endpoint. Der Scope-Entscheid steht in `design/README.md` („Nutzerseitige
 * Theme-Präferenz") und in US-14; die geräteübergreifende Variante ist bewusst verworfen.
 *
 * <p>„System" wird hier in JavaScript aufgelöst und wie eine feste Wahl als
 * `data-theme="light"`/`"dark"` geschrieben. Der Grund: `styles.scss` kennt Dunkel nur über
 * `:root[data-theme='dark']`, es gibt keinen `prefers-color-scheme`-Block. Ein zweiter Weg in
 * CSS wäre eine zweite Quelle für dieselbe Entscheidung — und {@link ChartTheme} beobachtet
 * genau dieses Attribut, um die Chart.js-Farben neu aufzubauen. Ein Media-Query-Zweig in CSS
 * würde dort nichts auslösen und die Diagramme im alten Theme stehen lassen.
 *
 * <p>Den *ersten* Wert setzt nicht dieser Service, sondern das Inline-Script in `index.html`:
 * bis das Bundle geladen ist, hätte der Browser das falsche Theme längst gezeichnet.
 */
@Injectable({ providedIn: 'root' })
export class Theme {
  private readonly document = inject(DOCUMENT);

  /** Gewählte Präferenz; Ausgangswert aus dem Storage, Default `system`. */
  private readonly preferenceState = signal<ThemePreference>(readStoredPreference(this.storage));

  /** Zeigt das Betriebssystem gerade Dunkel an? */
  private readonly systemDark = signal(false);

  /** Gewählte Präferenz (lesend). Schreiben geht nur über {@link select}. */
  readonly preference: Signal<ThemePreference> = this.preferenceState.asReadonly();

  /** Das Theme, das tatsächlich dargestellt wird. */
  readonly resolved: Signal<ResolvedTheme> = computed(() => {
    const preference = this.preferenceState();
    if (preference === 'system') {
      return this.systemDark() ? 'dark' : 'light';
    }
    return preference;
  });

  constructor() {
    this.watchSystemPreference();

    effect(() => {
      this.document.documentElement.setAttribute(THEME_ATTRIBUTE, this.resolved());
    });
  }

  /** Übernimmt eine Wahl aus den Einstellungen und merkt sie sich für den nächsten Besuch. */
  select(preference: ThemePreference): void {
    this.preferenceState.set(preference);

    // `localStorage` kann blockiert sein (Private Mode, Storage-Policy). Dann gilt die Wahl
    // für diese Sitzung und ist beim nächsten Besuch wieder `system` — das ist besser, als
    // die Umschaltung an einem Speicherfehler scheitern zu lassen.
    try {
      this.storage?.setItem(THEME_STORAGE_KEY, preference);
    } catch {
      /* absichtlich ignoriert — siehe oben */
    }
  }

  /**
   * Meldet den Service am Betriebssystem-Theme an.
   *
   * <p>`matchMedia` ist nicht überall vorhanden — in der Vitest-Umgebung etwa fehlt es. Ohne
   * die Abfrage bliebe „System" dann hell; der Service muss deswegen aber nicht scheitern,
   * sonst reisst er jede Komponente mit, die ihn injiziert.
   */
  private watchSystemPreference(): void {
    const view = this.document.defaultView;
    if (typeof view?.matchMedia !== 'function') {
      return;
    }

    const query = view.matchMedia(DARK_MEDIA_QUERY);
    this.systemDark.set(query.matches);

    const onChange = (event: MediaQueryListEvent) => this.systemDark.set(event.matches);
    query.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => query.removeEventListener('change', onChange));
  }

  /** `localStorage`, sofern der Zugriff darauf überhaupt erlaubt ist. */
  private get storage(): Storage | null {
    try {
      return this.document.defaultView?.localStorage ?? null;
    } catch {
      // Der blosse Zugriff auf `localStorage` wirft, wenn Cookies/Storage gesperrt sind.
      return null;
    }
  }
}

/** Liest die gespeicherte Wahl; alles Unbekannte gilt als `system` (Default nach AC3). */
function readStoredPreference(storage: Storage | null): ThemePreference {
  let stored: string | null = null;
  try {
    stored = storage?.getItem(THEME_STORAGE_KEY) ?? null;
  } catch {
    /* siehe `storage` — Zugriff kann werfen */
  }

  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
}
