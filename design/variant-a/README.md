# Variante A — «Klarheit» (Clean / Minimal)

> Dashboard: [`index.html`](index.html) · Transaktionen: [`transactions.html`](transactions.html)

## Designidee

Eine Finanz-App, die nicht nach Finanz-App aussieht. Der Screen besteht fast nur
aus Weissraum, dünnen Trennlinien und **einer** grossen Zahl: dem
Safe-to-Spend-Betrag. Alles andere ordnet sich diesem Wert unter — die Charts
sind Belege, nicht Hauptdarsteller.

Der Hintergedanke ist Laras Hürde aus dem Persona-Profil: Aufschieberitis. Ein
Screen, der beim Öffnen sofort eine einzige Zahl liefert und sonst nichts
verlangt, hat die niedrigste Einstiegsschwelle. Es gibt keinen Zustand, in dem
der Nutzer erst etwas suchen oder interpretieren muss.

## Farbsystem

| Rolle | Wert | Einsatz |
| --- | --- | --- |
| Akzent | `#0f6b5f` (Deep Teal) | Primär-Button, aktive Navigation, laufender Monat im Bar-Chart |
| Akzent weich | `#e6f2f0` | Aktiver Nav-Hintergrund, Fokus-Ring, Info-Notice |
| Text | `#101413` / `#5b6462` / `#8b9694` | Primär / Sekundär / Meta |
| Linien | `#e3e8e6`, `#cbd4d1` | Trennlinien, Input-Rahmen |
| Fläche | `#ffffff` auf `#f6f8f7` | Karten auf leicht getöntem Grund |

**Semantik für Beträge:**

| Zustand | Farbe | Zusätzliches Signal |
| --- | --- | --- |
| Safe-to-Spend positiv | `#0f6b5f` | — |
| Safe-to-Spend negativ | `#b3261e` | Meter füllt sich vollständig rot, Text nennt die Konsequenz |
| Kein Einkommen hinterlegt | `#8b9694` (Grau) | Betrag als `—`, Warn-Notice + Call-to-Action |
| Einnahme in der Liste | `#0f6b5f` | vorangestelltes `+` |
| Ausgabe in der Liste | Normaler Text `#101413` | kein Minus — in einer Ausgabenliste ist das Redundanz |

Ausgaben sind bewusst **nicht** rot. Rot ist für den einen Fall reserviert, der
wirklich Aufmerksamkeit braucht: ein negativer Safe-to-Spend. Wenn jede
Migros-Buchung rot leuchtet, sagt Rot nichts mehr.

Farbe ist nirgends der alleinige Informationsträger — positiv/negativ steht
zusätzlich im Vorzeichen und im Text, Kategorien zusätzlich im Label. Damit
funktionieren die Screens auch bei Rot-Grün-Schwäche.

**Kategorie-Palette:** 13 gedämpfte Töne, einer pro Kategorie aus
`Category.java` — von `Wohnen` `#0f6b5f` bis `Sonstiges` `#8b9694`. Sie sind
absichtlich entsättigt: bei 9 gleichzeitig sichtbaren Donut-Segmenten würden
volle Sättigungen den ruhigen Rest des Screens erschlagen.

## Typografie

`system-ui` — kein Webfont. Das spart einen Netzwerk-Request, verhindert
Layout-Shift beim Laden und sieht auf iOS/Android/Windows jeweils nativ aus.

| Stufe | Grösse | Einsatz |
| --- | --- | --- |
| Hero | 44px / 600 | Safe-to-Spend-Betrag |
| XL | 32px / 600 | Screen-Titel |
| LG | 20px / 600 | Card-Titel |
| MD | 16px / 400 | Fliesstext, Beträge in der Liste |
| SM | 14px / 400 | Sekundärtext, Buttons |
| XS | 12px / 400–600 | Labels, Meta, Legende |

Alle Beträge nutzen `font-variant-numeric: tabular-nums` — sonst springen die
Spalten in der Transaktionsliste bei jedem Scroll.

## Komponenten-Grundelemente

- **Wortmarke:** Inline-SVG — offener Ring mit Kern, greift den Donut des
  Dashboards auf. `pathLength="100"` macht die Lücke unabhängig vom Radius
  exakt steuerbar; die Farbe kommt über `currentColor`, das Zeichen ist also
  ohne zweite Datei umfärbbar.
- **Konto-Block:** Avatar-Initialen, Name, E-Mail und **Abmelden** am Fuss der
  Sidebar. Auf Mobile über den Avatar in der Topbar erreichbar.
- **Buttons:** `--primary` (Teal, gefüllt), `--ghost` (Rahmen), `--block`
  (volle Breite, Mobile-Default). Höhe ≥ 44px für die Touch-Zielgrösse.
- **Cards:** 1px Rahmen + fast unsichtbarer Schatten, Radius 8px, Padding 24px.
- **Badges:** Pill mit farbigem Punkt + Kategoriename in der Kategoriefarbe.
- **Inputs / Select:** 1px Rahmen, Radius 8px, Fokus über Teal-Rahmen + 3px
  weicher Ring.
- **Chips:** Pill-Buttons für die Kategorie-Auswahl in der Korrektur-UI.
- **Segment:** Umschalter für Alle / Ausgaben / Einnahmen.
- **Meter:** 6px Fortschrittsbalken für den Wochenverlauf.

**Spacing:** 4px-Raster (`4 · 8 · 12 · 16 · 24 · 32 · 48`).
**Radius:** `4px` klein, `8px` Standard, `999px` Pills. Bewusst zurückhaltend —
die Ruhe kommt aus Weissraum, nicht aus Rundung.

## Charts

Chart.js, konfiguriert in [`charts.js`](charts.js).

- **Donut** mit `cutout: '72%'` — ein dünner Ring. Er zeigt die Verteilung; die
  Details stehen in der Legende darunter.
- **Eigene HTML-Legende** statt der Chart.js-Legende: als 2-spaltiges Grid mit
  Beträgen. Auf 375px bricht die eingebaute Legende bei 9 Kategorien unkontrolliert
  um und schluckt die Chart-Höhe.
- **Bar-Chart** mit nur einer eingefärbten Säule — der laufende Monat im Akzent,
  die Historie neutralgrau. Das ersetzt eine zusätzliche Beschriftung.
- Y-Achse mit `maxTicksLimit: 4` und `k`-Kurzform, X-Achse ohne Grid. Weniger
  Linien, mehr lesbare Daten.

## Mobile-First & Desktop

Alle Basis-Regeln in `styles.scss` gelten für den Smartphone-Viewport
(375–414px). Desktop kommt ausschliesslich über `@media (min-width: 900px)` dazu.

| | Mobile (< 900px) | Desktop (≥ 900px) |
| --- | --- | --- |
| Navigation | Tab-Bar unten (Daumenzone), 4 Ziele | Sidebar links, 15rem, sticky über die volle Höhe |
| Topbar | sticky oben mit Marke + Avatar | ausgeblendet, Marke wandert in die Sidebar |
| Charts | untereinander, 220px hoch | nebeneinander im 2er-Grid, 260px hoch |
| Zustands-Demo | einspaltig | zweispaltig |
| Container | volle Breite, 16px Padding | max. 1180px zentriert, 32px Padding |
| Korrektur-UI | Bottom-Sheet (oben abgerundet, Grabber) | zentrierter Dialog, allseitig gerundet |

## Empfohlener Komponenten-Ansatz: **Custom SCSS Design System**

Ein eigenes Token-System (`$c-*`, `$sp-*`, `$fs-*`, `$r-*`) plus rund zehn
Basiskomponenten — genau das, was `styles.scss` bereits enthält.

**Warum hier:**

Der Look dieser Variante lebt von Reduktion — wenig Chrome, keine Schatten,
sparsame Rundung. Angular Material bringt genau das Gegenteil mit: Ripple,
Elevation, Material-typische Rundungen und eine Typo-Skala, die man erst
mühsam wieder wegkonfigurieren müsste. Man würde mehr Zeit mit
Überschreiben verbringen als mit Bauen.

Die tatsächlich benötigte Komponentenmenge ist zudem klein: Button, Card,
Badge, Input, Select, Chip, Segment, Meter, Bottom-Sheet. Das ist im MVP-Umfang
gut selbst tragbar.

**Trade-offs:**

| | |
| --- | --- |
| ✅ Aufwand | Am geringsten von allen drei Varianten — das Design *ist* das Framework, es gibt nichts zu überschreiben |
| ✅ Wiedererkennbarkeit | Hoch — sieht nach nichts von der Stange aus |
| ✅ Bundle | Keine UI-Library im Bundle |
| ⚠️ Konsistenz | Liegt vollständig in der Disziplin des Teams. Ohne konsequente Token-Nutzung driftet das System bei 3 Devs auseinander |
| ⚠️ Zugänglichkeit | Fokus-Ringe, ARIA und Tastaturbedienung müssen selbst gebaut werden — Material liefert das mit. Für Dialog und Bottom-Sheet ist das der teuerste Punkt (Fokus-Falle, Escape-Handling) |
| ⚠️ Komplexe Widgets | Datepicker o. ä. wären teuer. Im MVP-Scope aber nicht gefordert |

## Wann diese Variante Sinn macht

Wenn das Team auf **Vertrauen durch Ruhe** setzt und der Safe-to-Spend-Wert das
unbestrittene Zentrum des Produkts bleiben soll. Sie ist die sicherste Wahl:
sie altert langsam, funktioniert auf jedem Gerät und ist mit dem geringsten
Aufwand konsistent umzusetzen.

Sie ist die **falsche** Wahl, wenn sich BudgetBuddy im Review als zu nüchtern
oder austauschbar anfühlt — dann fehlt ihr die emotionale Bindung, die Variante B
über Wärme und Charakter herstellt.
