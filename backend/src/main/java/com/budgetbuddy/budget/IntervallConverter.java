package com.budgetbuddy.budget;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Bildet {@link Intervall} auf die {@code VARCHAR}-Spalte {@code fixed_costs.intervall} ab.
 *
 * <p>Bewusst ohne {@code autoApply}: Der Converter wird an {@link FixedCost#getIntervall()}
 * explizit per {@code @Convert} gesetzt, damit die Zuordnung am Feld sichtbar bleibt.
 *
 * <p>Gegenüber {@code @Enumerated(EnumType.STRING)} bleibt der DB-Wert die lesbare
 * Kleinschreibung ({@code monatlich}) statt des Konstantennamens ({@code MONATLICH}), und ein
 * unbekannter Wert schlägt beim Lesen über {@link Intervall#fromLabel(String)} laut fehl.
 */
@Converter
public class IntervallConverter implements AttributeConverter<Intervall, String> {

    @Override
    public String convertToDatabaseColumn(Intervall intervall) {
        return intervall == null ? null : intervall.getLabel();
    }

    @Override
    public Intervall convertToEntityAttribute(String label) {
        return label == null ? null : Intervall.fromLabel(label);
    }
}
