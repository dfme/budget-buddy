import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Basis-Eingabefeld der Design-Variante A (FE-UI-03).
 *
 * <p>Bewusst als Attribut-Selektor auf nativem `<input>`/`<select>` (analog {@link Button}): so
 * bleiben `type`, `formControlName`, `autocomplete`, Tastaturbedienung und der globale Fokus-Ring
 * aus dem Token-Fundament unverändert erhalten. Bei Validierungsfehlern setzt Angular
 * `ng-invalid`/`ng-touched` — daraus entsteht der rote Rahmen ohne Zutun des Consumers.
 *
 * <p>`<select>` teilt sich die Gestaltung mit `<input>`, statt sie in den Feature-Stylesheets
 * zu wiederholen (FE-FC-01: Intervall-Auswahl). Eine eigene Select-Komponente gibt es bewusst
 * nicht — die native Auswahl bringt Tastatur- und Touch-Verhalten mit, das ein Nachbau erst
 * wieder herstellen müsste.
 *
 * <p>Meist innerhalb einer {@link Field}-Komponente verwendet, die Label und Fehlermeldung
 * beisteuert.
 */
@Component({
  selector: 'input[appInput], select[appInput]',
  // `<ng-content />` statt eines leeren Templates: sonst verwirft Angular die Kind-Elemente
  // des Host-Tags — bei `<select>` also genau die `<option>`s. Für `<input>` ist es folgenlos,
  // weil ein void element keine Kinder haben kann (analog {@link Button}).
  template: '<ng-content />',
  styleUrl: './input.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Input {}
