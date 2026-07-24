# Variante C — «Ledger» (Fintech seriös)

> Übersicht: [`index.html`](index.html) · Buchungen: [`transactions.html`](transactions.html)

## Designidee

Eine App, die aussieht, als könnte sie mit Geld umgehen. Dunkler Grund,
kühle Blautöne, kantige Formen, enge Zeilenabstände und überall gleichbreite
Ziffern. Struktur entsteht über Linien, nicht über Kontrast oder Schatten.

Der zentrale Unterschied zu A und B steckt aber nicht in der Farbe, sondern in
einer inhaltlichen Entscheidung: **der Safe-to-Spend-Betrag steht hier neben
seiner Herleitung.** Einkommen minus Fixkosten minus bisherige Ausgaben, geteilt
durch die Resttage — die Rechnung ist auf dem Screen nachvollziehbar.

Das adressiert Marcs Hürde aus dem Persona-Profil direkt: Datenschutz-Skepsis ist
im Kern Kontrollverlust-Angst. Wer einer Web-App seine Kontodaten anvertraut,
will nicht eine schöne Zahl, sondern nachrechnen können, wie sie zustande kommt.
Der Hero ist deshalb bewusst kleiner als in den anderen beiden Varianten.

Die Buchungen sind eine **echte `<table>`** mit Valuta-Spalte und
Summenzeile — der Bezug zum Kontoauszug, aus dem die Daten stammen, bleibt
sichtbar.

## Farbsystem

Dunkel-first: Die Palette ist als Dark Theme entworfen, nicht aus einem hellen
Theme invertiert.

| Rolle | Wert | Einsatz |
| --- | --- | --- |
| Grund | `#0d141d` | Seitenhintergrund |
| Fläche | `#151e2a` / `#1b2634` | Karten / Tabellenkopf, Hover |
| Linien | `#253143` / `#33425a` | Tabellen- und Kartenrahmen, Inputs |
| Akzent | `#4f8ff7` | Buttons, aktive Navigation, laufender Monat im Bar-Chart |
| Akzent weich | `#16263f` | Aktiver Nav-Hintergrund, ausgewählte Chips |
| Text | `#e8eef6` / `#93a3b8` / `#64748b` | Primär / Sekundär / Meta |

Die Flächen unterscheiden sich absichtlich nur minimal (`#0d141d` → `#151e2a` →
`#1b2634`). Struktur kommt über Linien — dieselbe Logik wie bei einem
Kontoauszug.

**Semantik für Beträge:**

| Zustand | Farbe | Zusätzliches Signal |
| --- | --- | --- |
| Safe-to-Spend positiv | `#34d399` (Mint) | vollständige Herleitung darunter |
| Safe-to-Spend negativ | `#f87171` | Meter voll rot, Text nennt die Verrechnung |
| Kein Einkommen hinterlegt | `#64748b` (Grau) | Betrag als `—`, «Nicht berechenbar» + CTA |
| Gutschrift in der Tabelle | `#34d399` | vorangestelltes `+` |
| Belastung in der Tabelle | Normaler Text `#e8eef6` | kein Minus |
| Unsichere Kategorisierung | `#fbbf24` | 2px-Balken links **an der Zeile** |

Auf dunklem Grund brauchen Grün und Rot mehr Helligkeit und weniger Sättigung
als auf Weiss, sonst flimmern sie. Deshalb `#34d399` statt `#0f6b5f` und
`#f87171` statt `#b3261e`.

Farbe ist nirgends alleiniger Informationsträger: positiv/negativ steht
zusätzlich im Vorzeichen, unsichere Kategorien tragen zusätzlich den Text
«· prüfen», Kategorien zusätzlich das Label.

**Kategorie-Palette:** 13 aufgehellte Töne (400er-Stufen) für alle Kategorien
aus `Category.java` — von `Wohnen` `#60a5fa` bis `Sonstiges` `#94a3b8`.

## Typografie

`system-ui` ohne Webfont, kompakter als A und B: `line-height: 1.45`, Hero
40px statt 44/48px.

| Stufe | Grösse | Einsatz |
| --- | --- | --- |
| Hero | 40px / 600 | Safe-to-Spend-Betrag |
| XL | 24px / 600 | Screen-Titel |
| LG | 17px / 600 | (reserviert) |
| MD | 15px / 400 | Fliesstext |
| SM | 13px / 400 | Tabellenzellen, Sekundärtext |
| XS | 11px / 600 | Tabellenköpfe, Card-Titel, Badges — durchgehend `uppercase` mit `letter-spacing: 0.08em` |

`font-variant-numeric: tabular-nums` gilt hier für **alles Numerische** —
Beträge, Daten, Prozentanteile, Kontonummern. In den anderen Varianten ist das
eine Korrektur gegen springende Spalten; hier ist es Teil der Gestaltungsidee.

## Komponenten-Grundelemente

- **Wortmarke:** Inline-SVG — dieselbe Idee wie in Variante A (offener Rahmen
  mit Kern), aber kantig statt rund. `pathLength="100"` steuert die Lücke,
  `currentColor` die Farbe.
- **Konto-Block:** Avatar-Initialen, Name, Kontonummer und **Abmelden** am Fuss
  der Sidebar. Auf Mobile über den Kontowähler in der Topbar erreichbar.
- **Buttons:** Radius 3px, `min-height: 2.5rem` — kompakter als A und B.
- **Cards:** Kopfzeile mit Trennlinie und `uppercase`-Titel, Body separat.
  Kein Schatten, nur 1px Rahmen.
- **Tabelle:** `sticky` Kopfzeile, Hover-Zeile, `tfoot` mit Summe. Der
  Buchungstext ist die einzige Zelle, die umbrechen darf.
- **Badges:** ohne Fläche — nur ein 7px-Punkt und Text. Gefüllte Badges wirken
  in einer dichten Tabelle unruhig.
- **Chips:** kantig, mit Farbpunkt der Kategorie.
- **Breakdown (`<dl>`):** die Herleitung des Safe-to-Spend als Rechnung.
- **Ref (`<dl>`):** Referenzzeile im Korrektur-Panel, im selben Zahlenformat wie
  die Tabelle.
- **Notice:** farbiger 2px-Balken links statt gefüllter Box.

**Spacing:** 4px-Raster, aber durchgehend enger eingesetzt als in A und B.
**Radius:** `3px` klein, `6px` Karten, `999px` nur für Punkte.

## Charts

Chart.js, konfiguriert in [`charts.js`](charts.js).

- **Donut** mit `cutout: '78%'` — der dünnste der drei Varianten. Der Donut ist
  hier Beleg, nicht Blickfang; die eigentliche Auswertung steht in der Legende.
- **Segment-Trennlinien in der Kartenfarbe** (`#151e2a`) statt in Weiss — auf
  dunklem Grund wäre Weiss ein grelles Gitter.
- **Eigene HTML-Legende** mit Betrag **und** Prozentanteil. Die Chart.js-Legende
  kann keine zwei Werte pro Eintrag darstellen.
- **Bar-Chart** mit `borderRadius: 2` und breiten Säulen (`barPercentage: 0.72`).
  Y-Achse mit 5 Ticks und vollen Beträgen im CH-Format statt `k`-Kurzform —
  passend zur Genauigkeitshaltung der Variante.
- Tooltips mit Rahmen statt Schatten, Radius 3px.

## Mobile-First & Desktop

Alle Basis-Regeln in `styles.scss` gelten für den Smartphone-Viewport
(375–414px). Desktop kommt ausschliesslich über `@media (min-width: 900px)` dazu.

| | Mobile (< 900px) | Desktop (≥ 900px) |
| --- | --- | --- |
| Navigation | Tab-Bar unten, aktiv = Linie oben | Sidebar links, 13.5rem, aktiv = Linie links |
| Topbar | sticky, Marke + Kontowähler | ausgeblendet, Kontowähler wandert in die Sidebar |
| **Tabelle** | **Valuta-Spalte ausgeblendet** (`.table__wide`), 3 Spalten | 4 Spalten inkl. Valuta |
| Tabellenbreite | `overflow-x: auto` als Sicherheitsnetz | passt ohne Scroll |
| Hero + Charts | untereinander | Hero fix 22rem links, Charts rechts (`.grid-hero`) |
| Charts | 200px hoch | 240px hoch |
| Korrektur-UI | Bottom-Sheet | Seiten-Panel rechts |

Die Kernentscheidung fürs Mobile: Die Belastung/Gutschrift-Doppelspalte des
Schweizer Kontoauszugs wird zu **einer** vorzeichenbehafteten Betragsspalte
zusammengezogen. Zwei Betragsspalten sind auf 375px nicht unterzubringen, und
in einer App ist die Trennung ohnehin Redundanz — das Vorzeichen genügt.

## Empfohlener Komponenten-Ansatz: **Angular Material mit eigenem Theme**

Die einzige der drei Varianten, für die eine Komponentenbibliothek wirklich
sinnvoll ist.

**Warum hier:**

Diese Variante braucht genau die Komponenten, die Material stark macht und die
selbst zu bauen teuer ist: `MatTable` (mit Sortierung, Paginator und `sticky`
Header), `MatDialog` und `MatBottomSheet` (mit korrekter Fokus-Falle und
Escape-Handling), `MatSelect`, `MatMenu`. Die Tabelle ist der Kern des Screens —
sie selbst zu bauen hiesse, Sortierung, Paginierung und Tastaturnavigation
nachzuprogrammieren.

Und im Gegensatz zu A und B kollidiert der Look nicht mit Material: kantig,
dicht, systemisch und dunkel ist genau das, wofür Material-3-Theming gebaut ist.
Über `mat.define-dark-theme()` und die eigene Palette kommt man dem Ziel nahe,
ohne gegen das Framework zu arbeiten.

**Trade-offs:**

| | |
| --- | --- |
| ✅ Zugänglichkeit | Bester Stand der drei Varianten — Fokus-Management, ARIA und Tastaturbedienung kommen mit |
| ✅ Komplexe Widgets | Tabelle mit Sortierung/Paginierung ist ein Einzeiler statt eines Eigenbaus |
| ✅ Konsistenz | Vom Framework erzwungen, nicht von der Disziplin des Teams abhängig |
| ✅ Light Theme | Über dieselben Tokens ableitbar — Material-Theming liefert die Struktur dafür mit |
| ⚠️ Einarbeitung | Material-3-Theming in Angular 21 ist nicht trivial. Das kostet real 1–2 Tage, bevor die erste Komponente richtig aussieht |
| ⚠️ Bundle | Grösste der drei Varianten. Bei einem Single JAR auf Render mit SPA in `BOOT-INF/static/` relevant, aber nicht kritisch |
| ⚠️ Wiedererkennbarkeit | Geringste der drei — die App sieht kompetent aus, aber nicht eigen |
| ⚠️ Dark-only | Ein reines Dark Theme schliesst Nutzer aus, die hell bevorzugen. Der Light-Mode ist ableitbar, aber **zusätzlicher Aufwand**, der im Entscheid eingeplant werden muss |

## Wann diese Variante Sinn macht

Wenn das Team **Vertrauen als das eigentliche Produktproblem** sieht — also
Marcs Datenschutz-Skepsis und Risiko #2 (Liability & Compliance) höher gewichtet
als Laras Abbruchrisiko. Die nachvollziehbare Herleitung des Safe-to-Spend ist
das stärkste Vertrauensargument der drei Entwürfe und liesse sich auch in eine
andere Variante übernehmen.

Sie ist die **falsche** Wahl, wenn die Zielgruppe die App als leichtes
Alltagswerkzeug erleben soll. Dichte Tabellen und Herleitungsrechnungen belohnen
Nutzer, die sich mit ihren Finanzen beschäftigen wollen — Lara aus dem
Persona-Profil will das ausdrücklich nicht.
