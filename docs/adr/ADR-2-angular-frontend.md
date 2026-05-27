# ADR-2: Angular 21.x als Frontend-Framework

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy benötigt ein Frontend, das:

- Web-App für Studierende (low-tech Device-Profile)
- Responsive Design (Desktop, Tablet, Mobile)
- Sichere Authentifizierung + Token-Management (JWT)
- Komplexe Forms (Fixkosten-Wizard, PDF-Upload, Transaktions-Kategorisierung)
- State Management (User-Session, Transaktions-Cache, Kategorie-Daten)
- Charts/Visualisierungen (Kategorien-Pie, Vergleiche, Safe-to-Spend)
- Performance: Schnelles Initial Load, kein Bloat

Alternative Frontend Frameworks: React, Vue, Svelte

## Decision

Wir nutzen **Angular 21.x** mit folgender Konfiguration:

- **Runtime:** TypeScript 5.x
- **Components:** Standalone Components + Signals (Angular 21 Modern API)
- **State:** Angular Signals + RxJS Services (kein NgRx für MVP)
- **Forms:** Reactive Forms (FormGroup, FormBuilder)
- **Change Detection:** OnPush überall (Signals-kompatibel)
- **HTTP:** Functional HTTP Interceptors (JWT Bearer Token)
- **Charts:** Chart.js + ng2-charts (leichtgewichtig)
- **Build:** Angular CLI (esbuild-basiert)

## Consequences

### Positive

- **Typsicherheit:** Compile-time Fehler im Frontend (wie Backend)
- **Reactive Forms:** Ideal für komplexe Formulare (Fixkosten-Wizard, Validierung)
- **Developer Experience:** Alles eingebaut (Forms, Routing, HTTP, Testing, DI)
- **Testability:** Built-in TestBed mit Dependency Injection für einfaches Mocking
- **Long-term Support:** Offizielle Updates, LTS-Releases, große Community
- **Consistency:** Selbe Sprache und Type Safety wie Backend (Java → TypeScript)

### Negative

- **Bundle Size:** ~200 KB gzip (vs. React ~150 KB)
- **Learning Curve:** Steiler als React/Vue (Decorators, RxJS, DI)
- **Boilerplate:** Manchmal mehr Code als React
- **Startup:** Große `vendor.js` braucht mehr Parsing-Zeit

## Alternatives

### React 18.x

**Rejected.** Technisch solid, aber:
- Größere Community könnte zu zu vielen Tech-Choices führen (Redux, Formik, Router?)
- Weniger TypeScript-native als Angular
- Kein Built-in Dependency Injection
- Team sollte sich auf Standard-Pattern konzentrieren, nicht Entscheidungen

### Vue 3.x

**Rejected.** Einfacher Einstieg, aber:
- Kleinere Community als React/Angular
- Two-Way Binding ist weniger skalierbar bei komplexen Forms
- Weniger TypeScript-nativen als Angular

### Svelte 4.x

**Rejected.** Sehr schnell, aber:
- Compiler-basiert mit zu viel "Magie" für MVP
- Kleine Community; weniger Stack Overflow Antworten
- Compiler-Fehler schwerer zu debuggen

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (SPA + REST)
- **ADR-3:** REST vs. GraphQL
- **ADR-7:** JWT für Authentifizierung

---

## Alternatives Considered

### ⚠️ Option 1: React 18.x

**Entscheidung:** Abgelehnt

**Begründung:**
- TypeScript ist optional (weniger Compile-time Safety)
- Forms: Keine Standard-Lösung (Formik, React Hook Form, etc. = mehr Optionen)
- State Management: Viele Optionen (Redux, Recoil, Zustand, Context) = Decision Fatigue
- HTTP/Interceptors: Keine eingebaute Lösung (müssen Custom Hooks bauen)
- Dependency Injection: Nicht vorhanden (schwieriger zu testen)
- **Aber:** Größere Community und mehr Open-Source-Components; wenn mehr Dev-Ressourcen würde ich React erwägen

### ⚠️ Option 2: Vue 3.x

**Entscheidung:** Abgelehnt

**Begründung:**
- TypeScript support ist optional (nicht as first-class wie Angular/Svelte)
- Smaller community (schwieriger, Fragen zu beantworten)
- Performance ist ähnlich wie Angular
- Kleiner Job-Markt (für Absolventen schwerer zu learnen)
- **Aber:** Einfacher Einstieg, könnte für absoluten MVP sinnvoll sein

### ❌ Option 3: Svelte 4.x

**Entscheidung:** Abgelehnt

**Begründung:**
- Sehr kleine Community + Ecosystem (schwer, Answers zu finden)
- Compiler-Magie: schwieriger zu debuggen
- Libraries (Charts, Forms) weniger mature
- Job-Markt: Sehr klein (Absolventen-Nachteil)
- **Aber:** Performance und Bundle-Size sind optimal; wenn Zeit nicht knapp würde ich probieren

### ❌ Option 4: Astro / Static Site

**Entscheidung:** Abgelehnt

**Begründung:**
- Astro ist für Static Sites / MPA (Multi-Page App)
- BudgetBuddy ist SPA (Single-Page App mit Client-Side Navigation)
- Nicht passend

---

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (SPA)
- **ADR-3:** REST API (nicht GraphQL)

---

## Future Considerations

- **Signals-basierte Forms:** In Angular 21 stabil; Reactive Forms (FormGroup) bleibt vorerst Wahl für bewährtes Verhalten
- **Zoneless Change Detection:** Ab Angular 18+ experimental, in Angular 21 reif genug für MVP — Zone.js kann weggelassen werden
- **iOS/Android Native:** Später NativeScript oder Ionic für mobile (selbe Angular Skills)

---

## References

- [Angular 21 Documentation](https://angular.io)
- [Angular Signals Guide](https://angular.io/guide/signals)
- [Angular Reactive Forms](https://angular.io/guide/reactive-forms)
- [ng2-charts Documentation](https://valor-software.com/ng2-charts/)
- CLAUDE.md — Technology Stack
