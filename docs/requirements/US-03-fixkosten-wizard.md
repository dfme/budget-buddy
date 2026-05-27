# US-03: Fixkosten erfassen (Onboarding-Wizard)

**Persona:** Lara  
**MoSCoW:** Must  
**Story:** Als Lara möchte ich beim ersten Einrichten meine monatlichen Fixkosten (z.B. Miete, Krankenkasse, Handy) erfassen können, damit der Safe-to-Spend-Betrag von Anfang an realistisch berechnet wird.

---

## Acceptance Criteria

**Given** ich starte die App zum ersten Mal, **When** das Onboarding beginnt, **Then** wird ein Fixkosten-Wizard angezeigt, der nicht übersprungen werden kann, bis mindestens ein Eintrag gespeichert oder explizit "Keine Fixkosten" bestätigt wurde.

**Given** ich erfasse einen Fixkosten-Eintrag, **When** ich speichere, **Then** muss er die Pflichtfelder `Bezeichnung` (nicht leer), `Betrag in CHF > 0` und `Intervall ∈ {monatlich, quartalsweise, jährlich}` enthalten — andernfalls wird das Speichern mit einer feldspezifischen Fehlermeldung abgelehnt.

**Given** ich habe z.B. Miete 1.200 CHF (monatlich) und Krankenkasse 300 CHF (monatlich) erfasst, **When** ich das Dashboard öffne, **Then** werden in der Safe-to-Spend-Berechnung 1.500 CHF/Monat abgezogen — verifizierbar durch Vergleich der Werte vor/nach Erfassung.

**Given** ein bestehender Fixkosten-Eintrag, **When** ich ihn ändere oder lösche, **Then** wird der nächste Safe-to-Spend-Wert sofort entsprechend neu berechnet (auf den Rappen genau).

**Given** ich habe das Onboarding einmal abgeschlossen, **When** ich die App erneut öffne, **Then** wird der Wizard nicht mehr angezeigt.

**Given** ein Fixkosten-Eintrag mit Intervall `quartalsweise` (z.B. 300 CHF), **When** die Safe-to-Spend-Formel ausgeführt wird, **Then** wird der Monatsbetrag als 300 ÷ 3 = 100 CHF/Monat berechnet; bei `jährlich` (z.B. 1200 CHF) als 1200 ÷ 12 = 100 CHF/Monat — verifizierbar mit je einem Test-Szenario pro Intervall.

**Given** die Summe aller Fixkosten (auf Monatsbasis) ≥ Monatseinkommen, **When** ich speichere oder das Dashboard öffne, **Then** wird die Warnung "Deine Fixkosten übersteigen dein Einkommen — Safe-to-Spend kann nicht berechnet werden" angezeigt und der Safe-to-Spend-Wert als "–" dargestellt.

**Given** mindestens ein Monat importierter Transaktionen vorliegt, **When** ich den Fixkosten-Wizard oder die Fixkosten-Einstellungen öffne, **Then** schlägt das System wiederkehrende Belastungen (gleicher Empfänger, gleicher Betrag ±2%, mind. 2 aufeinanderfolgende Monate) automatisch als Fixkosten-Vorschläge vor — ich kann jeden Vorschlag einzeln bestätigen oder ablehnen.
