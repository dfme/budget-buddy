-- DB-03: fixed_costs-Tabelle
-- betrag als DECIMAL(10,2) (NUMERIC-Affinität) — niemals FLOAT/REAL (ADR-9).
-- intervall: Zahlungsintervall der Fixkosten-Position (z. B. monatlich, jaehrlich).
CREATE TABLE fixed_costs (
    id          INTEGER       PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER       NOT NULL,
    bezeichnung VARCHAR       NOT NULL,
    betrag      DECIMAL(10,2) NOT NULL,
    intervall   VARCHAR       NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
