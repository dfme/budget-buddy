import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Shell } from './core/layout/shell';

/**
 * Root-Component. Hält nur noch den Router-Outlet und reicht ihn als Inhalt in
 * die App-Shell (FE-UI-04) — Navigation, Topbar und Konto/Logout leben dort.
 *
 * <p>Bewusst genau ein Outlet: die Shell blendet ihre Chrome selbst aus,
 * solange niemand eingeloggt ist. Ihn stattdessen je Auth-Zustand zu
 * duplizieren würde die aktive Route-Komponente beim Login zerstören und neu
 * aufbauen.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Shell],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
