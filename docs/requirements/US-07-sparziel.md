# US-07: Sparziel definieren

**Persona:** Marc  
**MoSCoW:** Could  
**Story:** Als Marc möchte ich ein Sparziel definieren können, damit ich meinen Fortschritt in Richtung Notgroschen verfolgen kann.

---

## Acceptance Criteria

**Given** ich lege ein Sparziel mit `Betrag in CHF > 0` und `Zieldatum in der Zukunft` an, **When** ich speichere, **Then** wird das Ziel persistiert und ist im Dashboard sichtbar — andernfalls wird das Speichern mit einer Validierungsmeldung abgelehnt.

**Given** ein Sparziel von 1.000 CHF und bisher 250 CHF gespart, **When** ich das Dashboard öffne, **Then** sehe ich exakt "250 CHF / 1.000 CHF (25%)" — "bisher gespart" = Summe aller Transaktionen der Kategorie "Sparen" seit Erstellung des Ziels — verifizierbar mit definierten Test-Szenarien (0%, 50%, 100%, >100%).

**Given** das Zieldatum ist überschritten und das Ziel noch nicht erreicht, **When** ich das Dashboard öffne, **Then** wird ein Banner "Ziel verpasst" mit dem fehlenden Betrag angezeigt.

**Given** ich habe in einem Monat nichts gespart, **When** der Monat endet, **Then** erhalte ich einen Hinweis, der mindestens eine Ausgabenkategorie und einen CHF-Betrag nennt (z.B. "Du hast diesen Monat 90 CHF für Takeaway ausgegeben — 2x weniger bestellen spart dir 45 CHF").
