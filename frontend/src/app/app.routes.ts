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
    // FE-FC-07: Die Seite zeigt seit FE-FC-05 Fixkosten-Tabelle UND «Erkannte Abos» — der
    // Name «Fixkosten» passte seither nicht mehr, deshalb heisst sie «Ausgaben» (Route,
    // Nav-Label, h1). Bis FE-FC-07 stand hier, eine Umbenennung hätte «keinen Mehrwert» und
    // bräche nur Bookmarks; #355 hat das bewusst umgekehrt, die Bookmarks fangen die beiden
    // Redirects darunter auf. (Der historische Name `fixkosten` stammt aus der Zeit vor
    // INFRA-17, als `/fixed-costs` mit dem API-Prefix von FixedCostController kollidierte.)
    path: 'ausgaben',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () => import('./onboarding/fixed-cost-list').then((m) => m.FixedCostList),
  },
  {
    // Alter Pfad der Seite (bis FE-FC-07). Bleibt als Umleitung, damit Bookmarks und ältere
    // Links nicht im Catch-all aufs Dashboard landen. Guards braucht die Umleitung nicht —
    // /ausgaben bringt seine eigenen mit. `pathMatch: 'full'`, weil ein Redirect sonst als
    // Präfix greift und /fixkosten/x nach /ausgaben/x schickte.
    path: 'fixkosten',
    pathMatch: 'full',
    redirectTo: 'ausgaben',
  },
  {
    // Alter Pfad der Abo-Übersicht (bis FE-FC-05 eigene Seite, seither Abschnitt auf der
    // Ausgaben-Seite). Gleiche Begründung wie /fixkosten.
    path: 'abos',
    pathMatch: 'full',
    redirectTo: 'ausgaben',
  },
  {
    path: 'einstellungen',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () => import('./settings/settings').then((m) => m.Settings),
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
