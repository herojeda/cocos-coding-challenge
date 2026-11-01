package com.cocoscap.challenge.domain.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstrumentSearchTextTest {

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(new InstrumentSearchText("  moli  ").value()).isEqualTo("moli");
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> new InstrumentSearchText("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstrumentSearchText(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstrumentSearchText(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
