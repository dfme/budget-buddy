/**
 * Geteilte CHF-Betragsregeln — das einzige Package unterhalb von {@code com.budgetbuddy}, das
 * keine Domäne ist (BE-FC-04).
 *
 * <p>Die Package-Struktur folgt sonst der Domäne und nicht der Schicht (CLAUDE.md,
 * {@code docs/CONVENTIONS.md}): {@code auth}, {@code budget}, {@code categorization},
 * {@code report}, {@code transaction}. Dieses Package ist die begründete Ausnahme und bleibt
 * deshalb eng begrenzt.
 *
 * <p><strong>Was hier hinein darf:</strong> zustandslose Regeln über CHF-Beträge, die mehr als
 * ein Modul braucht und die keine Domänenkenntnis haben — die Rappen-Skala, die
 * Kapazitätsgrenze der {@code DECIMAL(10,2)}-Spalten, die Prüfung eines client-gelieferten
 * Betrags.
 *
 * <p><strong>Was hier nicht hinein darf:</strong> Repositories, Entities, Spring-Beans,
 * Fehlermeldungstexte und Exception-Typen. Meldung und Exception bleiben modul-lokal, weil die
 * Texte feldspezifisch sind (US-03, #148) und ein geteilter Exception-Typ der
 * modulübergreifende Zugriff wäre, den CLAUDE.md untersagt. Und nichts, was nur ein Modul
 * braucht — sonst wird aus dem Package über die Zeit eine Resterampe.
 */
package com.budgetbuddy.money;
