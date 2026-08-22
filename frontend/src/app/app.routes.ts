import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { devOnlyGuard } from './core/guards/dev-only.guard';
import { onboardingGuard } from './core/guards/onboarding.guard';

/**
 * Platzhalter-Routes für das Skeleton. Feature-Routes werden mit den jeweiligen
 * User Stories ergänzt (Struktur: docs CLAUDE.md → Frontend: Feature-Struktur).
 */
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  // `authGuard` entscheidet über anonyme Nutzer, `onboardingGuard` über den Wizard-Zwang
  // (FE-FC-02). Angular führt beide nebenläufig aus — die Reihenfolge hier ist Lesbarkeit,
  // keine Zusicherung; das Zusammenspiel steht in `onboarding.guard.ts`.
  {
    path: 'dashboard',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'categories',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () => import('./transactions/category-overview').then((m) => m.CategoryOverview),
  },
  {
    path: 'import',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () => import('./transactions/pdf-upload').then((m) => m.PdfUpload),
  },
  {
    // Vor INFRA-17 kollidierte `/fixed-costs` mit dem gleichnamigen API-Prefix von
    // FixedCostController (ein Angular-Pfad hätte beim Hard-Reload den Backend-Endpoint statt
    // index.html getroffen). Seit alle REST-Endpoints unter /api/** liegen, wäre der Name
    // frei — bleibt trotzdem `fixkosten`, eine Umbenennung hätte hier keinen Mehrwert und
    // würde nur Links/Bookmarks brechen.
    path: 'fixkosten',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () => import('./onboarding/fixed-cost-list').then((m) => m.FixedCostList),
  },
  {
    // Ohne `onboardingGuard` — das Ziel der Umleitung darf sich nicht selbst umleiten.
    path: 'onboarding',
    canActivate: [authGuard],
    loadComponent: () => import('./onboarding/fixed-cost-wizard').then((m) => m.FixedCostWizard),
  },
  {
    path: 'login',
    loadComponent: () => import('./auth/login').then((m) => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register').then((m) => m.Register),
  },
  {
    // Dev-only Komponenten-Showcase (FE-UI-03), nicht in der Navigation verlinkt.
    path: 'styleguide',
    canActivate: [devOnlyGuard],
    loadComponent: () => import('./styleguide/styleguide').then((m) => m.Styleguide),
  },
  { path: '**', redirectTo: 'dashboard' },
];
