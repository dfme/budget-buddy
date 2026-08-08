package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit-Test des {@link Intervall}-Enums: Label-Schreibweise (US-03) und Round-Trip über
 * {@link Intervall#fromLabel(String)}, den der {@link IntervallConverter} beim Lesen aus der DB
 * verwendet.
 */
class IntervallTest {

    @Test
    void labelsUseTheAsciiSpellingPersistedInTheDatabase() {
        assertThat(Intervall.MONATLICH.getLabel()).isEqualTo("monatlich");
        assertThat(Intervall.QUARTALSWEISE.getLabel()).isEqualTo("quartalsweise");
        assertThat(Intervall.JAEHRLICH.getLabel()).isEqualTo("jaehrlich");
    }

    @ParameterizedTest
    @EnumSource(Intervall.class)
    void fromLabelIsTheInverseOfGetLabel(Intervall intervall) {
        assertThat(Intervall.fromLabel(intervall.getLabel())).isEqualTo(intervall);
    }

    @Test
    void unknownLabelFailsLoudlyInsteadOfReturningNull() {
        assertThatThrownBy(() -> Intervall.fromLabel("woechentlich"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("woechentlich");
    }

    @Test
    void fromLabelIsCaseSensitiveAndRejectsTheEnumConstantName() {
        // Gegenprobe zu @Enumerated(STRING): 'MONATLICH' ist kein gültiger DB-Wert.
        assertThatThrownBy(() -> Intervall.fromLabel("MONATLICH"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
