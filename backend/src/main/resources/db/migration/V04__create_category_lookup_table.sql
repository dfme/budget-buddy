-- DB-04: category_lookup-Tabelle (Hybrid-Kategorisierung, Schritt 1 — siehe ADR-6).
-- Postgres-Syntax seit DB-05 / ADR-12.
--
-- empfaenger_pattern ist PK. Unter SQLite trug die Spalte COLLATE NOCASE, damit
-- 'migros' und 'MIGROS' denselben Eintrag treffen; Postgres kennt diese Collation nicht.
-- Ersatz ohne DB-Extension: Patterns werden ausschliesslich in Grossschreibung gespeichert
-- (CategoryLearningService normalisiert vor dem Save), und das Matching in
-- CategoryLookupRepository.findMatching ist über upper(...) auf beiden Seiten ohnehin
-- collation-unabhängig. Die Seeds unten sind deshalb durchgängig upper-case.
-- category enthält ausschliesslich Werte aus der fixen Kategorienliste (siehe CLAUDE.md).
CREATE TABLE category_lookup (
    empfaenger_pattern TEXT PRIMARY KEY,
    category           TEXT NOT NULL
);

-- Seed-Daten für bekannte Schweizer Händler (~70-80% der Transaktionen, ADR-6).
INSERT INTO category_lookup (empfaenger_pattern, category) VALUES
    ('MIGROS',      'Lebensmittel'),
    ('COOP',        'Lebensmittel'),
    ('DENNER',      'Lebensmittel'),
    ('ALDI',        'Lebensmittel'),
    ('LIDL',        'Lebensmittel'),
    ('SBB',         'Transport'),
    ('SWISS PASS',  'Transport'),
    ('SWISSCOM',    'Telekom'),
    ('SUNRISE',     'Telekom'),
    ('SALT',        'Telekom'),
    ('CSS',         'Versicherung'),
    ('HELSANA',     'Versicherung'),
    ('DIGITEC',     'Shopping'),
    ('GALAXUS',     'Shopping'),
    ('ZALANDO',     'Shopping'),
    ('NETFLIX',     'Freizeit'),
    ('SPOTIFY',     'Freizeit'),
    ('MCDONALD''S', 'Restaurant');
