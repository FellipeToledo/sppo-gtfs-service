package com.fajtech.sppogtfs.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineCodeTest {

    @Test
    void trimsAndUppercases() {
        LineCode code = LineCode.of("  sv789 ");
        assertThat(code.value()).isEqualTo("SV789");
    }

    @Test
    void numericKeyStripsLeadingZeros() {
        assertThat(LineCode.of("0100").numericKey()).isEqualTo("100");
        assertThat(LineCode.of("100").numericKey()).isEqualTo("100");
        assertThat(LineCode.of("000").numericKey()).isEqualTo("0");
    }

    @Test
    void relaxedKeysMatchAcrossLeadingZeros() {
        assertThat(LineCode.of("0100").numericKey())
                .isEqualTo(LineCode.of("100").numericKey());
    }

    @Test
    void nonNumericHasNoNumericKey() {
        LineCode code = LineCode.of("LECD05");
        assertThat(code.isNumeric()).isFalse();
        assertThat(code.numericKey()).isNull();
    }

    @Test
    void blankIsRejected() {
        assertThatThrownBy(() -> LineCode.of("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
