# US-06: Wöchentlicher Safe-to-Spend-Betrag

**Persona:** Lara  
**MoSCoW:** Must  
**Story:** Als Lara möchte ich einen wöchentlichen "Safe-to-Spend"-Betrag sehen, damit ich ohne schlechtes Gewissen Geld ausgeben kann.

---

## Acceptance Criteria

**Given** importierte Transaktionen und bekannte Fixkosten, **When** ich das Dashboard öffne, **Then** wird der Safe-to-Spend-Betrag nach folgender Formel berechnet und angezeigt:

```
(Einkommen − Fixkosten − bisherige Ausgaben im laufenden Monat) ÷ verbleibende Wochen im Monat
```

Bei einem Einkommen von 2000 CHF, Fixkosten von 800 CHF und bisherigen Ausgaben von 400 CHF in Woche 1 zeigt das Dashboard 200 CHF/Woche für die verbleibenden 3 Wochen.

**Given** der Betrag ist negativ, **When** ich das Dashboard öffne, **Then** wird ein rot hinterlegtes Banner mit dem Text "Achtung: Dein Budget für diese Woche ist überzogen" am oberen Rand des Dashboards angezeigt.

**Given** kein Monatseinkommen erfasst ist, **When** ich das Dashboard öffne, **Then** wird statt des Safe-to-Spend-Betrags der Hinweis "Bitte erfasse dein Monatseinkommen in den Einstellungen" angezeigt — keine Division wird ausgeführt.

**Given** weniger als 7 Tage im laufenden Monat verbleiben, **When** der Safe-to-Spend berechnet wird, **Then** wird als Divisor mindestens 1 (volle Woche) verwendet, um Division durch 0 oder unrealistische Beträge zu vermeiden — der Wert wird mit dem Hinweis "Letzte Woche des Monats" angezeigt.

**Given** importierte Transaktionen mindestens 2 Monate umfassen und eine regelmässige Gutschrift desselben Absenders mit gleichem Betrag (±5%) erkennbar ist, **When** das Dashboard geladen wird und kein Einkommen manuell erfasst ist, **Then** schlägt das System diesen Betrag automatisch als Monatseinkommen vor mit dem Hinweis "Regelmässige Gutschrift von X CHF erkannt — als Monatseinkommen übernehmen?" — eine manuelle Eingabe in den Einstellungen bleibt jederzeit möglich und überschreibt die automatische Schätzung.
