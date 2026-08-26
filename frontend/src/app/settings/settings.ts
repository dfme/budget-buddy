import { ChangeDetectionStrategy, Component } from '@angular/core';

import { Card } from '../shared/card/card';

/**
 * Einstellungen-Screen als Gerüst (FE-SET-01, US-14).
 *
 * <p>Nur Route, Navigation und drei leere Abschnitts-Cards. Der Inhalt kommt mit den
 * folgenden Tasks: Passwort ändern (FE-SET-02), Einkommen ändern (FE-SET-03),
 * Erscheinungsbild (FE-SET-04) — alle drei hängen an diesem Task.
 */
@Component({
  selector: 'app-settings',
  imports: [Card],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Settings {}
