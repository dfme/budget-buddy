# US-10: Monatsvergleich

**Persona:** Lara  
**MoSCoW:** Could  
**Story:** Als Lara möchte ich meine Ausgaben des aktuellen Monats mit dem Vormonat vergleichen, damit ich Trends in meinem Verhalten erkenne.

---

## Acceptance Criteria

**Given** Daten aus mindestens zwei vollständigen Monaten, **When** ich die Vergleichsansicht öffne, **Then** sehe ich pro Kategorie die Werte `aktueller Monat`, `Vormonat`, `Differenz in CHF` und `Differenz in Prozent` — Summen exakt auf den Rappen (z.B. Restaurant: 320 CHF vs. 250 CHF → +70 CHF / +28%).

**Given** weniger als zwei vollständige Monate Daten, **When** ich die Ansicht öffne, **Then** wird stattdessen der Hinweis "Vergleich ab zwei vollständigen Monaten verfügbar" angezeigt.

**Given** eine Kategorie mit Steigerung > 20% UND aktuellem Betrag ≥ 50 CHF (Mindestschwelle gegen Rauschen), **When** der Vergleich geladen wird, **Then** wird diese Kategorie visuell hervorgehoben (z.B. roter Balken oder Warnsymbol).

**Given** eine Kategorie hatte im Vormonat 0 CHF, **When** der Vergleich gerechnet wird, **Then** wird die Differenz in Prozent als "Neu" gekennzeichnet (keine Division durch Null, kein "∞%").
