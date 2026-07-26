# [FE-UI-01] UI-Design definieren: 3 klickbare Varianten

**Issue:** [#80](https://github.com/dfme/budget-buddy/issues/80)
**Task-ID:** `FE-UI-01`
**Branch:** `feature/FE-UI-01-design-varianten`
**Bestätigt am:** 2026-07-24

---

## Ziel

Drei visuell klar unterscheidbare Design-Varianten als klickbare, standalone HTML/SCSS-Prototypen,
damit das Team im Browser vergleichen und **eine** Variante als verbindliche Design-Grundlage für
die weitere Frontend-Umsetzung auswählen kann.

Kein Produktions-Code: `frontend/src/` wird in diesem Issue nicht angefasst.

---

## Entscheide

| Frage | Entscheid | Begründung |
| --- | --- | --- |
| Chart-Umsetzung | **Chart.js via CDN** (jsDelivr, Version gepinnt), Demo-Daten | Näher am späteren `ng2-charts`-Setup. Caveat: über `file://` ohne Netz bleiben die Chart-Flächen leer — für die Review via htmlpreview.github.com irrelevant. |
| SCSS-Delivery | **`styles.scss` als Quelle + eingechecktes, kompiliertes `styles.css`** | Browser können SCSS nicht rendern; ohne eingecheckte CSS wären die htmlpreview-Previews ungestylt. Compile via `frontend/node_modules/.bin/sass`. |
| Kategorie-Farben | Palette deckt die **13 fixen Kategorien** aus `Category.java` ab | Direkt auf das Angular-Frontend übertragbar. |
| Beispieldaten | Schweizer Händler aus dem `category_lookup`-Seed (V04), CHF `1'234.56`, Datum `dd.MM.yyyy` | Realitätsnah und konsistent mit dem Backend. |

---

## Die drei Varianten

| | Variante A | Variante B | Variante C |
| --- | --- | --- | --- |
| **Richtung** | Clean / Minimal | Freundlich / Verspielt | Fintech seriös |
| **Look** | Viel Weissraum, ein Akzent (Deep Teal), dünne Trennlinien, Radius 8px | Warme Pastelltöne, Radius 20px, weiche Schatten, Emoji-Kategorie-Badges, Gradient-Hero | Dunkel-first, Navy/Slate, dichte Datentabellen, tabular-nums, Radius 4px |
| **Typo** | system-ui, klare Skala 12/14/16/20/32/44 | system-ui, grössere Zeilenhöhe, runde Gewichte | system-ui + `font-variant-numeric: tabular-nums` für alle Beträge |
| **Chart-Look** | Donut dünn, Legende unten, gedämpfte Palette | Donut dick, verspielte Sättigung, Labels im Chart | Donut dünn, Bar mit Grid-Linien, gedeckte Institutspalette |
| **Komponenten-Ansatz** | Custom SCSS Design System (Token-basiert) | Lightweight Token-System + wenige Basiskomponenten | Angular Material mit eigenem Theme |

---

## Betroffene Files

Geänderte Files: **keine**.

Neue Files:

```
design/
  README.md                    Übersicht, Vergleichstabelle, htmlpreview-Links, Entscheid-Vorlage
  variant-a/
    index.html                 Dashboard: Safe-to-Spend-Hero, Donut, Bar, Negativ-/No-Income-State
    transactions.html          Transaktionsliste, Kategorie-Badges, Korrektur-UI (angedeutet)
    styles.scss                Quelle
    styles.css                 kompiliert, eingecheckt
    charts.js                  Chart.js-Configs im Varianten-Look
    README.md                  Designidee, Farb-/Typo-System, Komponenten-Ansatz + Trade-offs, Desktop-Verhalten
  variant-b/                   gleiche Struktur, eigenständiges Token-Set
  variant-c/                   gleiche Struktur, eigenständiges Token-Set
```

---

## Implementierungsschritte

1. `design/`-Struktur anlegen; gemeinsames Demo-Datenset festlegen (Migros, Coop, SBB, Swisscom,
   CSS, Digitec, Netflix …).
2. Variante A: `styles.scss` (Tokens → Basiskomponenten → Screens), `index.html`,
   `transactions.html`, `charts.js`, `README.md`.
3. Variante B analog, eigenständiges Token-Set.
4. Variante C analog, eigenständiges Token-Set.
5. Alle drei SCSS → CSS kompilieren.
6. `design/README.md` mit Vergleichstabelle, htmlpreview-Links und offenem Entscheid-Abschnitt.

Jedes Dashboard zeigt neben dem Normalfall auch den **negativen Safe-to-Spend** und den
**No-Income-State**, damit die semantischen Farben für positiv/negativ im Prototyp sichtbar
entschieden sind.

---

## Test-Strategie

Kein Produktions-Code → keine Unit-, Integration- oder E2E-Tests. Stattdessen als Verifikation:

- `sass --no-source-map` kompiliert alle drei Varianten fehlerfrei (Build-Check).
- Alle internen Links (`index.html` ↔ `transactions.html`) und Asset-Pfade sind relativ und
  aufgelöst.
- Manueller Browser-Check bei 375px und Desktop-Breite pro Variante. Playwright ist im Repo nicht
  vorhanden, dieser Schritt ist bewusst manuell.

---

## Acceptance Criteria (aus dem Issue)

- [ ] Drei visuell klar unterscheidbare Varianten als HTML/SCSS-Prototypen unter `design/variant-{a,b,c}/`.
- [ ] Jede Variante enthält Dashboard (mit Pie/Donut- + Bar-Chart) und Transaktionsliste mit Kategorien.
- [ ] Alle Prototypen sind mobile-first und im Smartphone-Viewport nutzbar; Desktop-Verhalten dokumentiert.
- [ ] Pro Variante ist der empfohlene Komponenten-Bibliotheks-/Design-System-Ansatz samt Begründung dokumentiert.
- [ ] Kurzes README je Variante: Designidee, Farb-/Typo-System, wann welche Variante Sinn macht.
- [ ] Review durch das Team → eine Variante wird als verbindliche Design-Grundlage ausgewählt.
      **Bleibt nach dem Merge offen** — der PR liefert nur die Entscheidungsgrundlage, der Entscheid
      wird im Issue dokumentiert.
