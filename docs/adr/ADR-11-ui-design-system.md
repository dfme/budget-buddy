# ADR-11: UI-Design-Richtung «Klarheit» (Variante A)

**Status:** Accepted
**Date:** 2026-07-25
**Betroffen:** Frontend UI / Design-System, alle Frontend-User-Stories

---

## Context

Bevor die App-Screens ausgebaut werden, musste die **visuelle Designrichtung**
(Look & Feel, Layout, Farb-/Typo-System, Chart-Darstellung) verbindlich
festgelegt werden. Ohne diese Grundlage würden Feature-Screens gegen ein noch
nicht existierendes Token- und Komponentensystem gebaut.

In [FE-UI-01 (#80)](https://github.com/dfme/budget-buddy/issues/80) wurden dazu
**drei klickbare HTML/SCSS-Prototypen** erstellt (unter `design/variant-{a,b,c}/`)
und im Team verglichen.

### Optionen

1. **Variante A «Klarheit»** — Clean / Minimal, viel Weissraum, ein Teal-Akzent,
   Custom SCSS Design System.
2. **Variante B «Buddy»** — Freundlich / Verspielt, warme Töne, Emoji-Kategorien,
   Lightweight-Token-Ansatz.
3. **Variante C «Ledger»** — Fintech seriös, dunkel-first, dichte Datentabelle,
   Angular Material.

Jede Variante enthielt Dashboard (Safe-to-Spend + Donut/Bar-Chart) und
Transaktionsliste; Details und Vergleich in
[`design/README.md`](../../design/README.md).

---

## Decision

Gewählt ist **Variante A «Klarheit»** als verbindliche Design-Grundlage für die
weitere Frontend-Umsetzung. Kanonische Referenz:
[`design/variant-a/`](../../design/variant-a/).

Die Farb-Tokens werden bei der Übernahme ins Frontend **theme-fähig** angelegt
(CSS Custom Properties + `data-theme`), damit ein Light/Dark-Betrieb und eine
spätere nutzerseitige Theme-Präferenz billig bleiben.

**Bewusst offen — nicht Teil dieses Entscheids:** der **Komponenten-Unterbau**
(Custom SCSS pur vs. Custom SCSS + `@angular/cdk` für die a11y-harten Teile wie
Korrektur-Dialog/Bottom-Sheet). Diese Frage wird in
[FE-UI-02 (#99)](https://github.com/dfme/budget-buddy/issues/99) entschieden;
dieses ADR wird danach ergänzt.

---

## Rationale

| Kriterium | A «Klarheit» | B «Buddy» | C «Ledger» |
| --- | --- | --- | --- |
| Einstiegshürde / Onboarding (Laras Abbruchrisiko) | **Hoch** — eine Zahl, ruhig | Hoch — freundlich | Mittel — dicht |
| Vertrauen / Nachvollziehbarkeit (Marc) | Mittel | Gering | **Hoch** (Herleitung) |
| Wiedererkennbarkeit | Mittel | Hoch | Gering |
| Umsetzungsaufwand | **Gering** | Gering–mittel | Mittel–hoch (Material) |
| Alterungsbeständigkeit | **Hoch** | Mittel | Hoch |

**Ablauf des Entscheids:** Team-Voting ergab A und C gleichauf, B ohne Stimme.
Der offene Punkt war, ob C's „professioneller" Eindruck am Dark Theme oder an
der dichten Struktur liegt. Zur Klärung wurde für A und C je ein
Hell/Dunkel-Umschalter ergänzt (alle vier Kombinationen vergleichbar). Nach
diesem Vergleich fiel der Entscheid auf **A**: die Ruhe und niedrige
Einstiegshürde adressieren das grösste Projektrisiko (Churn nach dem ersten
Import) am direktesten, bei zugleich geringstem Umsetzungsaufwand und guter
Alterungsbeständigkeit.

---

## Consequences

### ✅ Positive

- Ruhiges, vertrauenswürdiges UI mit dem Safe-to-Spend-Wert im Zentrum.
- Geringster Umsetzungsaufwand der drei Varianten — das Design *ist* das
  Framework, wenig zu überschreiben.
- Theme-fähige Tokens von Anfang an → Light/Dark und Theme-Präferenz später billig.
- Keine UI-Library im Bundle erzwungen (Unterbau bleibt wählbar).

### ⚠️ Negative

- Kann als nüchtern/austauschbar wahrgenommen werden (geringere emotionale
  Bindung als B).
- Barrierefreiheit und komplexe Widgets (Dialog/Bottom-Sheet, evtl. Tabelle)
  müssen bei „Custom SCSS pur" selbst gebaut werden — fehleranfällig.
- Der Komponenten-Unterbau ist noch nicht entschieden.

### 🔄 Mitigations

| Problem | Mitigation |
| --- | --- |
| a11y bei Eigenbau von Dialog/Sheet | Custom SCSS **+ `@angular/cdk`** als Option — CDK liefert Fokus-Falle/Overlay/Live-Announcer ohne Material-Optik. Entscheid in [FE-UI-02 (#99)](https://github.com/dfme/budget-buddy/issues/99) |
| „zu nüchtern" | Elemente aus C (z. B. Safe-to-Spend-Herleitung) sind look-unabhängig übernehmbar |
| Komponenten-Frage offen | Explizit in FE-UI-02 verortet; Tokens werden theme- und ansatz-neutral angelegt |

---

## Alternatives Considered

### ❌ Variante B «Buddy»

Abgelehnt — 0 Stimmen im Voting. Höchste Wiedererkennbarkeit, adressiert Churn
emotional, aber Emoji-Rendering ist plattformabhängig und die Tonalität
(„Kein Drama") kann bei realem Minus verharmlosend wirken.

### ⚠️ Variante C «Ledger»

Starker Zweitplatzierter. Die nachvollziehbare Safe-to-Spend-Herleitung und die
dichte Datentabelle sind das stärkste Vertrauensargument; verliert gegen A bei
Einstiegshürde und Umsetzungsaufwand. Die Prototypen bleiben im Repo als
Design-Rationale erhalten; einzelne Elemente (Herleitung) sind in A übernehmbar.
Der zu C empfohlene Material-Ansatz fliesst als Option in die
Unterbau-Entscheidung (FE-UI-02) ein.

---

## Related Decisions

- **ADR-2:** Angular 21 (Standalone Components, Signals, OnPush) — Umsetzungsbasis
- **FE-UI-01 (#80):** Design-Prototypen + Entscheid
- **FE-UI-02 (#99):** Token-Fundament + Komponenten-Unterbau (Custom SCSS vs. CDK)
- **FE-UI-03/04/05 (#100/#101/#102):** Basiskomponenten, App-Shell, Chart-Integration

---

## References

- [`design/README.md`](../../design/README.md) — Vergleich, Entscheid, Demo-Datensatz
- [`design/variant-a/`](../../design/variant-a/) — kanonische Design-Referenz (Variante A)
