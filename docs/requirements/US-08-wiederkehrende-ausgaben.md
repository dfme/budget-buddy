# US-08: Wiederkehrende Ausgaben (Abos) erkennen

**Persona:** Marc  
**MoSCoW:** Should  
**Story:** Als Marc möchte ich wiederkehrende Ausgaben (Abos, Ratenzahlungen) auf einen Blick sehen, damit ich versteckte Kosten erkennen kann.

---

## Acceptance Criteria

**Given** importierte Transaktionen, **When** ich die Abo-Übersicht öffne, **Then** werden alle Transaktionen gruppiert angezeigt, die vom selben Empfänger in mindestens 2 aufeinanderfolgenden Monaten mit demselben Betrag (Toleranz ±2%) verbucht wurden.

**Given** eine Transaktion zum ersten Mal als wiederkehrend erkannt wird, **When** sie in der Abo-Übersicht erscheint, **Then** wird sie mit einem "Neu"-Label markiert und ich erhalte eine In-App-Benachrichtigung.

**Given** eine Transaktion fälschlicherweise als wiederkehrend markiert ist, **When** ich auf "Kein Abo" klicke, **Then** wird sie aus der Abo-Übersicht entfernt und künftige Transaktionen desselben Empfängers werden nicht mehr automatisch als wiederkehrend erkannt.
