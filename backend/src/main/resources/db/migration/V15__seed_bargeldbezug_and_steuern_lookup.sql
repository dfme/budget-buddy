-- BE-CAT-17: Lookup-Seeds für die Kategorien Bargeldbezug und Steuern.
--
-- BE-CAT-10 hat beide Kategorien eingeführt, aber keine Seeds angelegt — Stufe 1 der
-- Hybrid-Kategorisierung (ADR-6) griff für sie deshalb gar nicht, und jeder Bancomat-Bezug
-- ging an Claude oder fiel auf 'Sonstiges'.
--
-- Reine Seed-Erweiterung der globalen, kuratierten Tabelle aus V04; kein Schema-Change.
-- Gelerntes gehört nach ADR-15 in user_category_lookup (V12) und nicht hierher.
--
-- Patterns durchgehend upper-case, wie V04 es begründet: Postgres kennt COLLATE NOCASE nicht,
-- die Gross-/Kleinschreib-Unabhängigkeit liegt im upper(...) auf beiden Seiten von
-- CategoryLookupRepository.findMatching.
--
-- Drei Auslassungen, jede bewusst:
--
--  * 'ATM' stand im Issue, bleibt aber draussen. Das Matching ist seit BE-CAT-14 reine
--    Substring-Suche über locate(...), ohne Wortgrenzen — drei Zeichen treffen mitten im
--    Wort, ein Lokal namens ATMOSPHERE genügt. Eine falsche Kategorie aus Stufe 1 ist
--    schlechter als gar keine: Claude sieht den Text danach nie wieder, weil der Lookup ihn
--    vorher abfängt. Genau die Abwägung, aus der BE-CAT-14 LIKE durch locate ersetzt hat.
--
--  * 'STEUERN' als Pattern träfe MEHRWERTSTEUER, VERRECHNUNGSSTEUER und STEUERBERATUNG.
--
--  * 'BARBEZUG' ist umgekehrt ein Buchungstyp in einer Spalte, die empfaenger_pattern heisst.
--    Hier zulässig, weil der Zahlungstyp die Kategorie *ist* — anders als LASTSCHRIFT oder
--    TWINT, die über alle Kategorien hinweg vorkommen.
--
-- ON CONFLICT, weil diese Tabelle nicht nur Seeds enthält. Bis V12 hat der Lerneffekt rohe,
-- upper-gecaste Buchungstexte in genau diese globale Tabelle geschrieben; ADR-15 hält fest, dass
-- diese verwaisten Zeilen auf Produktion bewusst stehen bleiben. empfaenger_pattern ist PK (V04):
-- Trägt eine davon einen der sieben Keys — denkbar bei einem Bezug, dessen Detailzeilen alle als
-- Rauschen wegfielen, sodass der gelernte Volltext nur noch aus dem Buchungstyp bestand — scheitert
-- diese Migration mit einer PK-Verletzung und der Deploy kommt nicht hoch. Der Zustand jener Zeilen
-- ist von hier aus nicht prüfbar, also muss die Migration gegen den dokumentierten Fall robust
-- sein und nicht gegen den vermuteten.
--
-- DO UPDATE statt DO NOTHING: Der kuratierte Seed soll einen versprengten Lerneintrag überschreiben
-- — das ist die Rangfolge, die ADR-15 für die globale Tabelle vorsieht. DO NOTHING liesse eine alte
-- Claude-Kategorie für dasselbe Pattern stehen, und zwar unsichtbar.
INSERT INTO category_lookup (empfaenger_pattern, category) VALUES
    ('BANCOMAT',         'Bargeldbezug'),
    ('POSTOMAT',         'Bargeldbezug'),
    ('GELDAUTOMAT',      'Bargeldbezug'),
    ('BARGELDBEZUG',     'Bargeldbezug'),
    ('BARBEZUG',         'Bargeldbezug'),
    ('STEUERVERWALTUNG', 'Steuern'),
    ('STEUERAMT',        'Steuern')
ON CONFLICT (empfaenger_pattern) DO UPDATE SET category = EXCLUDED.category;
