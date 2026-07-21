import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

/**
 * Platzhalter-Routes für das Skeleton. Feature-Routes werden mit den jeweiligen
 * User Stories ergänzt (Struktur: docs CLAUDE.md → Frontend: Feature-Struktur).
 */
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'categories',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./transactions/category-overview').then((m) => m.CategoryOverview),
  },
  {
    path: 'login',
    loadComponent: () => import('./auth/login').then((m) => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register').then((m) => m.Register),
  },
  { path: '**', redirectTo: 'dashboard' },
];
