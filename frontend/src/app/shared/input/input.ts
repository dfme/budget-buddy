import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Basis-Eingabefeld der Design-Variante A (FE-UI-03).
 *
 * <p>Bewusst als Attribut-Selektor auf nativem `<input>` (analog {@link Button}): so bleiben
 * `type`, `formControlName`, `autocomplete`, Tastaturbedienung und der globale Fokus-Ring aus
 * dem Token-Fundament unverändert erhalten. Bei Validierungsfehlern setzt Angular
 * `ng-invalid`/`ng-touched` — daraus entsteht der rote Rahmen ohne Zutun des Consumers.
 *
 * <p>Meist innerhalb einer {@link Field}-Komponente verwendet, die Label und Fehlermeldung
 * beisteuert.
 */
@Component({
  selector: 'input[appInput]',
  template: '',
  styleUrl: './input.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Input {}
