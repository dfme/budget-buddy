/**
 * Categorization-Modul: CategorizationService, LookupTable, CategorizationPort.
 *
 * <p>Hybrid: Lookup-Tabelle zuerst, Claude API nur für unbekannte Transaktionen
 * (ADR-6). Claude immer hinter {@code CategorizationPort}, Fallback {@code "Sonstiges"}.
 */
package com.budgetbuddy.categorization;
