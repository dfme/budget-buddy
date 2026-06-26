-- DB-01: users-Tabelle
-- monthly_income als DECIMAL(10,2) (NUMERIC-Affinität) — niemals FLOAT/REAL (ADR-9).
CREATE TABLE users (
    id                   INTEGER       PRIMARY KEY AUTOINCREMENT,
    email                TEXT          NOT NULL UNIQUE,
    password_hash        TEXT          NOT NULL,
    monthly_income       DECIMAL(10,2),
    onboarding_completed BOOLEAN       NOT NULL DEFAULT 0
);
