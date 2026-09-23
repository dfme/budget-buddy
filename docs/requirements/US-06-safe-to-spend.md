# US-06: Wöchentlicher Safe-to-Spend-Betrag

**Persona:** Lara  
**MoSCoW:** Must  
**Story:** Als Lara möchte ich einen wöchentlichen "Safe-to-Spend"-Betrag sehen, damit ich ohne schlechtes Gewissen Geld ausgeben kann.

---

## Acceptance Criteria

**Given** importierte Transaktionen und bekannte Fixkosten, **When** ich das Dashboard öffne, **Then** wird der Safe-to-Spend-Betrag nach folgender Formel berechnet und angezeigt:

```
(Einkommen − Fixkosten − bisherige variable Ausgaben im laufenden Monat) ÷ verbleibende Wochen im Monat
```

Bei einem Einkommen von 2000 CHF, Fixkosten von 800 CHF und bisherigen variablen Ausgaben von 400 CHF in Woche 1 zeigt das Dashboard 200 CHF/Woche für die verbleibenden 3 Wochen.

**Given** eine Fixkosten-Position wird per Dauerauftrag bezahlt und erscheint dadurch zusätzlich als Belastung unter den importierten Transaktionen, **When** der Safe-to-Spend berechnet wird, **Then** mindert sie den Betrag **genau einmal**: je erfasster Fixkosten-Position wird höchstens eine betragsgleiche Belastung des Monats aus den variablen Ausgaben ausgenommen. In der Kategorie-Übersicht bleibt diese Belastung sichtbar — das Konto wurde belastet.

Bei einer Miete von 1200 CHF als Fixkosten-Position und einer Belastung über 1200 CHF im selben Monat gehen 1200 CHF in die Rechnung ein, nicht 2400 CHF. Verglichen wird gegen den erfassten Betrag der Position (die tatsächliche Abbuchung), nicht gegen ihren normalisierten Monatsbetrag: eine jährliche Versicherung über 1200 CHF wird im Zahlungsmonat vollständig ausgenommen, während in jedem der zwölf Monate 100 CHF als Fixkosten zählen.

> Zuordnungsmechanismus, Grenzen und verworfene Alternativen: [ADR-13](../adr/ADR-13-fixkosten-transaktions-zuordnung.md) (BE-STS-04, [#154](https://github.com/dfme/budget-buddy/issues/154)).

**Given** ein erkanntes, nicht als «Kein Abo» markiertes Abo ([US-08](US-08-wiederkehrende-ausgaben.md)), das im laufenden Monat oder den zwei Monaten davor abgebucht wurde, **When** der Safe-to-Spend berechnet wird, **Then** wirkt es wie eine Fixkosten-Position: sein Betrag wird von Monatsbeginn an abgezogen, und höchstens eine Belastung des Monats innerhalb der Erkennungstoleranz (±2 %) wird aus den variablen Ausgaben ausgenommen — in diesem Fall zählt der abgebuchte Betrag. Das Abo mindert den Betrag **genau einmal**, auch bevor seine Abbuchung im Auszug steht. Ein Abo, das zusätzlich als Fixkosten-Position mit einem Betrag innerhalb derselben Toleranz erfasst ist, gilt als bereits erfasst und zählt nicht ein zweites Mal. Ein Abo ohne Abbuchung in diesem Fenster gilt als beendet und zählt nicht.

Bei einem erkannten Abo NETFLIX über 17.90 CHF, das im laufenden Monat noch nicht abgebucht wurde, gehen 17.90 CHF in die Rechnung ein; nach der Abbuchung sind es weiterhin 17.90 CHF, nicht 35.80 CHF. Ein Abo SALT, erkannt mit 59.00 CHF und diesen Monat mit 59.90 CHF abgebucht, zählt 59.90 CHF, nicht 118.90 CHF. Eine Fixkosten-Position «Handy» über 59.00 CHF neben einem erkannten Abo SWISSCOM über 59.00 CHF zählt 59.00 CHF, nicht 118.00 CHF.

> Erweiterung von ADR-13 auf erkannte Abos und die Deduplizierungsregel: [ADR-13, Nachtrag FE-FC-05](../adr/ADR-13-fixkosten-transaktions-zuordnung.md#nachtrag-fe-fc-05-338-erkannte-abos-wirken-wie-fixkosten-positionen) ([#338](https://github.com/dfme/budget-buddy/issues/338)).

**Given** der Betrag ist negativ, **When** ich das Dashboard öffne, **Then** wird ein rot hinterlegtes Banner mit dem Text "Achtung: Dein Budget für diese Woche ist überzogen" am oberen Rand des Dashboards angezeigt.

**Given** kein Monatseinkommen erfasst ist, **When** ich das Dashboard öffne, **Then** wird statt des Safe-to-Spend-Betrags der Hinweis "Bitte erfasse dein Monatseinkommen auf der Budget-Seite" angezeigt — keine Division wird ausgeführt.

**Given** weniger als 7 Tage im laufenden Monat verbleiben, **When** der Safe-to-Spend berechnet wird, **Then** wird als Divisor mindestens 1 (volle Woche) verwendet, um Division durch 0 oder unrealistische Beträge zu vermeiden — der Wert wird mit dem Hinweis "Letzte Woche des Monats" angezeigt.

**Given** importierte Transaktionen mindestens 2 Monate umfassen und eine regelmässige Gutschrift desselben Absenders mit gleichem Betrag (±5%) erkennbar ist, **When** das Dashboard geladen wird und kein Einkommen manuell erfasst ist, **Then** schlägt das System diesen Betrag automatisch als Monatseinkommen vor mit dem Hinweis "Regelmässige Gutschrift von X CHF erkannt — als Monatseinkommen übernehmen?" — eine manuelle Eingabe auf der Budget-Seite bleibt jederzeit möglich und überschreibt die automatische Schätzung.
