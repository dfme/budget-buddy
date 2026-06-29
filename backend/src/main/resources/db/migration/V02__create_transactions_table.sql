-- DB-02: transactions-Tabelle
-- betrag als DECIMAL(10,2) (NUMERIC-Affinität) — niemals FLOAT/REAL (ADR-9).
-- pdf_sha256 erlaubt NULL; category bleibt NULL bis zur Kategorisierung (US-05).
CREATE TABLE transactions (
    id            INTEGER       PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER       NOT NULL,
    buchungsdatum DATE          NOT NULL,
    text          VARCHAR       NOT NULL,
    betrag        DECIMAL(10,2) NOT NULL,
    is_income     BOOLEAN       NOT NULL DEFAULT 0,
    category      VARCHAR,
    pdf_sha256    VARCHAR,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
