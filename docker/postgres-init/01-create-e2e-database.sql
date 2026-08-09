-- Legt die separate Datenbank für die E2E-Suite an (DB-05, ADR-12).
--
-- Warum getrennt von `budgetbuddy`: Die E2E-Suite leert vor jedem Lauf die Nutzertabellen
-- (e2e/support/database.ts). Liefe sie gegen die Dev-Datenbank, wäre jeder Testlauf ein
-- stiller Datenverlust für den Entwickler, der gerade etwas von Hand angelegt hat.
--
-- Skripte in /docker-entrypoint-initdb.d laufen nur beim ERSTEN Start eines leeren Volumes.
-- Existiert das Volume schon, ohne diese Datenbank zu enthalten, scheitert der E2E-Start mit
-- »database "budgetbuddy_e2e" does not exist« — dann einmalig `docker compose down -v`.
CREATE DATABASE budgetbuddy_e2e;
