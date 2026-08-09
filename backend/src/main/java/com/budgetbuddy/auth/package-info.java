/**
 * Auth-Modul: AuthController, AuthService, User-Entity, JWT-Konfiguration.
 *
 * <p>Stateless JWT (HS256) als httpOnly Cookie, bcrypt-Passwörter (ADR-7).
 *
 * <p>Nach aussen gibt das Modul genau einen Port heraus: {@link com.budgetbuddy.auth.UserIncomePort}
 * liefert dem {@code budget}-Modul das monatliche Einkommen für die Fixkosten-Warnung aus US-03.
 * Alles andere — {@code User}, {@code UserRepository}, Passwort-Hashes — bleibt modulintern.
 */
package com.budgetbuddy.auth;
