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

### Hell/Dunkel-Umschalter (Varianten A und C)

Nach dem Team-Voting (A und C gleichauf, B raus) tragen **A und C je einen
Theme-Umschalter** — der Button unten rechts (☾/☀). Damit lässt sich die
Diskussion „professioneller Eindruck = Dark Theme oder = Struktur?" am Objekt
klären, statt sie zu vermuten:

- **A** startet hell, **C** startet dunkel (der jeweils native Look).
- Ein Klick zeigt dieselbe Variante im anderen Theme — inkl. Charts, die sich
  mit umfärben.
- So sind alle vier Felder vergleichbar: A-hell, A-dunkel, C-hell, C-dunkel.
  C-**hell** zeigt, ob C's dichte, „seriöse" Struktur auch ohne den dunklen
  Grund trägt; A-**dunkel**, ob A's Ruhe im Dark Theme professioneller wirkt.

Technisch ist das ein echtes, produktionsnahes Theming: Farb-Tokens als CSS
Custom Properties, umgeschaltet über `data-theme` auf `<html>`. Genau so würde
es später im Angular-Frontend laufen. (Variante B hat keinen Umschalter — sie
ist mit 0 Stimmen aus der Auswahl.)

### Alternativ lokal

```bash
# Aus dem Repo-Root
open design/variant-a/index.html
```

Funktioniert vollständig offline: Chart.js liegt lokal im Repo, es gibt keinen
externen Request.

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
    charts.js          Chart.js-Konfiguration (theme-fähig bei A und C)
    theme.js           Hell/Dunkel-Umschalter (nur A und C)
    README.md          Designidee, Farb-/Typo-System, Komponenten-Ansatz
  vendor/
    chart.umd.min.js   Chart.js 4.4.7, lokal (von allen Varianten genutzt)
```

Farb-Tokens liegen bei A und C als **CSS Custom Properties** (`--c-*`, `--cat-*`)
in `:root` bzw. `[data-theme="…"]`. `theme.js` schaltet nur das `data-theme`-
Attribut um und löst ein `themechange`-Event aus, auf das `charts.js` die
Chart.js-Instanzen mit den neuen Token-Farben neu aufbaut (ein Canvas kennt
keine CSS-Variablen). Variante B nutzt weiterhin reine Compile-Zeit-SCSS-Werte.

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

**Chart.js** liegt lokal unter [`design/vendor/chart.umd.min.js`](vendor/chart.umd.min.js)
(`chart.js@4.4.7`, unverändert vom CDN übernommen) und wird relativ eingebunden —
**nicht** per CDN. Grund: `htmlpreview.github.io` führt ausschliesslich Scripts
aus, die auf GitHub liegen, und verwirft externe CDN-Scripts stillschweigend.
Ein per CDN eingebundenes Chart.js bliebe in der Preview also wirkungslos und die
Charts leer — unabhängig vom Netz. Der lokale, relative Pfad wird dagegen wie
`charts.js` von GitHub geladen und funktioniert zusätzlich offline über `file://`.
Die Datenstrukturen in `charts.js` sind so aufgebaut, dass sie sich beim
Angular-Port direkt in ein Signal (bzw. `ng2-charts`-`[data]`) übernehmen lassen.

**Nicht enthalten** — bewusst, gemäss Issue-Scope: Onboarding-Wizard, PDF-Upload,
Auth, Settings, Sparziel, KI-Monatsbericht. Jede Variante definiert aber ein
vollständiges Token- und Komponenten-System, das auf diese Screens übertragbar ist.

---

## Entscheid

> **Entscheid: Variante A «Klarheit».** Das Team hat sich nach dem Voting
> (A und C gleichauf, B ohne Stimme) und dem Hell/Dunkel-Vergleich für **A**
> entschieden. Der **Komponenten-Unterbau bleibt bewusst offen** und wird im
> Fundament-Issue (FE-UI-02) entschieden — die Tokens werden dort in jedem Fall
> theme-fähig angelegt (CSS Custom Properties + `data-theme`), damit sowohl ein
> Custom-SCSS- als auch ein CDK-gestützter Weg offen bleibt.

Die folgenden Punkte waren die **Grundlage** des Entscheids (als Kontext erhalten):

1. **Welches Risiko wiegt schwerer** — Laras Abbruch nach dem ersten Import
   (Risiko #1) oder Marcs Vertrauensverlust (Risiko #2)? A und B zahlen auf das
   erste ein, C auf das zweite.
2. **Wie viel Frontend-Zeit steht zur Verfügung?** Der Material-Weg (C) kostet
   1–2 Tage Theming-Einarbeitung, bevor die erste Komponente sitzt.
3. **Elemente lassen sich kombinieren.** Die Safe-to-Spend-Herleitung aus C ist
   nicht an deren Look gebunden und wäre auch in A oder B sinnvoll.

### Komponenten-Unterbau: Material vs. Custom SCSS (möglicher Tiebreaker)

Die Komponenten-Frage ist mit der Varianten-Wahl gebündelt (A → Custom SCSS,
C → Angular Material), aber die beiden Achsen — Optik und Unterbau — sind
**trennbar**. Weil A und C gleichauf liegen, kann das den Ausschlag geben.

| Faktor | Angular Material | Custom SCSS Design System |
| --- | --- | --- |
| Barrierefreiheit (Fokus-Falle, ARIA, Tastatur) | **Kommt mit** — v. a. für Dialog/Bottom-Sheet und Tabelle | Selbst zu bauen, schwer korrekt, leicht kaputt |
| Komplexe Widgets (Tabelle mit Sortierung, Dialog, Select) | `MatTable`/`MatDialog`/`MatSelect` — quasi Einzeiler | Handarbeit — die dichte Tabelle ist das teuerste Stück |
| Visuelle Kontrolle | Eingeschränkt — Material-DNA (Elevation, Ripple, Density) | Voll — nichts zu überschreiben |
| Konsistenz | Framework-erzwungen | Team-Disziplin (3 Devs) |
| Einarbeitung | Material-3-Theming real 1–2 Tage | Gering — Tokens/Basiskomponenten aus den Prototypen skizziert |
| Bundle / Wiedererkennbarkeit | Grösser / „von der Stange" | Kleinstmöglich / eigener Look |

**Zuordnung zu den Varianten:**

- **C + Material ist kohärent.** C's Kern (Datentabelle + Dialoge) ist genau
  Materials Stärke, und C's kantig-systemische Ästhetik kämpft nicht gegen
  Material. Die Theming-Einarbeitung wird durch geschenkte Tabelle/Dialoge/a11y
  aufgewogen.
- **A + Custom SCSS ist kohärent.** Materials Look liefe A's Minimalismus
  zuwider. A zahlt a11y/Widgets selbst — aber A's Screens sind weniger
  Widget-lastig (luftige Liste statt dichter Tabelle), der Aufwand ist kleiner.

**Dritte Option, die den A-Nachteil entschärft:** `@angular/cdk` **ohne**
Materials Optik nehmen. CDK liefert die harten a11y-Primitiven (Overlay/Dialog,
Fokus-Falle, Live-Announcer, `cdk-table`, Virtual Scroll) — für Variante A ideal:
eigenen minimalen Look behalten, aber Korrektur-Dialog/Sheet (und evtl. die
Tabelle) über CDK absichern statt von Hand.

**Wie es einfliessen sollte:** nicht als primärer Treiber (die UX-Richtung führt),
aber als Tiebreaker. Pro C, wenn Tabellen-/Dialog-Reichhaltigkeit + „a11y for
free" zählen und die 1–2 Tage Material-Theming eingeplant sind. Pro A, wenn ein
eigenständiger Look gewünscht ist — dann bewusst CDK für die a11y-harten Stellen.
Kostenprofil beachten: Material-Einarbeitung ist **einmalig**, ein barrierefreier
Dialog + Tabelle von Hand ist **wiederkehrender**, oft unterschätzter Aufwand.
Nicht überankern an „wir haben ja schon die SCSS-Prototypen" — wiederverwendet
werden in beiden Wegen vor allem die **Tokens**; Komponenten entstehen so oder so
als Angular-Komponenten neu.

Nach dem Entscheid (offen, sofern nicht abgehakt):

- [x] Diese Datei um den Entscheid ergänzen (Variante A gewählt)
- [ ] Gewählte Variante im Issue #80 dokumentieren (mit Begründung)
- [ ] PR #96 mergen (nur durch einen Dev)
- [ ] Preview-Links oben von `feature/FE-UI-01-design-varianten` auf `main` umstellen (nach Merge)
- [ ] Fundament-Issue **FE-UI-02** anlegen: Tokens theme-fähig nach
      `frontend/src/styles.scss`, Komponenten-/CDK-Frage dort entscheiden.
      Danach FE-UI-03 (Basiskomponenten in `frontend/src/app/shared/`),
      FE-UI-04 (App-Shell), Chart-Integration (ng2-charts)
- [ ] ADR-11 (UI-Design-System) + docs/TECH-STACK.md / docs/ARCHITECTURE.md (Tech-Stack, ADR-Tabelle) nachziehen

### Nutzerseitige Theme-Präferenz — entschieden, jetzt Teil von US-14

> **Status: aufgenommen** — die Umschaltung gehört zu
> [US-14 (Einstellungen)](../docs/requirements/US-14-einstellungen.md).
> Die frühere Scope-Gabelung ist damit aufgelöst.

Der Hell/Dunkel-Umschalter in den Prototypen (A und C) war reine
**Review-Steuerung** und wurde **nicht** ins Frontend übernommen (`theme.js` ist
Wegwerf-Code). Die nutzerseitige Umschaltung ist eine eigene Umsetzung — ihr
Zuschnitt ist inzwischen entschieden:

- **Ort im UI:** Einstellungen, als Auswahl „Hell / Dunkel / System" (US-14).
- **Scope-Gabelung — entschieden für *client-only*:** Präferenz in
  `localStorage` + `prefers-color-scheme` als Default → reines Frontend-Thema.
  Die geräteübergreifende Variante (Präferenz im User-Profil/DB, Frontend **und**
  Backend) ist bewusst verworfen: sie kostet Migration und Endpoint für einen
  Nutzen, den ein Ein-Geräte-Nutzer wie Marc nicht spürt.
- **Mehr als CSS:** Theme vor dem ersten Paint anwenden (kein Flash) und
  OS-Default respektieren, solange „System" gewählt ist.
- **Voraussetzung war erfüllt:** FE-UI-02 hat die Token-Architektur
  **theme-fähig** angelegt (CSS Custom Properties + `data-theme`, beide Themes als
  Sets — wie in diesen Prototypen) in `frontend/src/styles.scss`. Umgesetzt ist die
  Umschaltung mit FE-SET-04: `frontend/src/app/core/theme/theme.ts` hält die Wahl,
  das Inline-Script in `frontend/src/index.html` wendet sie vor dem ersten
  Bildaufbau an.
