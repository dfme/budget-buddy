# UI-Design-Varianten (FE-UI-01)

Drei klickbare Design-Prototypen als Entscheidungsgrundlage für die
verbindliche Design-Richtung des BudgetBuddy-Frontends.

**Issue:** [#80 — \[FE-UI-01\] UI-Design definieren](https://github.com/dfme/budget-buddy/issues/80)

> **Kein Produktions-Code.** Diese Prototypen sind Entscheidungsgrundlage. Die
> Übertragung ins Angular-Frontend erfolgt in Folge-Issues auf Basis der
> gewählten Variante.

---

## Previews im Browser öffnen

Die Prototypen sind statisches HTML/CSS und lassen sich direkt über
[htmlpreview.github.io](https://htmlpreview.github.io) anschauen — ohne Checkout,
ohne Build.

| Variante | Dashboard | Transaktionen |
| --- | --- | --- |
| **A — «Klarheit»** | [Preview](https://htmlpreview.github.io/?https://github.com/dfme/budget-buddy/blob/feature/FE-UI-01-design-varianten/design/variant-a/index.html) | [Preview](https://htmlpreview.github.io/?https://github.com/dfme/budget-buddy/blob/feature/FE-UI-01-design-varianten/design/variant-a/transactions.html) |
| **B — «Buddy»** | [Preview](https://htmlpreview.github.io/?https://github.com/dfme/budget-buddy/blob/feature/FE-UI-01-design-varianten/design/variant-b/index.html) | [Preview](https://htmlpreview.github.io/?https://github.com/dfme/budget-buddy/blob/feature/FE-UI-01-design-varianten/design/variant-b/transactions.html) |
| **C — «Ledger»** | [Preview](https://htmlpreview.github.io/?https://github.com/dfme/budget-buddy/blob/feature/FE-UI-01-design-varianten/design/variant-c/index.html) | [Preview](https://htmlpreview.github.io/?https://github.com/dfme/budget-buddy/blob/feature/FE-UI-01-design-varianten/design/variant-c/transactions.html) |

Die Links zeigen auf den Branch `feature/FE-UI-01-design-varianten`. **Nach dem
Merge müssen sie auf `main` umgestellt werden** — dazu im Link
`feature/FE-UI-01-design-varianten` durch `main` ersetzen.

**Bitte im Smartphone-Viewport beurteilen.** Alle drei Varianten sind
mobile-first entworfen; die Desktop-Ansicht ist die Erweiterung, nicht der
Ausgangspunkt. In den DevTools: Device Toolbar → iPhone SE (375px) oder
Pixel (412px).

### Alternativ lokal

```bash
# Aus dem Repo-Root
open design/variant-a/index.html
```

Hinweis: Beim Öffnen via `file://` ohne Netzverbindung bleiben die
**Chart-Flächen leer**, weil Chart.js per CDN geladen wird. Layout, Farben und
Typografie sind davon nicht betroffen.

---

## Die drei Varianten im Vergleich

| | **A — «Klarheit»** | **B — «Buddy»** | **C — «Ledger»** |
| --- | --- | --- | --- |
| **Richtung** | Clean / Minimal | Freundlich / Verspielt | Fintech seriös |
| **Grundstimmung** | Ruhe, Weissraum | Wärme, Charakter | Kompetenz, Dichte |
| **Grund / Fläche** | Weiss auf `#f6f8f7` | Weiss auf Creme `#fff8f4` | Dark `#0d141d` |
| **Akzent** | Deep Teal `#0f6b5f` | Violett `#6c4ef0` + Koralle | Blau `#4f8ff7` |
| **Radius** | 8px | 20–28px | 3–6px |
| **Hero** | 44px, eine Zahl allein | 48px auf Farbverlauf | 40px **plus Herleitungsrechnung** |
| **Transaktionen** | Liste mit Trennlinien | Karten-Zeilen mit Emoji-Avataren | **Echte `<table>`** mit Valuta + Summenzeile |
| **Kategorie-Marker** | Farbpunkt + Label | Emoji + Pastell-Pill | Farbpunkt, ohne Fläche |
| **Donut `cutout`** | 72 % (dünn) | 58 % (dick, runde Segmente) | 78 % (sehr dünn) |
| **Mobile-Navigation** | Tab-Bar unten | schwebende Pill-Bar | Tab-Bar, aktiv = Linie |
| **Komponenten-Ansatz** | Custom SCSS Design System | Lightweight Tokens + wenige Komponenten | **Angular Material** mit eigenem Theme |
| **Umsetzungsaufwand** | gering | gering–mittel | mittel–hoch (Material-3-Theming) |
| **Wiedererkennbarkeit** | mittel | **hoch** | gering |
| **Zugänglichkeit ab Werk** | selbst zu bauen | selbst zu bauen | **kommt mit Material** |
| **Adressiert primär** | Laras Aufschieberitis | Laras Abbruchrisiko (Churn) | Marcs Datenschutz-Skepsis |

Details, Begründungen und Trade-offs je Variante:
[A](variant-a/README.md) · [B](variant-b/README.md) · [C](variant-c/README.md)

---

## Was in allen drei Varianten gleich ist

Damit der Vergleich die **Designrichtung** misst und nicht den Funktionsumfang,
zeigen alle drei Varianten denselben Inhalt und denselben Datensatz:

- **Dashboard:** Safe-to-Spend-Hero, Donut «Ausgaben nach Kategorie»,
  Bar-Chart «Ausgabenverlauf», plus eine Zustands-Sektion mit **negativem
  Safe-to-Spend** und **kein-Einkommen-Zustand**.
- **Transaktionen:** Monatswechsel (US-12), Filter, Buchungsliste mit
  Kategorie-Markern, eine unsicher kategorisierte Buchung und die
  Korrektur-UI (US-05).
- **App-Shell:** Wortmarke oben links, vier Navigationsziele, sowie
  Konto-Block mit Benutzer und **Abmelden**. Auf Mobile sitzt der Zugang zum
  Konto als Avatar in der Topbar (in der Tab-Bar ist kein Platz dafür, sie
  gehört den vier Hauptzielen), auf Desktop als Block am Fuss der Sidebar.
  Die Auth- und Settings-**Screens** sind laut Issue-Scope nicht gestaltet —
  der Einstieg dorthin gehört aber zur Shell und ist deshalb in allen
  Varianten vorhanden.
- **13 Kategorien** aus
  [`Category.java`](../backend/src/main/java/com/budgetbuddy/categorization/Category.java) —
  jede Variante definiert eine vollständige Palette dafür.
- **CH-Formate:** Beträge `1'234.56`, Daten `dd.MM.yyyy`, Sprache Deutsch.
- **Semantik positiv/negativ** ist nie nur farbcodiert — Vorzeichen und Text
  tragen die Information ebenfalls, damit die Screens bei Rot-Grün-Schwäche
  funktionieren.

---

## Demo-Datensatz

Ein einziger, in sich stimmiger Datensatz über alle Varianten — damit im Review
über Gestaltung diskutiert wird und nicht über widersprüchliche Zahlen.

**Aggregat Juli 2026 (Persona Lara)**

| Kennzahl | Wert |
| --- | --- |
| Monatseinkommen | 3'200.00 |
| − Fixkosten (aus dem Wizard, US-03) | 1'845.00 |
| − Variable Ausgaben bisher | 1'058.00 |
| **= Rest bis Monatsende** | **297.00** |
| ÷ 10 Resttage × 5 Tage dieser Woche | **Safe-to-Spend 148.50** |

**Ausgaben nach Kategorie, Juli 2026 — Total CHF 2'265.40**

| Kategorie | Betrag | Anteil |
| --- | ---: | ---: |
| Wohnen | 980.00 | 43.3 % |
| Lebensmittel | 412.65 | 18.2 % |
| Transport | 185.00 | 8.2 % |
| Versicherung | 168.40 | 7.4 % |
| Restaurant | 142.80 | 6.3 % |
| Gesundheit | 108.00 | 4.8 % |
| Freizeit | 96.50 | 4.3 % |
| Shopping | 78.90 | 3.5 % |
| Telekom | 59.00 | 2.6 % |
| Sonstiges | 34.15 | 1.5 % |

**Ausgabenverlauf:** Feb 2'340.10 · Mär 2'512.75 · Apr 2'198.40 ·
Mai 2'640.20 · Jun 2'405.60 · Jul 2'265.40

**Buchungen:** Die Transaktionsseite zeigt **12 von 47** Buchungen des Monats
(Schweizer Händler aus dem `category_lookup`-Seed: Migros, Coop, SBB, Swisscom,
CSS, Digitec, Netflix …). Die angezeigten 12 summieren sich deshalb auf
CHF 1'567.55, nicht auf das Monatstotal — der Rest liegt hinter
«Weitere 35 Buchungen laden».

Zwei Punkte, die im Review erfahrungsgemäss auffallen und Absicht sind:

- **Fixkosten (1'845.00) > fixe Buchungen im Juli (1'207.40).** Der
  Fixkostenwert stammt aus dem Onboarding-Wizard und enthält Positionen, die im
  Juli noch nicht gebucht wurden (u. a. Sparbetrag, ÖV-Abo).
- **Der Donut zeigt nur Ausgaben.** Die Kategorie `Einkommen` erscheint deshalb
  in der Transaktionsliste, aber nicht im Chart.

---

## Technische Umsetzung

```
design/
  variant-a|b|c/
    index.html         Dashboard
    transactions.html  Transaktionsliste
    styles.scss        Quelle — Tokens → Komponenten → Screens
    styles.css         kompiliert, eingecheckt (Browser können kein SCSS)
    charts.js          Chart.js-Konfiguration
    README.md          Designidee, Farb-/Typo-System, Komponenten-Ansatz
```

**SCSS neu kompilieren** nach Änderungen an `styles.scss`:

```bash
# Aus dem Repo-Root, nutzt das Sass aus der Angular-Toolchain
for v in a b c; do
  frontend/node_modules/.bin/sass --no-source-map --style=expanded \
    "design/variant-$v/styles.scss" "design/variant-$v/styles.css"
done
```

`styles.css` ist absichtlich eingecheckt: htmlpreview liefert die Dateien roh
aus, ein Browser kann SCSS nicht rendern. Ohne die kompilierte CSS wären die
Previews ungestylt.

**Chart.js** kommt per CDN (`chart.js@4.4.7`, Version gepinnt und per
[Subresource Integrity](https://developer.mozilla.org/en-US/docs/Web/Security/Subresource_Integrity)
abgesichert — wird die Datei auf dem CDN ausgetauscht, führt der Browser sie
nicht aus), damit die Prototypen dem späteren `ng2-charts`-Setup nahekommen. Die Datenstrukturen in
`charts.js` sind so aufgebaut, dass sie sich beim Angular-Port direkt in ein
Signal übernehmen lassen.

**Nicht enthalten** — bewusst, gemäss Issue-Scope: Onboarding-Wizard, PDF-Upload,
Auth, Settings, Sparziel, KI-Monatsbericht. Jede Variante definiert aber ein
vollständiges Token- und Komponenten-System, das auf diese Screens übertragbar ist.

---

## Entscheid

> **Status: offen** — zu treffen im Team-Review.

Vorgehen: Alle drei Previews im Smartphone-Viewport durchklicken, danach im
Issue [#80](https://github.com/dfme/budget-buddy/issues/80) kommentieren.

Fragen, die den Entscheid tragen sollten:

1. **Welches Risiko wiegt schwerer** — Laras Abbruch nach dem ersten Import
   (Risiko #1) oder Marcs Vertrauensverlust (Risiko #2)? A und B zahlen auf das
   erste ein, C auf das zweite.
2. **Wie viel Frontend-Zeit steht zur Verfügung?** Der Material-Weg (C) kostet
   1–2 Tage Theming-Einarbeitung, bevor die erste Komponente sitzt.
3. **Elemente lassen sich kombinieren.** Die Safe-to-Spend-Herleitung aus C ist
   nicht an deren Look gebunden und wäre auch in A oder B sinnvoll.

Nach dem Entscheid:

- [ ] Gewählte Variante im Issue #80 dokumentieren (mit Begründung)
- [ ] Diese Datei um den Entscheid ergänzen
- [ ] Preview-Links oben von `feature/FE-UI-01-design-varianten` auf `main` umstellen
- [ ] Folge-Issue für die Übertragung ins Angular-Frontend anlegen
      (Tokens nach `frontend/src/styles.scss`, Komponenten nach `frontend/src/app/shared/`)
