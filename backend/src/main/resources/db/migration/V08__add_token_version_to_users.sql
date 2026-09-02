-- BE-AUTH-11: Token-Version für Session-Invalidierung nach Passwort-Änderung (#201).
--
-- NOT NULL DEFAULT 0: jeder bestehende User startet bei Version 0, identisch mit frisch
-- registrierten Usern. Der JwtCookieAuthenticationFilter vergleicht diesen Wert gegen den
-- gleichnamigen Claim im JWT; eine Passwort-Änderung erhöht ihn und macht damit alle zuvor
-- ausgestellten Tokens ungültig (ADR-7-Nachtrag).
ALTER TABLE users
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
