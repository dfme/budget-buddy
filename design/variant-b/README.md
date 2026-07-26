# Variante B — «Buddy» (Freundlich / Verspielt)

> Dashboard: [`index.html`](index.html) · Buchungen: [`transactions.html`](transactions.html)

## Designidee

Der Name des Produkts ernst genommen: ein *Buddy*, nicht ein Kontoauszug. Warme
Creme-Töne, grosse Radien, ein Farbverlauf im Hero, Emoji als Kategorie-Anker
und eine Tonalität, die duzt und aufmuntert («Läuft gut», «Passt so!»).

Der Hintergedanke: Geldübersicht ist für die Zielgruppe emotional besetzt und
eher schambehaftet. Diese Variante versucht, dem die Schwere zu nehmen — der
negative Safe-to-Spend heisst hier ausdrücklich «Kein Drama», nicht «Budget
überschritten». Sie ist die einzige der drei Varianten, die aktiv Sympathie
aufbauen will statt nur Information zu liefern.

## Farbsystem

| Rolle | Wert | Einsatz |
| --- | --- | --- |
| Primär | `#6c4ef0` (Violett) | Buttons, aktive Navigation, laufender Monat im Bar-Chart |
| Primär weich | `#efeaff` | Aktiver Nav-Hintergrund, Info-Notice, ausgewählte Chips |
| Sekundär | `#ff7e6b` (Koralle) | Verlaufsende im Hero, Akzent-Illustrationen |
| Hero-Verlauf | `#7c5cff → #a855f7 → #ff7e6b` | **Nur** der Hero |
| Text | `#2e2a3f` / `#6b6480` / `#a49db5` | Primär / Sekundär / Meta — warm getönt, kein reines Grau |
| Fläche | `#ffffff` auf `#fff8f4` | Karten auf Creme |

Der Verlauf kommt exakt einmal im ganzen System vor. Zwei oder drei Verläufe auf
demselben Screen kippen den Look von «freundlich» zu «laut» — deshalb ist der
Hero die einzige Fläche, die ihn tragen darf.

**Semantik für Beträge:**

| Zustand | Farbe | Zusätzliches Signal |
| --- | --- | --- |
| Safe-to-Spend positiv | Violett-Koralle-Verlauf, weisse Schrift | — |
| Safe-to-Spend negativ | Verlauf `#e2496b → #ff7e6b` | Meter voll, Text nennt die Konsequenz beruhigend |
| Kein Einkommen hinterlegt | Weisse Karte, Betrag `—` in `#a49db5` | Direkter Call-to-Action statt Warnung |
| Einnahme in der Liste | `#12a06a` | vorangestelltes `+` |
| Ausgabe in der Liste | Normaler Text `#2e2a3f` | kein Minus |

Ausgaben sind bewusst nicht rot — Rot ist für den negativen Safe-to-Spend
reserviert. Farbe ist nirgends alleiniger Informationsträger: positiv/negativ
steht zusätzlich im Vorzeichen, Kategorien zusätzlich in Emoji und Label.

**Kategorie-Palette:** 13 kräftige Töne, je eine eigene Hue pro Kategorie aus
`Category.java`. Badge- und Avatar-Hintergründe werden per `color.mix($hue, #fff)`
aus der Vollfarbe abgeleitet — pro Kategorie ist damit nur **ein** Wert zu pflegen.

**Emoji als Kategorie-Icons:** 🏠 Wohnen · 🛒 Lebensmittel · 🚋 Transport ·
🛡️ Versicherung · 📱 Telekom · 💊 Gesundheit · 🎬 Freizeit · 🍽️ Restaurant ·
🛍️ Shopping · 📚 Bildung · 💰 Einkommen · 🐷 Sparen · ✨ Sonstiges

Das ist die pragmatischste Entscheidung dieser Variante: kein Icon-Set, kein
zusätzlicher Request, sofort verständlich. Der Preis steht unten in den
Trade-offs.

## Typografie

`system-ui` ohne Webfont, aber mit deutlich mehr Gewicht und Zeilenhöhe als
Variante A — 700/800 statt 600, `line-height: 1.6` statt 1.5.

| Stufe | Grösse | Einsatz |
| --- | --- | --- |
| Hero | 48px / 800 | Safe-to-Spend-Betrag |
| XL | 32px / 700 | Screen-Titel |
| LG | 22px / 700 | Card-Titel, Sheet-Titel |
| MD | 17px / 400 | Fliesstext, Beträge in der Liste |
| SM | 15px / 400 | Sekundärtext, Buttons |
| XS | 13px / 600–700 | Labels, Badges, Legende |

Alle Beträge nutzen `font-variant-numeric: tabular-nums`.

## Komponenten-Grundelemente

- **Wortmarke:** 🐷 als Emoji — die einzige Variante, die kein gezeichnetes
  Zeichen verwendet. Das ist konsequent zur Kategorie-Bildsprache, teilt aber
  deren Nachteil: das Sparschwein sieht auf iOS, Android und Windows
  unterschiedlich aus. Als echte Marke bräuchte es später ein SVG.
- **Konto-Block:** Avatar-Initialen, Name, E-Mail und **Abmelden** am Fuss der
  Sidebar. Auf Mobile über den Avatar in der Topbar erreichbar.
- **Buttons:** vollständig als Pill (`999px`), `min-height: 3rem`, 2px Rahmen bei
  `--ghost`. Grosse, gut treffbare Ziele.
- **Cards:** kein Rahmen, stattdessen weicher violetter Schatten, Radius 20px.
- **Hero:** Radius 28px, Verlauf, stärkerer Schatten.
- **Badges:** Pill mit Emoji + Label auf pastelligem Hintergrund der Kategoriefarbe.
- **Avatare:** 44px runder Emoji-Kreis vor jeder Transaktion.
- **Chips:** Pill mit Emoji für die Kategorie-Auswahl.
- **Inputs:** 2px Rahmen, Radius 12px.
- **Meter:** 10px Fortschrittsbalken, weiss auf dem Hero-Verlauf.

**Spacing:** 4px-Raster, aber grosszügiger eingesetzt als bei A.
**Radius:** `12px` klein, `20px` Karten, `28px` Hero, `999px` Pills. Nichts im
System hat eine harte Ecke — das ist das prägendste Merkmal der Variante.

## Charts

Chart.js, konfiguriert in [`charts.js`](charts.js).

- **Donut** mit `cutout: '58%'` — ein dicker Ring. Zusätzlich `borderRadius: 10`
  und `spacing: 2`: die Segmente bekommen runde Enden und stehen frei
  nebeneinander, dieselbe Formensprache wie die Pill-Buttons.
- **Eigene HTML-Legende** mit Emoji + Betrag statt der Chart.js-Legende. Auf 375px
  bricht die eingebaute Legende bei 9 Kategorien unkontrolliert um.
- **Bar-Chart** mit `borderRadius: 999` — vollständig gerundete Säulenköpfe,
  schmale Säulen (`barPercentage: 0.5`). Laufender Monat in Violett, Historie in
  hellem Lila.
- X-Achse ohne Grid und ohne Achsenlinie; Y-Achse mit `k`-Kurzform.

## Mobile-First & Desktop

Alle Basis-Regeln in `styles.scss` gelten für den Smartphone-Viewport
(375–414px). Desktop kommt ausschliesslich über `@media (min-width: 900px)` dazu.

| | Mobile (< 900px) | Desktop (≥ 900px) |
| --- | --- | --- |
| Navigation | **schwebende Pill-Bar** unten mit Abstand zum Rand, `position: fixed` | Sidebar links, 15rem, als abgerundete Karte |
| Freiraum | `padding-bottom: 5.5rem` am `.app`, damit die Bar nichts überdeckt | entfällt |
| Topbar | «Hoi Lara 👋» + Avatar | ausgeblendet, Marke wandert in die Sidebar |
| Charts | untereinander, 240px hoch | nebeneinander im 2er-Grid, 280px hoch |
| Kennzahlen | 3 Kacheln nebeneinander | unverändert 3 Kacheln |
| Container | volle Breite, 16px Padding | max. 1200px zentriert, 32px Padding |
| Korrektur-UI | Bottom-Sheet (oben abgerundet, Grabber) | zentrierter Dialog, allseitig gerundet |

## Empfohlener Komponenten-Ansatz: **Lightweight Token-System + wenige Basiskomponenten**

Ein schlankes SCSS-Token-Set (Farben, Radien, Spacing, Schatten) plus eine
Handvoll wiederverwendbarer Standalone-Komponenten — aber bewusst **kein**
vollständiges Design System wie bei Variante A.

**Warum hier:**

Der Look lebt von wenigen, stark wiederholten Entscheidungen: alles ist rund,
alles hat einen weichen Schatten, jede Kategorie hat eine Farbe und ein Emoji.
Das sind vier Tokens und fünf Komponenten — kein System, das eine formale
Governance braucht. Entscheidend ist, dass die Kategorie-Farb-und-Emoji-Tabelle
an **einer** Stelle liegt (`$categories` im SCSS + ein `CATEGORY_META`-Konstantenobjekt
in TypeScript), weil sie in Badge, Avatar, Chip, Legende und Chart gleichzeitig
gebraucht wird.

Angular Material scheidet hier deutlicher aus als bei A: Material Design ist im
Kern eine nüchterne, systemische Sprache. Diesen verspielten Look darauf
aufzusetzen hiesse, gegen das Framework zu arbeiten.

**Trade-offs:**

| | |
| --- | --- |
| ✅ Wiedererkennbarkeit | Am höchsten von allen drei — man erkennt die App auf einem Screenshot |
| ✅ Aufwand | Gering, solange die Kategorie-Tabelle zentral bleibt |
| ✅ Emotionale Bindung | Adressiert Laras Abbruch-Risiko direkt |
| ⚠️ Emoji-Rendering | Sehen auf iOS, Android und Windows **unterschiedlich** aus, teils deutlich. Wer volle Kontrolle will, braucht später doch ein SVG-Icon-Set — dann ist der Aufwand nachgeholt, nicht gespart |
| ⚠️ Screenreader | Emoji müssen konsequent `aria-hidden="true"` sein, sonst liest der Screenreader «Einkaufswagen Lebensmittel». Im Prototyp umgesetzt, in der Umsetzung leicht zu vergessen |
| ⚠️ Listendichte | Karten-Zeilen kosten deutlich mehr vertikalen Platz als die Listen in A und C. Bei 60 Buchungen im Monat wird viel gescrollt |
| ⚠️ Tonalität | «Kein Drama» kann bei jemandem, der real im Minus ist, auch als verharmlosend ankommen. Der Ton braucht ein Review durch das Team, nicht nur das Design |
| ⚠️ Zugänglichkeit | Wie bei A selbst zu bauen — Fokus-Falle im Sheet, Escape-Handling |

## Wann diese Variante Sinn macht

Wenn das Team **Churn als grösstes Risiko** ernst nimmt (Risiko #1 im Projekt)
und darauf setzt, dass Lara wiederkommt, weil die App sich gut anfühlt — nicht
nur, weil sie korrekt rechnet. Sie hat den höchsten Wiedererkennungswert und die
klarste Haltung.

Sie ist die **falsche** Wahl, wenn das Team Marcs Datenschutz-Skepsis für die
grössere Hürde hält: Wer einer Web-App Kontodaten nur zögerlich anvertraut, wird
von Emoji und Verläufen eher nicht überzeugt. Dafür ist Variante C gebaut.
