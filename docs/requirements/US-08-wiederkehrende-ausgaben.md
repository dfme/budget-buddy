# US-08: Wiederkehrende Ausgaben (Abos) erkennen

**Persona:** Marc  
**MoSCoW:** Should  
**Story:** Als Marc möchte ich wiederkehrende Ausgaben (Abos, Ratenzahlungen) auf einen Blick sehen, damit ich versteckte Kosten erkennen kann.

---

## Acceptance Criteria

> Die Abo-Übersicht ist seit FE-FC-05 ([#338](https://github.com/dfme/budget-buddy/issues/338)) der Abschnitt «Erkannte Abos» auf der Seite «Budget» (`/budget`, seit FE-FC-09 [#360](https://github.com/dfme/budget-buddy/issues/360)), keine eigene Seite mehr; `/abos`, `/fixkosten` (Name der Seite bis FE-FC-07, [#355](https://github.com/dfme/budget-buddy/issues/355)) und `/ausgaben` (Name der Seite bis FE-FC-09) leiten dorthin um. Erkannte, nicht verneinte Abos fliessen seither wie Fixkosten in den Safe-to-Spend ein, solange sie abgebucht werden ([US-06](US-06-safe-to-spend.md)).
>
> Seit FE-FC-07 zeigt die Seite über beiden Abschnitten das Total der monatlichen fixen Ausgaben: Fixkosten-Monatssumme plus die Beträge der erkannten, nicht verneinten Abos — eine einfache Addition. Der Safe-to-Spend rechnet anders (ein Abo mit betragsgleicher Fixkosten-Position zählt dort nicht doppelt, [ADR-13](../adr/ADR-13-fixkosten-transaktions-zuordnung.md)); das Total kann deshalb über der Safe-to-Spend-Minderung liegen.

**Given** importierte Transaktionen, **When** ich die Abo-Übersicht öffne, **Then** werden alle Transaktionen gruppiert angezeigt, die vom selben Empfänger in mindestens 2 aufeinanderfolgenden Monaten mit demselben Betrag (Toleranz ±2%) verbucht wurden.

**Given** eine Transaktion zum ersten Mal als wiederkehrend erkannt wird, **When** sie in der Abo-Übersicht erscheint, **Then** wird sie mit einem "Neu"-Label markiert und ich erhalte eine In-App-Benachrichtigung — **eine pro Import**, die alle in diesem Import neu erkannten Abos zusammenfasst («3 neue Abos erkannt: …»), nicht eine pro Abo (FE-NOTIF-04, #336). Das "Neu"-Label hängt am Gelesen-Zustand dieser Benachrichtigung: es bleibt, bis ich sie in der Glocke anklicke, bis ich «Alle als gelesen markieren» wähle, oder bis ich alle Abos des Imports als "Kein Abo" markiert habe — und verschwindet dann für alle Abos dieses Imports zusammen.

**Given** eine Transaktion fälschlicherweise als wiederkehrend markiert ist, **When** ich auf "Kein Abo" klicke, **Then** wird sie aus der Liste der Abos entfernt — sie bleibt auf der Seite in einem eigenen Abschnitt «Kein Abo» sichtbar, damit der Klick auf ihre Benachrichtigung nicht ins Leere führt (FE-NOTIF-03, #333) — und künftige Transaktionen desselben Empfängers werden nicht mehr automatisch als wiederkehrend erkannt.
