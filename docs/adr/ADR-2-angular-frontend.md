# ADR-2: Angular 19.x Frontend

**Status:** Accepted (locked)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Gesamte Frontend-Architektur

---

## Context

BudgetBuddy benötigt ein Frontend, das:

- Web-App für Studierende (low-tech Device-Profile)
- Responsive Design (Desktop, Tablet, Mobile)
- Sichere Authentifizierung + Token-Management (JWT)
- Komplexe Forms (Fixkosten-Wizard, PDF-Upload, Transaktions-Kategorisierung)
- State Management (User-Session, Transaktions-Cache, Kategorie-Daten)
- Charts/Visualisierungen (Kategorien-Pie, Vergleiche, Safe-to-Spend)
- Performance: Schnelles Initial Load, kein Bloat

### Optionen

1. **Angular 19.x** — Full-featured framework, TypeScript, Component-based
2. **React 18.x** — Library, flexibel, große Community
3. **Vue 3.x** — Einfacher Einstieg, Two-Way Binding
4. **Svelte 4.x** — Compiler-basiert, sehr schnell, kleiner Bundle
5. **Astro / Static Site** — Zu lightweight für SPA

---

## Decision

**Angular 19.x**

- **Runtime:** TypeScript 5.x
- **Rendering:** Standalone Components + Signals (Angular 19 modern)
- **State:** Angular Signals + RxJS Services (kein NgRx für MVP)
- **Forms:** Reactive Forms (FormGroup, FormBuilder)
- **Change Detection:** OnPush everywhere (Signals-compatible)
- **HTTP:** Functional HTTP Interceptors (JWT Bearer Token)
- **Charts:** Chart.js + ng2-charts (lightweight)
- **Styling:** CSS/SCSS (keine CSS-in-JS)
- **Testing:** Jasmine + Karma
- **Build Tool:** Angular CLI (esbuild-basiert, schnell)

---

## Rationale

| Kriterium | Angular | React | Vue | Svelte |
|-----------|---------|-------|-----|--------|
| **Learning Curve** | ⚠️ Steil | ✅ Sanft | ✅✅ Sanft | ⚠️ Compiler-Magie |
| **TypeScript Support** | ✅✅ Built-in | ⚠️ Optional | ⚠️ Optional | ✅ Built-in |
| **Forms** | ✅✅ Reactive Forms | ⚠️ Libs (Formik, RHF) | ✅ Two-way binding | ✅ Einfach |
| **Dependency Injection** | ✅✅ Built-in | ❌ Nicht vorhanden | ❌ Nicht vorhanden | ❌ Nicht vorhanden |
| **Component Structure** | ✅ Opinionated | ⚠️ Flexibel (zu flexibel) | ✅ Opinionated | ✅ Opinioniert |
| **State Management** | ✅ Signals/RxJS | ⚠️ Viele Optionen (Redux, Recoil, Zustand) | ✅ Pinia/Vuex | ✅ Stores |
| **Community Size** | ✅ Groß | ✅✅ Riesig | ✅ Mittel | ⚠️ Klein |
| **Enterprise Adoption** | ✅✅ Sehr verbreitet | ✅✅ Sehr verbreitet | ⚠️ Wachsend | ❌ Nischig |
| **Performance** | ✅ Gut | ✅ Gut | ✅ Gut | ✅✅ Sehr gut |
| **Bundle Size** (gzip) | ⚠️ ~200 KB | ⚠️ ~150 KB (+ Redux/Router) | ⚠️ ~130 KB | ✅ ~50 KB |

**Konkrete Vorteile für BudgetBuddy:**

1. **TypeScript First:** Typsicherheit im Frontend (compile-time Fehler, nicht Laufzeit)
2. **Reactive Forms:** Perfect für komplexe Forms (Fixkosten-Wizard, Validierung mit Custom Validators)
3. **Signals (Angular 19):** Modern reactivity ohne NgRx-Overhead; automatic change detection
4. **Dependency Injection:** Clean Architecture; Services + Interceptors einfach
5. **Opinionated:** "Angular Way" vs. "React Flexibility" — für MVP weniger Entscheidungen
6. **HTTP Interceptors:** JWT Token-Management zentral, nicht in jeder Component
7. **Angular CLI:** Zero-config development, fast builds

---

## Consequences

### ✅ Positive

- **Typsicherheit:** Compile-time Fehler im Frontend (wie Backend)
- **Developer Experience:** Alles eingebaut (Forms, Routing, HTTP, Testing)
- **Skalierbarkeit:** Für wachsende Codebase designed (großes Enterprise-Ökosystem)
- **Testability:** Built-in TestBed, Dependency Injection ermöglicht Mock-Injection
- **Long-term Maintenance:** Offizielle Updates, LTS-Releases, große Community

### ⚠️ Negative

- **Bundle Size:** Initial ~200 KB gzip (vs. React ~150 KB)
- **Learning Curve:** Mehr Konzepte (Decorators, DI, RxJS Observables, etc.)
- **Boilerplate:** Manchmal mehr Code als React (z.B. TypeScript interfaces + Component)
- **Startup-Zeit:** Große `vendor.js` braucht Parsing (aber mit Service Worker ok)

### 🔄 Mitigations

- Bundle-Size: Code Splitting + Lazy Loading per Route
- Learning: Umfangreiche Angular Docs und CLI-Scaffolding
- Boilerplate: Neuste Signals API reduziert RxJS-Boilerplate
- Performance: Service Worker + Aggressive Caching

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

- **Signals-basierte Forms (Angular 19+):** Signal-Form API wird stabiler, könnte RxJS ersetzen
- **Zoneless Change Detection:** Angular plant Removal von Zone.js → noch schneller
- **iOS/Android Native:** Später NativeScript oder Ionic für mobile (selbe Angular Skills)

---

## References

- [Angular 19 Documentation](https://angular.io)
- [Angular Signals Guide](https://angular.io/guide/signals)
- [Angular Reactive Forms](https://angular.io/guide/reactive-forms)
- [ng2-charts Documentation](https://valor-software.com/ng2-charts/)
- CLAUDE.md — Technology Stack
