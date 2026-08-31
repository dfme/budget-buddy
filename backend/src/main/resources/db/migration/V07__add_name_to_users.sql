-- BE-AUTH-05: Vor- und Nachname im User-Model (#114).
--
-- Nullable, kein Backfill: Bestandsuser haben keinen Namen und der Konto-Block der App-Shell
-- fällt für sie weiterhin auf die aus der E-Mail abgeleiteten Initialen zurück
-- (Shell.initialsFromEmail). Registrierung fragt Name bewusst nur optional ab (Churn-Risiko #1).
ALTER TABLE users
    ADD COLUMN first_name TEXT,
    ADD COLUMN last_name TEXT;
