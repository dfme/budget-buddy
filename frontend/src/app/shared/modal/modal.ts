import { CdkTrapFocus } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Button } from '../button/button';

/** Laufende Nummer, damit `aria-labelledby` auch bei mehreren Dialogen eindeutig bleibt. */
let nextId = 0;

/**
 * Bestätigungs-Dialog der Design-Variante A (FE-PDF-03) — Titel, projizierter Text und
 * zwei Aktionen.
 *
 * <p>Die Komponente hält bewusst **keinen** Offen-Zustand: der Parent rendert sie per `@if`,
 * genau wie {@link Notice}. Dadurch bleibt der State dort, wo die Entscheidung fällt, und der
 * Fokus wird beim Entfernen sauber zurückgegeben.
 *
 * <pre>
 * &#64;if (showDialog()) {
 *   &lt;app-modal title="…" confirmLabel="Trotzdem importieren"
 *              (confirm)="…" (cancel)="showDialog.set(false)"&gt;Text&lt;/app-modal&gt;
 * }
 * </pre>
 *
 * <p><strong>Warum kein natives `&lt;dialog&gt;`:</strong> Es brächte Fokus-Falle, Escape und
 * Top-Layer gratis mit, ist in der Testumgebung aber nicht ausführbar — jsdom 28 implementiert
 * `HTMLDialogElement` nicht (`showModal is not a function`), womit sich kein einziger
 * Dialog-Testfall schreiben liesse. Stattdessen `cdkTrapFocus` aus `@angular/cdk/a11y`: das ist
 * genau der in FE-UI-02 (#99) dokumentierte Zweck des CDK — eigener Variante-A-Look über
 * Tokens, a11y-harte Primitive aus der Bibliothek.
 */
@Component({
  selector: 'app-modal',
  imports: [Button, CdkTrapFocus],
  templateUrl: './modal.html',
  styleUrl: './modal.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    // Escape schliesst wie ein Klick auf «Abbrechen». Am document, nicht am Panel: der Fokus
    // liegt zwar in der Falle, aber `keydown` am body (z. B. direkt nach dem Öffnen) erreicht
    // ein Panel-Listener nicht.
    '(document:keydown.escape)': 'cancel.emit()',
  },
})
export class Modal {
  /** Überschrift des Dialogs; benennt ihn zugleich für Screenreader (`aria-labelledby`). */
  readonly title = input.required<string>();

  /** Beschriftung der bestätigenden Aktion. */
  readonly confirmLabel = input('Bestätigen');

  /** Beschriftung der abbrechenden Aktion. */
  readonly cancelLabel = input('Abbrechen');

  /** Der User hat die Aktion bestätigt. */
  readonly confirm = output<void>();

  /** Der User hat abgebrochen — über den Button, Escape oder einen Klick auf den Hintergrund. */
  readonly cancel = output<void>();

  /** ID des Titels für `aria-labelledby`. */
  readonly titleId = `modal-title-${nextId++}`;
}
