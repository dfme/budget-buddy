-- DB-04: category_lookup-Tabelle (Hybrid-Kategorisierung, Schritt 1 — siehe ADR-6).
-- empfaenger_pattern ist PK mit COLLATE NOCASE, damit der Lookup case-insensitive ist:
-- WHERE empfaenger_pattern = 'migros' matcht den Seed 'MIGROS'.
-- category enthält ausschliesslich Werte aus der fixen Kategorienliste (siehe CLAUDE.md).
CREATE TABLE category_lookup (
    empfaenger_pattern VARCHAR COLLATE NOCASE PRIMARY KEY,
    category           VARCHAR NOT NULL
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
