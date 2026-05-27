# US-05: Transaktionen in Kategorien sehen

**Persona:** Lara  
**MoSCoW:** Must  
**Story:** Als Lara möchte ich meine Transaktionen in Kategorien sehen, damit ich weiss, wofür ich wie viel ausgebe.

---

## Acceptance Criteria

**Given** importierte Transaktionen, **When** ich die Übersicht öffne, **Then** ist jede Transaktion genau einer Kategorie aus einer fest definierten Taxonomie zugeordnet (Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit, Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges) — Default bei Unsicherheit: "Sonstiges".

**Given** ein definiertes Test-Set von mindestens 200 manuell gelabelten Transaktionen, **When** der Auto-Kategorisierer evaluiert wird, **Then** sind mindestens 80% der Transaktionen korrekt zugeordnet.

**Given** eine Transaktion, **When** ich die Kategorie manuell ändere, **Then** wird die Änderung persistiert und die Kategorie-Totals der Übersicht aktualisieren sich innerhalb von 1 Sekunde — Summen pro Kategorie stimmen auf den Rappen mit der Summe der zugeordneten Transaktionen überein.

**Given** ich habe Transaktionen desselben Empfängers (z.B. "Migros") manuell umkategorisiert, **When** zukünftige Transaktionen desselben Empfängers importiert werden, **Then** übernimmt das System die zuletzt vom User gewählte Kategorie für diesen Empfänger.

**Given** die Übersichtsseite, **When** sie geladen wird, **Then** zeigt sie pro Kategorie: Summe in CHF, Anzahl Transaktionen und prozentualer Anteil am Gesamtmonat.

**Given** eine Transaktion importiert wird, **When** die Kategorisierung stattfindet, **Then** wird zunächst die statische Lookup-Tabelle (Händlername → Kategorie) geprüft — ausschliesslich für Transaktionen ohne Treffer wird die Claude API aufgerufen; bekannte Händler werden nie unnötig an die API gesendet.
